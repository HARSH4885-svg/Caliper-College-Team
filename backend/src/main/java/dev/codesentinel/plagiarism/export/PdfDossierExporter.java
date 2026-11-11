package dev.codesentinel.plagiarism.export;

import dev.codesentinel.plagiarism.PdfReportService;
import org.springframework.stereotype.Component;

/**
 * Concrete strategy implementing PDF export using OpenPDF.
 * Demonstrates Polymorphism and Strategy Pattern.
 */
@Component
public class PdfDossierExporter implements DossierExporter {

    private final PdfReportService pdfReportService;

    public PdfDossierExporter(PdfReportService pdfReportService) {
        this.pdfReportService = pdfReportService;
    }

    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String fileExtension() {
        return "pdf";
    }

    @Override
    public byte[] export(PdfReportService.AuditReportData data) {
        return pdfReportService.generateAuditReport(data);
    }
}
