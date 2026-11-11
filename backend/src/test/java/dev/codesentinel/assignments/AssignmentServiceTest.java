package dev.codesentinel.assignments;

import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.notify.NotificationService;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.storage.StoreRequest;
import dev.codesentinel.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentServiceTest {

    private FakeAssignmentRepository repository;
    private FakeAccess access;
    private FakeAuditService audit;
    private FakeNotificationService notes;
    private AssignmentFileService fileService;
    private AssignmentService service;

    private final Principal instructor = new Principal(1L, "inst@test.invalid", "Instructor", "INSTRUCTOR");

    static class FakeAccess extends Access {
        boolean allowOwner = true;
        boolean allowMember = true;
        boolean allowVisible = true;

        public FakeAccess() {
            super(null);
        }

        @Override
        public void requireCourseOwner(long courseId, Principal p) {
            if (!allowOwner) throw ApiException.notFound();
        }

        @Override
        public void requireCourseMember(long courseId, Principal p) {
            if (!allowMember) throw ApiException.notFound();
        }

        @Override
        public long requireAssignmentOwner(long assignmentId, Principal p) {
            if (!allowOwner) throw ApiException.notFound();
            return 10L;
        }

        @Override
        public long requireAssignmentVisible(long assignmentId, Principal p) {
            if (!allowVisible) throw ApiException.notFound();
            return 10L;
        }
    }

    static class FakeAuditService extends AuditService {
        final List<String> logs = new ArrayList<>();

        public FakeAuditService() {
            super(null);
        }

        @Override
        public void log(Long userId, String action, String targetType, Long targetId, String detail) {
            logs.add(action + ":" + targetId);
        }
    }

    static class FakeNotificationService extends NotificationService {
        final List<String> notifications = new ArrayList<>();

        public FakeNotificationService() {
            super(null);
        }

        @Override
        public void notifyCourseStudents(long courseId, String type, String title, String body, String link, String dedupeKey) {
            notifications.add(courseId + ":" + title);
        }
    }

    static class FakeFileStorageProvider implements FileStorageProvider {
        @Override
        public String name() {
            return "fake";
        }

        @Override
        public StoredObject store(StoreRequest request) {
            return new StoredObject("fake", "k", 100L, "fake-sha256");
        }

        @Override
        public InputStream read(String storageKey) {
            return InputStream.nullInputStream();
        }

        @Override
        public void delete(String storageKey) {
        }

        @Override
        public boolean exists(String storageKey) {
            return true;
        }
    }

    static class FakeAssignmentRepository implements AssignmentRepository {
        final Map<Long, AssignmentDto> store = new HashMap<>();
        long idSeq = 100L;
        boolean isQuizValue = false;
        int questionsOrTestsCount = 3;
        boolean publishedState = false;
        boolean archivedState = false;

        @Override
        public Optional<AssignmentDto> findDtoById(long id, boolean isInstructor) {
            AssignmentDto d = store.get(id);
            if (d != null) return Optional.of(d);
            return Optional.of(new AssignmentDto(
                    id, 10L, "Sample", "Desc", "java", Instant.now(), 2000, 256,
                    publishedState, archivedState, List.of(), List.of(), 0, "CODING", "ASSIGNMENT", "EASY",
                    null, null, null, 1, "ALLOW", 0, null, null, false, false, "IMMEDIATE", false, 0
            ));
        }

        @Override
        public List<AssignmentDto> listDtosByCourse(long courseId, Principal p, boolean includeArchived) {
            return new ArrayList<>(store.values());
        }

        @Override
        public long insertAssignment(long courseId, String title, String description, String language,
                                      Instant deadline, int timeLimitMs, int memoryLimitMb, String kind, String category) {
            long id = idSeq++;
            return id;
        }

        @Override
        public void updateAssignmentFields(long id, String title, String description, Instant deadline, Integer timeLimitMs, Integer memoryLimitMb) {
        }

        @Override
        public void applyOptions(long id, Options options) {
        }

        @Override
        public void setPublished(long id, boolean published) {
            this.publishedState = published;
        }

        @Override
        public void setArchived(long id, boolean archived) {
            this.archivedState = archived;
        }

        @Override
        public long duplicateAssignment(long id) {
            return idSeq++;
        }

        @Override
        public void replaceTestCases(long id, List<TestCaseInput> tests) {
        }

        @Override
        public void insertAssignmentFile(long assignmentId, String kind, String name, String storageKey, long sizeBytes, String sha256) {
        }

        @Override
        public Optional<String[]> findFileForDownload(long assignmentId, long fileId) {
            return Optional.of(new String[]{"STARTER", "Starter.java", "key-123"});
        }

        @Override
        public List<String[]> listFilesForDuplication(long assignmentId) {
            return Collections.singletonList(new String[]{"STARTER", "Starter.java", "key-123"});
        }

        @Override
        public boolean isQuiz(long id) {
            return isQuizValue;
        }

        @Override
        public int countQuestionsOrTests(long id, boolean isQuiz) {
            return questionsOrTestsCount;
        }

        @Override
        public Object[] getAssignmentNotificationInfo(long id) {
            return new Object[]{10L, "Lab 1", "CS101", Instant.now(), true};
        }
    }

    @BeforeEach
    void setUp() {
        repository = new FakeAssignmentRepository();
        access = new FakeAccess();
        audit = new FakeAuditService();
        notes = new FakeNotificationService();
        fileService = new AssignmentFileService(repository, new FakeFileStorageProvider(), access, audit);
        service = new AssignmentService(repository, fileService, access, audit, notes);
    }

    @Test
    void createAssignmentValidatesCodingLanguage() {
        CreateAssignment request = new CreateAssignment("Test", "Desc", "none", Instant.now(), 1000, 256, "CODING", null);
        assertThrows(ApiException.class, () -> service.createAssignment(10L, request, instructor));
    }

    @Test
    void publishAssignmentRejectsWhenNoQuestionsOrTests() {
        repository.questionsOrTestsCount = 0;
        assertThrows(ApiException.class, () -> service.publishAssignment(5L, instructor));
        assertFalse(repository.publishedState);
    }

    @Test
    void publishAssignmentSucceedsAndNotifiesWhenAvailable() {
        repository.questionsOrTestsCount = 2;
        AssignmentDto result = service.publishAssignment(5L, instructor);

        assertNotNull(result);
        assertTrue(repository.publishedState);
        assertTrue(audit.logs.contains("ASSIGNMENT_PUBLISH:5"));
        assertEquals(1, notes.notifications.size());
        assertTrue(notes.notifications.get(0).contains("Lab 1"));
    }

    @Test
    void duplicateAssignmentCopiesFilesAndLogsAudit() throws Exception {
        AssignmentDto result = service.duplicateAssignment(5L, instructor);
        assertNotNull(result);
        assertTrue(audit.logs.stream().anyMatch(l -> l.startsWith("ASSIGNMENT_DUPLICATE:")));
    }

    @Test
    void archiveAssignmentUpdatesStateAndLogs() {
        AssignmentDto result = service.archiveAssignment(5L, true, instructor);
        assertNotNull(result);
        assertTrue(repository.archivedState);
        assertTrue(audit.logs.contains("ASSIGNMENT_ARCHIVE:5"));

        result = service.archiveAssignment(5L, false, instructor);
        assertFalse(repository.archivedState);
        assertTrue(audit.logs.contains("ASSIGNMENT_UNARCHIVE:5"));
    }

    @Test
    void requireOwnerEnforcedOnPublish() {
        access.allowOwner = false;
        assertThrows(ApiException.class, () -> service.publishAssignment(5L, instructor));
    }

    @Test
    void replaceTestCasesEnforcesLimit() {
        List<TestCaseInput> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(new TestCaseInput("test" + i, "in", "out", false));
        }
        assertThrows(ApiException.class, () -> service.replaceTestCases(5L, tooMany, instructor));
    }
}
