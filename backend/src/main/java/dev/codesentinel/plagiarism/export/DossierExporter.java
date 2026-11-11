package dev.codesentinel.plagiarism.export;

import dev.codesentinel.plagiarism.PdfReportService;

/**
 * Strategy interface defining polymorphic export behavior for academic integrity dossiers.
 * Adheres to the Strategy Pattern, Abstraction, and Open/Closed Principle (OCP).
 */
public interface DossierExporter {

    /** Format key (e.g. "pdf", "html", "json"). */
    String format();

    /** MIME content type of the exported document. */
    String contentType();

    /** File extension for downloaded attachments. */
    String fileExtension();

    /** Generates the raw document bytes from the audit report data model. */
    byte[] export(PdfReportService.AuditReportData data);
}
