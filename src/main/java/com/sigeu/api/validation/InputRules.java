package com.sigeu.api.validation;

import java.util.regex.Pattern;

public final class InputRules {
    public static final int USERNAME_MAX = 30;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72;
    public static final int NAME_MAX = 120;
    public static final int TITLE_MAX = 120;
    public static final int DESCRIPTION_MAX = 1000;
    public static final int LOCATION_MAX = 120;
    public static final int TYPE_MAX = 40;
    public static final int DELETE_REASON_MAX = 180;
    public static final int IMAGE_MAX = 6_000_000;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{4,30}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Z])[a-zA-Z0-9@#_.-]{8,72}$");

    private InputRules() {}

    public static String clean(String value) {
        if (value == null) return null;
        return value.trim().replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean exceeds(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    public static boolean validUsername(String value) {
        return value != null && USERNAME_PATTERN.matcher(value).matches();
    }

    public static boolean validPassword(String value) {
        return value != null && PASSWORD_PATTERN.matcher(value).matches();
    }
}
