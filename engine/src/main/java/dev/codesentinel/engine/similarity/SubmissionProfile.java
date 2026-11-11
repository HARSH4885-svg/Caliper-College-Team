package dev.codesentinel.engine.similarity;

import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.lexer.TokenType;
import dev.codesentinel.engine.normalization.NormalizedToken;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All files of one submission in one language, plus the concatenated token view used by the structural matcher.
 * File boundaries are separated by unique sentinel tokens that can never equal a token of another submission, so no
 * tile can span two files.
 */
final class SubmissionProfile {
    final String id;
    final Language language;
    final List<FileProfile> files;
    final Set<Long> fingerprintSet = new HashSet<>();
    final Set<Long> kgramSet = new HashSet<>();

    // concatenated view
    final int[] ids;
    final NormalizedToken[] tokens;     // null at sentinel positions
    final boolean[] suppressed;         // true at suppressed and sentinel positions
    final boolean[] informative;
    final long[] kgramAt;               // k-gram hash starting here
    final boolean[] kgramValid;
    final int informativeCount;
    final int suppressedCount;

    SubmissionProfile(String id, Language language, List<FileProfile> files, int submissionIndex,
                      Map<String, Integer> dictionary) {
        this.id = id;
        this.language = language;
        this.files = files;
        int total = 0;
        for (FileProfile f : files) total += f.tokens.size() + 1;
        ids = new int[total];
        tokens = new NormalizedToken[total];
        suppressed = new boolean[total];
        informative = new boolean[total];
        kgramAt = new long[total];
        kgramValid = new boolean[total];
        int pos = 0, info = 0, sup = 0;
        for (int fi = 0; fi < files.size(); fi++) {
            FileProfile f = files.get(fi);
            for (int t = 0; t < f.tokens.size(); t++, pos++) {
                NormalizedToken nt = f.tokens.get(t);
                ids[pos] = dictionary.computeIfAbsent(nt.value(), v -> dictionary.size());
                tokens[pos] = nt;
                suppressed[pos] = f.suppressed[t];
                boolean inf = !nt.origin().type().isStructuralOnly() && !(nt.origin().type() == TokenType.INCLUDE);
                informative[pos] = inf && !f.suppressed[t];
                if (informative[pos]) info++;
                if (f.suppressed[t] && inf) sup++;
                if (t < f.kgramHashes.length) {
                    kgramAt[pos] = f.kgramHashes[t];
                    kgramValid[pos] = f.kgramValid[t];
                }
            }
            String sentinel = "\u0000SENT:" + submissionIndex + ":" + fi;
            ids[pos] = dictionary.computeIfAbsent(sentinel, v -> dictionary.size());
            suppressed[pos] = true;
            pos++;
            f.fingerprints.forEach(fp -> fingerprintSet.add(fp.hash()));
            for (int p = 0; p < f.kgramHashes.length; p++) {
                if (f.kgramValid[p]) kgramSet.add(f.kgramHashes[p]);
            }
        }
        informativeCount = info;
        suppressedCount = sup;
    }

    int totalTokens() {
        int c = 0;
        for (FileProfile f : files) c += f.tokens.size();
        return c;
    }
}
