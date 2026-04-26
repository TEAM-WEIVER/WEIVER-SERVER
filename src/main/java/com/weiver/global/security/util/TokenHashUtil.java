package com.weiver.global.security.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TokenHashUtil {
    private TokenHashUtil() {}

    public static String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(encodedHash.length * 2);

            for(byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);

                if(hex.length() == 1) hexString.append('0');

                hexString.append(hex);
            }

            return hexString.toString();
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
