package com.example.bitly.shortcode;

/** Base62 ([0-9a-zA-Z]) encoding: URL-safe, case-sensitive, 62^7 ≈ 3.5 trillion codes in 7 chars. */
public final class Base62 {

    static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {}

    public static String encode(long value) {
        return encode(value, 1);
    }

    /** Encodes {@code value}, left-padding with '0' to at least {@code minLength} characters. */
    public static String encode(long value, int minLength) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative: " + value);
        }
        StringBuilder sb = new StringBuilder();
        do {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        } while (value > 0);
        while (sb.length() < minLength) {
            sb.append(ALPHABET.charAt(0));
        }
        return sb.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("encoded value must not be empty");
        }
        long value = 0;
        for (char c : encoded.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("not a base62 character: '" + c + "'");
            }
            value = Math.addExact(Math.multiplyExact(value, BASE), digit);
        }
        return value;
    }
}
