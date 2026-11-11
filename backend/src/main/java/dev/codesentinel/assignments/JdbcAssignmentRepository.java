package dev.codesentinel.assignments;

import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAssignmentRepository implements AssignmentRepository {

    private final JdbcClient jdbc;

    public JdbcAssignmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant inst(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    @Override
    public Optional<AssignmentDto> findDtoById(long id, boolean isInstructor) {
        var baseOpt = jdbc.sql("""
                SELECT id, course_id, title, description, language, deadline, time_limit_ms, memory_limit_mb, published, archived, kind, category, difficulty,
                       max_marks, passing_marks, available_from, max_attempts, late_policy, late_penalty_pct, late_until, quiz_minutes, shuffle_questions,
                       shuffle_options, release_mode, show_answers, (SELECT count(*) FROM questions q WHERE q.assignment_id=assignments.id)
                FROM assignments WHERE id=?""").param(id)
                .query((rs, n) -> new Object[]{
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getInt(7), rs.getInt(8),
                        rs.getBoolean(9), rs.getBoolean(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getBigDecimal(14), rs.getBigDecimal(15), inst(rs.getTimestamp(16)),
                        rs.getInt(17), rs.getString(18), rs.getInt(19), inst(rs.getTimestamp(20)), (Integer) rs.getObject(21), rs.getBoolean(22), rs.getBoolean(23), rs.getString(24),
                        rs.getBoolean(25), rs.getInt(26)
                }).optional();

        if (baseOpt.isEmpty()) return Optional.empty();

        List<FileDto> files = jdbc.sql("SELECT id, kind, name, size_bytes FROM assignment_files WHERE assignment_id=?" + (isInstructor ? "" : " AND kind='STARTER'") + " ORDER BY id")
                .param(id).query((rs, n) -> new FileDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4))).list();

        List<TestCaseDto> tcs = jdbc.sql("SELECT id, name, sample, input, expected_output FROM test_cases WHERE assignment_id=?" + (isInstructor ? "" : " AND sample") + " ORDER BY ord, id")
                .param(id).query((rs, n) -> new TestCaseDto(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5))).list();

        int hidden = jdbc.sql("SELECT count(*) FROM test_cases WHERE assignment_id=? AND NOT sample").param(id).query(Integer.class).single();

        Object[] b = baseOpt.get();
        return Optional.of(new AssignmentDto(
                (Long) b[0], (Long) b[1], (String) b[2], (String) b[3], (String) b[4], (Instant) b[5], (Integer) b[6], (Integer) b[7],
                (Boolean) b[8], (Boolean) b[9], files, tcs, hidden, (String) b[10], (String) b[11], (String) b[12], (BigDecimal) b[13], (BigDecimal) b[14],
                (Instant) b[15], (Integer) b[16], (String) b[17], (Integer) b[18], (Instant) b[19], (Integer) b[20], (Boolean) b[21], (Boolean) b[22], (String) b[23],
                (Boolean) b[24], (Integer) b[25]
        ));
    }

    @Override
    public List<AssignmentDto> listDtosByCourse(long courseId, Principal p, boolean includeArchived) {
        String filter = p.instructor()
                ? (includeArchived ? "" : " AND NOT archived")
                : " AND published AND NOT archived AND (available_from IS NULL OR available_from <= now())";

        return jdbc.sql("SELECT id FROM assignments WHERE course_id=?" + filter + " ORDER BY deadline")
                .param(courseId)
                .query(Long.class).list().stream()
                .map(id -> findDtoById(id, p.instructor()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public long insertAssignment(long courseId, String title, String description, String language,
                                  Instant deadline, int timeLimitMs, int memoryLimitMb, String kind, String category) {
        return jdbc.sql("INSERT INTO assignments(course_id, title, description, language, deadline, time_limit_ms, memory_limit_mb, kind, category) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id")
                .params(courseId, title.trim(), description, language, Timestamp.from(deadline), timeLimitMs, memoryLimitMb, kind, category)
                .query(Long.class).single();
    }

    @Override
    public void updateAssignmentFields(long id, String title, String description, Instant deadline,
                                       Integer timeLimitMs, Integer memoryLimitMb) {
        if (title != null && !title.isBlank()) {
            jdbc.sql("UPDATE assignments SET title=? WHERE id=?").params(title.trim(), id).update();
        }
        if (description != null && !description.isBlank()) {
            jdbc.sql("UPDATE assignments SET description=? WHERE id=?").params(description, id).update();
        }
        if (deadline != null) {
            jdbc.sql("UPDATE assignments SET deadline=? WHERE id=?").params(Timestamp.from(deadline), id).update();
        }
        if (timeLimitMs != null) {
            jdbc.sql("UPDATE assignments SET time_limit_ms=? WHERE id=?").params(timeLimitMs, id).update();
        }
        if (memoryLimitMb != null) {
            jdbc.sql("UPDATE assignments SET memory_limit_mb=? WHERE id=?").params(memoryLimitMb, id).update();
        }
    }

    @Override
    public void applyOptions(long id, Options o) {
        if (o == null) return;
        record Set(String col, Object v) {}
        var sets = new ArrayList<Set>();
        if (o.category() != null) sets.add(new Set("category", o.category()));
        if (o.difficulty() != null) sets.add(new Set("difficulty", o.difficulty()));
        if (o.maxMarks() != null) sets.add(new Set("max_marks", o.maxMarks()));
        if (o.passingMarks() != null) sets.add(new Set("passing_marks", o.passingMarks()));
        if (o.availableFrom() != null) sets.add(new Set("available_from", Timestamp.from(o.availableFrom())));
        if (Boolean.TRUE.equals(o.clearAvailableFrom())) sets.add(new Set("available_from", null));
        if (o.maxAttempts() != null) sets.add(new Set("max_attempts", o.maxAttempts()));
        if (o.latePolicy() != null) sets.add(new Set("late_policy", o.latePolicy()));
        if (o.latePenaltyPct() != null) sets.add(new Set("late_penalty_pct", o.latePenaltyPct()));
        if (o.lateUntil() != null) sets.add(new Set("late_until", Timestamp.from(o.lateUntil())));
        if (Boolean.TRUE.equals(o.clearLateUntil())) sets.add(new Set("late_until", null));
        if (o.quizMinutes() != null) sets.add(new Set("quiz_minutes", o.quizMinutes()));
        if (Boolean.TRUE.equals(o.clearQuizMinutes())) sets.add(new Set("quiz_minutes", null));
        if (o.shuffleQuestions() != null) sets.add(new Set("shuffle_questions", o.shuffleQuestions()));
        if (o.shuffleOptions() != null) sets.add(new Set("shuffle_options", o.shuffleOptions()));
        if (o.releaseMode() != null) sets.add(new Set("release_mode", o.releaseMode()));
        if (o.showAnswers() != null) sets.add(new Set("show_answers", o.showAnswers()));

        for (Set x : sets) {
            jdbc.sql("UPDATE assignments SET " + x.col() + "=? WHERE id=?").params(x.v(), id).update();
        }

        var check = jdbc.sql("SELECT deadline, late_until, max_marks, passing_marks FROM assignments WHERE id=?").param(id)
                .query((rs, n) -> new Object[]{rs.getTimestamp(1), rs.getTimestamp(2), rs.getBigDecimal(3), rs.getBigDecimal(4)}).single();
        if (check[1] != null && ((Timestamp) check[1]).before((Timestamp) check[0])) {
            throw ApiException.bad("Late submissions must close after the deadline");
        }
        if (check[3] != null && ((BigDecimal) check[3]).compareTo((BigDecimal) check[2]) > 0) {
            throw ApiException.bad("Passing marks cannot exceed the maximum marks");
        }
    }

    @Override
    public void setPublished(long id, boolean published) {
        jdbc.sql("UPDATE assignments SET published=? WHERE id=?").params(published, id).update();
    }

    @Override
    public void setArchived(long id, boolean archived) {
        jdbc.sql("UPDATE assignments SET archived=? WHERE id=?").params(archived, id).update();
    }

    @Override
    public long duplicateAssignment(long id) {
        long copy = jdbc.sql("""
                INSERT INTO assignments(course_id, title, description, language, deadline, time_limit_ms, memory_limit_mb, kind, category, difficulty, max_marks, passing_marks,
                    max_attempts, late_policy, late_penalty_pct, quiz_minutes, shuffle_questions, shuffle_options, release_mode, show_answers)
                SELECT course_id, left(title, 138) || ' (copy)', description, language, now() + interval '7 days', time_limit_ms, memory_limit_mb, kind, category, difficulty,
                    max_marks, passing_marks, max_attempts, late_policy, late_penalty_pct, quiz_minutes, shuffle_questions, shuffle_options, release_mode, show_answers
                FROM assignments WHERE id=? RETURNING id""")
                .param(id).query(Long.class).single();

        jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) SELECT ?, name, input, expected_output, sample, ord FROM test_cases WHERE assignment_id=?")
                .params(copy, id).update();

        jdbc.sql("""
                INSERT INTO questions(assignment_id, ord, type, prompt, marks, difficulty, required, options, answer, settings, explanation, bank_id)
                SELECT ?, ord, type, prompt, marks, difficulty, required, options, answer, settings, explanation, bank_id FROM questions WHERE assignment_id=?""")
                .params(copy, id).update();

        return copy;
    }

    @Override
    public void replaceTestCases(long id, List<TestCaseInput> tests) {
        jdbc.sql("DELETE FROM test_cases WHERE assignment_id=?").param(id).update();
        int ord = 0;
        for (TestCaseInput t : tests) {
            jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) VALUES (?,?,?,?,?,?)")
                    .params(id, t.name().trim(), t.input() == null ? "" : t.input(), t.expectedOutput(), t.sample(), ord++).update();
        }
    }

    @Override
    public void insertAssignmentFile(long assignmentId, String kind, String name, String storageKey, long sizeBytes, String sha256) {
        jdbc.sql("INSERT INTO assignment_files(assignment_id, kind, name, storage_key, size_bytes, sha256) VALUES (?,?,?,?,?,?)")
                .params(assignmentId, kind, name, storageKey, sizeBytes, sha256).update();
    }

    @Override
    public Optional<String[]> findFileForDownload(long assignmentId, long fileId) {
        return jdbc.sql("SELECT kind, name, storage_key FROM assignment_files WHERE id=? AND assignment_id=?")
                .params(fileId, assignmentId)
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)})
                .optional();
    }

    @Override
    public List<String[]> listFilesForDuplication(long assignmentId) {
        return jdbc.sql("SELECT kind, name, storage_key FROM assignment_files WHERE assignment_id=? ORDER BY id")
                .param(assignmentId)
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)})
                .list();
    }

    @Override
    public boolean isQuiz(long id) {
        return "QUIZ".equals(jdbc.sql("SELECT kind FROM assignments WHERE id=?").param(id).query(String.class).single());
    }

    @Override
    public int countQuestionsOrTests(long id, boolean isQuiz) {
        String sql = isQuiz ? "SELECT count(*) FROM questions WHERE assignment_id=?" : "SELECT count(*) FROM test_cases WHERE assignment_id=?";
        return jdbc.sql(sql).param(id).query(Integer.class).single();
    }

    @Override
    public Object[] getAssignmentNotificationInfo(long id) {
        return jdbc.sql("SELECT a.course_id, a.title, c.code, a.deadline, a.available_from IS NULL OR a.available_from <= now() FROM assignments a JOIN courses c ON c.id=a.course_id WHERE a.id=?")
                .param(id)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getBoolean(5)})
                .single();
    }
}
