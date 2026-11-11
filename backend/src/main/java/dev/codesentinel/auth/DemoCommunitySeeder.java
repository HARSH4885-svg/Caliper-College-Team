package dev.codesentinel.auth;

import dev.codesentinel.common.AppProperties;
import dev.codesentinel.notify.NotificationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Demo only (SEED_DEMO=true): course modules with links and notes, a few discussion threads, and welcome notifications. Runs once. */
@Component
@Order(30)
public class DemoCommunitySeeder implements ApplicationRunner {
    private final AppProperties props;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final NotificationService notes;

    public DemoCommunitySeeder(AppProperties props, JdbcClient jdbc, TransactionTemplate tx, NotificationService notes) {
        this.props = props;
        this.jdbc = jdbc;
        this.tx = tx;
        this.notes = notes;
    }

    record M(String module, String title, String kind, String url, String body) {
    }

    static final Map<String, List<M>> MATERIALS = Map.of(
            "CS201", List.of(new M("Week 1: Complexity", "Lecture notes: asymptotic notation", "NOTE", null, "Big-O bounds growth from above, Big-Omega from below and Big-Theta both. Drop constants and lower-order terms; compare by the dominant term.\n\nPractice: rank n log n, n^1.5, 2^n and n^2 for large n."),
                    new M("Week 1: Complexity", "Big-O cheat sheet", "LINK", "https://www.bigocheatsheet.com/", ""),
                    new M("Week 3: Stacks and queues", "Visualising stacks and queues", "LINK", "https://visualgo.net/en/list", ""),
                    new M("Week 5: Trees and heaps", "Heap operations walkthrough", "NOTE", null, "Insert: append, then sift up. Extract-min: move the last item to the root, then sift down. Both are O(log n); building a heap from an array is O(n)."),
                    new M("Week 5: Trees and heaps", "Binary search tree animations", "LINK", "https://www.cs.usfca.edu/~galles/visualization/BST.html", "")),
            "CS101", List.of(new M("Getting started", "Installing Python and an editor", "NOTE", null, "Install Python 3.12 from python.org and VS Code with the Python extension. In the lab you can also use the CodeSentinel workspace, which needs nothing installed."),
                    new M("Getting started", "The official Python tutorial", "LINK", "https://docs.python.org/3/tutorial/", ""),
                    new M("Collections", "Lists, tuples and dictionaries", "LINK", "https://docs.python.org/3/tutorial/datastructures.html", "")),
            "CS202", List.of(new M("SQL", "PostgreSQL SELECT reference", "LINK", "https://www.postgresql.org/docs/current/sql-select.html", ""),
                    new M("Normalisation", "Normal forms in one page", "NOTE", null, "1NF: atomic values. 2NF: no partial dependency on part of a composite key. 3NF: no transitive dependency on the key. BCNF: every determinant is a candidate key.")),
            "CS302", List.of(new M("Scheduling", "Scheduling algorithms compared", "NOTE", null, "FCFS: simple, convoy effect. SJF: optimal average waiting time, needs burst estimates, can starve. Round robin: fair and responsive; the quantum trades switches against response time."),
                    new M("Labs", "xv6 source and book", "LINK", "https://pdos.csail.mit.edu/6.828/2023/xv6.html", "")),
            "CS203", List.of(new M("Inheritance", "Java tutorial: inheritance", "LINK", "https://docs.oracle.com/javase/tutorial/java/IandI/subclasses.html", "")),
            "CS250", List.of(new M("Techniques", "Prefix sums and difference arrays", "NOTE", null, "prefix[i] = a1 + ... + ai answers any range sum in O(1): sum(l..r) = prefix[r] - prefix[l-1]. Use 64-bit integers when values can reach 10^9."),
                    new M("Practice", "CSES problem set", "LINK", "https://cses.fi/problemset/", "")));

    record T(String course, String title, String body, String[] replies, boolean staffAnswer) {
    }

