package dev.codesentinel.judge.sandbox;

/** The platform (Docker, image, host) failed - never the student's program. Maps to verdict IE. */
public class SandboxException extends Exception {
    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
