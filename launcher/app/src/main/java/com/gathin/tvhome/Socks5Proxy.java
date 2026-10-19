package com.gathin.tvhome;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The limiter's local SOCKS5 server, on 127.0.0.1 only. hev-socks5-tunnel turns Netflix's packets into
 * SOCKS5 requests to it; it opens the real connections (the launcher is outside the VPN, so they go
 * straight to Wi-Fi) and copies data back, with downloads paced by one shared token bucket.
 * UDP is relayed for DNS only: anything else (QUIC) is dropped so the app falls back to TCP, which is paced.
 */
final class Socks5Proxy {

    /** Download pacing shared by every connection. Rate 0 means unlimited. */
    static final class Bucket {
        private volatile long rate;   // bytes per second
        private double tokens;
        private long last = System.nanoTime();

        void setRate(long bytesPerSecond) {
            rate = bytesPerSecond;
        }

        /** Blocks until n bytes may pass. A burst of at most 100 ms keeps the app's speed estimate honest. */
        void take(int n) throws InterruptedException {
            while (true) {
                long r = rate;
                if (r <= 0) return;
                long wait;
                synchronized (this) {
                    long now = System.nanoTime();
                    tokens = Math.min(r / 10.0, tokens + (now - last) * r / 1e9);
                    last = now;
                    if (tokens >= n || tokens >= r / 10.0) {
                        tokens -= n;
                        return;
                    }
                    wait = (long) ((n - tokens) * 1e9 / r);
                }
                long ms = Math.max(1, wait / 1_000_000L);
                Thread.sleep(Math.min(ms, 200));
            }
        }
    }

