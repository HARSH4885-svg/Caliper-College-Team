package dev.codesentinel.engine;

import dev.codesentinel.engine.api.AnalysisRequest;
import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.reporting.Fragment;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.reporting.ReportState;
import dev.codesentinel.engine.similarity.SimilarityEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 23 fixtures as executable scenarios. Every program here is synthetic. A "crowd" of five independent
 * submissions is added so rarity weighting is active (it needs >= 5 submissions in the language group).
 */
class EngineScenarioTest {
    static final String STUDENT_MARK = "// ---- Student part below ----";
    static final List<String> CROWD = List.of("I05_stl", "I08_struct_stats", "I09_map_counting", "I10_shell_lowerbound", "I12_set_dedupe");
    static final String BASE = "I11_heap_manual";

    static AnalysisResult run(List<SubmissionInput> subs, boolean withStarter) {
        return new SimilarityEngine().analyze(new AnalysisRequest(withStarter ? Fixtures.starter() : List.of(), subs, EngineConfig.defaults()));
    }

    static List<SubmissionInput> crowdPlus(SubmissionInput... extra) {
        List<SubmissionInput> l = new ArrayList<>();
        for (String c : CROWD) l.add(Fixtures.sub(c));
        Collections.addAll(l, extra);
        return l;
    }

    static Optional<PairReport> pair(AnalysisResult r, String a, String b) {
        return r.pairs().stream().filter(p -> (p.submissionA().equals(a) && p.submissionB().equals(b))
                || (p.submissionA().equals(b) && p.submissionB().equals(a))).findFirst();
    }

    static String tail(String src) {
        return src.substring(src.indexOf(STUDENT_MARK));
    }

    static String head(String src) {
        return src.substring(0, src.indexOf(STUDENT_MARK));
    }

