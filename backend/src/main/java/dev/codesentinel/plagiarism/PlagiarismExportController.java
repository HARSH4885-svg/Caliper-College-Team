package dev.codesentinel.plagiarism;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.storage.FileStorageProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Instructor-only exports of similarity evidence (FR-REPORT-04). Contains only what the instructor can already see in the
 * UI: pair metrics, explanation, matched line excerpts of the two students' own code and review history. It never touches
 * hidden tests or reference solutions. All cell/HTML content is escaped (student names are user-controlled).
 */
@RestController
@RequestMapping("/api")
public class PlagiarismExportController {
    private final PlagiarismController plag;
    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final FileStorageProvider storage;

    public PlagiarismExportController(PlagiarismController plag, JdbcClient jdbc, Access access, AuditService audit, FileStorageProvider storage) {
        this.plag = plag;
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.storage = storage;
    }

    /** Neutralises spreadsheet formula injection and quotes per RFC 4180. */
    static String csv(String v) {
        if (v == null) v = "";
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static ResponseEntity<byte[]> file(String body, String type, String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(body.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/assignments/{assignmentId}/plagiarism-report.csv")
    ResponseEntity<byte[]> csvReport(@PathVariable long assignmentId) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentId, p);
        PlagiarismController.PairList list = plag.list(assignmentId, null);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(csv(PlagiarismController.DISCLAIMER)).append("\r\n");
        if (list.run() != null) sb.append("# run ").append(list.run().id()).append(" settings ").append(csv(list.run().config())).append("\r\n");
        sb.append("pair_id,state,evidence_score,fingerprint_dice,distinctive_tokens,matched_tokens,longest_tile,student_a,submission_a,student_b,submission_b,instructor_decision,instructor_note\r\n");
        for (PlagiarismController.PairSummary s : list.pairs()) {
            sb.append(s.id()).append(',').append(s.state()).append(',').append(String.format(Locale.ROOT, "%.4f", s.score())).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", s.fingerprintDice())).append(',').append(s.distinctiveTokens()).append(',').append(s.matchedTokens()).append(',').append(s.longestTile()).append(',')
                    .append(csv(s.a().studentName())).append(',').append(s.a().submissionId()).append(',').append(csv(s.b().studentName())).append(',').append(s.b().submissionId()).append(',')
                    .append(csv(s.latestReview() == null ? "" : s.latestReview().decision())).append(',').append(csv(s.latestReview() == null ? "" : s.latestReview().note())).append("\r\n");
        }
        audit.log(p.id(), "REPORT_EXPORT_CSV", "assignment", assignmentId, null);
        return file(sb.toString(), "text/csv;charset=UTF-8", "similarity-assignment-" + assignmentId + ".csv");
    }

    private List<String> lines(long submissionId, String path) {
        var key = jdbc.sql("SELECT storage_key FROM submission_files WHERE submission_id=? AND path=?").params(submissionId, path).query(String.class).optional();
        if (key.isEmpty()) return List.of();
        try (InputStream in = storage.read(key.get())) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1));
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String esc(Object o) {
        return HtmlUtils.htmlEscape(String.valueOf(o == null ? "" : o));
    }

