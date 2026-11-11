package dev.codesentinel.assignments;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for Assignment endpoints.
 * Responsibilities are strictly limited to HTTP request mapping, validation, and delegation to application services.
 * Adheres to the Single Responsibility Principle (SRP).
 */
@RestController
@RequestMapping("/api")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentFileService fileService;

    public AssignmentController(AssignmentService assignmentService, AssignmentFileService fileService) {
        this.assignmentService = assignmentService;
        this.fileService = fileService;
    }

    @GetMapping("/courses/{courseId}/assignments")
    public List<AssignmentDto> list(@PathVariable long courseId, @RequestParam(defaultValue = "false") boolean includeArchived) {
        Principal p = CurrentUser.get();
        return assignmentService.listAssignments(courseId, includeArchived, p);
    }

    @PostMapping("/courses/{courseId}/assignments")
    public AssignmentDto create(@PathVariable long courseId, @Valid @RequestBody CreateAssignment r) {
        Principal p = CurrentUser.instructor();
        return assignmentService.createAssignment(courseId, r, p);
    }

    @GetMapping("/assignments/{id}")
    public AssignmentDto get(@PathVariable long id) {
        Principal p = CurrentUser.get();
        return assignmentService.getAssignment(id, p);
    }

    @PatchMapping("/assignments/{id}")
    public AssignmentDto patch(@PathVariable long id, @Valid @RequestBody PatchAssignment r) {
        Principal p = CurrentUser.instructor();
        return assignmentService.patchAssignment(id, r, p);
    }

    @PostMapping("/assignments/{id}/publish")
    public AssignmentDto publish(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        return assignmentService.publishAssignment(id, p);
    }

    @PostMapping("/assignments/{id}/unpublish")
    public AssignmentDto unpublish(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        return assignmentService.unpublishAssignment(id, p);
    }

    @PostMapping("/assignments/{id}/duplicate")
    public AssignmentDto duplicate(@PathVariable long id) throws IOException {
        Principal p = CurrentUser.instructor();
        return assignmentService.duplicateAssignment(id, p);
    }

    @PostMapping("/assignments/{id}/archive")
    public AssignmentDto archive(@PathVariable long id, @RequestParam(defaultValue = "true") boolean archived) {
        Principal p = CurrentUser.instructor();
        return assignmentService.archiveAssignment(id, archived, p);
    }

    @PostMapping("/assignments/{id}/test-cases")
    public AssignmentDto replaceTests(@PathVariable long id, @Valid @RequestBody List<@Valid TestCaseInput> tests) {
        Principal p = CurrentUser.instructor();
        return assignmentService.replaceTestCases(id, tests, p);
    }

    @PostMapping(value = "/assignments/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssignmentDto upload(@PathVariable long id, @RequestParam("kind") String kind, @RequestParam("file") MultipartFile file) throws IOException {
        Principal p = CurrentUser.instructor();
        return fileService.uploadFile(id, kind, file, p);
    }

    @GetMapping("/assignments/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id, @PathVariable long fileId) {
        Principal p = CurrentUser.get();
        return fileService.downloadFile(id, fileId, p);
    }
}
