package dev.codesentinel.plagiarism;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates formal, publication-grade Academic Integrity Dossier PDF reports
 * documenting lexical and AST structural similarity evidence for institutional review boards.
 */
@Service
public class PdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    private static final Color PRIMARY_COLOR = new Color(24, 24, 27);      // zinc-900
    private static final Color ACCENT_COLOR = new Color(15, 118, 110);     // teal-700
    private static final Color ALERT_COLOR = new Color(185, 28, 28);       // red-700
    private static final Color MUTED_BG = new Color(244, 244, 245);        // zinc-100
    private static final Color BORDER_COLOR = new Color(228, 228, 231);    // zinc-200

    public record AuditReportData(
            long pairId,
            long runId,
            long assignmentId,
            String assignmentTitle,
            String courseCode,
            String courseName,
            String instructorName,
            PlagiarismController.PairDetail detail,
            Instant generatedAt
    ) {}

    /**
     * Builds a formal PDF byte array for an academic integrity dossier.
     */
    public byte[] generateAuditReport(AuditReportData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY_COLOR);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(113, 113, 122));
            Font sectionHeadingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, PRIMARY_COLOR);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PRIMARY_COLOR);
            Font codeFont = FontFactory.getFont(FontFactory.COURIER, 8, PRIMARY_COLOR);
            Font disclaimerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(113, 113, 122));

            // --- HEADER ---
            Paragraph title = new Paragraph("CALIPER", titleFont);
            title.setSpacingAfter(2);
            doc.add(title);

            Paragraph subtitle = new Paragraph("ACADEMIC INTEGRITY FORENSIC AUDIT DOSSIER", sectionHeadingFont);
            subtitle.setSpacingAfter(4);
            doc.add(subtitle);

            String timestamp = DATE_FMT.format(data.generatedAt() != null ? data.generatedAt() : Instant.now());
            Paragraph metaHeader = new Paragraph("Report Reference: CALIPER-PAIR-" + data.pairId() + " | Generated: " + timestamp, subtitleFont);
            metaHeader.setSpacingAfter(14);
            doc.add(metaHeader);

            // Divider line
            doc.add(createDivider());

            // --- CASE SUMMARY TABLE ---
            doc.add(new Paragraph("1. Case Metadata & Academic Context", sectionHeadingFont));
            doc.add(createSpacer(6));

            PdfPTable metaTable = new PdfPTable(4);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{22, 28, 22, 28});

            addCell(metaTable, "Course:", boldFont, MUTED_BG);
            addCell(metaTable, data.courseCode() + " - " + data.courseName(), bodyFont, null);
            addCell(metaTable, "Assignment:", boldFont, MUTED_BG);
            addCell(metaTable, data.assignmentTitle(), bodyFont, null);

            addCell(metaTable, "Instructor of Record:", boldFont, MUTED_BG);
            addCell(metaTable, data.instructorName() != null ? data.instructorName() : "Faculty Reviewer", bodyFont, null);
            addCell(metaTable, "Evaluation State:", boldFont, MUTED_BG);
            Font stateFont = data.detail().summary().state().contains("HIGH") ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ALERT_COLOR) : boldFont;
            addCell(metaTable, data.detail().summary().state(), stateFont, null);

            doc.add(metaTable);
            doc.add(createSpacer(12));

            // --- PARTY COMPARISON TABLE ---
            doc.add(new Paragraph("2. Submissions Under Review", sectionHeadingFont));
            doc.add(createSpacer(6));

            PdfPTable partiesTable = new PdfPTable(2);
            partiesTable.setWidthPercentage(100);
            partiesTable.setWidths(new float[]{50, 50});

            // Party A
            PdfPCell cellA = new PdfPCell();
            cellA.setPadding(8);
            cellA.setBorderColor(BORDER_COLOR);
            cellA.setBackgroundColor(MUTED_BG);
            cellA.addElement(new Paragraph("PARTY A: " + data.detail().summary().a().studentName(), boldFont));
            cellA.addElement(new Paragraph("Student ID: " + data.detail().summary().a().studentId() + " | Submission #" + data.detail().summary().a().submissionId(), bodyFont));
            cellA.addElement(new Paragraph("Attempt: #" + data.detail().summary().a().attempt() + " | Informative Tokens: " + data.detail().summary().matchedTokens(), bodyFont));
            cellA.addElement(new Paragraph("Coverage: " + String.format("%.1f%%", data.detail().coverageA() * 100), bodyFont));
            partiesTable.addCell(cellA);

            // Party B
            PdfPCell cellB = new PdfPCell();
            cellB.setPadding(8);
            cellB.setBorderColor(BORDER_COLOR);
            cellB.setBackgroundColor(MUTED_BG);
            cellB.addElement(new Paragraph("PARTY B: " + data.detail().summary().b().studentName(), boldFont));
            cellB.addElement(new Paragraph("Student ID: " + data.detail().summary().b().studentId() + " | Submission #" + data.detail().summary().b().submissionId(), bodyFont));
            cellB.addElement(new Paragraph("Attempt: #" + data.detail().summary().b().attempt() + " | Informative Tokens: " + data.detail().summary().matchedTokens(), bodyFont));
            cellB.addElement(new Paragraph("Coverage: " + String.format("%.1f%%", data.detail().coverageB() * 100), bodyFont));
            partiesTable.addCell(cellB);

            doc.add(partiesTable);
            doc.add(createSpacer(12));

            // --- DUAL METRIC SCORES ---
            doc.add(new Paragraph("3. Dual-Engine Forensic Metrics", sectionHeadingFont));
            doc.add(createSpacer(6));

            PdfPTable metricTable = new PdfPTable(4);
            metricTable.setWidthPercentage(100);
            metricTable.setWidths(new float[]{25, 25, 25, 25});

            addMetricCell(metricTable, "Lexical Distinctive Score", String.format("%.2f", data.detail().summary().score()), "Winnowing + RKR-GST", boldFont, bodyFont);
            addMetricCell(metricTable, "AST Structural Score", String.format("%.2f", data.detail().summary().astStructuralScore()), "Tree-sitter Syntax Tree", boldFont, bodyFont);
            addMetricCell(metricTable, "Fingerprint Dice", String.format("%.2f", data.detail().summary().fingerprintDice()), "Raw Multiset Overlap", boldFont, bodyFont);
            addMetricCell(metricTable, "Distinctive Tokens", String.valueOf(data.detail().summary().distinctiveTokens()), "Non-common tokens", boldFont, bodyFont);

            doc.add(metricTable);
            doc.add(createSpacer(12));

            // --- MATCHED FRAGMENTS TABLE ---
            doc.add(new Paragraph("4. Aligned Evidence & Code Fragments", sectionHeadingFont));
            doc.add(createSpacer(6));

            PdfPTable fragTable = new PdfPTable(5);
            fragTable.setWidthPercentage(100);
            fragTable.setWidths(new float[]{24, 16, 24, 16, 20});

            addHeaderCell(fragTable, "File A", boldFont);
            addHeaderCell(fragTable, "Lines (A)", boldFont);
            addHeaderCell(fragTable, "File B", boldFont);
            addHeaderCell(fragTable, "Lines (B)", boldFont);
            addHeaderCell(fragTable, "Evidence Type", boldFont);

            // Lexical fragments
            List<PlagiarismController.FragmentDto> fragments = data.detail().fragments();
            int maxShow = Math.min(12, fragments.size());
            for (int i = 0; i < maxShow; i++) {
                var f = fragments.get(i);
                addCell(fragTable, f.fileA(), codeFont, null);
                addCell(fragTable, f.startLineA() + " - " + f.endLineA(), codeFont, null);
                addCell(fragTable, f.fileB(), codeFont, null);
                addCell(fragTable, f.startLineB() + " - " + f.endLineB(), codeFont, null);
                addCell(fragTable, "Lexical (" + f.tokenCount() + " toks)", bodyFont, null);
            }

            // AST Structural fragments
            if (data.detail().structuralFragments() != null) {
                int maxAst = Math.min(8, data.detail().structuralFragments().size());
                for (int i = 0; i < maxAst; i++) {
                    var sf = data.detail().structuralFragments().get(i);
                    addCell(fragTable, sf.fileA(), codeFont, MUTED_BG);
                    addCell(fragTable, sf.startLineA() + " - " + sf.endLineA(), codeFont, MUTED_BG);
                    addCell(fragTable, sf.fileB(), codeFont, MUTED_BG);
                    addCell(fragTable, sf.startLineB() + " - " + sf.endLineB(), codeFont, MUTED_BG);
                    addCell(fragTable, "AST: " + sf.nodeType() + " (" + sf.nodeCount() + " nodes)", bodyFont, MUTED_BG);
                }
            }

            if (fragments.isEmpty() && (data.detail().structuralFragments() == null || data.detail().structuralFragments().isEmpty())) {
                PdfPCell empty = new PdfPCell(new Phrase("No contiguous tile fragments recorded above significance threshold.", bodyFont));
                empty.setColspan(5);
                empty.setPadding(8);
                fragTable.addCell(empty);
            }

            doc.add(fragTable);
            doc.add(createSpacer(12));

            // --- FINDINGS & EXPLANATION ---
            doc.add(new Paragraph("5. Forensic Engine Findings", sectionHeadingFont));
            doc.add(createSpacer(6));

            com.lowagie.text.List bulletList = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
            bulletList.setSymbolIndent(12);
            for (String explanation : data.detail().explanation()) {
                bulletList.add(new ListItem(explanation, bodyFont));
            }
            doc.add(bulletList);
            doc.add(createSpacer(12));

            // --- INSTRUCTOR REVIEW / HEARING ACTIONS ---
            if (data.detail().reviews() != null && !data.detail().reviews().isEmpty()) {
                doc.add(new Paragraph("6. Recorded Institutional Review", sectionHeadingFont));
                doc.add(createSpacer(6));
                for (var r : data.detail().reviews()) {
                    Paragraph rev = new Paragraph("Decision: " + r.decision() + " by " + r.reviewer() + " on " + DATE_FMT.format(r.at()), boldFont);
                    doc.add(rev);
                    if (r.note() != null && !r.note().isBlank()) {
                        Paragraph note = new Paragraph("Notes: " + r.note(), bodyFont);
                        note.setSpacingAfter(6);
                        doc.add(note);
                    }
                }
                doc.add(createSpacer(8));
            }

            // --- DISCLAIMER ---
            doc.add(createDivider());
            Paragraph disc = new Paragraph(
                    "NOTICE & INSTITUTIONAL DISCLAIMER: " + PlagiarismController.DISCLAIMER +
                    " This dossier serves as an investigative artifact for academic review boards and does not constitute a disciplinary ruling.",
                    disclaimerFont
            );
            disc.setSpacingBefore(8);
            doc.add(disc);

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render academic integrity PDF dossier: " + e.getMessage(), e);
        }
    }

    private static void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setPadding(5);
        c.setBorderColor(BORDER_COLOR);
        if (bg != null) c.setBackgroundColor(bg);
        table.addCell(c);
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(6);
        c.setBackgroundColor(MUTED_BG);
        c.setBorderColor(BORDER_COLOR);
        table.addCell(c);
    }

    private static void addMetricCell(PdfPTable table, String label, String value, String subtext, Font boldFont, Font bodyFont) {
        PdfPCell c = new PdfPCell();
        c.setPadding(6);
        c.setBorderColor(BORDER_COLOR);
        c.addElement(new Paragraph(label, bodyFont));
        Paragraph valP = new Paragraph(value, boldFont);
        valP.setSpacingAfter(2);
        c.addElement(valP);
        c.addElement(new Paragraph(subtext, FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(113, 113, 122))));
        table.addCell(c);
    }

    private static Paragraph createSpacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private static PdfPTable createDivider() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(1.2f);
        cell.setFixedHeight(6f);
        table.addCell(cell);
        return table;
    }
}
