/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.util.time;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class BangabdaCalendarUtil {

    private static final String[] MONTH_NAMES = new String[]{
            "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
            "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"
    };

    private static final String[] MONTH_NAMES_TRANSLITERATED = new String[]{
            "Boishakh", "Joishtho", "Asharh", "Shrabon", "Bhadra", "Ashwin",
            "Kartik", "Agrahayan", "Poush", "Magh", "Falgun", "Chaitra"
    };

    private static final String[] DIGITS_BENGALI = new String[]{
            "০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"
    };

    public static final int FLAG_INCLUDE_DATE = 1;
    public static final int FLAG_INCLUDE_MONTH = 1 << 1;
    public static final int FLAG_INCLUDE_YEAR = 1 << 2;
    public static final int FLAG_USE_TRANSLITERATION = 1 << 3;
    public static final int FLAG_USE_BENGALI_DIGITS = 1 << 4;

    private BangabdaCalendarUtil() {}

    public static String getBangabdaDateString() {
        return getBangabdaDateString(FLAG_INCLUDE_MONTH | FLAG_INCLUDE_DATE);
    }

    public static String getBangabdaDateString(int flag) {
        return getBangabdaDateString(Calendar.getInstance(), flag);
    }

    public static String getBangabdaDateString(Calendar calendar, int flag) {
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH) + 1;
        int year = calendar.get(Calendar.YEAR);

        BangabdaDate bangabda = convertToBangabda(day, month, year);

        boolean useTransliteration = (flag & FLAG_USE_TRANSLITERATION) == FLAG_USE_TRANSLITERATION;
        boolean useBengaliDigits = (flag & FLAG_USE_BENGALI_DIGITS) == FLAG_USE_BENGALI_DIGITS;

        StringBuilder sb = new StringBuilder();

        if ((flag & FLAG_INCLUDE_DATE) == FLAG_INCLUDE_DATE) {
            sb.append(convNumber(bangabda.day, useBengaliDigits)).append(" ");
        }

        if ((flag & FLAG_INCLUDE_MONTH) == FLAG_INCLUDE_MONTH) {
            sb.append(convMonth(bangabda.month, useTransliteration)).append(" ");
        }

        if ((flag & FLAG_INCLUDE_YEAR) == FLAG_INCLUDE_YEAR) {
            sb.append(convNumber(bangabda.year, useBengaliDigits)).append(" বঙ্গাব্দ");
        }

        return sb.toString().trim();
    }

    private static String convMonth(int monthIndex, boolean useTransliteration) {
        if (monthIndex >= 0 && monthIndex < MONTH_NAMES.length) {
            return useTransliteration ? MONTH_NAMES_TRANSLITERATED[monthIndex] : MONTH_NAMES[monthIndex];
        }
        return "";
    }

    private static String convNumber(int number, boolean useBengaliDigits) {
        if (!useBengaliDigits) return String.valueOf(number);
        String numStr = String.valueOf(number);
        StringBuilder result = new StringBuilder();
        for (char c : numStr.toCharArray()) {
            if (Character.isDigit(c)) {
                result.append(DIGITS_BENGALI[c - '0']);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static BangabdaDate convertToBangabda(int day, int month, int year) {
        int bengaliYear = year - 593;
        int newYearMonth = 4;
        int newYearDay = 15;

        GregorianCalendar gregDate = new GregorianCalendar(year, month - 1, day);
        GregorianCalendar bengaliNewYear = new GregorianCalendar(year, newYearMonth - 1, newYearDay);

        if (gregDate.before(bengaliNewYear)) {
            bengaliYear -= 1;
        }

        int daysSinceBoishakh = (int) ((gregDate.getTimeInMillis() - bengaliNewYear.getTimeInMillis()) / (24 * 60 * 60 * 1000));
        if (daysSinceBoishakh < 0) {
            GregorianCalendar prevNewYear = new GregorianCalendar(year - 1, newYearMonth - 1, newYearDay);
            daysSinceBoishakh = (int) ((gregDate.getTimeInMillis() - prevNewYear.getTimeInMillis()) / (24 * 60 * 60 * 1000));
        }

        int[] monthLengths = {31,31,31,31,31,30,30,30,30,30,30,30};
        int monthIndex = 0;

        while (monthIndex < monthLengths.length && daysSinceBoishakh >= monthLengths[monthIndex]) {
            daysSinceBoishakh -= monthLengths[monthIndex];
            monthIndex++;
        }

        int bengaliDay = daysSinceBoishakh + 1;
        return new BangabdaDate(bengaliDay, monthIndex, bengaliYear);
    }

    private static class BangabdaDate {
        int day;
        int month;
        int year;

        BangabdaDate(int day, int month, int year) {
            this.day = day;
            this.month = month;
            this.year = year;
        }
    }
}
