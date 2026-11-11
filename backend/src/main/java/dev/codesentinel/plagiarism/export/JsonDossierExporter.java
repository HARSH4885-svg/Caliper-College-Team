package dev.codesentinel.plagiarism.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.plagiarism.PdfReportService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Concrete strategy implementing structured JSON export for machine-readable dossiers.
 * Enables institutional archiving and external LMS sync.
 */
@Component
public class JsonDossierExporter implements DossierExporter {

    private final ObjectMapper objectMapper;

    public JsonDossierExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public String format() {
        return "json";
    }

    @Override
    public String contentType() {
        return "application/json; charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return "json";
    }

    @Override
    public byte[] export(PdfReportService.AuditReportData data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize audit report to JSON", e);
        }
    }
}
