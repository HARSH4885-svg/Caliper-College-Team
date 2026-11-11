package dev.codesentinel.assignments;

import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.storage.StoreRequest;
import dev.codesentinel.storage.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Service encapsulating assignment file operations (uploads, downloads, and storage duplication).
 * Adheres to the Single Responsibility Principle (SRP).
 */
@Service
public class AssignmentFileService {

    private static final long MAX_ATTACHMENT = 1L << 20;

    private final AssignmentRepository repository;
    private final FileStorageProvider storage;
    private final Access access;
    private final AuditService audit;

    public AssignmentFileService(AssignmentRepository repository, FileStorageProvider storage, Access access, AuditService audit) {
        this.repository = repository;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
    }

    public AssignmentDto uploadFile(long assignmentId, String kind, MultipartFile file, Principal p) throws IOException {
        access.requireAssignmentOwner(assignmentId, p);
        if (!"STARTER".equals(kind) && !"REFERENCE".equals(kind)) {
            throw ApiException.bad("kind must be STARTER or REFERENCE");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.matches("[A-Za-z0-9._+()-]{1,100}")) {
            throw ApiException.bad("Invalid file name");
        }
        if (file.isEmpty() || file.getSize() > MAX_ATTACHMENT) {
            throw ApiException.bad("File must be between 1 byte and 1 MiB");
        }

        String key = "assign/" + assignmentId + "/" + UUID.randomUUID();
        StoredObject o = storage.store(new StoreRequest(key, file.getInputStream()));
        repository.insertAssignmentFile(assignmentId, kind, name, key, o.sizeBytes(), o.sha256());
        audit.log(p.id(), "ASSIGNMENT_FILE_ADD", "assignment", assignmentId, kind + ":" + name);

        return repository.findDtoById(assignmentId, p.instructor()).orElseThrow(ApiException::notFound);
    }

    public ResponseEntity<InputStreamResource> downloadFile(long assignmentId, long fileId, Principal p) {
        access.requireAssignmentVisible(assignmentId, p);
        var row = repository.findFileForDownload(assignmentId, fileId).orElseThrow(ApiException::notFound);
        if (!p.instructor() && !"STARTER".equals(row[0])) {
            throw ApiException.notFound();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(row[1]).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(storage.read(row[2])));
    }

    public void duplicateFiles(long sourceAssignmentId, long targetAssignmentId) throws IOException {
        List<String[]> files = repository.listFilesForDuplication(sourceAssignmentId);
        for (String[] f : files) {
            String key = "assign/" + targetAssignmentId + "/" + UUID.randomUUID();
            StoredObject o;
            try (InputStream in = storage.read(f[2])) {
                o = storage.store(new StoreRequest(key, in));
            }
            repository.insertAssignmentFile(targetAssignmentId, f[0], f[1], key, o.sizeBytes(), o.sha256());
        }
    }
}
