package dev.codesentinel.engine.ast;

import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.lexer.Language;

import java.util.*;

/**
 * Analyzes structural similarity between two submissions by parsing source files into
 * normalized Abstract Syntax Trees (ASTs), computing subtree structure hashes with variable/literal
 * abstraction, and measuring semantic syntax overlap.
 */
public class AstStructuralAnalyzer {

    private static final int MIN_SUBTREE_SIZE = 3;
    private static final int MAX_FRAGMENTS = 50;

    private final AstLanguageParser parser = new AstLanguageParser();

    /**
     * Precomputed AST profile for a submission in a specific language.
     */
    public record SubmissionAstProfile(
            String submissionId,
            Language language,
            List<AstNode> roots,
            Map<Long, List<AstNode>> subtrees,
            int totalNodes
    ) {
        public static SubmissionAstProfile empty(String id, Language lang) {
            return new SubmissionAstProfile(id, lang, List.of(), Map.of(), 0);
        }
    }

    /**
     * Builds a reusable structural profile for a submission.
     */
    public SubmissionAstProfile profile(SubmissionInput sub, Language language) {
        if (sub == null || language == null) {
            return SubmissionAstProfile.empty("", language);
        }
        List<AstNode> roots = parseSubmission(sub, language);
        if (roots.isEmpty()) {
            return SubmissionAstProfile.empty(sub.id(), language);
        }
        Map<Long, List<AstNode>> subtrees = collectSubtrees(roots);
        int total = countTotalNodes(subtrees);
        return new SubmissionAstProfile(sub.id(), language, roots, subtrees, total);
    }

    /**
     * Compares two submissions structurally and produces an {@link AstStructuralResult}.
     */
    public AstStructuralResult compare(SubmissionInput subA, SubmissionInput subB, Language language) {
        if (subA == null || subB == null || language == null) {
            return AstStructuralResult.empty("Missing input submissions or language.");
        }
        return compare(profile(subA, language), profile(subB, language));
    }

    /**
     * Compares two precomputed {@link SubmissionAstProfile} instances.
     */
    public AstStructuralResult compare(SubmissionAstProfile profA, SubmissionAstProfile profB) {
        if (profA == null || profB == null || profA.totalNodes() == 0 || profB.totalNodes() == 0) {
            return AstStructuralResult.empty("Submissions contain too few structural syntax nodes for reliable AST analysis.");
        }

        Map<Long, List<AstNode>> subtreesA = profA.subtrees();
        Map<Long, List<AstNode>> subtreesB = profB.subtrees();

        int totalA = profA.totalNodes();
        int totalB = profB.totalNodes();

        // Calculate multiset intersection of structural subtree hashes
        int matched = 0;
        List<AstStructuralFragment> matchedFragments = new ArrayList<>();

        for (Map.Entry<Long, List<AstNode>> entryA : subtreesA.entrySet()) {
            long hash = entryA.getKey();
            List<AstNode> nodesA = entryA.getValue();
            List<AstNode> nodesB = subtreesB.get(hash);

            if (nodesB != null && !nodesB.isEmpty()) {
                int pairMatches = Math.min(nodesA.size(), nodesB.size());
                matched += pairMatches;

                // Record structural fragments for top matches
                for (int i = 0; i < pairMatches && matchedFragments.size() < MAX_FRAGMENTS; i++) {
                    AstNode na = nodesA.get(i);
                    AstNode nb = nodesB.get(i);
                    // Filter out tiny 1-line trivial expressions from top fragment reporting
                    if (na.subtreeSize() >= 4 || (na.endLine() - na.startLine() >= 1)) {
                        matchedFragments.add(new AstStructuralFragment(
                                na.file(), na.startLine(), na.endLine(),
                                nb.file(), nb.startLine(), nb.endLine(),
                                na.type(), na.subtreeSize()
                        ));
                    }
                }
            }
        }

        double score = (2.0 * matched) / (totalA + totalB);
        double roundedScore = Math.round(score * 10000.0) / 10000.0;

        // Sort fragments by subtree size descending and filter overlapping children
        matchedFragments.sort(Comparator.comparingInt(AstStructuralFragment::nodeCount).reversed());
        List<AstStructuralFragment> nonOverlapping = pruneOverlapping(matchedFragments);

        String explanation = String.format(Locale.ROOT,
                "AST Structural Analysis: %.2f similarity (%d shared structural syntax subtrees out of %d in A and %d in B). Variable names, literals, and comments abstracted.",
                roundedScore, matched, totalA, totalB);

        return new AstStructuralResult(roundedScore, matched, totalA, totalB, nonOverlapping, explanation);
    }

    private List<AstNode> parseSubmission(SubmissionInput sub, Language language) {
        List<AstNode> roots = new ArrayList<>();
        for (SourceFileInput file : sub.files()) {
            Optional<Language> fileLang = Language.fromFileName(file.path());
            if (fileLang.isPresent() && fileLang.get() == language) {
                parser.parse(file.path(), file.content(), language).ifPresent(roots::add);
            }
        }
        return roots;
    }

    private Map<Long, List<AstNode>> collectSubtrees(List<AstNode> roots) {
        Map<Long, List<AstNode>> map = new HashMap<>();
        for (AstNode root : roots) {
            traverse(root, map);
        }
        return map;
    }

    private void traverse(AstNode node, Map<Long, List<AstNode>> map) {
        if (node == null) return;
        if (node.subtreeSize() >= MIN_SUBTREE_SIZE) {
            map.computeIfAbsent(node.structuralHash(), k -> new ArrayList<>()).add(node);
        }
        for (AstNode child : node.children()) {
            traverse(child, map);
        }
    }

    private int countTotalNodes(Map<Long, List<AstNode>> subtrees) {
        int count = 0;
        for (List<AstNode> list : subtrees.values()) {
            count += list.size();
        }
        return count;
    }

    private List<AstStructuralFragment> pruneOverlapping(List<AstStructuralFragment> fragments) {
        List<AstStructuralFragment> result = new ArrayList<>();
        for (AstStructuralFragment f : fragments) {
            boolean covered = false;
            for (AstStructuralFragment kept : result) {
                if (kept.fileA().equals(f.fileA()) && kept.fileB().equals(f.fileB())
                        && kept.startLineA() <= f.startLineA() && kept.endLineA() >= f.endLineA()
                        && kept.startLineB() <= f.startLineB() && kept.endLineB() >= f.endLineB()) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                result.add(f);
            }
            if (result.size() >= 25) break;
        }
        return result;
    }
}
