package dev.codesentinel.plagiarism;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AppProperties;
import dev.codesentinel.engine.api.*;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.reporting.Fragment;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.similarity.SimilarityEngine;
import dev.codesentinel.storage.FileStorageProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Orchestration only: loads same-assignment sources, calls the frozen engine contract, persists the evidence. */
@Service
public class PlagiarismService {
    private final JdbcClient jdbc;
    private final FileStorageProvider storage;
    private final AppProperties props;
    private final TransactionTemplate tx;
    private final ObjectMapper json = new ObjectMapper();
    private final PlagiarismEngine engine;

    @org.springframework.beans.factory.annotation.Autowired
    public PlagiarismService(JdbcClient jdbc, FileStorageProvider storage, AppProperties props, TransactionTemplate tx, PlagiarismEngine engine) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
        this.engine = engine != null ? engine : new SimilarityEngine();
    }

    public PlagiarismService(JdbcClient jdbc, FileStorageProvider storage, AppProperties props, TransactionTemplate tx) {
        this(jdbc, storage, props, tx, new SimilarityEngine());
    }

    public EngineConfig config() {
        AppProperties.Plag p = props.plag();
        return EngineConfig.defaults().withKW(p.k(), p.w()).withCandidateCutoff(p.candidateCutoff()).withHighThreshold(p.highPriorityThreshold());
    }

    private String read(String key) {
        try (InputStream in = storage.read(key)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read stored file", e);
        }
    }

    /** @return the new run id. Only the latest attempt of each student is compared; only this assignment's submissions are used (FR-PLAG-14). */
    public long run(long assignmentId) {
        List<Long> latest = jdbc.sql("""
                SELECT DISTINCT ON (student_id) id FROM submissions WHERE assignment_id=?
                AND EXISTS (SELECT 1 FROM submission_files f WHERE f.submission_id=submissions.id)
                ORDER BY student_id, attempt DESC""").param(assignmentId).query(Long.class).list();
        if (latest.size() < 2) throw ApiException.conflict("At least two students with submissions are needed to compare");

        List<SubmissionInput> inputs = new ArrayList<>();
        for (long sid : latest) {
            List<SourceFileInput> files = new ArrayList<>();
            for (var f : jdbc.sql("SELECT path, storage_key FROM submission_files WHERE submission_id=? ORDER BY path").param(sid)
                    .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).list()) {
                if (Language.fromFileName(f[0]).isPresent()) files.add(new SourceFileInput(f[0], read(f[1])));
            }
            inputs.add(new SubmissionInput(Long.toString(sid), files));
        }
        List<SourceFileInput> starter = new ArrayList<>();
        for (var f : jdbc.sql("SELECT name, storage_key FROM assignment_files WHERE assignment_id=? AND kind='STARTER'").param(assignmentId)
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).list()) {
            if (Language.fromFileName(f[0]).isPresent()) starter.add(new SourceFileInput(f[0], read(f[1])));
        }
        AnalysisResult res = engine.analyze(new AnalysisRequest(starter, inputs, config()));
        String notes;
        try {
            notes = json.writeValueAsString(res.notes());
        } catch (JsonProcessingException e) {
            notes = "[]";
        }
        final String notesJson = notes;
        return tx.execute(s -> {
            long runId = jdbc.sql("INSERT INTO plagiarism_runs(assignment_id, algorithm_version, config, submissions, candidate_pairs, elapsed_ms, notes) VALUES (?,?,?,?,?,?,?) RETURNING id")
                    .params(assignmentId, res.algorithmVersion(), res.config().fingerprintVersion(), res.stats().submissions(), res.stats().candidatePairs(), res.stats().elapsedMillis(), notesJson)
                    .query(Long.class).single();
            for (PairReport p : res.pairs()) persist(runId, assignmentId, p);
            return runId;
        });
    }

    private void persist(long runId, long assignmentId, PairReport p) {
        long a = Long.parseLong(p.submissionA()), b = Long.parseLong(p.submissionB());
        boolean swap = a > b;
        long sa = swap ? b : a, sb = swap ? a : b;
        String expl;
        try {
            expl = json.writeValueAsString(p.explanation());
        } catch (JsonProcessingException e) {
            expl = "[]";
        }
        long pid = jdbc.sql("""
                INSERT INTO similarity_pairs(run_id, assignment_id, submission_a, submission_b, state, distinctive_score, fingerprint_dice, weighted_dice, coverage_a, coverage_b,
                  matched_tokens, distinctive_tokens, longest_tile, suppressed_a, suppressed_b, rarity_active, explanation, ast_structural_score)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id""")
                .params(runId, assignmentId, sa, sb, p.state().name(), p.distinctiveScore(), p.fingerprintDice(), p.weightedDice(),
                        swap ? p.coverageB() : p.coverageA(), swap ? p.coverageA() : p.coverageB(), p.matchedTokens(), p.distinctiveMatchedTokens(), p.longestTile(),
                        swap ? p.suppressedTokensB() : p.suppressedTokensA(), swap ? p.suppressedTokensA() : p.suppressedTokensB(), p.rarityWeightingActive(), expl, p.astStructuralScore())
                .query(Long.class).single();
        for (Fragment f : p.fragments()) {
            jdbc.sql("INSERT INTO matched_fragments(pair_id, file_a, start_line_a, end_line_a, file_b, start_line_b, end_line_b, token_count, rarity_weight, distinctive) VALUES (?,?,?,?,?,?,?,?,?,?)")
                    .params(pid, swap ? f.fileB() : f.fileA(), swap ? f.startLineB() : f.startLineA(), swap ? f.endLineB() : f.endLineA(),
                            swap ? f.fileA() : f.fileB(), swap ? f.startLineA() : f.startLineB(), swap ? f.endLineA() : f.endLineB(),
                            f.tokenCount(), f.rarityWeight(), f.distinctive()).update();
        }
        if (p.structuralFragments() != null) {
            for (var sf : p.structuralFragments()) {
                jdbc.sql("INSERT INTO structural_fragments(pair_id, file_a, start_line_a, end_line_a, file_b, start_line_b, end_line_b, node_type, node_count) VALUES (?,?,?,?,?,?,?,?,?)")
                        .params(pid, swap ? sf.fileB() : sf.fileA(), swap ? sf.startLineB() : sf.startLineA(), swap ? sf.endLineB() : sf.endLineA(),
                                swap ? sf.fileA() : sf.fileB(), swap ? sf.startLineA() : sf.startLineB(), swap ? sf.endLineA() : sf.endLineB(),
                                sf.nodeType(), sf.nodeCount()).update();
            }
        }
    }
}
