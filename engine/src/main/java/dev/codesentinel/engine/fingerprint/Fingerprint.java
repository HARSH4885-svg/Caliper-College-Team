package dev.codesentinel.engine.fingerprint;

/** A selected hash and the index of the first token of its k-gram in the file's normalized token stream. */
public record Fingerprint(long hash, int position) {
}
