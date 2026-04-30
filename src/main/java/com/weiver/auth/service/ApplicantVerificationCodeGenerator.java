package com.weiver.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ApplicantVerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateCode() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}
