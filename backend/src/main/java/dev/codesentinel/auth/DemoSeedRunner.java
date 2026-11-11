package dev.codesentinel.auth;

import dev.codesentinel.common.AppProperties;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.storage.StoreRequest;
import dev.codesentinel.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Optional demo data (SEED_DEMO=true). Everything is labelled synthetic in the database and in display names. The credentials
 * are fixed, public, development-only values and exist only while this flag is on.
 */
@Component
@org.springframework.core.annotation.Order(10)
public class DemoSeedRunner implements ApplicationRunner {
    public static final String INSTRUCTOR_EMAIL = "demo-instructor@demo.invalid";
    public static final String PASSWORD = "demo-password-1";
    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);
    private static final String[] FILES = {"student1_original", "student2_renamed_copy", "student3_class_based", "student4_merge_sort",
            "student5_heap", "student6_bubble", "student7_wrong_output", "student8_syntax_error"};

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcClient jdbc;
    private final FileStorageProvider storage;

    public DemoSeedRunner(AppProperties props, UserRepository users, PasswordEncoder encoder, JdbcClient jdbc, FileStorageProvider storage) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!props.seedDemo() || users.findByEmail(INSTRUCTOR_EMAIL).isPresent()) return;
        String hash = encoder.encode(PASSWORD);
        long inst = users.insert(INSTRUCTOR_EMAIL, "Demo Instructor (synthetic)", hash, "INSTRUCTOR", true);
        long course = jdbc.sql("INSERT INTO courses(code, title, enrollment_code, instructor_id, synthetic) VALUES ('DEMO101','Programming Lab (synthetic demo)','CS-DEMO101SYN',?,TRUE) RETURNING id")
                .param(inst).query(Long.class).single();
        long asg = jdbc.sql("""
                INSERT INTO assignments(course_id, title, description, language, deadline, time_limit_ms, memory_limit_mb, published)
                VALUES (?, 'Array statistics (synthetic)', 'Read integers from standard input. Print four lines: "sorted: <values>", "sum: <total>", "median: <median>" (for an even count: the average of the two middle values, printed as a decimal such as 5.0), "mode: <smallest most frequent value>".', 'python', ?, 2000, 128, TRUE) RETURNING id""")
                .params(course, Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS))).query(Long.class).single();
        jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) VALUES (?,?,?,?,?,0)")
                .params(asg, "sample", "3 1 2\n", "sorted: 1 2 3\nsum: 6\nmedian: 2\nmode: 1", true).update();
        jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) VALUES (?,?,?,?,?,1)")
                .params(asg, "hidden-even-count", "5 5 1 9 5 2\n", "sorted: 1 2 5 5 5 9\nsum: 27\nmedian: 5.0\nmode: 5", false).update();
        int i = 1;
        for (String f : FILES) {
            long sid = users.insert("student" + i + "@demo.invalid", "Student " + i + " (synthetic)", hash, "STUDENT", true);
            jdbc.sql("INSERT INTO enrollments(course_id, student_id) VALUES (?,?)").params(course, sid).update();
            byte[] code;
            try (InputStream in = new ClassPathResource("demo/" + f + ".py").getInputStream()) {
                code = in.readAllBytes();
            }
            long sub = jdbc.sql("INSERT INTO submissions(assignment_id, student_id, attempt, status, synthetic) VALUES (?,?,1,'QUEUED',TRUE) RETURNING id").params(asg, sid).query(Long.class).single();
            String key = "sub/" + sub + "/f0";
            StoredObject o = storage.store(new StoreRequest(key, new ByteArrayInputStream(code)));
            jdbc.sql("INSERT INTO submission_files(submission_id, path, storage_key, size_bytes, sha256) VALUES (?,?,?,?,?)").params(sub, "main.py", key, o.sizeBytes(), o.sha256()).update();
            jdbc.sql("INSERT INTO evaluation_jobs(submission_id) VALUES (?)").param(sub).update();
            i++;
        }
        log.info("Synthetic demo data created (instructor {}).", INSTRUCTOR_EMAIL);
    }
}
