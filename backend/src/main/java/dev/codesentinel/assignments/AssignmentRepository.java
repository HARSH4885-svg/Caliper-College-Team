package dev.codesentinel.assignments;

import dev.codesentinel.auth.Principal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access abstraction for Assignment persistence.
 * Adheres to the Single Responsibility Principle (SRP) and Dependency Inversion Principle (DIP).
 */
public interface AssignmentRepository {

    Optional<AssignmentDto> findDtoById(long id, boolean isInstructor);

    List<AssignmentDto> listDtosByCourse(long courseId, Principal p, boolean includeArchived);

    long insertAssignment(long courseId, String title, String description, String language,
                           Instant deadline, int timeLimitMs, int memoryLimitMb, String kind, String category);

    void updateAssignmentFields(long id, String title, String description, Instant deadline,
                                Integer timeLimitMs, Integer memoryLimitMb);

    void applyOptions(long id, Options options);

    void setPublished(long id, boolean published);

    void setArchived(long id, boolean archived);

    long duplicateAssignment(long id);

    void replaceTestCases(long id, List<TestCaseInput> tests);

    void insertAssignmentFile(long assignmentId, String kind, String name, String storageKey, long sizeBytes, String sha256);

    Optional<String[]> findFileForDownload(long assignmentId, long fileId);

    List<String[]> listFilesForDuplication(long assignmentId);

    boolean isQuiz(long id);

    int countQuestionsOrTests(long id, boolean isQuiz);

    Object[] getAssignmentNotificationInfo(long id);
}