    private static String excerpt(List<String> src, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(1, from); i <= Math.min(to, src.size()); i++) sb.append(String.format("%4d  %s%n", i, esc(src.get(i - 1).replace("\r", ""))));
        return sb.toString();
    }

    @GetMapping("/plagiarism-pairs/{pairId}/report.html")
    ResponseEntity<byte[]> htmlReport(@PathVariable long pairId) {
        Principal p = CurrentUser.instructor();
        PlagiarismController.PairDetail d = plag.detail(pairId); // performs the ownership check (404 otherwise)
        var s = d.summary();
        StringBuilder h = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Similarity evidence - pair ").append(pairId)
                .append("</title><style>body{font:15px/1.5 system-ui,sans-serif;max-width:960px;margin:24px auto;padding:0 16px;color:#1c2230}pre{background:#f4f6fa;padding:8px;overflow:auto;font-size:12px}")
                .append("table{border-collapse:collapse}td,th{border:1px solid #ccd;padding:4px 8px;text-align:left}.note{background:#e8ecff;padding:10px 14px;border-radius:6px}</style></head><body>");
        h.append("<h1>Similarity evidence: ").append(esc(s.a().studentName())).append(" and ").append(esc(s.b().studentName())).append("</h1>");
        h.append("<p class=\"note\">").append(esc(d.disclaimer())).append("</p>");
        h.append("<p><strong>State:</strong> ").append(esc(s.state())).append(" (investigation state, not a verdict). <strong>Evidence score:</strong> ")
                .append(String.format(Locale.ROOT, "%.1f%%", s.score() * 100)).append("</p><table><tr><th>Metric</th><th>Value</th></tr>");
        String[][] rows = {{"Fingerprint Dice", String.format(Locale.ROOT, "%.3f", s.fingerprintDice())}, {"Weighted Dice", String.format(Locale.ROOT, "%.3f", d.weightedDice())},
                {"Distinctive matched tokens", "" + s.distinctiveTokens()}, {"All matched tokens", "" + s.matchedTokens()}, {"Longest matching run (tokens)", "" + s.longestTile()},
                {"Coverage of A / B", String.format(Locale.ROOT, "%.1f%% / %.1f%%", d.coverageA() * 100, d.coverageB() * 100)}, {"Rarity weighting active", "" + d.rarityActive()}};
        for (String[] r : rows) h.append("<tr><td>").append(esc(r[0])).append("</td><td>").append(esc(r[1])).append("</td></tr>");
        h.append("</table><h2>Why this pair was surfaced</h2><ul>");
        for (String e : d.explanation()) h.append("<li>").append(esc(e)).append("</li>");
        h.append("</ul><h2>Matched fragments</h2>");
        int n = 1;
        for (PlagiarismController.FragmentDto f : d.fragments()) {
            h.append("<h3>Fragment ").append(n++).append(" (").append(f.tokenCount()).append(" tokens, ").append(f.distinctive() ? "distinctive" : "common / starter-like").append(")</h3>");
            h.append("<table><tr><th>").append(esc(s.a().studentName())).append(": ").append(esc(f.fileA())).append(" lines ").append(f.startLineA()).append("-").append(f.endLineA())
                    .append("</th><th>").append(esc(s.b().studentName())).append(": ").append(esc(f.fileB())).append(" lines ").append(f.startLineB()).append("-").append(f.endLineB()).append("</th></tr><tr><td><pre>")
                    .append(excerpt(lines(s.a().submissionId(), f.fileA()), f.startLineA(), f.endLineA())).append("</pre></td><td><pre>")
                    .append(excerpt(lines(s.b().submissionId(), f.fileB()), f.startLineB(), f.endLineB())).append("</pre></td></tr></table>");
        }
        h.append("<h2>Instructor decisions</h2>");
        if (d.reviews().isEmpty()) h.append("<p>None recorded.</p>");
        else {
            h.append("<ul>");
            for (var r : d.reviews()) h.append("<li>").append(esc(r.decision())).append(" by ").append(esc(r.reviewer())).append(" at ").append(esc(r.at())).append(r.note() == null ? "" : " - " + esc(r.note())).append("</li>");
            h.append("</ul>");
        }
        h.append("<p style=\"color:#667\">Generated by CodeSentinel. Settings: submission ids ").append(s.a().submissionId()).append(" and ").append(s.b().submissionId()).append(".</p></body></html>");
        audit.log(p.id(), "REPORT_EXPORT_HTML", "pair", pairId, null);
        return file(h.toString(), "text/html;charset=UTF-8", "similarity-pair-" + pairId + ".html");
    }
}
