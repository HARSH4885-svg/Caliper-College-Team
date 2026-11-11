package dev.codesentinel.engine;

import dev.codesentinel.engine.fingerprint.KGramHasher;
import dev.codesentinel.engine.fingerprint.TokenHasher;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.lexer.LexerFactory;
import dev.codesentinel.engine.normalization.NormalizationConfig;
import dev.codesentinel.engine.normalization.NormalizationConfig.IdentifierMode;
import dev.codesentinel.engine.normalization.NormalizationConfig.LiteralMode;
import dev.codesentinel.engine.normalization.NormalizedToken;
import dev.codesentinel.engine.normalization.TokenNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NormalizationAndHashingTest {

    static String norm(Language l, String src, NormalizationConfig cfg) {
        var toks = LexerFactory.forLanguage(l).tokenize(src, "t");
        return new TokenNormalizer().normalize(l, toks, cfg).tokens().stream().map(NormalizedToken::value)
                .collect(Collectors.joining(" "));
    }

    static final NormalizationConfig FILE = new NormalizationConfig(LiteralMode.SMART, true, IdentifierMode.FIRST_OCCURRENCE_FILE);
    static final NormalizationConfig UNIT = NormalizationConfig.defaults();

    @Test
    void identifiersAreRenamedByFirstOccurrenceAndRepeatsReuseTheId() {
        assertEquals("int ID_1 = ID_2 ; ID_2 = ID_1 + ID_3 ;", norm(Language.C, "int firstName = other; other = firstName + third;", FILE));
    }

    @Test
    void renamedProgramNormalizesIdentically() {
        String a = "int total = 0; for (int i = 0; i < n; i++) total += i;";
        String b = "int acc = 0; for (int k = 0; k < limit; k++) acc += k;";
        assertEquals(norm(Language.C, a, FILE), norm(Language.C, b, FILE));
    }

    @Test
    void wellKnownLibraryNamesAreNotRenamed() {
        assertEquals("printf ( STR , ID_1 ) ;", norm(Language.C, "printf(\"value is %d\", counter);", FILE));
        assertEquals("print ( ID_1 )", norm(Language.PYTHON, "print(counter)", FILE).replace(" NL", ""));
    }

    @Test
    void smartLiteralPolicyKeepsTinyIntegersAndCategorisesTheRest() {
        assertEquals("ID_1 = NUM:0 ; ID_1 = NUM:1 ; ID_1 = NUM ; ID_1 = NUMF ;", norm(Language.C, "x = 0; x = 1; x = 77; x = 2.5;", FILE));
        assertEquals("ID_1 = NUM:77 ;", norm(Language.C, "x = 77;", new NormalizationConfig(LiteralMode.KEEP_ALL, true, IdentifierMode.FIRST_OCCURRENCE_FILE)));
        assertEquals("ID_1 = NUM ;", norm(Language.C, "x = 1;", new NormalizationConfig(LiteralMode.CATEGORY_ONLY, true, IdentifierMode.FIRST_OCCURRENCE_FILE)));
    }

    @Test
    void shortStringsKeepValueLongStringsBecomeOneCategory() {
        assertEquals("STR:; STR STR:", norm(Language.C, "\";\" \"a long message\" \"\"", FILE));
    }

    @Test
    void commonIncludesAreDroppedOthersKept() {
        String out = norm(Language.C, "#include <stdio.h>\n#include \"mylib.h\"\nint x;", FILE);
        assertEquals("INC:mylib.h int ID_1 ;", out);
        String kept = norm(Language.C, "#include <stdio.h>\nint x;", new NormalizationConfig(LiteralMode.SMART, false, IdentifierMode.FIRST_OCCURRENCE_FILE));
        assertTrue(kept.startsWith("INC:stdio.h"));
    }

    @Test
    void unitScopeRestartsNumberingPerTopLevelFunctionFileScopeDoesNot() {
        String src = "int f(int a) { return a; } int g(int b) { return b; }";
        assertEquals("int ID_1 ( int ID_2 ) { return ID_2 ; } int ID_1 ( int ID_2 ) { return ID_2 ; }", norm(Language.C, src, UNIT));
        assertEquals("int ID_1 ( int ID_2 ) { return ID_2 ; } int ID_3 ( int ID_4 ) { return ID_4 ; }", norm(Language.C, src, FILE));
    }

    @Test
    void unitScopeMakesReorderedFunctionsNormalizeToTheSameMultiset() {
        String ab = "int f(int a) { return a + 1; } int g(int b, int c) { return b * c; }";
        String ba = "int g(int b, int c) { return b * c; } int f(int a) { return a + 1; }";
        String nab = norm(Language.C, ab, UNIT), nba = norm(Language.C, ba, UNIT);
        assertNotEquals(nab, nba); // order differs...
        assertNotEquals(norm(Language.C, ab, FILE), norm(Language.C, ba, FILE));
        // ...but each function body is byte-identical under UNIT scope
        assertTrue(nab.contains("int ID_1 ( int ID_2 ) { return ID_2 + NUM:1 ; }"));
        assertTrue(nba.contains("int ID_1 ( int ID_2 ) { return ID_2 + NUM:1 ; }"));
    }

    @Test
    void blindModeCollapsesAllIdentifiers() {
        NormalizationConfig blind = new NormalizationConfig(LiteralMode.SMART, true, IdentifierMode.BLIND);
        assertEquals("ID = ID + ID ;", norm(Language.C, "a = b + c;", blind));
    }

    // ---- hashing ----------------------------------------------------------------------------------------------

    @Test
    void tokenHashIsStableFnv1aPinnedAgainstAnIndependentPythonImplementation() {
        // FNV-1a 64 of "int", computed independently with a Python script; a change of algorithm or byte order breaks this
        long expected = Long.parseUnsignedLong(PINNED_INT_HASH_HEX, 16);
        assertEquals(expected, TokenHasher.hash("int"));
    }

    static final String PINNED_INT_HASH_HEX = "2B9FFF192BD4C83E";

    @Test
    void rollingHashEqualsNaiveHashForEveryWindow() {
        Random r = new Random(42);
        long[] toks = new long[500];
        for (int i = 0; i < toks.length; i++) toks[i] = r.nextLong();
        for (int k : new int[]{1, 2, 5, 10, 37}) {
            long[] h = KGramHasher.hashes(toks, k);
            assertEquals(toks.length - k + 1, h.length);
            for (int i = 0; i < h.length; i++) assertEquals(KGramHasher.naive(toks, i, k), h[i], "k=" + k + " i=" + i);
        }
    }

    @Test
    void kGramBoundaryConditions() {
        long[] t = TokenHasher.hashAll(new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"});
        assertEquals(0, KGramHasher.hashes(new long[0], 10).length);          // empty
        assertEquals(0, KGramHasher.hashes(java.util.Arrays.copyOf(t, 9), 10).length); // shorter than k
        assertEquals(1, KGramHasher.hashes(t, 10).length);                    // exactly k
        assertEquals(2, KGramHasher.hashes(java.util.Arrays.copyOf(t, 10), 9).length);
    }

    @Test
    void identicalTokenStreamsGiveIdenticalKGramHashesAndOneChangedTokenChangesOnlyOverlappingOnes() {
        String[] a = new String[30], b;
        for (int i = 0; i < 30; i++) a[i] = "t" + i;
        b = a.clone();
        b[15] = "CHANGED";
        long[] ha = KGramHasher.hashes(TokenHasher.hashAll(a), 10), hb = KGramHasher.hashes(TokenHasher.hashAll(b), 10);
        int differing = 0;
        for (int i = 0; i < ha.length; i++) if (ha[i] != hb[i]) differing++;
        assertEquals(10, differing); // exactly the 10 windows covering token 15
    }
}