    final Bucket bucket = new Bucket();
    final AtomicLong down = new AtomicLong(), up = new AtomicLong();
    private final InetAddress dns;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "limiter");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket server;
    private volatile boolean running;

    /** dns: the real resolver (the Wi-Fi network's) that relayed DNS queries go to. */
    Socks5Proxy(InetAddress dns) {
        this.dns = dns;
    }

    /** Starts listening; returns the port. */
    int start() throws IOException {
        server = new ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"));
        running = true;
        pool.execute(() -> {
            while (running) {
                try {
                    Socket s = server.accept();
                    pool.execute(() -> handle(s));
                } catch (IOException e) {
                    if (!running) return;
                }
            }
        });
        return server.getLocalPort();
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void handle(Socket client) {
        try {
            client.setTcpNoDelay(true);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            // Greeting: version 5, any methods; answer "no authentication".
            if (in.read() != 5) throw new IOException("not socks5");
            int nMethods = in.read();
            for (int i = 0; i < nMethods; i++) in.read();
            out.write(new byte[]{5, 0});
            // Request: VER CMD RSV ATYP ADDR PORT
            byte[] head = readFully(in, 4);
            int cmd = head[1];
            InetAddress addr;
            int atyp = head[3];
            if (atyp == 1) addr = InetAddress.getByAddress(readFully(in, 4));
            else if (atyp == 4) addr = InetAddress.getByAddress(readFully(in, 16));
            else if (atyp == 3) addr = InetAddress.getByName(new String(readFully(in, in.read()), "US-ASCII"));
            else throw new IOException("bad address type");
            byte[] p = readFully(in, 2);
            int port = ((p[0] & 0xff) << 8) | (p[1] & 0xff);
            if (cmd == 1) connect(client, in, out, addr, port);
            else if (cmd == 3) udpAssociate(client, in, out);
            else reply(out, 7, null, 0);
        } catch (Exception e) {
            close(client);
        }
    }

    private void connect(Socket client, InputStream cin, OutputStream cout, InetAddress addr, int port) throws IOException {
        Socket remote = new Socket();
        try {
            remote.setTcpNoDelay(true);
            remote.connect(new InetSocketAddress(addr, port), 10_000);
        } catch (IOException e) {
            reply(cout, 5, null, 0);   // connection refused
            close(client);
            return;
        }
        reply(cout, 0, InetAddress.getByName("0.0.0.0"), 0);
        InputStream rin = remote.getInputStream();
        OutputStream rout = remote.getOutputStream();
        // Upload as fast as it comes; download through the bucket.
        pool.execute(() -> pump(cin, rout, false, client, remote));
        pump(rin, cout, true, client, remote);
    }

    private void pump(InputStream from, OutputStream to, boolean download, Socket a, Socket b) {
        byte[] buf = new byte[16 * 1024];
        try {
            for (int n; (n = from.read(buf)) > 0; ) {
                if (download) {
                    bucket.take(n);
                    down.addAndGet(n);
                } else {
                    up.addAndGet(n);
                }
                to.write(buf, 0, n);
            }
        } catch (Exception ignored) {
        } finally {
            close(a);
            close(b);
        }
    }

    /**
     * SOCKS5 UDP ASSOCIATE, for DNS only. The relay socket lives as long as the control connection.
     * Datagrams: RSV RSV FRAG ATYP ADDR PORT DATA.
     */
    private void udpAssociate(Socket client, InputStream cin, OutputStream cout) throws IOException {
        DatagramSocket relay = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        DatagramSocket upstream = new DatagramSocket();
        reply(cout, 0, InetAddress.getByName("127.0.0.1"), relay.getLocalPort());
        final InetSocketAddress[] peer = {null};
        // The address and port the app sent its query to: answers are labelled as coming from there.
        final byte[][] asked = {null};
        final int[] askedPort = {53};
        pool.execute(() -> {
            byte[] buf = new byte[4096];
            try {
                upstream.setSoTimeout(0);
                while (!relay.isClosed()) {
                    DatagramPacket r = new DatagramPacket(buf, buf.length);
                    upstream.receive(r);
                    if (peer[0] == null || asked[0] == null) continue;
                    // Wrap the answer as coming from the DNS address the app asked.
                    byte[] ip = asked[0];
                    byte[] pkt = new byte[4 + ip.length + 2 + r.getLength()];
                    pkt[3] = (byte) (ip.length == 4 ? 1 : 4);
                    System.arraycopy(ip, 0, pkt, 4, ip.length);
                    pkt[4 + ip.length] = (byte) (askedPort[0] >> 8);
                    pkt[5 + ip.length] = (byte) askedPort[0];
                    System.arraycopy(buf, 0, pkt, 6 + ip.length, r.getLength());
                    relay.send(new DatagramPacket(pkt, pkt.length, peer[0]));
                }
            } catch (IOException ignored) {
            }
        });
        pool.execute(() -> {
            byte[] buf = new byte[4096];
            try {
                while (!relay.isClosed()) {
                    DatagramPacket q = new DatagramPacket(buf, buf.length);
                    relay.receive(q);
                    peer[0] = (InetSocketAddress) q.getSocketAddress();
                    int atyp = buf[3], off;
                    if (atyp == 1) off = 4 + 4;
                    else if (atyp == 4) off = 4 + 16;
                    else continue;   // hev sends addresses, not names
                    int port = ((buf[off] & 0xff) << 8) | (buf[off + 1] & 0xff);
                    if (port != 53) continue;   // not DNS: drop, the app falls back to TCP
                    asked[0] = java.util.Arrays.copyOfRange(buf, 4, off);
                    askedPort[0] = port;
                    int len = q.getLength() - off - 2;
                    // Every DNS query goes to the real resolver, whatever address the app used.
                    upstream.send(new DatagramPacket(buf, off + 2, len, dns, 53));
                }
            } catch (IOException ignored) {
            }
        });
        // Hold until the control connection closes.
        try {
            client.setSoTimeout(0);
            while (cin.read() >= 0) { }
        } catch (SocketTimeoutException ignored) {
        } finally {
            relay.close();
            upstream.close();
            close(client);
        }
    }

    private static void reply(OutputStream out, int code, InetAddress bind, int port) throws IOException {
        byte[] ip = bind != null ? bind.getAddress() : new byte[4];
        byte[] r = new byte[4 + ip.length + 2];
        r[0] = 5;
        r[1] = (byte) code;
        r[3] = (byte) (ip.length == 4 ? 1 : 4);
        System.arraycopy(ip, 0, r, 4, ip.length);
        r[4 + ip.length] = (byte) (port >> 8);
        r[5 + ip.length] = (byte) port;
        out.write(r);
        out.flush();
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        for (int off = 0; off < n; ) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("eof");
            off += r;
        }
        return b;
    }

    private static void close(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
