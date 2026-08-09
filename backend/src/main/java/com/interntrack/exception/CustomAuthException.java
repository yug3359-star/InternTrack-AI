package com.interntrack.exception;

/**
 * Domain exception triggered during authentication failure, token revocation, 
 * or unauthenticated attempt to access restricted academic department records.
 */
public class CustomAuthException extends RuntimeException {

    public CustomAuthException(String message) {
        super(message);
    }

    public CustomAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
