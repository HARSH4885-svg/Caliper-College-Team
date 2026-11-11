package dev.codesentinel.engine.api;

/** One source file: a relative path (used for language detection and reporting) and its text. */
public record SourceFileInput(String path, String content) {
}
