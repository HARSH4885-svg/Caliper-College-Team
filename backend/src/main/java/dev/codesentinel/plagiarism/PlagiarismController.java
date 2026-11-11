package dev.codesentinel.plagiarism;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.plagiarism.export.DossierExportRegistry;
import dev.codesentinel.plagiarism.export.DossierExporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Instructor-only. Responses are evidence for human review; no field implies a finding of misconduct. */
@RestController
@RequestMapping("/api")
public class PlagiarismController {
    public static final String DISCLAIMER = "Similarity is an investigation signal, not a finding. Short or common code can match by chance; only the instructor can record an academic decision.";

    public record PartyDto(long submissionId, long studentId, String studentName, int attempt) {
    }

    public record ReviewDto(String decision, String note, String reviewer, Instant at) {
    }

    public record PairSummary(long id, String state, double score, double fingerprintDice, double astStructuralScore, int matchedTokens, int distinctiveTokens, int longestTile,
                              PartyDto a, PartyDto b, ReviewDto latestReview) {
    }

    public record RunInfo(long id, Instant at, String algorithmVersion, String config, int submissions, int candidatePairs, long elapsedMs, List<Map<String, Object>> notes) {
    }

    public record PairList(RunInfo run, List<PairSummary> pairs, String disclaimer) {
    }

    public record FragmentDto(String fileA, int startLineA, int endLineA, String fileB, int startLineB, int endLineB, int tokenCount, double rarityWeight, boolean distinctive) {
    }

    public record StructuralFragmentDto(String fileA, int startLineA, int endLineA, String fileB, int startLineB, int endLineB, String nodeType, int nodeCount) {
    }

    public record FileRef(long id, String path) {
    }

    public record PairDetail(PairSummary summary, double weightedDice, double coverageA, double coverageB, int suppressedA, int suppressedB, boolean rarityActive,
                             List<String> explanation, List<FragmentDto> fragments, List<StructuralFragmentDto> structuralFragments,
                             List<FileRef> filesA, List<FileRef> filesB, List<ReviewDto> reviews, String disclaimer) {
    }

    record ReviewRequest(@NotBlank @Pattern(regexp = "CONFIRMED_CONCERN|DISMISSED|INCONCLUSIVE") String decision, @Size(max = 2000) String note) {
    }

    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final PlagiarismService service;
    private final PdfReportService pdfService;
    private final DossierExportRegistry exportRegistry;
    private final ObjectMapper json = new ObjectMapper();

