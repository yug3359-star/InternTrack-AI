package com.interntrack.service;

public interface EmailProvider {
    void sendVerificationEmail(String toEmail, String toName, String role, String verificationLink);
}
