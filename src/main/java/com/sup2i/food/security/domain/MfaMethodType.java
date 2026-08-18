package com.sup2i.food.security.domain;

public enum MfaMethodType {
    TOTP,
    WEBAUTHN,
    EMAIL_OTP,
    OTHER
}