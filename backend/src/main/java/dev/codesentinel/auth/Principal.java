package dev.codesentinel.auth;

public record Principal(long id, String email, String displayName, String role) {
    public boolean instructor() {
        return "INSTRUCTOR".equals(role);
    }
}
