package dev.codesentinel.assignments;

import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.notify.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Application service for Assignment lifecycle operations and business rule orchestration.
 * Adheres to the Single Responsibility Principle (SRP) and Dependency Inversion Principle (DIP).
 */
@Service
public class AssignmentService {

    private final AssignmentRepository repository;
    private final AssignmentFileService fileService;
    private final Access access;
    private final AuditService audit;
    private final NotificationService notes;

    public AssignmentService(AssignmentRepository repository,
                             AssignmentFileService fileService,
                             Access access,
                             AuditService audit,
                             NotificationService notes) {
        this.repository = repository;
        this.fileService = fileService;
        this.access = access;
        this.audit = audit;
        this.notes = notes;
    }

    public List<AssignmentDto> listAssignments(long courseId, boolean includeArchived, Principal p) {
        access.requireCourseMember(courseId, p);
        return repository.listDtosByCourse(courseId, p, includeArchived);
    }

    @Transactional
    public AssignmentDto createAssignment(long courseId, CreateAssignment r, Principal p) {
        access.requireCourseOwner(courseId, p);
        String kind = r.kind() == null ? "CODING" : r.kind();
        String language = kind.equals("QUIZ") ? "none" : r.language();
        if (kind.equals("CODING") && (language == null || language.equals("none"))) {
            throw ApiException.bad("Choose the programming language for a coding assignment");
        }

        int timeLimitMs = r.timeLimitMs() == null ? 2000 : r.timeLimitMs();
        int memoryLimitMb = r.memoryLimitMb() == null ? 256 : r.memoryLimitMb();
        String category = kind.equals("QUIZ") ? "QUIZ" : "ASSIGNMENT";

        long id = repository.insertAssignment(courseId, r.title().trim(), r.description(), language,
                r.deadline(), timeLimitMs, memoryLimitMb, kind, category);
        repository.applyOptions(id, r.options());
        audit.log(p.id(), "ASSIGNMENT_CREATE", "assignment", id, r.title());

        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }

    public AssignmentDto getAssignment(long id, Principal p) {
        access.requireAssignmentVisible(id, p);
        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public AssignmentDto patchAssignment(long id, PatchAssignment r, Principal p) {
        access.requireAssignmentOwner(id, p);
        repository.updateAssignmentFields(id, r.title(), r.description(), r.deadline(), r.timeLimitMs(), r.memoryLimitMb());
        repository.applyOptions(id, r.options());
        audit.log(p.id(), "ASSIGNMENT_UPDATE", "assignment", id, null);

        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public AssignmentDto publishAssignment(long id, Principal p) {
        access.requireAssignmentOwner(id, p);
        boolean quiz = repository.isQuiz(id);
        int items = repository.countQuestionsOrTests(id, quiz);
        if (items == 0) {
            throw ApiException.conflict(quiz ? "Add at least one question before publishing" : "Add at least one test case before publishing");
        }

        repository.setPublished(id, true);
        audit.log(p.id(), "ASSIGNMENT_PUBLISH", "assignment", id, null);

        Object[] info = repository.getAssignmentNotificationInfo(id);
        if ((Boolean) info[4]) {
            notes.notifyCourseStudents((Long) info[0], "ASSIGNMENT", "New in " + info[2] + ": " + info[1],
                    "Due " + info[3], "/assignments/" + id, "asg-" + id);
        }

        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public AssignmentDto unpublishAssignment(long id, Principal p) {
        access.requireAssignmentOwner(id, p);
        repository.setPublished(id, false);
        audit.log(p.id(), "ASSIGNMENT_UNPUBLISH", "assignment", id, null);

        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public AssignmentDto duplicateAssignment(long id, Principal p) throws IOException {
        long courseId = access.requireAssignmentOwner(id, p);
        long copyId = repository.duplicateAssignment(id);
        fileService.duplicateFiles(id, copyId);
        audit.log(p.id(), "ASSIGNMENT_DUPLICATE", "assignment", copyId, "from " + id + " in course " + courseId);

        return repository.findDtoById(copyId, p.instructor()).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public AssignmentDto archiveAssignment(long id, boolean archived, Principal p) {
        access.requireAssignmentOwner(id, p);
        repository.setArchived(id, archived);
        audit.log(p.id(), archived ? "ASSIGNMENT_ARCHIVE" : "ASSIGNMENT_UNARCHIVE", "assignment", id, null);

        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public AssignmentDto replaceTestCases(long id, List<TestCaseInput> tests, Principal p) {
        access.requireAssignmentOwner(id, p);
        if (tests.size() > 100) {
            throw ApiException.bad("At most 100 test cases");
        }

        repository.replaceTestCases(id, tests);
        audit.log(p.id(), "TESTCASES_REPLACE", "assignment", id, tests.size() + " tests");

        return repository.findDtoById(id, p.instructor()).orElseThrow(ApiException::notFound);
    }
}
