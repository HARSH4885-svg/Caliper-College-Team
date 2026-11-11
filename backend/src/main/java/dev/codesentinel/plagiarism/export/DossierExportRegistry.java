package dev.codesentinel.plagiarism.export;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry managing and resolving {@link DossierExporter} strategies.
 * Demonstrates Open/Closed Principle (OCP) and Dependency Injection.
 */
@Component
public class DossierExportRegistry {

    private final Map<String, DossierExporter> exporters = new LinkedHashMap<>();

    public DossierExportRegistry(List<DossierExporter> exporterList) {
        if (exporterList != null) {
            for (DossierExporter exporter : exporterList) {
                exporters.put(exporter.format().toLowerCase(Locale.ROOT), exporter);
            }
        }
    }

    public Optional<DossierExporter> forFormat(String format) {
        if (format == null) return Optional.empty();
        return Optional.ofNullable(exporters.get(format.trim().toLowerCase(Locale.ROOT)));
    }

    public Set<String> supportedFormats() {
        return Collections.unmodifiableSet(exporters.keySet());
    }
}
