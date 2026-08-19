package com.sup2i.food.security.service;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class Base32Codec {

    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            .toCharArray();

    private Base32Codec() {
    }

    public static String encode(
        byte[] data
    ) {
        if (
            data == null
            || data.length == 0
        ) {
            return "";
        }

        StringBuilder result =
            new StringBuilder();

        int buffer = 0;
        int bitsLeft = 0;

        for (byte value : data) {

            buffer =
                (buffer << 8)
                    | (value & 0xff);

            bitsLeft += 8;

            while (bitsLeft >= 5) {

                int index =
                    (
                        buffer
                            >> (bitsLeft - 5)
                    )
                    & 0x1f;

                bitsLeft -= 5;

                result.append(
                    ALPHABET[index]
                );
            }
        }

        if (bitsLeft > 0) {

            int index =
                (
                    buffer
                        << (5 - bitsLeft)
                )
                & 0x1f;

            result.append(
                ALPHABET[index]
            );
        }

        return result.toString();
    }

    public static byte[] decode(
        String value
    ) {
        String normalized =
            value
                .replace("=", "")
                .replace(" ", "")
                .replace("-", "")
                .toUpperCase(
                    Locale.ROOT
                );

        ByteArrayOutputStream output =
            new ByteArrayOutputStream();

        int buffer = 0;
        int bitsLeft = 0;

        for (
            int position = 0;
            position < normalized.length();
            position++
        ) {
            char character =
                normalized.charAt(
                    position
                );

            int index =
                indexOf(character);

            if (index < 0) {
                throw new IllegalArgumentException(
                    "Invalid Base32 value."
                );
            }

            buffer =
                (buffer << 5)
                    | index;

            bitsLeft += 5;

            if (bitsLeft >= 8) {

                bitsLeft -= 8;

                output.write(
                    (
                        buffer
                            >> bitsLeft
                    )
                    & 0xff
                );
            }
        }

        return output.toByteArray();
    }

    private static int indexOf(
        char character
    ) {
        for (
            int index = 0;
            index < ALPHABET.length;
            index++
        ) {
            if (
                ALPHABET[index]
                    == character
            ) {
                return index;
            }
        }

        return -1;
    }
}