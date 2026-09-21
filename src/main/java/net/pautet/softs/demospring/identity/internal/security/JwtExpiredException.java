package net.pautet.softs.demospring.identity.internal.security;

public class JwtExpiredException extends RuntimeException {
    public JwtExpiredException(String message) {
        super(message);
    }
}