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

public class VikramSamvatCalendarUtil {

    private static final String[] MONTH_NAMES = new String[]{
            "चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद",
            "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"
    };

    private static final String[] MONTH_NAMES_TRANSLITERATED = new String[]{
            "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashvina", "Kartika", "Margashirsha", "Pausha", "Magha", "Phalguna"
    };

    private static final String[] DIGITS_DEVANAGARI = new String[]{
            "०", "१", "२", "३", "४", "५", "६", "७", "८", "९"
    };

    public static final int FLAG_INCLUDE_DATE = 1;
    public static final int FLAG_INCLUDE_MONTH = 1 << 1;
    public static final int FLAG_INCLUDE_YEAR = 1 << 2;
    public static final int FLAG_USE_TRANSLITERATION = 1 << 3;
    public static final int FLAG_USE_DEVANAGARI_DIGITS = 1 << 4;

    private VikramSamvatCalendarUtil() {}

    public static String getVikramSamvatDateString() {
        return getVikramSamvatDateString(FLAG_INCLUDE_MONTH | FLAG_INCLUDE_DATE);
    }

    public static String getVikramSamvatDateString(int flag) {
        return getVikramSamvatDateString(Calendar.getInstance(), flag);
    }

    public static String getVikramSamvatDateString(Calendar calendar, int flag) {
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH) + 1;
        int year = calendar.get(Calendar.YEAR);

        VikramSamvatDate vikramDate = convertToVikramSamvat(day, month, year);

        boolean useTransliteration = (flag & FLAG_USE_TRANSLITERATION) == FLAG_USE_TRANSLITERATION;
        boolean useDevanagariDigits = (flag & FLAG_USE_DEVANAGARI_DIGITS) == FLAG_USE_DEVANAGARI_DIGITS;

        StringBuilder sb = new StringBuilder();

        if ((flag & FLAG_INCLUDE_DATE) == FLAG_INCLUDE_DATE) {
            sb.append(convNumber(vikramDate.day, useDevanagariDigits)).append(" ");
        }

        if ((flag & FLAG_INCLUDE_MONTH) == FLAG_INCLUDE_MONTH) {
            sb.append(convMonth(vikramDate.month, useTransliteration)).append(" ");
        }

        if ((flag & FLAG_INCLUDE_YEAR) == FLAG_INCLUDE_YEAR) {
            sb.append(convNumber(vikramDate.year, useDevanagariDigits));
            sb.append(useTransliteration ? " VS" : " विसं");
        }

        return sb.toString().trim();
    }

    private static String convMonth(int monthIndex, boolean useTransliteration) {
        if (monthIndex >= 0 && monthIndex < MONTH_NAMES.length) {
            return useTransliteration ? MONTH_NAMES_TRANSLITERATED[monthIndex] : MONTH_NAMES[monthIndex];
        }
        return "";
    }

    private static String convNumber(int number, boolean useDevanagariDigits) {
        if (!useDevanagariDigits) return String.valueOf(number);
        String numStr = String.valueOf(number);
        StringBuilder result = new StringBuilder();
        for (char c : numStr.toCharArray()) {
            if (Character.isDigit(c)) {
                result.append(DIGITS_DEVANAGARI[c - '0']);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static VikramSamvatDate convertToVikramSamvat(int day, int month, int year) {
        int vikramYear = year + 56;
        int newYearMonth = 4;
        int newYearDay = 14;

        GregorianCalendar gregDate = new GregorianCalendar(year, month - 1, day);
        GregorianCalendar vikramNewYear = new GregorianCalendar(year, newYearMonth - 1, newYearDay);
        if (gregDate.before(vikramNewYear)) {
            vikramYear -= 1;
        }

        int daysSinceChaitra = (int) ((gregDate.getTimeInMillis() - vikramNewYear.getTimeInMillis()) / (24 * 60 * 60 * 1000));
        if (daysSinceChaitra < 0) {
            GregorianCalendar prevNewYear = new GregorianCalendar(year - 1, newYearMonth - 1, newYearDay);
            daysSinceChaitra = (int) ((gregDate.getTimeInMillis() - prevNewYear.getTimeInMillis()) / (24 * 60 * 60 * 1000));
        }

        int[] monthLengths = {30, 31, 31, 31, 31, 31, 30, 30, 29, 30, 30, 30};
        int monthIndex = 0;

        while (monthIndex < monthLengths.length && daysSinceChaitra >= monthLengths[monthIndex]) {
            daysSinceChaitra -= monthLengths[monthIndex];
            monthIndex++;
        }

        int vikramDay = daysSinceChaitra + 1;
        return new VikramSamvatDate(vikramDay, monthIndex, vikramYear);
    }

    private static class VikramSamvatDate {
        int day;
        int month;
        int year;

        VikramSamvatDate(int day, int month, int year) {
            this.day = day;
            this.month = month;
            this.year = year;
        }
    }
}
