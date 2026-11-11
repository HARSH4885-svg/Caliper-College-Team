package dev.codesentinel.engine.cli;

import dev.codesentinel.engine.api.AnalysisRequest;
import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.reporting.Fragment;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.similarity.SimilarityEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stand-alone harness: verify the plagiarism engine without the web application.
 *
 * <pre>
 * java -jar codesentinel-engine.jar --dir submissions/ [--starter starter/] [--truth ground_truth.csv]
 *                                   [--k 10] [--w 5] [--cutoff 0.5] [--high 0.8] [--fragments]
 * </pre>
 * {@code --dir} holds one sub-directory per submission (the directory name is the submission id).
 */
public final class EngineCli {

    public static void main(String[] args) throws IOException {
        Path dir = null, starter = null, truth = null;
        EngineConfig cfg = EngineConfig.defaults();
        boolean fragments = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir" -> dir = Path.of(args[++i]);
                case "--starter" -> starter = Path.of(args[++i]);
                case "--truth" -> truth = Path.of(args[++i]);
                case "--k" -> cfg = cfg.withKW(Integer.parseInt(args[++i]), cfg.w());
                case "--w" -> cfg = cfg.withKW(cfg.k(), Integer.parseInt(args[++i]));
                case "--cutoff" -> cfg = cfg.withCandidateCutoff(Double.parseDouble(args[++i]));
                case "--high" -> cfg = cfg.withHighThreshold(Double.parseDouble(args[++i]));
                case "--fragments" -> fragments = true;
                default -> {
                    System.err.println("unknown argument " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }
        if (dir == null) {
            usage();
            System.exit(2);
        }
        List<SubmissionInput> subs = loadSubmissions(dir);
        List<SourceFileInput> starterFiles = starter == null ? List.of() : readTree(starter);
        AnalysisResult res = new SimilarityEngine().analyze(new AnalysisRequest(starterFiles, subs, cfg));

        System.out.println("CodeSentinel engine - " + res.algorithmVersion());
        System.out.println(AnalysisResult.DISCLAIMER);
        System.out.printf(Locale.ROOT, "%d submissions, %d language group(s), %d possible pairs, %d candidate pairs, %d ms%n",
                res.stats().submissions(), res.stats().languageGroups(), res.stats().possiblePairs(),
                res.stats().candidatePairs(), res.stats().elapsedMillis());
        res.stats().unsupportedFiles().forEach(f -> System.out.println("UNSUPPORTED FILE: " + f));
        res.notes().stream().filter(n -> n.insufficientEvidence())
                .forEach(n -> System.out.println("INSUFFICIENT EVIDENCE: " + n.submissionId() + " " + n.reasons()));
        System.out.printf(Locale.ROOT, "%-24s %-28s %-28s %6s %6s %6s %6s%n", "STATE", "A", "B", "dice", "wDice", "score", "tokens");
        for (PairReport p : res.pairs()) {
            System.out.printf(Locale.ROOT, "%-24s %-28s %-28s %6.2f %6.2f %6.2f %6d%n", p.state(), p.submissionA(),
                    p.submissionB(), p.fingerprintDice(), p.weightedDice(), p.distinctiveScore(), p.distinctiveMatchedTokens());
            if (fragments) {
                for (Fragment f : p.fragments()) {
                    System.out.printf(Locale.ROOT, "    %s:%d-%d  <->  %s:%d-%d  (%d tokens, weight %.2f%s)%n", f.fileA(),
                            f.startLineA(), f.endLineA(), f.fileB(), f.startLineB(), f.endLineB(), f.tokenCount(),
                            f.rarityWeight(), f.distinctive() ? "" : ", common");
                }
                p.explanation().forEach(e -> System.out.println("    - " + e));
            }
        }
        if (truth != null) {
            Set<String> truthPairs = new HashSet<>();
            for (String line : Files.readAllLines(truth)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] c = line.split(",");
                truthPairs.add(QualityEvaluator.key(c[0].trim(), c[1].trim()));
            }
            List<String> ids = subs.stream().map(SubmissionInput::id).sorted().toList();
            System.out.println("any flag (needs review or high): " + QualityEvaluator.evaluate(res, ids, truthPairs, QualityEvaluator.ANY_FLAG));
            System.out.println("high-priority only            : " + QualityEvaluator.evaluate(res, ids, truthPairs, QualityEvaluator.HIGH_ONLY));
        }
    }

    static List<SubmissionInput> loadSubmissions(Path dir) throws IOException {
        List<SubmissionInput> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            for (Path sub : s.filter(Files::isDirectory).sorted().toList()) {
                out.add(new SubmissionInput(sub.getFileName().toString(), readTree(sub)));
            }
        }
        return out;
    }

    static List<SourceFileInput> readTree(Path root) throws IOException {
        List<SourceFileInput> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (Language.fromFileName(rel).isEmpty()) {
                    files.add(new SourceFileInput(rel, "")); // keep it visible so it is reported as unsupported
                } else {
                    files.add(new SourceFileInput(rel, Files.readString(p, StandardCharsets.UTF_8)));
                }
            }
        }
        return files;
    }

    private static void usage() {
        System.err.println("usage: --dir <dir of submission dirs> [--starter <dir>] [--truth <csv>] [--k N] [--w N] [--cutoff X] [--high X] [--fragments]");
    }
}
