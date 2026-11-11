package dev.codesentinel.engine.similarity;

import dev.codesentinel.engine.api.AnalysisRequest;
import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.api.PlagiarismEngine;
import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.ast.AstStructuralAnalyzer;
import dev.codesentinel.engine.ast.AstStructuralResult;
import dev.codesentinel.engine.indexing.InvertedIndex;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.normalization.NormalizedToken;
import dev.codesentinel.engine.normalization.StarterCodeFilter;
import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.reporting.AnalysisStats;
import dev.codesentinel.engine.reporting.FileFingerprints;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.reporting.SubmissionNote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the pipe-and-filter pipeline (section 15.2) for ONE assignment:
 * tokenize -> normalize -> starter suppression -> k-grams -> rolling hash -> winnowing -> inverted-index candidates ->
 * Dice filter -> RKR-GST verification -> evidence/line mapping.
 *
 * <p>Submissions are only compared with submissions of the same language (FR-PLAG-15). The engine is pure: no I/O.
 */
public final class SimilarityEngine implements PlagiarismEngine {

    @Override
    public AnalysisResult analyze(AnalysisRequest req) {
        long t0 = System.nanoTime();
        EngineConfig cfg = req.config() == null ? EngineConfig.defaults() : req.config();
        ProfileBuilder builder = new ProfileBuilder(cfg);
        List<String> unsupported = new ArrayList<>();

        // starter code, one filter per language
        Map<Language, StarterCodeFilter> starters = new EnumMap<>(Language.class);
        if (req.starterFiles() != null) {
            for (SourceFileInput f : req.starterFiles()) {
                Language.fromFileName(f.path()).ifPresentOrElse(lang -> {
                    StarterCodeFilter flt = starters.computeIfAbsent(lang, l -> new StarterCodeFilter(cfg.k()));
                    flt.addStarterFile(builder.normalized(lang, f.path(), f.content()));
                }, () -> unsupported.add("starter:" + f.path()));
            }
        }

        // profiles grouped by language
        Map<String, SubmissionInput> rawInputs = new HashMap<>();
        Map<String, Integer> dictionary = new HashMap<>();
        Map<Language, List<SubmissionProfile>> groups = new EnumMap<>(Language.class);
        List<FileFingerprints> fingerprintRecords = new ArrayList<>();
        List<SubmissionNote> notes = new ArrayList<>();
        int subIdx = 0;
        for (SubmissionInput sub : req.submissions()) {
            rawInputs.put(sub.id(), sub);
            Map<Language, List<SourceFileInput>> byLang = new EnumMap<>(Language.class);
            boolean hasCpp = sub.files().stream().anyMatch(f -> Language.fromFileName(f.path()).orElse(null) == Language.CPP);
            for (SourceFileInput f : sub.files()) {
                var lang = Language.fromFileName(f.path());
                if (lang.isEmpty()) {
                    unsupported.add(sub.id() + ":" + f.path());
                    continue;
                }
                Language l = lang.get();
                if (l == Language.C && Language.isBareCHeader(f.path()) && hasCpp) l = Language.CPP;
                byLang.computeIfAbsent(l, x -> new ArrayList<>()).add(f);
            }
            if (byLang.isEmpty()) {
                notes.add(new SubmissionNote(sub.id(), null, 0, 0, 0, 0, true,
                        List.of("No supported source files (unsupported-language result).")));
            }
            for (Map.Entry<Language, List<SourceFileInput>> e : byLang.entrySet()) {
                List<SourceFileInput> files = new ArrayList<>(e.getValue());
                files.sort(Comparator.comparing(SourceFileInput::path));
                List<FileProfile> profiles = new ArrayList<>();
                for (SourceFileInput f : files) {
                    FileProfile fp = builder.build(e.getKey(), f.path(), f.content(), starters.get(e.getKey()));
                    profiles.add(fp);
                    fingerprintRecords.add(new FileFingerprints(sub.id(), f.path(), e.getKey(), fp.fingerprints));
                }
                SubmissionProfile sp = new SubmissionProfile(sub.id(), e.getKey(), profiles, subIdx, dictionary);
                groups.computeIfAbsent(e.getKey(), l -> new ArrayList<>()).add(sp);
                notes.add(noteFor(sp, cfg));
            }
            subIdx++;
        }

        // per-language comparison
        List<PairReport> pairs = new ArrayList<>();
        long possible = 0;
        int candidates = 0;
        PairAnalyzer analyzer = new PairAnalyzer(cfg);
        for (List<SubmissionProfile> group : groups.values()) {
            int n = group.size();
            possible += (long) n * (n - 1) / 2;
            if (n < 2) continue;
            List<Set<Long>> fpSets = new ArrayList<>();
            for (SubmissionProfile p : group) fpSets.add(p.fingerprintSet);
            InvertedIndex index = new InvertedIndex(fpSets);

            Map<Long, Integer> fpDf = new HashMap<>();
            for (Set<Long> s : fpSets) for (long h : s) fpDf.merge(h, 1, Integer::sum);
            Map<Long, Integer> kDf = new HashMap<>();
            for (SubmissionProfile p : group) for (long h : p.kgramSet) kDf.merge(h, 1, Integer::sum);
            PairAnalyzer.Rarity rarity = new PairAnalyzer.Rarity(n, cfg.rarityMinSubmissions(), fpDf, kDf);

            int maxPostings = n <= 20 ? n : Math.max(10, n / 2);
            double containmentCut = Math.min(0.95, cfg.candidateCutoff() + 0.20);
            List<SubmissionProfile[]> selected = new ArrayList<>();
            for (InvertedIndex.CandidatePair cp : sortedPairs(index.candidatePairs(maxPostings))) {
                SubmissionProfile a = group.get(cp.a()), b = group.get(cp.b());
                Set<Long> small = a.fingerprintSet.size() <= b.fingerprintSet.size() ? a.fingerprintSet : b.fingerprintSet;
                Set<Long> large = small == a.fingerprintSet ? b.fingerprintSet : a.fingerprintSet;
                int inter = 0;
                for (long h : small) if (large.contains(h)) inter++;
                double dice = Dice.coefficient(inter, a.fingerprintSet.size(), b.fingerprintSet.size());
                double containment = small.isEmpty() ? 0 : (double) inter / small.size();
                if (dice < cfg.candidateCutoff() && containment < containmentCut) continue;
                // rarity gate: sharing only common fingerprints is not a candidate (saves the structural stage)
                if (rarity.active && analyzer.weightedDice(a, b, rarity) < cfg.candidateCutoff() / 2) continue;
                // canonical order by submission id so (A,B) and (B,A) cannot both appear
                selected.add(a.id.compareTo(b.id) <= 0 ? new SubmissionProfile[]{a, b} : new SubmissionProfile[]{b, a});
            }
            candidates += selected.size();
            AstStructuralAnalyzer astAnalyzer = new AstStructuralAnalyzer();
            Map<String, AstStructuralAnalyzer.SubmissionAstProfile> astProfiles = new ConcurrentHashMap<>();
            (group.size() > 16 ? group.parallelStream() : group.stream()).forEach(p -> {
                SubmissionInput input = rawInputs.get(p.id);
                if (input != null) {
                    astProfiles.put(p.id, astAnalyzer.profile(input, p.language));
                }
            });

            // pair analysis is independent and read-only -> parallel; the ordered collect keeps results deterministic
            var stream = selected.size() > 16 ? selected.parallelStream() : selected.stream();
            pairs.addAll(stream.map(ab -> {
                AstStructuralAnalyzer.SubmissionAstProfile profA = astProfiles.get(ab[0].id);
                AstStructuralAnalyzer.SubmissionAstProfile profB = astProfiles.get(ab[1].id);
                AstStructuralResult ast = (profA != null && profB != null)
                        ? astAnalyzer.compare(profA, profB)
                        : null;
                return analyzer.analyze(ab[0], ab[1], rarity, ast);
            }).toList());
        }
        pairs.sort(Comparator.comparingInt((PairReport p) -> rank(p)).thenComparing(Comparator.comparingDouble(PairReport::distinctiveScore).reversed())
                .thenComparing(PairReport::submissionA).thenComparing(PairReport::submissionB));

        long ms = (System.nanoTime() - t0) / 1_000_000;
        AnalysisStats stats = new AnalysisStats(req.submissions().size(), groups.size(), possible, candidates, ms, unsupported);
        return new AnalysisResult(cfg.fingerprintVersion(), cfg, pairs, notes, fingerprintRecords, stats);
    }

