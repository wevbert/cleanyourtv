# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# LimiterVpn: hev-socks5-tunnel's JNI finds this class by name and registers its native methods on it.
-keep class com.gathin.tvhome.LimiterVpn {
    native <methods>;
}