    static String rename(String text, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) text = text.replaceAll("\\b" + Pattern.quote(pairs[i]) + "\\b", pairs[i + 1]);
        return text;
    }

    static void assertState(AnalysisResult r, String a, String b, ReportState expected) {
        PairReport p = pair(r, a, b).orElseThrow(() -> new AssertionError("pair not surfaced: " + a + " / " + b));
        assertEquals(expected, p.state(), String.join("\n", p.explanation()));
    }

    static boolean flagged(AnalysisResult r, String a, String b) {
        return pair(r, a, b).map(p -> p.state() == ReportState.HIGH_PRIORITY_REVIEW || p.state() == ReportState.NEEDS_REVIEW).orElse(false);
    }

    // ---- positives ----------------------------------------------------------------------------------------------

    @Test
    void exactDuplicateIsHighPriority() {
        String src = Fixtures.main(BASE);
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_dup", src)), true);
        assertState(r, BASE, "Z_dup", ReportState.HIGH_PRIORITY_REVIEW);
        PairReport p = pair(r, BASE, "Z_dup").get();
        assertEquals(1.0, p.fingerprintDice(), 1e-9);
        assertTrue(p.rarityWeightingActive());
        assertFalse(p.fragments().isEmpty());
    }

    @Test
    void identifierRenamedCopyIsStillDetectedStrongly() {
        String src = Fixtures.main(BASE);
        String copy = head(src) + rename(tail(src), "siftDown", "pushDown", "heapSort", "sortHeap", "swapIdx", "target", "child", "kid", "root", "top", "evens", "pairCount", "odds", "singles");
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_renamed", copy)), true);
        assertState(r, BASE, "Z_renamed", ReportState.HIGH_PRIORITY_REVIEW);
    }

    @Test
    void commentAndWhitespaceOnlyChangesDoNotDefeatDetection() {
        String src = Fixtures.main(BASE);
        String copy = src.replace("    ", "\t").replace("\n", "\n\n").replace(";", ";   // trailing note /* x */");
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_ws", "/* header\n comment */\n" + copy)), true);
        assertState(r, BASE, "Z_ws", ReportState.HIGH_PRIORITY_REVIEW);
    }

    @Test
    void braceStyleChangeDoesNotDefeatDetection() {
        String src = Fixtures.main(BASE);
        String copy = src.replace(") {\n", ")\n{\n").replace(" else\n", "\nelse\n");
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_brace", copy)), true);
        assertState(r, BASE, "Z_brace", ReportState.HIGH_PRIORITY_REVIEW);
    }

    @Test
    void redundantStatementsStillSurfaceThePairForReview() {
        String src = Fixtures.main(BASE);
        String copy = head(src) + tail(src).replaceAll("(?m)^( +)(int \\w+ = [^;(]*;)$", "$1$2\n$1int junk = 0; junk = junk + 1;");
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_pad", copy)), true);
        assertTrue(flagged(r, BASE, "Z_pad"), "padding must not hide a copy from review: " + pair(r, BASE, "Z_pad"));
    }

    @Test
    void functionsMovedAroundStillSurfaceThePairForReview() {
        String src = Fixtures.main(BASE).replace("\r\n", "\n");
        String t = tail(src).substring(STUDENT_MARK.length()).trim();
        String[] chunks = t.split("(\r?\n){2,}");
        assertTrue(chunks.length >= 3);
        String tmp = chunks[0];
        chunks[0] = chunks[1];
        chunks[1] = tmp;
        String copy = head(src) + STUDENT_MARK + "\n\n" + String.join("\n\n", chunks) + "\n";
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_moved", copy)), true);
        assertTrue(flagged(r, BASE, "Z_moved"), String.valueOf(pair(r, BASE, "Z_moved")));
    }

    // ---- fairness: things that must NOT be accused ------------------------------------------------------------

    @Test
    void independentImplementationsOfTheSameStandardAlgorithmsAreNotFlagged() {
        // I01 and I02 are both "bubble sort + iterative binary search" written independently
        var r = run(crowdPlus(Fixtures.sub("I01_bubble_binary"), Fixtures.sub("I02_bubble_flag_binary")), true);
        assertFalse(flagged(r, "I01_bubble_binary", "I02_bubble_flag_binary"), String.valueOf(pair(r, "I01_bubble_binary", "I02_bubble_flag_binary")));
    }

    @Test
    void sharedStarterCodeIsSuppressedAndAccountedFor() {
        var r = run(crowdPlus(Fixtures.sub("I01_bubble_binary"), Fixtures.sub("I04_selection_linear")), true);
        var note = r.notes().stream().filter(n -> n.submissionId().equals("I01_bubble_binary")).findFirst().orElseThrow();
        assertTrue(note.suppressedTokens() > 60, "starter tokens must be reported as suppressed, got " + note.suppressedTokens());
        assertFalse(flagged(r, "I01_bubble_binary", "I04_selection_linear"));
    }

    @Test
    void starterCodeAloneNeverMakesTwoStudentsSimilar() {
        // two students who only added a tiny, DIFFERENT main on top of the starter
        String a = Fixtures.starterText() + "\nint main() { vector<int> v = readNumbers(3); printNumbers(v); return 0; }\n";
        String b = Fixtures.starterText() + "\nint main() { int k; cin >> k; vector<int> w = readNumbers(k); w.push_back(9); printNumbers(w); }\n";
        var r = run(List.of(Fixtures.sub("S1", a), Fixtures.sub("S2", b)), true);
        assertTrue(r.pairs().stream().noneMatch(p -> p.state() == ReportState.HIGH_PRIORITY_REVIEW || p.state() == ReportState.NEEDS_REVIEW));
        // without starter suppression the shared boilerplate WOULD make them look alike - that is the failure mode being avoided
        var without = run(List.of(Fixtures.sub("S1", a), Fixtures.sub("S2", b)), false);
        double withDice = pair(r, "S1", "S2").map(PairReport::fingerprintDice).orElse(0.0);
        double withoutDice = pair(without, "S1", "S2").map(PairReport::fingerprintDice).orElse(0.0);
        assertTrue(withoutDice > withDice, "starter suppression must lower the apparent similarity: " + withoutDice + " vs " + withDice);
    }

    @Test
    void veryShortIdenticalProgramsReturnInsufficientEvidenceNotAPercentage() {
        String tiny = "#include <stdio.h>\nint main() { printf(\"hello\"); return 0; }\n";
        var r = new SimilarityEngine().analyze(new AnalysisRequest(List.of(),
                List.of(new SubmissionInput("T1", List.of(new SourceFileInput("a.c", tiny))),
                        new SubmissionInput("T2", List.of(new SourceFileInput("a.c", tiny)))), EngineConfig.defaults()));
        assertTrue(r.notes().stream().allMatch(n -> n.insufficientEvidence()));
        assertTrue(r.pairs().stream().noneMatch(p -> p.state() == ReportState.HIGH_PRIORITY_REVIEW || p.state() == ReportState.NEEDS_REVIEW));
        r.pairs().forEach(p -> assertEquals(ReportState.INSUFFICIENT_EVIDENCE, p.state()));
    }

    @Test
    void programsShorterThanKProduceNoFingerprintsAndAnExplicitNote() {
        var r = new SimilarityEngine().analyze(new AnalysisRequest(List.of(),
                List.of(new SubmissionInput("T1", List.of(new SourceFileInput("a.py", "x = 1\n"))),
                        new SubmissionInput("T2", List.of(new SourceFileInput("a.py", "x = 1\n")))), EngineConfig.defaults()));
        assertTrue(r.pairs().isEmpty());
        assertTrue(r.notes().stream().allMatch(n -> n.insufficientEvidence() && n.fingerprintCount() == 0));
    }

    @Test
    void unrelatedProgramsAreNotCandidates() {
        var r = run(crowdPlus(Fixtures.sub("I06_merge_binary")), true);
        assertTrue(r.pairs().stream().noneMatch(p -> p.state() == ReportState.HIGH_PRIORITY_REVIEW || p.state() == ReportState.NEEDS_REVIEW));
    }

    // ---- languages, files, determinism ------------------------------------------------------------------------

    static final String PY = """
            import sys
            from collections import defaultdict

            def tally(words):
                \"\"\"count words
                over several lines # not a comment\"\"\"
                counts = defaultdict(int)
                for w in words:
                    if len(w) > 2:
                        counts[w] += 1
                    else:
                        counts["short"] += 1
                return counts

            def top(counts, limit):
                ordered = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
                result = []
                for name, c in ordered:
                    if len(result) >= limit:
                        break
                    result.append((name, c))
                return result

            def main():
                words = sys.stdin.read().split()
                counts = tally(words)
                for name, c in top(counts, 5):
                    print(name, c)
                total = 0
                for c in counts.values():
                    total += c
                print("total", total)

            if __name__ == "__main__":
                main()
            """;

    @Test
    void pythonRenamedCopyWithDifferentCommentsIsDetectedAndExplainsWhenRarityIsInactive() {
        String copy = PY.replace("tally", "count_up").replace("top(", "best(").replace("counts", "freq").replace("words", "tokens")
                .replace("result", "picked").replace("# not a comment", "# renamed");
        copy = "# my own solution\n" + copy.replace("\n\n", "\n\n\n");
        var r = new SimilarityEngine().analyze(new AnalysisRequest(List.of(),
                List.of(new SubmissionInput("P1", List.of(new SourceFileInput("main.py", PY))),
                        new SubmissionInput("P2", List.of(new SourceFileInput("main.py", copy)))), EngineConfig.defaults()));
        PairReport p = pair(r, "P1", "P2").orElseThrow();
        assertEquals(ReportState.HIGH_PRIORITY_REVIEW, p.state(), String.join("\n", p.explanation()));
        assertFalse(p.rarityWeightingActive());
        assertTrue(p.explanation().stream().anyMatch(e -> e.contains("Rarity weighting inactive")));
    }

    @Test
    void differentLanguagesAreNeverCompared() {
        var r = new SimilarityEngine().analyze(new AnalysisRequest(List.of(),
                List.of(new SubmissionInput("P1", List.of(new SourceFileInput("main.py", PY))),
                        new SubmissionInput("C1", List.of(new SourceFileInput("main.cpp", Fixtures.main(BASE))))), EngineConfig.defaults()));
        assertTrue(r.pairs().isEmpty());
        assertEquals(2, r.stats().languageGroups());
        assertEquals(0, r.stats().possiblePairs());
    }

    @Test
    void unsupportedFilesAreReportedNotSilentlyTreatedAsText() {
        var r = new SimilarityEngine().analyze(new AnalysisRequest(List.of(),
                List.of(new SubmissionInput("U1", List.of(new SourceFileInput("logo.png", "\u0089PNG"), new SourceFileInput("notes.txt", "hi"))),
                        new SubmissionInput("U2", List.of(new SourceFileInput("main.py", PY)))), EngineConfig.defaults()));
        assertTrue(r.stats().unsupportedFiles().contains("U1:logo.png"));
        assertTrue(r.notes().stream().anyMatch(n -> n.submissionId().equals("U1") && n.insufficientEvidence() && n.language() == null));
    }

    @Test
    void multiFileSubmissionsMapFragmentsToTheRightFilesAndLines() {
        String lib = "int helperOne(int a) {\n    int r = 0;\n    for (int i = 0; i < a; i++) {\n        r += i * a - 3;\n        if (r > 100) r -= 7;\n    }\n    return r;\n}\n"
                + "int helperTwo(int a, int b) {\n    while (b != 0) {\n        int t = a % b;\n        a = b;\n        b = t;\n    }\n    return a;\n}\n";
        String main1 = "int main() {\n    int x = helperOne(9);\n    int y = helperTwo(x, 12);\n    return x + y;\n}\n";
        String pre = "// preamble line\n// preamble line\n// preamble line\n// preamble line\n";
        var subA = new SubmissionInput("A", List.of(new SourceFileInput("src/lib.cpp", lib), new SourceFileInput("src/main.cpp", main1)));
        var subB = new SubmissionInput("B", List.of(new SourceFileInput("code/util.cpp", pre + lib), new SourceFileInput("code/app.cpp", main1)));
        var r = new SimilarityEngine().analyze(new AnalysisRequest(List.of(), List.of(subA, subB), EngineConfig.defaults().withKW(10, 5)));
        PairReport p = pair(r, "A", "B").orElseThrow();
        Fragment best = p.fragments().get(0);
        assertEquals("src/lib.cpp", best.fileA());
        assertEquals("code/util.cpp", best.fileB());
        assertEquals(1, best.startLineA());
        assertEquals(5, best.startLineB());           // 4 preamble lines were added in B
        assertTrue(best.endLineA() >= 14 && best.endLineA() <= 16);
        assertEquals(best.endLineA() + 4, best.endLineB());
    }

    @Test
    void resultIsDeterministicAndIndependentOfSubmissionOrder() {
        List<SubmissionInput> subs = crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_dup", Fixtures.main(BASE)));
        var r1 = run(subs, true);
        var r2 = run(subs, true);
        List<SubmissionInput> shuffled = new ArrayList<>(subs);
        Collections.reverse(shuffled);
        var r3 = run(shuffled, true);
        assertEquals(r1.pairs(), r2.pairs());
        assertEquals(r1.pairs().stream().map(p -> p.submissionA() + p.submissionB() + p.state() + p.fingerprintDice()).sorted().toList(),
                r3.pairs().stream().map(p -> p.submissionA() + p.submissionB() + p.state() + p.fingerprintDice()).sorted().toList());
    }

    @Test
    void pairsAreCanonicalAndUnique() {
        var r = run(crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_dup", Fixtures.main(BASE))), true);
        for (PairReport p : r.pairs()) assertTrue(p.submissionA().compareTo(p.submissionB()) < 0);
        assertEquals(r.pairs().size(), r.pairs().stream().map(p -> p.submissionA() + "|" + p.submissionB()).distinct().count());
    }

    @Test
    void fingerprintsAreExportedWithAVersionForPersistence() {
        var r = run(crowdPlus(Fixtures.sub(BASE)), true);
        assertTrue(r.algorithmVersion().startsWith("norm-v1;k=10;w=5"));
        assertEquals(CROWD.size() + 1, r.fingerprints().stream().map(f -> f.submissionId()).distinct().count());
        assertTrue(r.fingerprints().stream().allMatch(f -> f.language() == Language.CPP && !f.fingerprints().isEmpty()));
    }

    @Test
    void highThresholdIsConfigurableAndRecordedInTheResult() {
        String src = Fixtures.main(BASE);
        EngineConfig strict = EngineConfig.defaults().withHighThreshold(1.01); // unreachable
        var r = new SimilarityEngine().analyze(new AnalysisRequest(Fixtures.starter(), crowdPlus(Fixtures.sub(BASE), Fixtures.sub("Z_dup", src)), strict));
        assertEquals(1.01, r.config().highThreshold());
        assertEquals(ReportState.NEEDS_REVIEW, pair(r, BASE, "Z_dup").orElseThrow().state());
    }
}
