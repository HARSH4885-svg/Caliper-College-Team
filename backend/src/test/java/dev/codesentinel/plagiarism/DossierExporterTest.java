package dev.codesentinel.plagiarism;

import dev.codesentinel.plagiarism.export.DossierExportRegistry;
import dev.codesentinel.plagiarism.export.DossierExporter;
import dev.codesentinel.plagiarism.export.HtmlDossierExporter;
import dev.codesentinel.plagiarism.export.JsonDossierExporter;
import dev.codesentinel.plagiarism.export.PdfDossierExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DossierExporterTest {

    @Test
    @DisplayName("DossierExportRegistry resolves PDF, HTML, and JSON exporters and polymorphically generates dossier bytes")
    void registryResolvesExportersPolymorphically() {
        PdfReportService pdfService = new PdfReportService();
        PdfDossierExporter pdfExporter = new PdfDossierExporter(pdfService);
        HtmlDossierExporter htmlExporter = new HtmlDossierExporter();
        JsonDossierExporter jsonExporter = new JsonDossierExporter(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        DossierExportRegistry registry = new DossierExportRegistry(List.of(pdfExporter, htmlExporter, jsonExporter));

        assertEquals(3, registry.supportedFormats().size());
        assertTrue(registry.supportedFormats().containsAll(List.of("pdf", "html", "json")));

        // Test PDF Exporter
        assertTrue(registry.forFormat("pdf").isPresent());
        DossierExporter pdf = registry.forFormat("pdf").get();
        assertEquals("pdf", pdf.format());
        assertEquals("application/pdf", pdf.contentType());

        // Test HTML Exporter
        assertTrue(registry.forFormat("html").isPresent());
        DossierExporter html = registry.forFormat("html").get();
        assertEquals("html", html.format());
        assertEquals("text/html; charset=UTF-8", html.contentType());

        // Test JSON Exporter
        assertTrue(registry.forFormat("json").isPresent());
        DossierExporter json = registry.forFormat("json").get();
        assertEquals("json", json.format());
        assertEquals("application/json; charset=UTF-8", json.contentType());

        PlagiarismController.PartyDto partyA = new PlagiarismController.PartyDto(101L, 1L, "Alice Smith", 1);
        PlagiarismController.PartyDto partyB = new PlagiarismController.PartyDto(102L, 2L, "Bob Jones", 1);
        PlagiarismController.PairSummary summary = new PlagiarismController.PairSummary(
                55L, "HIGH_PRIORITY_REVIEW", 0.92, 0.88, 0.95, 340, 220, 45, partyA, partyB, null
        );
        PlagiarismController.PairDetail detail = new PlagiarismController.PairDetail(
                summary, 0.88, 0.90, 0.92, 0, 0, true,
                List.of("Shared 220 distinctive tokens in sorting routine."),
                List.of(), List.of(), List.of(), List.of(), List.of(), "Investigation signal only."
        );

        PdfReportService.AuditReportData data = new PdfReportService.AuditReportData(
                55L, 1L, 10L, "Binary Search Tree", "CS101", "Intro to Algorithms",
                "Prof. Alan Turing", detail, Instant.now()
        );

        // Verify PDF output
        byte[] pdfOutput = pdf.export(data);
        assertNotNull(pdfOutput);
        assertTrue(pdfOutput.length > 500, "Exported PDF bytes should be substantial");
        assertEquals('%', (char) pdfOutput[0]);
        assertEquals('P', (char) pdfOutput[1]);

        // Verify HTML output
        byte[] htmlOutput = html.export(data);
        assertNotNull(htmlOutput);
        String htmlStr = new String(htmlOutput, StandardCharsets.UTF_8);
        assertTrue(htmlStr.contains("<!DOCTYPE html>"));
        assertTrue(htmlStr.contains("Caliper Academic Integrity Dossier"));
        assertTrue(htmlStr.contains("Alice Smith"));
        assertTrue(htmlStr.contains("Bob Jones"));

        // Verify JSON output
        byte[] jsonOutput = json.export(data);
        assertNotNull(jsonOutput);
        String jsonStr = new String(jsonOutput, StandardCharsets.UTF_8);
        assertTrue(jsonStr.contains("\"pairId\" : 55"));
        assertTrue(jsonStr.contains("\"courseCode\" : \"CS101\""));
    }
}