    static final List<T> THREADS = List.of(
            new T("CS201", "Why is my Balanced Brackets solution failing the hidden tests?", "My code passes the sample but fails two hidden tests. I count opening and closing brackets of each type. What am I missing?",
                    new String[]{"Counting is not enough: \")(\" has equal counts but is not balanced. You need a stack so that each closing bracket matches the most recent opening one.", "Thanks, the stack version passes now!"}, true),
            new T("CS201", "Is the mid-sem syllabus up to heaps?", "Will questions on heaps be in the mid-semester exam or only up to trees?", new String[]{"Yes, heaps are included. Graphs are not."}, true),
            new T("CS201", "Difference between complete and full binary trees", "I keep mixing these up. Can someone explain with an example?", new String[]{"Full: every node has 0 or 2 children. Complete: every level is filled except possibly the last, which fills from the left. A complete tree need not be full."}, false),
            new T("CS101", "Can I use list comprehensions in the Word Frequency lab?", "Is it allowed or should we stick to loops?", new String[]{"Anything in the standard library is fine, including comprehensions and collections.Counter."}, true),
            new T("CS302", "fork() output order is different each time", "When I run the fork example the lines come out in a different order. Is my code wrong?", new String[]{"No: after fork the scheduler decides which process runs first, so the order is not deterministic. The count of lines is what is fixed."}, true),
            new T("CS250", "Getting WA on Range Sum Queries large values", "Works on the sample, fails on large values. Using int.", new String[]{}, false));

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedDemo()) return;
        if (jdbc.sql("SELECT count(*) FROM courses WHERE synthetic AND code='CS201'").query(Long.class).single() == 0) return;
        if (jdbc.sql("SELECT count(*) FROM course_materials m JOIN courses c ON c.id=m.course_id WHERE c.synthetic").query(Long.class).single() > 0) return;
        tx.executeWithoutResult(s -> seed());
    }

    private void seed() {
        Random rnd = new Random(7);
        Instant now = Instant.now();
        for (var e : MATERIALS.entrySet()) {
            var c = jdbc.sql("SELECT id, instructor_id FROM courses WHERE synthetic AND code=?").param(e.getKey()).query((rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}).optional();
            if (c.isEmpty()) continue;
            int ord = 0;
            for (M m : e.getValue())
                jdbc.sql("INSERT INTO course_materials(course_id, author_id, module, title, kind, url, body, ord, created_at) VALUES (?,?,?,?,?,?,?,?,?)")
                        .params(c.get()[0], c.get()[1], m.module(), m.title(), m.kind(), m.url(), m.body() == null ? "" : m.body(), ord++, Timestamp.from(now.minus(60 - ord * 5L, ChronoUnit.DAYS))).update();
        }
        for (T t : THREADS) {
            var c = jdbc.sql("SELECT id, instructor_id FROM courses WHERE synthetic AND code=?").param(t.course()).query((rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}).optional();
            if (c.isEmpty()) continue;
            List<Long> students = jdbc.sql("SELECT student_id FROM enrollments WHERE course_id=? ORDER BY student_id").param(c.get()[0]).query(Long.class).list();
            long asker = students.get(1 + rnd.nextInt(students.size() - 1));
            Instant at = now.minus(2 + rnd.nextInt(20), ChronoUnit.DAYS);
            long id = jdbc.sql("INSERT INTO discussion_threads(course_id, author_id, title, body, created_at, last_activity) VALUES (?,?,?,?,?,?) RETURNING id")
                    .params(c.get()[0], asker, t.title(), t.body(), Timestamp.from(at), Timestamp.from(at)).query(Long.class).single();
            for (int i = 0; i < t.replies().length; i++) {
                at = at.plus(1 + rnd.nextInt(10), ChronoUnit.HOURS);
                long who = i == 0 ? (t.staffAnswer() ? c.get()[1] : students.get(rnd.nextInt(students.size()))) : asker;
                jdbc.sql("INSERT INTO discussion_posts(thread_id, author_id, body, endorsed, created_at) VALUES (?,?,?,?,?)").params(id, who, t.replies()[i], i == 0 && !t.staffAnswer(), Timestamp.from(at)).update();
                jdbc.sql("UPDATE discussion_threads SET last_activity=? WHERE id=?").params(Timestamp.from(at), id).update();
            }
            if (t.title().startsWith("Is the mid-sem")) jdbc.sql("UPDATE discussion_threads SET pinned=TRUE WHERE id=?").param(id).update();
        }
        // the presenter's student account gets a few realistic notifications from recent activity
        jdbc.sql("SELECT id FROM users WHERE email='student1@demo.invalid'").query(Long.class).optional().ifPresent(sid -> {
            jdbc.sql("""
                    SELECT a.id, a.title, c.code FROM assignments a JOIN courses c ON c.id=a.course_id JOIN enrollments e ON e.course_id=c.id AND e.student_id=?
                    WHERE a.published AND a.deadline > now() AND (a.available_from IS NULL OR a.available_from <= now()) ORDER BY a.deadline LIMIT 3""").param(sid)
                    .query((rs, n) -> { notes.notifyUser(sid, "ASSIGNMENT", "New in " + rs.getString(3) + ": " + rs.getString(2), "", "/assignments/" + rs.getLong(1), "asg-" + rs.getLong(1)); return 0; }).list();
            jdbc.sql("SELECT q.id, a.title FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id WHERE q.student_id=? AND q.released ORDER BY q.submitted_at DESC LIMIT 2").param(sid)
                    .query((rs, n) -> { notes.notifyUser(sid, "GRADE", "Marks released: " + rs.getString(2), "", "/attempts/" + rs.getLong(1), "att-" + rs.getLong(1) + "-GRADED"); return 0; }).list();
        });
    }
}