    private static int rank(PairReport p) {
        return switch (p.state()) {
            case HIGH_PRIORITY_REVIEW -> 0;
            case NEEDS_REVIEW -> 1;
            case INSUFFICIENT_EVIDENCE -> 2;
            case LOW_SIMILARITY -> 3;
            case NO_MEANINGFUL_MATCH -> 4;
        };
    }

    private static List<InvertedIndex.CandidatePair> sortedPairs(Set<InvertedIndex.CandidatePair> set) {
        List<InvertedIndex.CandidatePair> l = new ArrayList<>(set);
        l.sort(Comparator.comparingInt(InvertedIndex.CandidatePair::a).thenComparingInt(InvertedIndex.CandidatePair::b));
        return l;
    }

    private SubmissionNote noteFor(SubmissionProfile sp, EngineConfig cfg) {
        List<String> reasons = new ArrayList<>();
        boolean insufficient = false;
        if (sp.informativeCount < cfg.minInformativeTokens()) {
            insufficient = true;
            reasons.add("Only " + sp.informativeCount + " informative tokens (minimum " + cfg.minInformativeTokens() + ").");
        }
        if (sp.fingerprintSet.isEmpty()) {
            insufficient = true;
            reasons.add("No fingerprints could be produced (fewer than k=" + cfg.k() + " usable tokens after suppression).");
        }
        if (sp.suppressedCount > 0 && sp.informativeCount < sp.suppressedCount) {
            reasons.add("Mostly starter/common code (" + sp.suppressedCount + " suppressed tokens).");
        }
        return new SubmissionNote(sp.id, sp.language, sp.totalTokens(), sp.informativeCount, sp.suppressedCount,
                sp.fingerprintSet.size(), insufficient, reasons);
    }
}
