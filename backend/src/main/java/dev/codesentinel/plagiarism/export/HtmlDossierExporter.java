package dev.codesentinel.plagiarism.export;

import dev.codesentinel.plagiarism.PdfReportService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Concrete strategy implementing semantic HTML export for academic dossiers.
 * Enables in-browser review and archival without PDF plugins.
 */
@Component
public class HtmlDossierExporter implements DossierExporter {

    @Override
    public String format() {
        return "html";
    }

    @Override
    public String contentType() {
        return "text/html; charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return "html";
    }

    @Override
    public byte[] export(PdfReportService.AuditReportData data) {
        var summary = data.detail().summary();
        var partyA = summary.a();
        var partyB = summary.b();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<title>Caliper Academic Integrity Dossier - Pair #").append(data.pairId()).append("</title>\n")
                .append("<style>\n")
                .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.5; color: #1e293b; max-width: 900px; margin: 40px auto; padding: 0 20px; }\n")
                .append(".header { border-bottom: 2px solid #0f172a; padding-bottom: 16px; margin-bottom: 24px; }\n")
                .append("h1 { margin: 0 0 8px; font-size: 24px; color: #0f172a; }\n")
                .append(".meta { color: #64748b; font-size: 14px; }\n")
                .append(".score-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin: 20px 0; display: flex; gap: 32px; }\n")
                .append(".score-item { text-align: center; }\n")
                .append(".score-val { font-size: 28px; font-weight: 700; color: #0f172a; }\n")
                .append(".score-lbl { font-size: 12px; text-transform: uppercase; color: #64748b; }\n")
                .append(".audit-section { margin-top: 24px; padding: 16px; background: #f1f5f9; border-radius: 8px; }\n")
                .append("table { width: 100%; border-collapse: collapse; margin-top: 16px; }\n")
                .append("th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #cbd5e1; font-size: 14px; }\n")
                .append("th { background: #e2e8f0; font-weight: 600; }\n")
                .append("ul { margin: 8px 0; padding-left: 20px; }\n")
                .append(".disclaimer { margin-top: 40px; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; padding-top: 16px; }\n")
                .append("</style>\n</head>\n<body>\n");

        sb.append("<div class=\"header\">\n")
                .append("<h1>CALIPER</h1>\n")
                .append("<h2>ACADEMIC INTEGRITY FORENSIC AUDIT DOSSIER</h2>\n")
                .append("<div class=\"meta\">Course: <strong>").append(escape(data.courseCode())).append(" - ").append(escape(data.courseName())).append("</strong> &bull; Assignment: <strong>").append(escape(data.assignmentTitle())).append("</strong> &bull; Generated: ").append(DateTimeFormatter.ISO_INSTANT.format(data.generatedAt())).append("</div>\n")
                .append("</div>\n");

        sb.append("<div class=\"score-box\">\n")
                .append("<div class=\"score-item\"><div class=\"score-val\">").append(String.format("%.1f%%", summary.score() * 100.0)).append("</div><div class=\"score-lbl\">Combined Score</div></div>\n")
                .append("<div class=\"score-item\"><div class=\"score-val\">").append(String.format("%.1f%%", summary.fingerprintDice() * 100.0)).append("</div><div class=\"score-lbl\">Lexical (Winnowing)</div></div>\n")
                .append("<div class=\"score-item\"><div class=\"score-val\">").append(String.format("%.1f%%", summary.astStructuralScore() * 100.0)).append("</div><div class=\"score-lbl\">Structural (AST)</div></div>\n")
                .append("</div>\n");

        sb.append("<h2>Compared Submissions</h2>\n")
                .append("<table>\n<tr><th>Role</th><th>Student Name</th><th>Student ID</th><th>Submission ID</th><th>Attempt</th><th>Coverage</th></tr>\n")
                .append("<tr><td>Party A</td><td>").append(escape(partyA.studentName())).append("</td><td>").append(partyA.studentId()).append("</td><td>#").append(partyA.submissionId()).append("</td><td>#").append(partyA.attempt()).append("</td><td>").append(String.format("%.1f%%", data.detail().coverageA() * 100.0)).append("</td></tr>\n")
                .append("<tr><td>Party B</td><td>").append(escape(partyB.studentName())).append("</td><td>").append(partyB.studentId()).append("</td><td>#").append(partyB.submissionId()).append("</td><td>#").append(partyB.attempt()).append("</td><td>").append(String.format("%.1f%%", data.detail().coverageB() * 100.0)).append("</td></tr>\n")
                .append("</table>\n");

        sb.append("<div class=\"audit-section\">\n")
                .append("<h3>Evaluation State & Signals</h3>\n")
                .append("<p><strong>Triage State:</strong> ").append(escape(summary.state())).append("</p>\n");
        if (data.detail().explanation() != null && !data.detail().explanation().isEmpty()) {
            sb.append("<h4>Observed Detection Explanations:</h4>\n<ul>\n");
            for (String sig : data.detail().explanation()) {
                sb.append("<li>").append(escape(sig)).append("</li>\n");
            }
            sb.append("</ul>\n");
        }
        sb.append("</div>\n");

        sb.append("<div class=\"disclaimer\">\n")
                .append("NOTICE: ").append(escape(data.detail().disclaimer())).append("\n")
                .append("</div>\n</body>\n</html>");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
