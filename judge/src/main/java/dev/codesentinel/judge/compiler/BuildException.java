package dev.codesentinel.judge.compiler;

/** The submission cannot even be built/launched (no entry point, no source of the right type, unsafe file name). */
public class BuildException extends Exception {
    public BuildException(String message) {
        super(message);
    }
}