    public PlagiarismController(JdbcClient jdbc, Access access, AuditService audit,
                                PlagiarismService service, PdfReportService pdfService,
                                DossierExportRegistry exportRegistry) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.service = service;
        this.pdfService = pdfService;
        this.exportRegistry = exportRegistry;
    }

    @PostMapping("/assignments/{assignmentId}/plagiarism/run")
    PairList run(@PathVariable long assignmentId) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentId, p);
        long runId = service.run(assignmentId);
        audit.log(p.id(), "PLAGIARISM_RUN", "assignment", assignmentId, "run " + runId);
        return list(assignmentId, null);
    }

    @GetMapping("/assignments/{assignmentId}/plagiarism-pairs")
    public PairList list(@PathVariable long assignmentId, @RequestParam(required = false) String state) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentId, p);
        var runId = jdbc.sql("SELECT max(id) FROM plagiarism_runs WHERE assignment_id=?").param(assignmentId).query(Long.class).optional();
        if (runId.isEmpty() || runId.get() == 0) return new PairList(null, List.of(), DISCLAIMER);
        long rid = runId.get();
        RunInfo info = jdbc.sql("SELECT id, created_at, algorithm_version, config, submissions, candidate_pairs, elapsed_ms, notes FROM plagiarism_runs WHERE id=?").param(rid)
                .query((rs, n) -> new RunInfo(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getLong(7), notes(rs.getString(8)))).single();
        List<Long> ids = jdbc.sql("SELECT id FROM similarity_pairs WHERE run_id=? AND (?::text IS NULL OR state=?) ORDER BY distinctive_score DESC, id").params(rid, state, state).query(Long.class).list();
        return new PairList(info, ids.stream().map(this::summary).toList(), DISCLAIMER);
    }

    private List<Map<String, Object>> notes(String s) {
        try {
            return json.readValue(s == null ? "[]" : s, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private PartyDto party(long sid) {
        return jdbc.sql("SELECT s.id, u.id, u.display_name, s.attempt FROM submissions s JOIN users u ON u.id=s.student_id WHERE s.id=?").param(sid)
                .query((rs, n) -> new PartyDto(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4))).single();
    }

    /** The newest review for the same two submissions, even if recorded against an earlier run. */
    private ReviewDto latestReview(long a, long b) {
        return jdbc.sql("""
                SELECT r.decision, r.note, u.display_name, r.created_at FROM plagiarism_reviews r JOIN similarity_pairs sp ON sp.id=r.pair_id JOIN users u ON u.id=r.reviewer_id
                WHERE sp.submission_a=? AND sp.submission_b=? ORDER BY r.created_at DESC, r.id DESC LIMIT 1""").params(a, b)
                .query((rs, n) -> new ReviewDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant())).optional().orElse(null);
    }

    private PairSummary summary(long id) {
        return jdbc.sql("SELECT id, state, distinctive_score, fingerprint_dice, ast_structural_score, matched_tokens, distinctive_tokens, longest_tile, submission_a, submission_b FROM similarity_pairs WHERE id=?").param(id)
                .query((rs, n) -> new PairSummary(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), rs.getInt(6), rs.getInt(7), rs.getInt(8),
                        party(rs.getLong(9)), party(rs.getLong(10)), latestReview(rs.getLong(9), rs.getLong(10)))).optional().orElseThrow(ApiException::notFound);
    }

    private long assignmentOfPair(long pairId) {
        return jdbc.sql("SELECT assignment_id FROM similarity_pairs WHERE id=?").param(pairId).query(Long.class).optional().orElseThrow(ApiException::notFound);
    }

    @GetMapping("/plagiarism-pairs/{pairId}")
    public PairDetail detail(@PathVariable long pairId) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentOfPair(pairId), p);
        PairSummary s = summary(pairId);
        var m = jdbc.sql("SELECT weighted_dice, coverage_a, coverage_b, suppressed_a, suppressed_b, rarity_active, explanation FROM similarity_pairs WHERE id=?").param(pairId)
                .query((rs, n) -> new Object[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5), rs.getBoolean(6), rs.getString(7)}).single();
        List<String> explanation;
        try {
            explanation = json.readValue((String) m[6], new TypeReference<>() {
            });
        } catch (Exception e) {
            explanation = List.of();
        }
        List<FragmentDto> frags = jdbc.sql("SELECT file_a, start_line_a, end_line_a, file_b, start_line_b, end_line_b, token_count, rarity_weight, distinctive FROM matched_fragments WHERE pair_id=? ORDER BY start_line_a, id")
                .param(pairId).query((rs, n) -> new FragmentDto(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getDouble(8), rs.getBoolean(9))).list();
        List<StructuralFragmentDto> structFrags = jdbc.sql("SELECT file_a, start_line_a, end_line_a, file_b, start_line_b, end_line_b, node_type, node_count FROM structural_fragments WHERE pair_id=? ORDER BY start_line_a, id")
                .param(pairId).query((rs, n) -> new StructuralFragmentDto(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getInt(8))).list();
        List<ReviewDto> reviews = jdbc.sql("""
                SELECT r.decision, r.note, u.display_name, r.created_at FROM plagiarism_reviews r JOIN users u ON u.id=r.reviewer_id JOIN similarity_pairs sp ON sp.id=r.pair_id
                WHERE sp.submission_a=? AND sp.submission_b=? ORDER BY r.created_at DESC, r.id DESC""").params(s.a().submissionId(), s.b().submissionId())
                .query((rs, n) -> new ReviewDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant())).list();
        return new PairDetail(s, (Double) m[0], (Double) m[1], (Double) m[2], (Integer) m[3], (Integer) m[4], (Boolean) m[5], explanation, frags, structFrags,
                files(s.a().submissionId()), files(s.b().submissionId()), reviews, DISCLAIMER);
    }

    private List<FileRef> files(long sid) {
        return jdbc.sql("SELECT id, path FROM submission_files WHERE submission_id=? ORDER BY path").param(sid).query((rs, n) -> new FileRef(rs.getLong(1), rs.getString(2))).list();
    }

    @PostMapping("/plagiarism-pairs/{pairId}/review")
    PairDetail review(@PathVariable long pairId, @Valid @RequestBody ReviewRequest r) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentOfPair(pairId), p);
        jdbc.sql("INSERT INTO plagiarism_reviews(pair_id, reviewer_id, decision, note) VALUES (?,?,?,?)").params(pairId, p.id(), r.decision(), r.note()).update();
        audit.log(p.id(), "PLAGIARISM_REVIEW", "pair", pairId, r.decision());
        return detail(pairId);
    }

    @GetMapping(value = "/plagiarism-pairs/{pairId}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdfReport(@PathVariable long pairId) {
        return exportDossier(pairId, "pdf");
    }

    @GetMapping(value = "/plagiarism-pairs/{pairId}/dossier")
    public ResponseEntity<byte[]> exportDossier(@PathVariable long pairId,
                                                @RequestParam(value = "format", defaultValue = "pdf") String format) {
        Principal p = CurrentUser.instructor();
        long assignmentId = assignmentOfPair(pairId);
        access.requireAssignmentOwner(assignmentId, p);

        PairDetail detail = detail(pairId);

        var asgInfo = jdbc.sql("""
                SELECT a.title, c.code, c.name, u.display_name, sp.run_id
                FROM similarity_pairs sp
                JOIN assignments a ON a.id = sp.assignment_id
                JOIN courses c ON c.id = a.course_id
                JOIN users u ON u.id = c.instructor_id
                WHERE sp.id = ?
                """).param(pairId).query((rs, n) -> new String[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), String.valueOf(rs.getLong(5))
        }).single();

        PdfReportService.AuditReportData data = new PdfReportService.AuditReportData(
                pairId,
                Long.parseLong(asgInfo[4]),
                assignmentId,
                asgInfo[0],
                asgInfo[1],
                asgInfo[2],
                asgInfo[3],
                detail,
                Instant.now()
        );

        DossierExporter exporter = exportRegistry.forFormat(format)
                .orElseThrow(() -> ApiException.bad("Unsupported dossier format: " + format + ". Supported: " + exportRegistry.supportedFormats()));
        byte[] bytes = exporter.export(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"caliper-audit-report-pair-" + pairId + "." + exporter.fileExtension() + "\"")
                .contentType(MediaType.parseMediaType(exporter.contentType()))
                .body(bytes);
    }
}
