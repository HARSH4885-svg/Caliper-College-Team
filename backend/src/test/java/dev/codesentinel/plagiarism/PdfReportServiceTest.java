package dev.codesentinel.plagiarism;

import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfReportServiceTest {

    @Test
    @DisplayName("Generates publication-grade academic integrity PDF report with valid %PDF header and structural metadata")
    void testGenerateAuditReport() throws IOException {
        PdfReportService service = new PdfReportService();

        PlagiarismController.PartyDto partyA = new PlagiarismController.PartyDto(101L, 11L, "Alice Johnson", 1);
        PlagiarismController.PartyDto partyB = new PlagiarismController.PartyDto(102L, 12L, "Bob Smith", 1);

        PlagiarismController.ReviewDto review = new PlagiarismController.ReviewDto(
                "CONFIRMED_CONCERN",
                "Submissions share identical recursive AST syntax subtree and identical variable flow.",
                "Prof. Ada Lovelace",
                Instant.now()
        );

        PlagiarismController.PairSummary summary = new PlagiarismController.PairSummary(
                42L, "HIGH_PRIORITY_REVIEW", 0.94, 0.91, 0.96, 320, 290, 85,
                partyA, partyB, review
        );

        List<PlagiarismController.FragmentDto> fragments = List.of(
                new PlagiarismController.FragmentDto("Tree.java", 15, 45, "Tree.java", 18, 48, 85, 0.95, true),
                new PlagiarismController.FragmentDto("Tree.java", 55, 80, "Tree.java", 60, 85, 55, 0.90, true)
        );

        List<PlagiarismController.StructuralFragmentDto> structuralFrags = List.of(
                new PlagiarismController.StructuralFragmentDto("Tree.java", 15, 45, "Tree.java", 18, 48, "method_declaration", 42),
                new PlagiarismController.StructuralFragmentDto("Tree.java", 22, 38, "Tree.java", 25, 41, "while_statement", 28)
        );

        List<String> explanation = List.of(
                "Fingerprint Dice 0.91: 42 shared fingerprints out of 46 / 48.",
                "AST Structural Analysis: 0.96 similarity (45 shared structural syntax subtrees).",
                "High-priority review: distinctive score 0.94 and fingerprint Dice 0.91 both exceed 0.80."
        );

        PlagiarismController.PairDetail detail = new PlagiarismController.PairDetail(
                summary, 0.89, 0.92, 0.88, 12, 14, true,
                explanation, fragments, structuralFrags,
                List.of(new PlagiarismController.FileRef(1L, "Tree.java")),
                List.of(new PlagiarismController.FileRef(2L, "Tree.java")),
                List.of(review),
                PlagiarismController.DISCLAIMER
        );

        PdfReportService.AuditReportData data = new PdfReportService.AuditReportData(
                42L,
                5L,
                10L,
                "Binary Search Trees & Balancing",
                "CS-201",
                "Data Structures and Algorithms",
                "Prof. Ada Lovelace",
                detail,
                Instant.now()
        );

        byte[] pdf = service.generateAuditReport(data);

        assertNotNull(pdf);
        assertTrue(pdf.length > 2000, "PDF size should be substantial (got " + pdf.length + " bytes)");

        // Check PDF Magic Number (%PDF-)
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "Should start with standard PDF magic bytes %PDF-");

        // Verify readable by OpenPDF reader
        PdfReader reader = new PdfReader(pdf);
        assertTrue(reader.getNumberOfPages() >= 1, "Should have at least 1 page");
        reader.close();
    }
}
