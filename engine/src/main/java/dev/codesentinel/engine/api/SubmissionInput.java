package dev.codesentinel.engine.api;

import java.util.List;

public record SubmissionInput(String id, List<SourceFileInput> files) {
}
