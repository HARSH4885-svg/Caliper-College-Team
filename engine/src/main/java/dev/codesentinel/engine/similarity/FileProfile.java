package dev.codesentinel.engine.similarity;

import dev.codesentinel.engine.fingerprint.Fingerprint;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.normalization.NormalizedToken;

import java.util.List;

/** Normalized tokens, starter mask and fingerprints of one file. */
final class FileProfile {
    final String path;
    final Language language;
    final List<NormalizedToken> tokens;
    final boolean[] suppressed;
    final long[] kgramHashes;       // all k-grams of the stream (index = start token)
    final boolean[] kgramValid;     // false if the k-gram touches a suppressed token
    final List<Fingerprint> fingerprints;

    FileProfile(String path, Language language, List<NormalizedToken> tokens, boolean[] suppressed, long[] kgramHashes,
                boolean[] kgramValid, List<Fingerprint> fingerprints) {
        this.path = path;
        this.language = language;
        this.tokens = tokens;
        this.suppressed = suppressed;
        this.kgramHashes = kgramHashes;
        this.kgramValid = kgramValid;
        this.fingerprints = fingerprints;
    }
}
