package com.gathin.tvhome;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * The greeting on Home and the screensaver: an occasion when there is one (Ghana's public holidays,
 * the owner's birthday), else the weekday on weekends, else the time of day.
 */
final class Greeting {

    final String text;
    /** A Ghanaian national day: Home draws the flag's red, gold and green under the greeting. */
    final boolean national;

    private Greeting(String text, boolean national) {
        this.text = text;
        this.national = national;
    }

    static Greeting now(Context ctx, String name) {
        Calendar c = Calendar.getInstance();
        int month = c.get(Calendar.MONTH) + 1, day = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);

        int[] birthday = parseDayMonth(ctx.getString(R.string.owner_birthday));
        if (birthday != null && birthday[0] == day && birthday[1] == month) {
            return new Greeting(ctx.getString(R.string.occasion_birthday, name), false);
        }
        Integer national = nationalDay(c, month, day);
        if (national != null) return new Greeting(ctx.getString(national, name), true);
        Integer holiday = holiday(c, month, day);
        if (holiday != null) return new Greeting(ctx.getString(holiday, name), false);

        int weekday = c.get(Calendar.DAY_OF_WEEK);
        if ((weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY) && hour >= 5 && hour < 17) {
            int res = weekday == Calendar.SATURDAY ? R.string.happy_saturday : R.string.happy_sunday;
            return new Greeting(ctx.getString(res, name), false);
        }
        // Late at night it stays "Good evening": "Good night" is a goodbye.
        int part = hour >= 5 && hour < 12 ? R.string.good_morning
                : hour >= 12 && hour < 17 ? R.string.good_afternoon : R.string.good_evening;
        return new Greeting(ctx.getString(R.string.greeting_name, ctx.getString(part), name), false);
    }

    /** Ghana's national days (the ones the flag line marks). */
    private static Integer nationalDay(Calendar c, int month, int day) {
        if (month == 3 && day == 6) return R.string.occasion_independence;
        if (month == 7 && day == 1) return R.string.occasion_republic;
        if (month == 8 && day == 4) return R.string.occasion_founders;
        if (month == 9 && day == 21) return R.string.occasion_nkrumah;
        if (month == 1 && day == 7) return R.string.occasion_constitution;
        return null;
    }

    private static Integer holiday(Calendar c, int month, int day) {
        if (month == 1 && day == 1) return R.string.occasion_new_year;
        if (month == 5 && day == 1) return R.string.occasion_may_day;
        if (month == 12 && day == 25) return R.string.occasion_christmas;
        if (month == 12 && day == 26) return R.string.occasion_boxing_day;
        if (month == 12 && day == 31) return R.string.occasion_new_years_eve;
        // Farmers' Day: the first Friday of December.
        if (month == 12 && c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && day <= 7) return R.string.occasion_farmers;
        int year = c.get(Calendar.YEAR);
        Calendar easter = easterSunday(year);
        int doy = c.get(Calendar.DAY_OF_YEAR), e = easter.get(Calendar.DAY_OF_YEAR);
        if (doy == e - 2) return R.string.occasion_good_friday;
        if (doy == e || doy == e + 1) return R.string.occasion_easter;
        // Eid by the Umm al-Qura calendar; the day Ghana announces can differ by one.
        try {
            IslamicCalendar h = new IslamicCalendar();
            h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            h.setTimeInMillis(c.getTimeInMillis());
            int hm = h.get(IslamicCalendar.MONTH), hd = h.get(IslamicCalendar.DAY_OF_MONTH);
            if (hm == IslamicCalendar.SHAWWAL && hd == 1) return R.string.occasion_eid_fitr;
            if (hm == IslamicCalendar.DHU_AL_HIJJAH && hd == 10) return R.string.occasion_eid_adha;
        } catch (RuntimeException ignored) {
            // No ICU Islamic calendar on this build: no Eid greeting.
        }
        return null;
    }

    /** Easter Sunday (Gregorian), by the anonymous Gregorian algorithm. */
    static Calendar easterSunday(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return new GregorianCalendar(y, month - 1, day);
    }

    /** "05-05" (day-month) to {5, 5}; null when empty or malformed. */
    private static int[] parseDayMonth(String s) {
        String[] p = s.trim().split("-");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
