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

import android.icu.util.IndianCalendar;

public class IndianSakaCalendarUtil {
    
    private static final String[] MONTH_NAMES = new String[]{
            "चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ", "श्रावण", "भाद्रपद",
            "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"
    };
    
    private static final String[] MONTH_NAMES_TRANSLITERATED = new String[]{
            "Chaitra", "Vaisakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashwin", "Kartik", "Margashirsha", "Pausha", "Magha", "Phalguna"
    };
    
    private static final String[] DIGITS_DEVANAGARI = new String[]{
            "०", "१", "२", "३", "४", "५", "६", "७", "८", "९"
    };
    
    public static final int FLAG_INCLUDE_DATE = 1;
    public static final int FLAG_INCLUDE_MONTH = 1 << 1;
    public static final int FLAG_INCLUDE_YEAR = 1 << 2;
    public static final int FLAG_USE_TRANSLITERATION = 1 << 3;
    public static final int FLAG_USE_DEVANAGARI_DIGITS = 1 << 4;

    private IndianSakaCalendarUtil() {
    }

    public static String getSakaDateString() {
        return getSakaDateString(FLAG_INCLUDE_MONTH | FLAG_INCLUDE_DATE);
    }

    public static String getSakaDateString(int flag) {
        return getSakaDateString(new IndianCalendar(), flag);
    }

    public static String getSakaDateString(IndianCalendar indianCalendar, int flag) {
        StringBuilder sb = new StringBuilder();
        boolean useTransliteration = (flag & FLAG_USE_TRANSLITERATION) == FLAG_USE_TRANSLITERATION;
        boolean useDevanagariDigits = (flag & FLAG_USE_DEVANAGARI_DIGITS) == FLAG_USE_DEVANAGARI_DIGITS;
        
        if ((flag & FLAG_INCLUDE_YEAR) == FLAG_INCLUDE_YEAR) {
            int year = indianCalendar.get(IndianCalendar.YEAR);
            sb.append(convYear(year, useDevanagariDigits));
            sb.append(" ");
        }
        
        if ((flag & FLAG_INCLUDE_MONTH) == FLAG_INCLUDE_MONTH) {
            int month = indianCalendar.get(IndianCalendar.MONTH);
            sb.append(convMonth(month, useTransliteration));
        }
        
        if ((flag & FLAG_INCLUDE_DATE) == FLAG_INCLUDE_DATE) {
            int date = indianCalendar.get(IndianCalendar.DATE);
            sb.append(" ");
            sb.append(convDate(date, useDevanagariDigits));
        }
        
        return sb.toString().trim();
    }

    private static String convYear(int year, boolean useDevanagariDigits) {
        String yearStr = useDevanagariDigits ? toDevanagariDigits(year) : String.valueOf(year);
        return yearStr + " शक";
    }

    private static String convMonth(int month, boolean useTransliteration) {
        if (month >= 0 && month < MONTH_NAMES.length) {
            return useTransliteration ? MONTH_NAMES_TRANSLITERATED[month] : MONTH_NAMES[month];
        }
        return "";
    }

    private static String convDate(int date, boolean useDevanagariDigits) {
        if (useDevanagariDigits) {
            return toDevanagariDigits(date);
        }
        return String.valueOf(date);
    }

    private static String toDevanagariDigits(int number) {
        String numStr = String.valueOf(number);
        StringBuilder result = new StringBuilder();
        for (char c : numStr.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = c - '0';
                result.append(DIGITS_DEVANAGARI[digit]);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}