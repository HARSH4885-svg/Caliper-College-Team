package dev.codesentinel.engine.similarity;

import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.fingerprint.Fingerprint;
import dev.codesentinel.engine.fingerprint.KGramHasher;
import dev.codesentinel.engine.fingerprint.TokenHasher;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.lexer.LanguageTokenizer;
import dev.codesentinel.engine.lexer.LexerFactory;
import dev.codesentinel.engine.normalization.NormalizedToken;
import dev.codesentinel.engine.normalization.StarterCodeFilter;
import dev.codesentinel.engine.normalization.TokenNormalizer;
import dev.codesentinel.engine.winnowing.Winnower;

import java.util.ArrayList;
import java.util.List;

/** Runs the per-file pipeline: tokenize -> normalize -> starter suppression -> k-grams -> hashes -> winnowing. */
final class ProfileBuilder {
    private final EngineConfig cfg;
    private final TokenNormalizer normalizer = new TokenNormalizer();

    ProfileBuilder(EngineConfig cfg) {
        this.cfg = cfg;
    }

    List<NormalizedToken> normalized(Language lang, String path, String content) {
        LanguageTokenizer tz = LexerFactory.forLanguage(lang);
        return normalizer.normalize(lang, tz.tokenize(content, path), cfg.normalization()).tokens();
    }

    FileProfile build(Language lang, String path, String content, StarterCodeFilter starter) {
        LanguageTokenizer tz = LexerFactory.forLanguage(lang);
        List<dev.codesentinel.engine.lexer.SourceToken> raw = tz.tokenize(content, path);
        List<NormalizedToken> nt = normalizer.normalize(lang, raw, cfg.normalization()).tokens();
        int n = nt.size();
        boolean[] sup = starter == null ? new boolean[n] : starter.suppressionMask(nt);
        if (starter != null && !starter.isEmpty()) {
            // second pass: starter identifiers must not consume ID numbers of the student's own code
            nt = normalizer.normalize(lang, raw, cfg.normalization(), sup).tokens();
        }

        String[] vals = new String[n];
        for (int i = 0; i < n; i++) vals[i] = nt.get(i).value();
        long[] kh = KGramHasher.hashes(TokenHasher.hashAll(vals), cfg.k());

        // a k-gram is valid only if none of its tokens is suppressed (prefix sums of the mask)
        int[] pref = new int[n + 1];
        for (int i = 0; i < n; i++) pref[i + 1] = pref[i] + (sup[i] ? 1 : 0);
        boolean[] valid = new boolean[kh.length];
        int validCount = 0;
        for (int p = 0; p < kh.length; p++) {
            valid[p] = pref[p + cfg.k()] - pref[p] == 0;
            if (valid[p]) validCount++;
        }
        long[] vh = new long[validCount];
        int[] vp = new int[validCount];
        int c = 0;
        for (int p = 0; p < kh.length; p++) {
            if (valid[p]) {
                vh[c] = kh[p];
                vp[c] = p;
                c++;
            }
        }
        List<Fingerprint> fps = new ArrayList<>(Winnower.winnow(vh, vp, cfg.w()));
        return new FileProfile(path, lang, nt, sup, kh, valid, fps);
    }
}
