package dev.codesentinel.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.auth.UserRepository;
import dev.codesentinel.jobs.JobWorker;
import dev.codesentinel.judge.sandbox.DockerRunner;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * End-to-end through the real HTTP layer, real PostgreSQL (database codesentinel_test, wiped at start), real storage on a
 * temp dir and, where available, the real Docker judge.
 */
@SpringBootTest(properties = "app.judge-self-test=false")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AcceptanceTest {
    static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "cs-accept-" + System.nanoTime());

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://127.0.0.1:5432/codesentinel_test"));
        r.add("app.rate-limit-per-minute", () -> "100000"); // the limiter itself is unit-tested; this suite logs in many times
        r.add("app.static-dir", () -> { try { Path d = Files.createDirectories(STORAGE.resolveSibling(STORAGE.getFileName() + "-ui")); Files.writeString(d.resolve("index.html"), "<!doctype html><title>spa</title>"); Files.createDirectories(d.resolve("assets")); Files.writeString(d.resolve("assets/app.js"), "console.log(1)"); return d.toString(); } catch (Exception e) { throw new RuntimeException(e); } });
        r.add("spring.datasource.password", AcceptanceTest::resolveDbPassword);
        r.add("spring.flyway.clean-disabled", () -> "false");
        r.add("app.jwt.secret", () -> "test-secret-test-secret-test-secret-0123456789");
        r.add("app.storage.root", STORAGE::toString);
        r.add("app.bootstrap.instructor-email", () -> "prof@example.test");
        r.add("app.bootstrap.instructor-password", () -> "prof-password-1");
        r.add("app.worker.poll-millis", () -> "3600000"); // the test drives the worker explicitly
        r.add("app.registration-enabled", () -> "true");
    }

    private static String resolveDbPassword() {
        String env = System.getenv("POSTGRES_PASSWORD");
        if (env != null && !env.isBlank()) return env;
        for (Path p : List.of(Path.of(".env"), Path.of("../.env"))) {
            if (Files.isRegularFile(p)) {
                try {
                    for (String line : Files.readAllLines(p)) {
                        line = line.trim();
                        if (line.startsWith("POSTGRES_PASSWORD=")) {
                            return line.substring("POSTGRES_PASSWORD=".length()).trim();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    @TestConfiguration
    static class CleanFlyway {
        @Bean
        FlywayMigrationStrategy clean() {
            return f -> {
                f.clean();
                f.migrate();
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JobWorker worker;
    @Autowired dev.codesentinel.quiz.QuizCodeMarker codeMarker;
    @Autowired dev.codesentinel.quiz.QuizGrader quizGrader;
    @Autowired dev.codesentinel.quiz.strategy.QuestionGraderRegistry questionGraderRegistry;
    @Autowired DockerRunner docker;

    static String prof, otherProf;
    static long courseId, assignmentId;
    static String enrollCode;

    // ---- helpers ------------------------------------------------------------------------------------------------------
    JsonNode json(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString());
    }

    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    int status(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    JsonNode ok(MockHttpServletRequestBuilder b) throws Exception {
        MvcResult r = mvc.perform(b).andReturn();
        assertTrue(r.getResponse().getStatus() / 100 == 2, "expected 2xx but " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
        return json(r);
    }

    String login(String email, String pw) throws Exception {
        return ok(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(java.util.Map.of("email", email, "password", pw)))).get("token").asText();
    }

    String registerStudent(String email, String name) throws Exception {
        return ok(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(java.util.Map.of("email", email, "displayName", name, "password", "student-pass-1", "enrollmentCode", enrollCode)))).get("token").asText();
    }

    long submit(String token, String filename, String content) throws Exception {
        return ok(auth(multipart("/api/assignments/" + assignmentId + "/submissions").file(new MockMultipartFile("file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8))), token)).get("id").asLong();
    }

    static String sumProgram(String body) {
        return body;
    }

    // ---- 1. authentication and roles ------------------------------------------------------------------------------------
    @Test @Order(1)
    void unauthenticatedAndBadLoginAreRejected() throws Exception {
        assertEquals(401, status(get("/api/courses")));
        assertEquals(401, status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"prof@example.test\",\"password\":\"wrong-password\"}")));
        assertEquals(401, status(get("/api/courses").header("Authorization", "Bearer not.a.token")));
        prof = login("prof@example.test", "prof-password-1");
        assertEquals("INSTRUCTOR", ok(auth(get("/api/auth/me"), prof)).get("role").asText());
    }

    @Test @Order(2)
    void repeatedFailuresLockTheAccountTemporarily() throws Exception {
        for (int i = 0; i < 5; i++) status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"locktest@example.test\",\"password\":\"bad-password\"}"));
        assertEquals(429, status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"locktest@example.test\",\"password\":\"bad-password\"}")));
    }

    @Test @Order(3)
    void instructorBuildsCourseAndAssignmentWithHiddenTestsAndReferenceSolution() throws Exception {
        JsonNode c = ok(auth(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"SE101\",\"title\":\"Software Engineering (test)\"}"), prof));
        courseId = c.get("id").asLong();
        enrollCode = c.get("enrollmentCode").asText();
        assertTrue(enrollCode.startsWith("CS-"));
        String deadline = Instant.now().plus(7, ChronoUnit.DAYS).toString();
        JsonNode a = ok(auth(post("/api/courses/" + courseId + "/assignments").contentType(MediaType.APPLICATION_JSON).content(
                "{\"title\":\"Sum three numbers\",\"description\":\"Read three integers, print their sum.\",\"language\":\"python\",\"deadline\":\"" + deadline + "\",\"timeLimitMs\":2000,\"memoryLimitMb\":128}"), prof));
        assignmentId = a.get("id").asLong();
        assertEquals(409, status(auth(post("/api/assignments/" + assignmentId + "/publish"), prof)), "cannot publish without tests");
        ok(auth(post("/api/assignments/" + assignmentId + "/test-cases").contentType(MediaType.APPLICATION_JSON).content(
                "[{\"name\":\"sample\",\"input\":\"1 2 3\\n\",\"expectedOutput\":\"6\",\"sample\":true},"
                        + "{\"name\":\"secret-case-name\",\"input\":\"HIDDEN-INPUT-777\\n\",\"expectedOutput\":\"TOP-SECRET-EXPECTED\",\"sample\":false}]"), prof));
        ok(auth(multipart("/api/assignments/" + assignmentId + "/files").file(new MockMultipartFile("file", "reference.py", "text/plain", "print('REFERENCE-SOLUTION-BODY')".getBytes())).param("kind", "REFERENCE"), prof));
        ok(auth(post("/api/assignments/" + assignmentId + "/publish"), prof));
    }

    @Test @Order(4)
    void studentsRegisterWithEnrollmentCodeOnly() throws Exception {
        assertEquals(400, status(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.test\",\"displayName\":\"X\",\"password\":\"student-pass-1\",\"enrollmentCode\":\"CS-WRONG\"}")));
        String s = registerStudent("alice@example.test", "Alice (test)");
        assertEquals("STUDENT", ok(auth(get("/api/auth/me"), s)).get("role").asText());
        assertEquals(1, ok(auth(get("/api/courses"), s)).size());
        assertEquals(403, status(auth(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\",\"title\":\"Y\"}"), s)), "students cannot create courses");
        assertEquals(409, status(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ALICE@example.test\",\"displayName\":\"A\",\"password\":\"student-pass-1\",\"enrollmentCode\":\"" + enrollCode + "\"}")), "email unique case-insensitively");
    }

    @Test @Order(5)
    void studentNeverReceivesHiddenTestsOrReferenceSolution() throws Exception {
        String s = login("alice@example.test", "student-pass-1");
        MvcResult r = mvc.perform(auth(get("/api/assignments/" + assignmentId), s)).andReturn();
        String body = r.getResponse().getContentAsString();
        assertEquals(200, r.getResponse().getStatus());
        assertFalse(body.contains("TOP-SECRET-EXPECTED"));
        assertFalse(body.contains("HIDDEN-INPUT-777"));
        assertFalse(body.contains("secret-case-name"));
        assertFalse(body.contains("reference.py"));
        assertTrue(body.contains("\"hiddenTestCount\":1"));
        assertTrue(body.contains("\"sample\":true"));
        JsonNode instr = ok(auth(get("/api/assignments/" + assignmentId), prof));
        assertEquals(2, instr.get("testCases").size());
        assertEquals(1, instr.get("files").size());
        // download of the reference file by the student is a 404 (not 403: existence is not confirmed)
        long refId = instr.get("files").get(0).get("id").asLong();
        assertEquals(404, status(auth(get("/api/assignments/" + assignmentId + "/files/" + refId), s)));
        assertEquals(200, status(auth(get("/api/assignments/" + assignmentId + "/files/" + refId), prof)));
    }

    @Test @Order(6)
    void unpublishedAssignmentIsInvisibleToStudentsAndOtherInstructors() throws Exception {
        long draft = ok(auth(post("/api/courses/" + courseId + "/assignments").contentType(MediaType.APPLICATION_JSON).content(
                "{\"title\":\"Draft\",\"description\":\"d\",\"language\":\"c\",\"deadline\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\",\"timeLimitMs\":1000,\"memoryLimitMb\":64}"), prof)).get("id").asLong();
        String s = login("alice@example.test", "student-pass-1");
        assertEquals(404, status(auth(get("/api/assignments/" + draft), s)));
        assertEquals(1, ok(auth(get("/api/courses/" + courseId + "/assignments"), s)).size());
        users.insert("other@example.test", "Other Instructor", encoder.encode("other-pass-123"), "INSTRUCTOR", false);
        otherProf = login("other@example.test", "other-pass-123");
        assertEquals(404, status(auth(get("/api/courses/" + courseId), otherProf)));
        assertEquals(404, status(auth(get("/api/assignments/" + assignmentId), otherProf)));
        assertEquals(404, status(auth(get("/api/assignments/" + assignmentId + "/plagiarism-pairs"), otherProf)));
    }

    // ---- 2. uploads ------------------------------------------------------------------------------------------------------
    static byte[] zip(String name, byte[] data) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            z.putNextEntry(new ZipEntry(name));
            z.write(data);
            z.closeEntry();
        }
        return bo.toByteArray();
    }

    @Test @Order(7)
    void hostileUploadsAreRejected() throws Exception {
        String s = login("alice@example.test", "student-pass-1");
        String url = "/api/assignments/" + assignmentId + "/submissions";
        assertEquals(400, status(auth(multipart(url).file(new MockMultipartFile("file", "x.zip", "application/zip", zip("../../evil.py", "print(1)".getBytes()))), s)), "path traversal zip");
        assertEquals(400, status(auth(multipart(url).file(new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1, 2, 3})), s)), "disallowed type");
        assertEquals(400, status(auth(multipart(url).file(new MockMultipartFile("file", "main.py", "text/plain", new byte[]{'a', 0, 'b'})), s)), "binary content");
        assertEquals(400, status(auth(multipart(url).file(new MockMultipartFile("file", "main.c", "text/plain", "int main(){}".getBytes())), s)), "wrong language for this assignment");
        assertEquals(400, status(auth(multipart(url).file(new MockMultipartFile("file", "../main.py", "text/plain", "print(1)".getBytes())), s)), "bad file name");
        assertEquals(401, status(multipart(url).file(new MockMultipartFile("file", "main.py", "text/plain", "print(1)".getBytes()))));
        assertEquals(403, status(auth(multipart(url).file(new MockMultipartFile("file", "main.py", "text/plain", "print(1)".getBytes())), prof)), "instructors do not submit");
    }

    // ---- 3. judge, via the real worker and real containers -----------------------------------------------------------------
    @Test @Order(8)
    void correctAndWrongSubmissionsAreJudgedAndHiddenDetailsStayHidden() throws Exception {
        assumeTrue(docker.available() && docker.imageExists("codesentinel/runner-python:1"), "Docker or runner image unavailable");
        String alice = login("alice@example.test", "student-pass-1");
        String bob = registerStudent("bob@example.test", "Bob (test)");
        // The hidden case's expected output is deliberately unguessable, so a correct sum program fails it -> WA overall. Replace it with a real hidden case.
        ok(auth(post("/api/assignments/" + assignmentId + "/test-cases").contentType(MediaType.APPLICATION_JSON).content(
                "[{\"name\":\"sample\",\"input\":\"1 2 3\\n\",\"expectedOutput\":\"6\",\"sample\":true},"
                        + "{\"name\":\"secret-case-name\",\"input\":\"10 20 30\\n\",\"expectedOutput\":\"60\",\"sample\":false}]"), prof));
        long good = submit(alice, "main.py", "print(sum(map(int, input().split())))\n");
        long bad = submit(bob, "main.py", "print(6)\n");
        while (worker.processOne()) { /* drain */ }
        JsonNode g = ok(auth(get("/api/submissions/" + good + "/evaluation"), alice));
        assertEquals("AC", g.get("verdict").asText(), g.toString());
        assertEquals(2, g.get("passed").asInt());
        JsonNode b = ok(auth(get("/api/submissions/" + bad + "/evaluation"), bob));
        assertEquals("WA", b.get("verdict").asText(), b.toString());
        assertEquals(1, b.get("passed").asInt());
        String bobView = b.toString();
        assertFalse(bobView.contains("secret-case-name"), "hidden test name hidden from the student");
        assertTrue(bobView.contains("Hidden test 2"));
        JsonNode hiddenRow = b.get("tests").get(1);
        assertTrue(hiddenRow.get("detail").isNull() && hiddenRow.get("stdoutExcerpt").isNull(), "no hidden detail for students");
        JsonNode instrView = ok(auth(get("/api/submissions/" + bad + "/evaluation"), prof));
        assertTrue(instrView.toString().contains("secret-case-name"));
    }

    @Test @Order(9)
    void studentsCannotReadEachOthersSubmissions() throws Exception {
        String alice = login("alice@example.test", "student-pass-1");
        String bob = login("bob@example.test", "student-pass-1");
        long aliceSub = ok(auth(get("/api/assignments/" + assignmentId + "/submissions/me"), alice)).get(0).get("id").asLong();
        long fileId = ok(auth(get("/api/submissions/" + aliceSub), alice)).get("files").get(0).get("id").asLong();
        assertEquals(200, status(auth(get("/api/submissions/" + aliceSub + "/files/" + fileId), alice)));
        assertEquals(404, status(auth(get("/api/submissions/" + aliceSub), bob)));
        assertEquals(404, status(auth(get("/api/submissions/" + aliceSub + "/files/" + fileId), bob)));
        assertEquals(404, status(auth(get("/api/submissions/" + aliceSub + "/evaluation"), bob)));
        assertEquals(404, status(auth(get("/api/submissions/" + aliceSub), otherProf)));
        assertEquals(200, status(auth(get("/api/submissions/" + aliceSub), prof)));
        assertEquals(403, status(auth(get("/api/assignments/" + assignmentId + "/submissions"), bob)), "student cannot list all submissions");
        assertEquals(403, status(auth(get("/api/assignments/" + assignmentId + "/plagiarism-pairs"), bob)));
        assertEquals(403, status(auth(get("/api/plagiarism-pairs/1"), bob)), "students are forbidden from pair evidence");
    }

    // ---- 4. plagiarism triage ----------------------------------------------------------------------------------------------
    static final String ORIGINAL = """
            import sys

            def read_numbers(stream):
                values = []
                for line in stream:
                    for token in line.split():
                        values.append(int(token))
                return values

            def insertion_sort(items):
                result = list(items)
                for i in range(1, len(result)):
                    key = result[i]
                    j = i - 1
                    while j >= 0 and result[j] > key:
                        result[j + 1] = result[j]
                        j -= 1
                    result[j + 1] = key
                return result

            def median(sorted_values):
                n = len(sorted_values)
                if n == 0:
                    return 0
                mid = n // 2
                if n % 2 == 1:
                    return sorted_values[mid]
                return (sorted_values[mid - 1] + sorted_values[mid]) / 2

            def mode(values):
                counts = {}
                for v in values:
                    counts[v] = counts.get(v, 0) + 1
                best = None
                for v in sorted(counts):
                    if best is None or counts[v] > counts[best]:
                        best = v
                return best

            def main():
                numbers = read_numbers(sys.stdin)
                ordered = insertion_sort(numbers)
                total = 0
                for v in ordered:
                    total += v
                print("sorted:", " ".join(str(v) for v in ordered))
                print("sum:", total)
                print("median:", median(ordered))
                print("mode:", mode(ordered))

            main()
            """;

    // Same program: identifiers renamed, comments added, two independent functions swapped, literal unchanged.
    static final String COPY = """
            import sys
            # helper utilities for the lab
            def parse_input(src):
                collected = []
                for row in src:
                    for piece in row.split():
                        collected.append(int(piece))
                return collected

            def find_mode(data):
                freq = {}
                for x in data:
                    freq[x] = freq.get(x, 0) + 1
                top = None
                for x in sorted(freq):
                    if top is None or freq[x] > freq[top]:
                        top = x
                return top

            def central(seq):
                length = len(seq)
                if length == 0:
                    return 0
                half = length // 2
                if length % 2 == 1:
                    return seq[half]
                return (seq[half - 1] + seq[half]) / 2

            def order_values(vals):
                out = list(vals)
                for a in range(1, len(out)):
                    cur = out[a]
                    b = a - 1
                    while b >= 0 and out[b] > cur:
                        out[b + 1] = out[b]
                        b -= 1
                    out[b + 1] = cur
                return out

            def run():
                nums = parse_input(sys.stdin)
                srt = order_values(nums)
                acc = 0
                for x in srt:
                    acc += x
                print("sorted:", " ".join(str(x) for x in srt))
                print("sum:", acc)
                print("median:", central(srt))
                print("mode:", find_mode(srt))

            run()
            """;

    static final String INDEPENDENT_1 = """
            import sys
            from collections import Counter

            class Stats:
                def __init__(self, data):
                    self.data = sorted(data)
                    self.counter = Counter(self.data)

                def total(self):
                    return sum(self.data)

                def middle(self):
                    n = len(self.data)
                    if not n:
                        return 0
                    return self.data[n // 2] if n & 1 else (self.data[n // 2 - 1] + self.data[n // 2]) / 2

                def commonest(self):
                    if not self.counter:
                        return None
                    best_count = max(self.counter.values())
                    return min(k for k, c in self.counter.items() if c == best_count)

            if __name__ == "__main__":
                raw = sys.stdin.read().split()
                st = Stats([int(t) for t in raw])
                print("sorted:", *st.data)
                print("sum:", st.total())
                print("median:", st.middle())
                print("mode:", st.commonest())
            """;

    static final String INDEPENDENT_2 = """
            import sys
            from functools import reduce

            def merge(left, right):
                merged, i, j = [], 0, 0
                while i < len(left) and j < len(right):
                    if left[i] <= right[j]:
                        merged.append(left[i]); i += 1
                    else:
                        merged.append(right[j]); j += 1
                return merged + left[i:] + right[j:]

            def msort(xs):
                if len(xs) < 2:
                    return xs
                m = len(xs) // 2
                return merge(msort(xs[:m]), msort(xs[m:]))

            nums = list(map(int, sys.stdin.read().split()))
            ys = msort(nums)
            s = reduce(lambda a, b: a + b, ys, 0)
            med = 0 if not ys else (ys[len(ys) // 2] if len(ys) % 2 else (ys[len(ys) // 2 - 1] + ys[len(ys) // 2]) / 2)
            tally = {}
            for y in ys:
                tally[y] = tally.get(y, 0) + 1
            winner = max(sorted(tally), key=lambda k: tally[k]) if tally else None
            print("sorted: " + " ".join(map(str, ys)))
            print("sum: %d" % s)
            print("median: %s" % med)
            print("mode: %s" % winner)
            """;

    static final String INDEPENDENT_3 = """
            import heapq
            import sys

            def drain(values):
                heap = list(values)
                heapq.heapify(heap)
                while heap:
                    yield heapq.heappop(heap)

            def parse(text):
                return [int(w) for w in text.split()]

            def summarize(ordered):
                size = len(ordered)
                running = 0
                best_val, best_run, cur_val, cur_run = None, 0, None, 0
                for v in ordered:
                    running += v
                    if v == cur_val:
                        cur_run += 1
                    else:
                        cur_val, cur_run = v, 1
                    if cur_run > best_run:
                        best_val, best_run = cur_val, cur_run
                if size == 0:
                    mid = 0
                elif size % 2:
                    mid = ordered[size // 2]
                else:
                    mid = (ordered[size // 2 - 1] + ordered[size // 2]) / 2
                return running, mid, best_val

            data = list(drain(parse(sys.stdin.read())))
            total, mid, common = summarize(data)
            print("sorted:", " ".join(str(d) for d in data))
            print("sum:", total)
            print("median:", mid)
            print("mode:", common)
            """;

    static final String INDEPENDENT_4 = """
            import sys

            def bubble(arr):
                arr = arr[:]
                swapped = True
                while swapped:
                    swapped = False
                    for k in range(len(arr) - 1):
                        if arr[k] > arr[k + 1]:
                            arr[k], arr[k + 1] = arr[k + 1], arr[k]
                            swapped = True
                return arr

            line_items = []
            for ln in sys.stdin:
                line_items.extend(int(p) for p in ln.split())
            arranged = bubble(line_items)
            count = len(arranged)
            if count == 0:
                med_value = 0
            elif count % 2 == 0:
                med_value = (arranged[count // 2] + arranged[count // 2 - 1]) / 2
            else:
                med_value = arranged[(count - 1) // 2]
            histogram = dict()
            for item in arranged:
                histogram[item] = histogram.setdefault(item, 0) + 1
            frequent = None
            for item, times in sorted(histogram.items()):
                if frequent is None or times > histogram[frequent]:
                    frequent = item
            print("sorted:", " ".join(map(str, arranged)))
            print("sum:", sum(arranged))
            print("median:", med_value)
            print("mode:", frequent)
            """;

    static long plagA, plagB;

    @Test @Order(10)
    void plagiarismTriageFlagsTheCopiedPairAndNotTheIndependentOnes() throws Exception {
        // Fresh assignment so judging state of earlier tests does not matter.
        String deadline = Instant.now().plus(7, ChronoUnit.DAYS).toString();
        assignmentId = ok(auth(post("/api/courses/" + courseId + "/assignments").contentType(MediaType.APPLICATION_JSON).content(
                "{\"title\":\"Stats\",\"description\":\"stats\",\"language\":\"python\",\"deadline\":\"" + deadline + "\",\"timeLimitMs\":2000,\"memoryLimitMb\":128}"), prof)).get("id").asLong();
        ok(auth(post("/api/assignments/" + assignmentId + "/test-cases").contentType(MediaType.APPLICATION_JSON).content(
                "[{\"name\":\"t\",\"input\":\"3 1 2\\n\",\"expectedOutput\":\"x\",\"sample\":true}]"), prof));
        ok(auth(post("/api/assignments/" + assignmentId + "/publish"), prof));
        String[] progs = {ORIGINAL, COPY, INDEPENDENT_1, INDEPENDENT_2, INDEPENDENT_3, INDEPENDENT_4};
        long[] subIds = new long[progs.length];
        for (int i = 0; i < progs.length; i++) {
            String tok = i == 0 ? login("alice@example.test", "student-pass-1") : i == 1 ? login("bob@example.test", "student-pass-1") : registerStudent("s" + i + "@example.test", "Student " + i + " (test)");
            subIds[i] = submit(tok, "main.py", progs[i]);
        }
        plagA = Math.min(subIds[0], subIds[1]);
        plagB = Math.max(subIds[0], subIds[1]);

        assertEquals(403, status(auth(post("/api/assignments/" + assignmentId + "/plagiarism/run"), login("alice@example.test", "student-pass-1"))));
        JsonNode res = ok(auth(post("/api/assignments/" + assignmentId + "/plagiarism/run"), prof));
        assertTrue(res.get("disclaimer").asText().contains("not a finding"));
        JsonNode pairs = res.get("pairs");
        JsonNode copied = null;
        int flagged = 0;
        for (JsonNode p : pairs) {
            String st = p.get("state").asText();
            boolean isCopy = p.get("a").get("submissionId").asLong() == plagA && p.get("b").get("submissionId").asLong() == plagB;
            if (isCopy) copied = p;
            if (!isCopy && (st.equals("NEEDS_REVIEW") || st.equals("HIGH_PRIORITY_REVIEW"))) flagged++;
        }
        assertNotNull(copied, "the copied pair must be a candidate: " + res);
        assertTrue(copied.get("state").asText().matches("NEEDS_REVIEW|HIGH_PRIORITY_REVIEW"), copied.toString());
        assertEquals(0, flagged, "independent solutions must not be queued for review: " + pairs);
        assertTrue(copied.get("a").get("submissionId").asLong() < copied.get("b").get("submissionId").asLong(), "canonical ordering");

        JsonNode detail = ok(auth(get("/api/plagiarism-pairs/" + copied.get("id").asLong()), prof));
        assertTrue(detail.get("fragments").size() > 0);
        assertTrue(detail.get("explanation").size() > 0);
        String text = detail.toString().toLowerCase();
        for (String forbidden : new String[]{"guilty", "cheat", "plagiarism confirmed", "plagiarised", "plagiarized"})
            assertFalse(text.contains(forbidden), "neutral wording only: " + forbidden);
        // student endpoints never reveal pairs
        assertEquals(403, status(auth(get("/api/plagiarism-pairs/" + copied.get("id").asLong()), login("bob@example.test", "student-pass-1"))));
        assertEquals(404, status(auth(get("/api/plagiarism-pairs/" + copied.get("id").asLong()), otherProf)));

        // review decision is recorded, validated, and survives a re-run
        assertEquals(400, status(auth(post("/api/plagiarism-pairs/" + copied.get("id").asLong() + "/review").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"GUILTY\"}"), prof)));
        ok(auth(post("/api/plagiarism-pairs/" + copied.get("id").asLong() + "/review").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"INCONCLUSIVE\",\"note\":\"Shared lab handout structure; asked students to explain.\"}"), prof));
        JsonNode rerun = ok(auth(post("/api/assignments/" + assignmentId + "/plagiarism/run"), prof));
        boolean carried = false;
        for (JsonNode p : rerun.get("pairs"))
            if (p.get("a").get("submissionId").asLong() == plagA && p.get("b").get("submissionId").asLong() == plagB)
                carried = p.get("latestReview") != null && !p.get("latestReview").isNull() && p.get("latestReview").get("decision").asText().equals("INCONCLUSIVE");
        assertTrue(carried, "review carries over to the same two submissions after a re-run");
    }

    @Test @Order(11)
    void plagiarismIsScopedToOneAssignment() throws Exception {
        // The Stats assignment has 6 submissions; the first assignment has 2. Their submission ids must never mix.
        JsonNode pairs = ok(auth(get("/api/assignments/" + assignmentId + "/plagiarism-pairs"), prof)).get("pairs");
        JsonNode subs = ok(auth(get("/api/assignments/" + assignmentId + "/submissions"), prof));
        java.util.Set<Long> ids = new java.util.HashSet<>();
        subs.forEach(s -> ids.add(s.get("id").asLong()));
        for (JsonNode p : pairs) {
            assertTrue(ids.contains(p.get("a").get("submissionId").asLong()));
            assertTrue(ids.contains(p.get("b").get("submissionId").asLong()));
        }
    }

    @Test @Order(12)
    void dashboardsAreComputedFromData() throws Exception {
        JsonNode d = ok(auth(get("/api/dashboard/instructor"), prof));
        assertEquals(1, d.get("courses").asInt());
        assertTrue(d.get("submissions").asInt() >= 8);
        assertTrue(d.get("pairsNeedingReview").asInt() == 0, "the only flagged pair was reviewed");
        JsonNode sd = ok(auth(get("/api/dashboard/student"), login("alice@example.test", "student-pass-1")));
        assertTrue(sd.get("recent").size() >= 2);
        assertEquals(403, status(auth(get("/api/dashboard/instructor"), login("alice@example.test", "student-pass-1"))));
    }

    @Test @Order(13)
    void auditTrailRecordsKeyActionsWithoutSecrets() throws Exception {
        // Direct DB read through the repository-free route: use the instructor API surface only by checking behaviour indirectly is not possible,
        // so we assert via a JDBC query.
        var jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(dataSource);
        var actions = jdbc.sql("SELECT DISTINCT action FROM audit_logs").query(String.class).list();
        for (String a : new String[]{"LOGIN", "LOGIN_FAILED", "COURSE_CREATE", "ASSIGNMENT_CREATE", "ASSIGNMENT_PUBLISH", "SUBMISSION_CREATE", "PLAGIARISM_RUN", "PLAGIARISM_REVIEW"})
            assertTrue(actions.contains(a), "missing audit action " + a + " in " + actions);
        assertEquals(0, jdbc.sql("SELECT count(*) FROM audit_logs WHERE detail ILIKE '%password%' OR detail ILIKE '%token%'").query(Long.class).single());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM users WHERE password_hash NOT LIKE '$2%'").query(Long.class).single(), "only BCrypt hashes are stored");
    }

    // ---- 5. lifecycle: unpublish, archive, deadline, worker recovery -----------------------------------------------------
    long newAssignment(String title, Instant deadline, long course) throws Exception {
        long id = ok(auth(post("/api/courses/" + course + "/assignments").contentType(MediaType.APPLICATION_JSON).content(
                "{\"title\":\"" + title + "\",\"description\":\"d\",\"language\":\"python\",\"deadline\":\"" + deadline + "\",\"timeLimitMs\":1000,\"memoryLimitMb\":64}"), prof)).get("id").asLong();
        ok(auth(post("/api/assignments/" + id + "/test-cases").contentType(MediaType.APPLICATION_JSON).content("[{\"name\":\"t\",\"input\":\"\",\"expectedOutput\":\"x\",\"sample\":true}]"), prof));
        ok(auth(post("/api/assignments/" + id + "/publish"), prof));
        return id;
    }

    @Test @Order(14)
    void submissionsAfterTheDeadlineAreRejectedOnTheBackend() throws Exception {
        long late = newAssignment("Late", Instant.now().minus(1, ChronoUnit.DAYS), courseId);
        String alice = login("alice@example.test", "student-pass-1");
        assertEquals(409, status(auth(multipart("/api/assignments/" + late + "/submissions").file(new MockMultipartFile("file", "main.py", "text/plain", "print(1)".getBytes())), alice)));
    }

    @Test @Order(15)
    void unpublishAndArchiveHideAssignmentsFromStudentsAndBlockSubmissions() throws Exception {
        long a = newAssignment("Lifecycle", Instant.now().plus(3, ChronoUnit.DAYS), courseId);
        String alice = login("alice@example.test", "student-pass-1");
        assertEquals(200, status(auth(get("/api/assignments/" + a), alice)));
        ok(auth(post("/api/assignments/" + a + "/unpublish"), prof));
        assertEquals(404, status(auth(get("/api/assignments/" + a), alice)));
        assertEquals(404, status(auth(multipart("/api/assignments/" + a + "/submissions").file(new MockMultipartFile("file", "main.py", "text/plain", "print(1)".getBytes())), alice)));
        ok(auth(post("/api/assignments/" + a + "/publish"), prof));
        assertEquals(200, status(auth(get("/api/assignments/" + a), alice)));
        assertTrue(ok(auth(post("/api/assignments/" + a + "/archive"), prof)).get("archived").asBoolean());
        assertEquals(404, status(auth(get("/api/assignments/" + a), alice)));
        boolean listedForStudent = false;
        for (JsonNode n : ok(auth(get("/api/courses/" + courseId + "/assignments"), alice))) listedForStudent |= n.get("id").asLong() == a;
        assertFalse(listedForStudent);
        boolean listedDefault = false, listedWithArchived = false;
        for (JsonNode n : ok(auth(get("/api/courses/" + courseId + "/assignments"), prof))) listedDefault |= n.get("id").asLong() == a;
        for (JsonNode n : ok(auth(get("/api/courses/" + courseId + "/assignments?includeArchived=true"), prof))) listedWithArchived |= n.get("id").asLong() == a;
        assertFalse(listedDefault);
        assertTrue(listedWithArchived);
        assertEquals(403, status(auth(post("/api/assignments/" + a + "/unpublish"), alice)), "students cannot unpublish");
        assertEquals(404, status(auth(post("/api/assignments/" + a + "/archive"), otherProf)), "other instructors cannot archive");
        ok(auth(post("/api/assignments/" + a + "/archive?archived=false"), prof));
        assertEquals(200, status(auth(get("/api/assignments/" + a), alice)));
    }

    @Test @Order(16)
    void archivedCourseDisappearsForStudentsAndRejectsEnrollmentButIsRestorable() throws Exception {
        JsonNode c = ok(auth(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ARC1\",\"title\":\"Archive me\"}"), prof));
        long cid = c.get("id").asLong();
        String code = c.get("enrollmentCode").asText();
        String alice = login("alice@example.test", "student-pass-1");
        ok(auth(post("/api/courses/" + cid + "/enroll").contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentCode\":\"" + code + "\"}"), alice));
        assertEquals(200, status(auth(get("/api/courses/" + cid), alice)));
        ok(auth(post("/api/courses/" + cid + "/archive"), prof));
        assertEquals(404, status(auth(get("/api/courses/" + cid), alice)));
        for (JsonNode n : ok(auth(get("/api/courses"), alice))) assertNotEquals(cid, n.get("id").asLong());
        for (JsonNode n : ok(auth(get("/api/courses"), prof))) assertNotEquals(cid, n.get("id").asLong());
        boolean seen = false;
        for (JsonNode n : ok(auth(get("/api/courses?includeArchived=true"), prof))) seen |= n.get("id").asLong() == cid;
        assertTrue(seen);
        assertEquals(400, status(auth(post("/api/courses/" + cid + "/enroll").contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentCode\":\"" + code + "\"}"), registerStudentToken())));
        ok(auth(post("/api/courses/" + cid + "/archive?archived=false"), prof));
        assertEquals(200, status(auth(get("/api/courses/" + cid), alice)));
    }

    String registerStudentToken() throws Exception {
        return registerStudent("late-" + System.nanoTime() + "@example.test", "Late Joiner (test)");
    }

    @Test @Order(17)
    void workerRetriesThenFailsSafelyAndRecoversStuckJobs() throws Exception {
        var jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(dataSource);
        long student = jdbc.sql("SELECT id FROM users WHERE email='alice@example.test'").query(Long.class).single();
        // Earlier tests left judge jobs queued (never drained); park them so this test controls the queue.
        jdbc.sql("UPDATE evaluation_jobs SET status='DONE' WHERE status='QUEUED'").update();
        // A submission whose stored file is missing: every attempt throws inside the worker.
        long sub = jdbc.sql("INSERT INTO submissions(assignment_id, student_id, attempt, status) VALUES (?,?,99,'QUEUED') RETURNING id").params(assignmentId, student).query(Long.class).single();
        jdbc.sql("INSERT INTO submission_files(submission_id, path, storage_key, size_bytes, sha256) VALUES (?, 'main.py', 'sub/missing/f0', 1, ?)").params(sub, "0".repeat(64)).update();
        long job = jdbc.sql("INSERT INTO evaluation_jobs(submission_id) VALUES (?) RETURNING id").param(sub).query(Long.class).single();
        assertTrue(worker.processOne());
        assertEquals("QUEUED", jdbc.sql("SELECT status FROM evaluation_jobs WHERE id=?").param(job).query(String.class).single(), "first failure is retried");
        assertTrue(worker.processOne());
        assertEquals("FAILED", jdbc.sql("SELECT status FROM evaluation_jobs WHERE id=?").param(job).query(String.class).single(), "attempt cap reached");
        assertEquals("IE", jdbc.sql("SELECT verdict FROM evaluation_results WHERE submission_id=?").param(sub).query(String.class).single(), "student sees a judge problem, not a verdict on their code");
        assertEquals("FAILED", jdbc.sql("SELECT status FROM submissions WHERE id=?").param(sub).query(String.class).single());
        assertFalse(worker.processOne(), "nothing left in the queue");

        // Jobs left RUNNING by a crashed worker: re-queued while attempts remain, failed once exhausted.
        long s2 = jdbc.sql("INSERT INTO submissions(assignment_id, student_id, attempt, status) VALUES (?,?,98,'RUNNING') RETURNING id").params(assignmentId, student).query(Long.class).single();
        long j1 = jdbc.sql("INSERT INTO evaluation_jobs(submission_id, status, attempts, locked_at) VALUES (?, 'RUNNING', 1, now() - interval '1 hour') RETURNING id").param(s2).query(Long.class).single();
        long j2 = jdbc.sql("INSERT INTO evaluation_jobs(submission_id, status, attempts, locked_at) VALUES (?, 'RUNNING', 2, now() - interval '1 hour') RETURNING id").param(s2).query(Long.class).single();
        long j3 = jdbc.sql("INSERT INTO evaluation_jobs(submission_id, status, attempts, locked_at) VALUES (?, 'RUNNING', 1, now()) RETURNING id").param(s2).query(Long.class).single();
        worker.recoverStuck();
        assertEquals("QUEUED", jdbc.sql("SELECT status FROM evaluation_jobs WHERE id=?").param(j1).query(String.class).single());
        assertEquals("FAILED", jdbc.sql("SELECT status FROM evaluation_jobs WHERE id=?").param(j2).query(String.class).single());
        assertEquals("RUNNING", jdbc.sql("SELECT status FROM evaluation_jobs WHERE id=?").param(j3).query(String.class).single(), "a fresh job is not touched");
        jdbc.sql("DELETE FROM evaluation_jobs WHERE submission_id=?").param(s2).update();
    }

    @Test @Order(18)
    void evidenceReportsAreInstructorOnlyAndNeutral() throws Exception {
        JsonNode pairs = ok(auth(get("/api/assignments/" + assignmentId + "/plagiarism-pairs"), prof)).get("pairs");
        long pid = -1;
        for (JsonNode p : pairs) if (p.get("a").get("submissionId").asLong() == plagA && p.get("b").get("submissionId").asLong() == plagB) pid = p.get("id").asLong();
        assertTrue(pid > 0);
        String bob = login("bob@example.test", "student-pass-1");
        String html = mvc.perform(auth(get("/api/plagiarism-pairs/" + pid + "/report.html"), prof)).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Matched fragments") && html.contains("not a finding"), "report carries the evidence and the disclaimer");
        assertTrue(html.contains("def ") || html.contains("print"), "matched code excerpts are included");
        String csv = mvc.perform(auth(get("/api/assignments/" + assignmentId + "/plagiarism-report.csv"), prof)).andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("# ") && csv.contains("pair_id,state"));
        for (String out : new String[]{html, csv}) {
            String low = out.toLowerCase();
            for (String bad : new String[]{"guilty", "cheat", "plagiarised", "plagiarized", "plagiarism confirmed", "expected_output", "expectedoutput"})
                assertFalse(low.contains(bad), "forbidden wording/material in export: " + bad);
        }
        assertEquals(403, status(auth(get("/api/plagiarism-pairs/" + pid + "/report.html"), bob)));
        assertEquals(403, status(auth(get("/api/assignments/" + assignmentId + "/plagiarism-report.csv"), bob)));
        assertEquals(404, status(auth(get("/api/plagiarism-pairs/" + pid + "/report.html"), otherProf)));
        assertEquals(404, status(auth(get("/api/assignments/" + assignmentId + "/plagiarism-report.csv"), otherProf)));
        assertEquals(401, status(get("/api/plagiarism-pairs/" + pid + "/report.html")));
    }

    @Test @Order(19)
    void builtUiIsServedPubliclyButApiStaysProtected() throws Exception {
        assertEquals(200, status(get("/")));
        assertEquals(200, status(get("/courses/12/assignments")), "client-side routes fall back to index.html");
        assertTrue(mvc.perform(get("/courses/12")).andReturn().getResponse().getContentAsString().contains("<title>spa"));
        assertEquals(200, status(get("/assets/app.js")));
        assertEquals(404, status(get("/assets/missing.js")), "missing files are not masked by the SPA fallback");
        assertEquals(401, status(get("/api/courses")));
        assertEquals(401, status(get("/api/does-not-exist")), "unknown API paths are never answered with the UI");
        assertEquals(401, status(post("/")), "only GET is public outside the API");
        var h = mvc.perform(get("/")).andReturn().getResponse();
        assertTrue(h.getHeader("Content-Security-Policy").contains("default-src 'self'"));
        assertEquals("nosniff", h.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", h.getHeader("X-Frame-Options"));
        int t = status(get("/..%2f..%2fetc/passwd"));
        assertTrue(t == 400 || t == 404 || t == 403, "traversal rejected, got " + t);
    }

    @Autowired javax.sql.DataSource dataSource;

    @Test @Order(20)
    void studentsCanJoinAClassWithTheCodeAlone() throws Exception {
        JsonNode c = ok(auth(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"JOIN1\",\"title\":\"Join by code\"}"), prof));
        String code = c.get("enrollmentCode").asText();
        String stu = registerStudent("joiner@example.test", "Joiner");
        assertEquals(400, status(auth(post("/api/courses/join").contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentCode\":\"CS-WRONG\"}"), stu)));
        assertEquals(403, status(auth(post("/api/courses/join").contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentCode\":\"" + code + "\"}"), prof)), "instructors do not enroll");
        JsonNode joined = ok(auth(post("/api/courses/join").contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentCode\":\"" + code + "\"}"), stu));
        assertEquals(c.get("id").asLong(), joined.get("id").asLong());
        assertEquals(200, status(auth(post("/api/courses/join").contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentCode\":\"" + code + "\"}"), stu)), "joining twice is harmless");
    }

    @Test @Order(21)
    void instructorInsightsAndGradebookAreOwnerOnlyAndConsistent() throws Exception {
        String stu = registerStudent("insights-student@example.test", "=cmd|' /C calc'!A0");
        long sub = submit(stu, "main.py", "print(sum(map(int, input().split())))");
        while (worker.processOne()) { /* drain */ }
        JsonNode a = ok(auth(get("/api/assignments/" + assignmentId + "/analytics"), prof));
        assertTrue(a.get("enrolled").asLong() >= 1);
        assertTrue(a.get("submitted").asLong() >= 1);
        long judged = 0;
        for (var e : a.get("verdicts")) judged += e.asLong();
        assertEquals(a.get("submitted").asLong(), judged + a.get("pending").asLong(), "every student's latest attempt is counted exactly once");
        long inBuckets = 0;
        for (JsonNode b : a.get("scoreBuckets")) inBuckets += b.get("count").asLong();
        assertEquals(judged, inBuckets);
        assertTrue(a.get("tests").size() >= 1);
        for (JsonNode t : a.get("tests")) assertTrue(t.get("passed").asLong() <= t.get("total").asLong());

        JsonNode g = ok(auth(get("/api/courses/" + courseId + "/gradebook"), prof));
        assertTrue(g.get("assignments").size() >= 1);
        boolean found = false;
        for (JsonNode r : g.get("rows")) if (r.get("email").asText().equals("insights-student@example.test")) {
            found = true;
            assertEquals(g.get("assignments").size(), r.get("cells").size());
        }
        assertTrue(found);
        var csv = mvc.perform(auth(get("/api/courses/" + courseId + "/gradebook.csv"), prof)).andReturn().getResponse();
        assertEquals(200, csv.getStatus());
        String body = csv.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.startsWith("student,email,"));
        assertTrue(body.contains("\"'=cmd"), "spreadsheet formulas in names are neutralised");
        assertEquals(403, status(auth(get("/api/assignments/" + assignmentId + "/analytics"), stu)));
        assertEquals(403, status(auth(get("/api/courses/" + courseId + "/gradebook"), stu)));
        assertEquals(404, status(auth(get("/api/assignments/" + assignmentId + "/analytics"), otherProf)));
        assertEquals(404, status(auth(get("/api/courses/" + courseId + "/gradebook.csv"), otherProf)));
    }

    @Test @Order(22)
    void accountNameAndPasswordChangeRequireTheCurrentPassword() throws Exception {
        String stu = registerStudent("account@example.test", "Account Holder");
        var json = MediaType.APPLICATION_JSON;
        assertEquals("New Name", ok(auth(patch("/api/auth/me").contentType(json).content("{\"displayName\":\"  New Name \"}"), stu)).get("displayName").asText());
        assertEquals("New Name", ok(auth(get("/api/auth/me"), stu)).get("displayName").asText(), "persisted");
        assertEquals(400, status(auth(patch("/api/auth/me").contentType(json).content("{\"displayName\":\"   \"}"), stu)));
        assertEquals(400, status(auth(post("/api/auth/password").contentType(json).content("{\"currentPassword\":\"nope-nope-1\",\"newPassword\":\"brand-new-pass-1\"}"), stu)), "wrong current password");
        assertEquals(400, status(auth(post("/api/auth/password").contentType(json).content("{\"currentPassword\":\"student-pass-1\",\"newPassword\":\"short\"}"), stu)), "too short");
        assertEquals(400, status(auth(post("/api/auth/password").contentType(json).content("{\"currentPassword\":\"student-pass-1\",\"newPassword\":\"student-pass-1\"}"), stu)), "must differ");
        assertEquals(204, status(auth(post("/api/auth/password").contentType(json).content("{\"currentPassword\":\"student-pass-1\",\"newPassword\":\"brand-new-pass-1\"}"), stu)));
        assertEquals(401, status(post("/api/auth/login").contentType(json).content("{\"email\":\"account@example.test\",\"password\":\"student-pass-1\"}")), "old password stops working");
        assertNotNull(login("account@example.test", "brand-new-pass-1"));
        assertEquals(401, status(post("/api/auth/password").contentType(json).content("{\"currentPassword\":\"x\",\"newPassword\":\"yyyyyyyyy\"}")), "needs sign-in");
    }

    @Test @Order(23)
    void announcementsAreInstructorWrittenAndMemberReadable() throws Exception {
        var json = MediaType.APPLICATION_JSON;
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"ANN1\",\"title\":\"Announcements\"}"), prof));
        long cid = c.get("id").asLong();
        String member = ok(post("/api/auth/register").contentType(json).content(om.writeValueAsString(java.util.Map.of("email", "ann-member@example.test", "displayName", "Member", "password", "student-pass-1", "enrollmentCode", c.get("enrollmentCode").asText())))).get("token").asText();
        String outsider = registerStudent("ann-outsider@example.test", "Outsider");
        JsonNode a = ok(auth(post("/api/courses/" + cid + "/announcements").contentType(json).content("{\"title\":\"Lab moved\",\"body\":\"Room 12 today.\",\"pinned\":false}"), prof));
        JsonNode b = ok(auth(post("/api/courses/" + cid + "/announcements").contentType(json).content("{\"title\":\"Syllabus\",\"body\":\"Read it.\",\"pinned\":true}"), prof));
        JsonNode list = ok(auth(get("/api/courses/" + cid + "/announcements"), member));
        assertEquals(2, list.size());
        assertEquals("Syllabus", list.get(0).get("title").asText(), "pinned first");
        assertEquals(403, status(auth(post("/api/courses/" + cid + "/announcements").contentType(json).content("{\"title\":\"x\",\"body\":\"y\"}"), member)), "students cannot post");
        assertEquals(404, status(auth(post("/api/courses/" + cid + "/announcements").contentType(json).content("{\"title\":\"x\",\"body\":\"y\"}"), otherProf)), "other instructors cannot post");
        assertEquals(404, status(auth(get("/api/courses/" + cid + "/announcements"), outsider)), "non-members cannot read");
        assertEquals(400, status(auth(post("/api/courses/" + cid + "/announcements").contentType(json).content("{\"title\":\"  \",\"body\":\"y\"}"), prof)));
        assertEquals(403, status(auth(delete("/api/announcements/" + a.get("id").asLong()), member)));
        assertEquals(404, status(auth(delete("/api/announcements/" + a.get("id").asLong()), otherProf)));
        assertFalse(ok(auth(post("/api/announcements/" + b.get("id").asLong() + "/pin?pinned=false"), prof)).get("pinned").asBoolean());
        assertEquals(204, status(auth(delete("/api/announcements/" + a.get("id").asLong()), prof)));
        assertEquals(1, ok(auth(get("/api/courses/" + cid + "/announcements"), member)).size());
    }

    @Test @Order(24)
    void duplicateBulkRerunSearchAndExportWork() throws Exception {
        var json = MediaType.APPLICATION_JSON;
        JsonNode copy = ok(auth(post("/api/assignments/" + assignmentId + "/duplicate"), prof));
        JsonNode orig = ok(auth(get("/api/assignments/" + assignmentId), prof));
        assertNotEquals(orig.get("id").asLong(), copy.get("id").asLong());
        assertTrue(copy.get("title").asText().endsWith("(copy)"));
        assertFalse(copy.get("published").asBoolean(), "copies start as drafts");
        assertEquals(orig.get("testCases").size(), copy.get("testCases").size());
        assertEquals(orig.get("files").size(), copy.get("files").size());
        assertEquals(orig.get("courseId").asLong(), copy.get("courseId").asLong());
        assertEquals(404, status(auth(post("/api/assignments/" + assignmentId + "/duplicate"), otherProf)));
        assertEquals(403, status(auth(post("/api/assignments/" + assignmentId + "/duplicate"), registerStudent("dup-student@example.test", "Dup"))));

        // The live assignment still has finished submissions from earlier tests; re-queue them all and let the worker finish them.
        JsonNode r = ok(auth(post("/api/assignments/" + assignmentId + "/rejudge?verdict=all"), prof));
        assertTrue(r.get("queued").asInt() >= 1);
        assertEquals(400, status(auth(post("/api/assignments/" + assignmentId + "/rejudge?verdict=banana"), prof)));
        assertEquals(404, status(auth(post("/api/assignments/" + assignmentId + "/rejudge?verdict=all"), otherProf)));
        assertEquals(0, ok(auth(post("/api/assignments/" + assignmentId + "/rejudge?verdict=all"), prof)).get("queued").asInt(), "attempts already waiting are not queued twice");
        while (worker.processOne()) { /* drain */ }

        String csv = mvc.perform(auth(get("/api/assignments/" + assignmentId + "/submissions.csv"), prof)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("Student,Email,Attempt,"));
        assertTrue(csv.contains("\"'=cmd"), "formula-looking names are neutralised in the export");
        assertEquals(404, status(auth(get("/api/assignments/" + assignmentId + "/submissions.csv"), otherProf)));

        assertTrue(ok(auth(get("/api/assignments/" + assignmentId + "/submissions?q=insights-student"), prof)).size() >= 1, "search by email");
        assertEquals(0, ok(auth(get("/api/assignments/" + assignmentId + "/submissions?q=zzz-nobody"), prof)).size());
        assertEquals(0, ok(auth(get("/api/assignments/" + assignmentId + "/submissions?q=%25"), prof)).size(), "a percent sign is literal, not a wildcard");

        JsonNode hits = ok(auth(get("/api/search?q=insights-student"), prof));
        assertTrue(hits.size() >= 1);
        assertEquals("student", hits.get(0).get("type").asText());
        assertEquals(0, ok(auth(get("/api/search?q=insights-student"), otherProf)).size(), "another instructor's students are not searchable");
        assertEquals(0, ok(auth(get("/api/search?q=a"), prof)).size(), "one letter is too short to search");
        assertEquals(0, ok(auth(get("/api/search?q=%25%25"), prof)).size(), "wildcards are escaped");
        String stu = login("insights-student@example.test", "student-pass-1");
        for (JsonNode h : ok(auth(get("/api/search?q=" + orig.get("title").asText().substring(0, 3)), stu))) assertNotEquals("student", h.get("type").asText(), "students never see other students");
        assertEquals(401, status(get("/api/search?q=abc")));
    }

    @Test @Order(25)
    void studentsSeeOnlyTheirOwnProgress() throws Exception {
        String stu = login("insights-student@example.test", "student-pass-1");
        String other = registerStudent("progress-other@example.test", "Other Progress");
        JsonNode mine = ok(auth(get("/api/courses/" + courseId + "/my-progress"), stu));
        assertEquals(1, mine.get("rows").size(), "exactly one row: the caller's");
        assertEquals("insights-student@example.test", mine.get("rows").get(0).get("email").asText());
        assertTrue(mine.get("assignments").size() >= 1);
        JsonNode theirs = ok(auth(get("/api/courses/" + courseId + "/my-progress"), other));
        assertEquals("progress-other@example.test", theirs.get("rows").get(0).get("email").asText());
        for (JsonNode c : theirs.get("rows").get(0).get("cells")) {
            assertTrue(c.get("score").isNull(), "a student with no submissions has no results to show");
            assertTrue(java.util.Set.of("NOT_STARTED", "MISSING").contains(c.get("status").asText()));
        }
        assertEquals(403, status(auth(get("/api/courses/" + courseId + "/my-progress"), prof)), "instructors use the gradebook");
        String outsider = ok(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(java.util.Map.of("email", "progress-outsider@example.test", "displayName", "Outsider", "password", "student-pass-1", "enrollmentCode", ok(auth(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"PRG\",\"title\":\"Other course\"}"), prof)).get("enrollmentCode").asText())))).get("token").asText();
        assertEquals(404, status(auth(get("/api/courses/" + courseId + "/my-progress"), outsider)), "not enrolled in this course");
    }

    @Test @Order(26)
    void quizBuilderAttemptsMarkingAndRelease() throws Exception {
        assertNotNull(quizGrader, "QuizGrader bean must be injected by Spring");
        assertNotNull(questionGraderRegistry, "QuestionGraderRegistry bean must be injected by Spring");
        for (String type : java.util.Set.of("MCQ", "MULTI", "TRUE_FALSE", "SHORT", "OUTPUT", "BLANK", "FILL_BLANK", "LONG", "FILE", "CODE")) {
            assertTrue(questionGraderRegistry.isSupported(type), "Registry must support " + type);
            assertTrue(quizGrader.isSupported(type), "QuizGrader must support " + type);
        }

        var json = MediaType.APPLICATION_JSON;
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"QZ1\",\"title\":\"Quiz course\",\"description\":\"About quizzes\",\"subject\":\"CS\"}"), prof));
        long cid = c.get("id").asLong();
        assertEquals("About quizzes", c.get("description").asText());
        String stu = ok(post("/api/auth/register").contentType(json).content(om.writeValueAsString(java.util.Map.of("email", "quizzer@example.test", "displayName", "Quizzer", "password", "student-pass-1", "enrollmentCode", c.get("enrollmentCode").asText())))).get("token").asText();
        String due = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        JsonNode a = ok(auth(post("/api/courses/" + cid + "/assignments").contentType(json).content("{\"title\":\"Mixed quiz\",\"description\":\"Answer all\",\"deadline\":\"" + due + "\",\"kind\":\"QUIZ\",\"options\":{\"maxAttempts\":2,\"releaseMode\":\"MANUAL\",\"quizMinutes\":30}}"), prof));
        long aid = a.get("id").asLong();
        assertEquals("QUIZ", a.get("kind").asText());
        assertEquals("none", a.get("language").asText());
        assertEquals(409, status(auth(post("/api/assignments/" + aid + "/publish"), prof)), "no questions yet");
        String qs = """
                [{"type":"MCQ","prompt":"2+2?","marks":2,"options":["3","4","5"],"answer":{"correct":[1]},"settings":{"negative":0.5},"explanation":"basic"},
                 {"type":"MULTI","prompt":"Primes?","marks":4,"options":["2","4","5","9"],"answer":{"correct":[0,2]},"settings":{"partial":true}},
                 {"type":"FILL_BLANK","prompt":"The capital of France is ___.","marks":1,"answer":{"blanks":[["Paris"]]}},
                 {"type":"OUTPUT","prompt":"Output?","marks":3,"settings":{"code":"print(1+1)","codeLanguage":"python"},"answer":{"accepted":["2"]}},
                 {"type":"LONG","prompt":"Explain recursion.","marks":10}]""";
        assertEquals(400, status(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content("[{\"type\":\"MCQ\",\"prompt\":\"x\",\"marks\":1,\"options\":[\"a\",\"b\"],\"answer\":{\"correct\":[5]}}]"), prof)), "correct option out of range");
        assertEquals(400, status(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content("[{\"type\":\"FILL_BLANK\",\"prompt\":\"no blanks\",\"marks\":1,\"answer\":{\"blanks\":[[\"x\"]]}}]"), prof)), "blank count mismatch");
        JsonNode saved = ok(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content(qs), prof));
        assertEquals(5, saved.size());
        assertEquals(403, status(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content(qs), stu)));
        assertEquals(404, status(auth(get("/api/assignments/" + aid + "/questions"), otherProf)));
        assertEquals(20.0, ok(auth(get("/api/assignments/" + aid), prof)).get("maxMarks").asDouble(), "total marks follow the questions");
        JsonNode preview = ok(auth(get("/api/assignments/" + aid + "/preview"), prof));
        assertFalse(preview.get("questions").get(0).has("answer"), "the preview shows what students see");
        ok(auth(post("/api/assignments/" + aid + "/publish"), prof));

        JsonNode att = ok(auth(post("/api/assignments/" + aid + "/attempts"), stu));
        long tid = att.get("id").asLong();
        assertNotNull(att.get("endsAt").asText(null), "a timed quiz says when it ends");
        for (JsonNode q : att.get("questions")) {
            assertFalse(q.has("answer"), "no answer key before submission");
            assertFalse(q.has("explanation"));
        }
        assertEquals(tid, ok(auth(post("/api/assignments/" + aid + "/attempts"), stu)).get("id").asLong(), "starting again resumes the open attempt");
        java.util.Map<String, Long> ids = new java.util.HashMap<>();
        for (JsonNode q : saved) ids.put(q.get("type").asText(), q.get("id").asLong());
        String answers = "{\"answers\":{\"" + ids.get("MCQ") + "\":{\"choice\":1},\"" + ids.get("MULTI") + "\":{\"choices\":[0]},\"" + ids.get("FILL_BLANK") + "\":{\"blanks\":[\" paris \"]},\""
                + ids.get("OUTPUT") + "\":{\"text\":\"2\\n\"},\"" + ids.get("LONG") + "\":{\"text\":\"A function calling itself.\"}}}";
        ok(auth(put("/api/attempts/" + tid + "/responses").contentType(json).content(answers), stu));
        assertEquals(404, status(auth(put("/api/attempts/" + tid + "/responses").contentType(json).content(answers), registerStudent("intruder@example.test", "Intruder"))), "another student's attempt");
        assertEquals(400, status(auth(put("/api/attempts/" + tid + "/responses").contentType(json).content("{\"answers\":{\"999999\":{}}}"), stu)));
        JsonNode sub = ok(auth(post("/api/attempts/" + tid + "/submit"), stu));
        assertEquals("SUBMITTED", sub.get("status").asText(), "the long answer still needs a marker");
        assertTrue(sub.get("score").isNull(), "manual release: nothing shown yet");
        assertEquals(409, status(auth(post("/api/attempts/" + tid + "/submit"), stu)), "cannot submit twice");

        JsonNode list = ok(auth(get("/api/assignments/" + aid + "/attempts"), prof));
        assertEquals(1, list.get(0).get("needsMarking").asInt());
        JsonNode full = ok(auth(get("/api/attempts/" + tid), prof));
        assertEquals(1.0, full.get("questions").get(0).get("answer").get("correct").get(0).asDouble(), "instructors see the key");
        assertEquals(400, status(auth(post("/api/attempts/" + tid + "/grade").contentType(json).content("{\"marks\":[{\"questionId\":" + ids.get("LONG") + ",\"score\":11}]}"), prof)), "over the question's marks");
        JsonNode graded = ok(auth(post("/api/attempts/" + tid + "/grade").contentType(json).content("{\"marks\":[{\"questionId\":" + ids.get("LONG") + ",\"score\":7,\"feedback\":\"Give an example\"}],\"feedback\":\"Good\",\"privateNote\":\"watch\"}"), prof));
        // 2 (MCQ) + 2 (half of MULTI) + 1 (blank) + 3 (output) + 7 (manual) = 15
        assertEquals(15.0, graded.get("score").asDouble());
        assertEquals("GRADED", graded.get("status").asText());
        JsonNode hidden = ok(auth(get("/api/attempts/" + tid), stu));
        assertTrue(hidden.get("score").isNull(), "graded but not released");
        assertFalse(hidden.has("privateNote"));
        assertEquals(1, ok(auth(post("/api/assignments/" + aid + "/release"), prof)).get("released").asInt());
        JsonNode shown = ok(auth(get("/api/attempts/" + tid), stu));
        assertEquals(15.0, shown.get("score").asDouble());
        assertEquals("Good", shown.get("feedback").asText());
        assertTrue(shown.get("questions").get(0).has("answer"), "answers revealed after release");
        assertFalse(shown.has("privateNote"), "private notes stay private");

        // changing the key re-marks everyone the same way, and answered questions cannot be deleted
        String changed = qs.replace("\"correct\":[1]", "\"correct\":[0]").replace("\"type\":\"MCQ\",\"prompt\"", "\"id\":" + ids.get("MCQ") + ",\"type\":\"MCQ\",\"prompt\"");
        String withIds = changed;
        for (String t2 : java.util.List.of("MULTI", "FILL_BLANK", "OUTPUT", "LONG")) withIds = withIds.replace("{\"type\":\"" + t2 + "\"", "{\"id\":" + ids.get(t2) + ",\"type\":\"" + t2 + "\"");
        ok(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content(withIds), prof));
        assertEquals(12.0, ok(auth(get("/api/attempts/" + tid), prof)).get("score").asDouble(), "the MCQ is now wrong: -1 instead of +2");
        assertEquals(409, status(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content("[" + withIds.substring(withIds.indexOf("{\"id\":" + ids.get("LONG")))), prof)), "answered questions cannot be removed");

        JsonNode second = ok(auth(post("/api/assignments/" + aid + "/attempts"), stu));
        assertEquals(2, second.get("attempt").asInt());
        ok(auth(post("/api/attempts/" + second.get("id").asLong() + "/submit"), stu));
        assertEquals(409, status(auth(post("/api/assignments/" + aid + "/attempts"), stu)), "attempt limit of 2");
        JsonNode book = ok(auth(get("/api/courses/" + cid + "/gradebook"), prof));
        JsonNode cell = book.get("rows").get(0).get("cells").get(0);
        assertEquals(12.0, cell.get("score").asDouble(), "best attempt counts");
        assertEquals(2, cell.get("attempts").asInt());
        JsonNode ov = ok(auth(get("/api/courses/" + cid + "/overview"), prof));
        assertEquals(1, ov.get("students").asInt());
        JsonNode mine = ok(auth(get("/api/courses/" + cid + "/overview"), stu));
        assertTrue(mine.get("atRisk").isEmpty(), "students never see other students");
        assertEquals(403, status(auth(get("/api/courses/" + cid + "/analytics"), stu)));
        assertEquals(200, status(auth(get("/api/courses/" + cid + "/analytics"), prof)));
        // coding assignments reject quiz attempts and vice versa
        assertEquals(400, status(auth(post("/api/assignments/" + assignmentId + "/attempts"), login("insights-student@example.test", "student-pass-1"))));
    }

    @Test @Order(27)
    void questionBankIsPrivateAndFeedsRandomSets() throws Exception {
        var json = MediaType.APPLICATION_JSON;
        for (int i = 0; i < 3; i++)
            ok(auth(post("/api/question-bank").contentType(json).content("{\"type\":\"TRUE_FALSE\",\"prompt\":\"Bank statement " + i + "\",\"marks\":1,\"subject\":\"Networks\",\"topic\":\"TCP\",\"answer\":{\"value\":true}}"), prof));
        assertEquals(400, status(auth(post("/api/question-bank").contentType(json).content("{\"type\":\"TRUE_FALSE\",\"prompt\":\"x\",\"marks\":1}"), prof)), "missing answer");
        assertEquals(3, ok(auth(get("/api/question-bank?subject=Networks"), prof)).size());
        assertEquals(1, ok(auth(get("/api/question-bank").param("q", "statement 2"), prof)).size());
        assertEquals(0, ok(auth(get("/api/question-bank"), otherProf)).size(), "banks are private");
        assertEquals(403, status(auth(get("/api/question-bank"), login("insights-student@example.test", "student-pass-1"))));
        long bankId = ok(auth(get("/api/question-bank"), prof)).get(0).get("id").asLong();
        assertEquals(404, status(auth(delete("/api/question-bank/" + bankId), otherProf)));
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"BANK\",\"title\":\"Bank course\"}"), prof));
        long aid = ok(auth(post("/api/courses/" + c.get("id").asLong() + "/assignments").contentType(json).content("{\"title\":\"Random\",\"description\":\"r\",\"deadline\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\",\"kind\":\"QUIZ\"}"), prof)).get("id").asLong();
        assertEquals(2, ok(auth(post("/api/assignments/" + aid + "/questions/random").contentType(json).content("{\"subject\":\"Networks\",\"count\":2}"), prof)).size());
        assertEquals(409, status(auth(post("/api/assignments/" + aid + "/questions/random").contentType(json).content("{\"subject\":\"Networks\",\"count\":2}"), prof)), "only one unused question left");
        assertEquals(204, status(auth(delete("/api/question-bank/" + bankId), prof)));
    }

    @Test @Order(28)
    void coursesHaveStaffBulkEnrolmentCloneAndSafeDelete() throws Exception {
        var json = MediaType.APPLICATION_JSON;
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"STF\",\"title\":\"Staffed\",\"startsOn\":\"2026-08-01\",\"endsOn\":\"2026-12-01\"}"), prof));
        long cid = c.get("id").asLong();
        assertEquals(400, status(auth(post("/api/courses").contentType(json).content("{\"code\":\"BAD\",\"title\":\"Bad dates\",\"startsOn\":\"2026-08-01\",\"endsOn\":\"2026-07-01\"}"), prof)));
        assertEquals(404, status(auth(get("/api/courses/" + cid + "/students"), otherProf)));
        ok(auth(post("/api/courses/" + cid + "/staff").contentType(json).content("{\"email\":\"other@example.test\",\"role\":\"TA\"}"), prof));
        assertEquals(200, status(auth(get("/api/courses/" + cid + "/students"), otherProf)), "a TA can now manage the course");
        assertEquals(403, status(auth(post("/api/courses/" + cid + "/staff").contentType(json).content("{\"email\":\"prof@example.test\"}"), otherProf)), "only the lead adds staff");
        assertEquals(403, status(auth(delete("/api/courses/" + cid), otherProf)), "only the lead deletes");
        assertEquals(400, status(auth(post("/api/courses/" + cid + "/staff").contentType(json).content("{\"email\":\"insights-student@example.test\"}"), prof)), "students cannot teach");
        JsonNode bulk = ok(auth(post("/api/courses/" + cid + "/enrollments").contentType(json).content("{\"emails\":[\"insights-student@example.test\",\"nobody@example.test\",\"INSIGHTS-STUDENT@example.test\"]}"), prof));
        assertEquals(1, bulk.get("added").size());
        assertEquals(1, bulk.get("notFound").size());
        assertEquals(1, bulk.get("alreadyEnrolled").size(), "same address in another case");
        long sid = ok(auth(get("/api/courses/" + cid + "/students"), prof)).get(0).get("id").asLong();
        JsonNode clone = ok(auth(post("/api/courses/" + cid + "/clone"), prof));
        assertEquals(0, clone.get("students").asInt(), "students are not copied");
        assertEquals(204, status(auth(delete("/api/courses/" + clone.get("id").asLong()), prof)), "an empty course can be deleted");
        assertEquals(204, status(auth(delete("/api/courses/" + cid + "/students/" + sid), prof)));
        assertEquals(409, status(auth(delete("/api/courses/" + courseId), prof)), "a course with submitted work cannot be deleted");
        ok(auth(delete("/api/courses/" + cid + "/staff/" + users.findByEmail("other@example.test").orElseThrow().id()), prof));
        assertEquals(404, status(auth(get("/api/courses/" + cid + "/students"), otherProf)));
    }

    @Test @Order(29)
    void codingSubmissionsCanBeMarkedByHand() throws Exception {
        var json = MediaType.APPLICATION_JSON;
        String stu = login("insights-student@example.test", "student-pass-1");
        long sub = ok(auth(get("/api/assignments/" + assignmentId + "/submissions/me"), stu)).get(0).get("id").asLong();
        JsonNode auto = ok(auth(get("/api/submissions/" + sub + "/grade"), stu));
        assertFalse(auto.has("privateNote"));
        assertEquals(400, status(auth(post("/api/submissions/" + sub + "/grade").contentType(json).content("{\"score\":100000}"), prof)));
        JsonNode g = ok(auth(post("/api/submissions/" + sub + "/grade").contentType(json).content("{\"score\":42,\"feedback\":\"Nice\",\"privateNote\":\"x\"}"), prof));
        assertEquals(42.0, g.get("score").asDouble());
        assertEquals("Nice", ok(auth(get("/api/submissions/" + sub + "/grade"), stu)).get("feedback").asText());
        assertEquals(403, status(auth(post("/api/submissions/" + sub + "/grade").contentType(json).content("{\"score\":1}"), stu)));
        assertEquals(404, status(auth(post("/api/submissions/" + sub + "/grade").contentType(json).content("{\"score\":1}"), otherProf)));
        ok(auth(post("/api/submissions/" + sub + "/grade").contentType(json).content("{\"clearScore\":true}"), prof));
    }

    @Test @Order(30)
    void runCodeAgainstSamplesAndCustomInputWithoutLeakingHiddenTests() throws Exception {
        assumeTrue(docker.available() && docker.imageExists("codesentinel/runner-python:1"), "Docker or runner image unavailable");
        var json = MediaType.APPLICATION_JSON;
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"RUN\",\"title\":\"Run course\"}"), prof));
        String stu = ok(post("/api/auth/register").contentType(json).content(om.writeValueAsString(java.util.Map.of("email", "runner@example.test", "displayName", "Runner", "password", "student-pass-1", "enrollmentCode", c.get("enrollmentCode").asText())))).get("token").asText();
        long assignmentId = ok(auth(post("/api/courses/" + c.get("id").asLong() + "/assignments").contentType(json).content("{\"title\":\"Add\",\"description\":\"a+b\",\"language\":\"python\",\"deadline\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\",\"timeLimitMs\":2000,\"memoryLimitMb\":128}"), prof)).get("id").asLong();
        ok(auth(post("/api/assignments/" + assignmentId + "/test-cases").contentType(json).content("[{\"name\":\"s\",\"input\":\"1 2\\n\",\"expectedOutput\":\"3\",\"sample\":true},{\"name\":\"h\",\"input\":\"5 5\\n\",\"expectedOutput\":\"10\",\"sample\":false}]"), prof));
        ok(auth(post("/api/assignments/" + assignmentId + "/publish"), prof));
        String good = om.writeValueAsString(java.util.Map.of("source", "print(sum(map(int, input().split())))", "mode", "samples"));
        JsonNode r = ok(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content(good), stu));
        assertEquals("AC", r.get("verdict").asText());
        assertTrue(r.get("total").asInt() >= 1);
        for (JsonNode t : r.get("tests")) {
            assertTrue(t.get("sample").asBoolean(), "students only run sample tests");
            assertNotNull(t.get("expected").asText(null));
        }
        assertEquals(403, status(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content(good.replace("samples", "all")), stu)));
        JsonNode all = ok(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content(good.replace("samples", "all")), prof));
        assertTrue(all.get("total").asInt() > r.get("total").asInt(), "staff can check hidden tests too");
        JsonNode custom = ok(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content(om.writeValueAsString(java.util.Map.of("source", "print(sum(map(int, input().split())))", "mode", "custom", "input", "40 2\n"))), stu));
        assertEquals("OK", custom.get("verdict").asText());
        assertEquals("42", custom.get("tests").get(0).get("actual").asText().trim());
        JsonNode broken = ok(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content(om.writeValueAsString(java.util.Map.of("source", "print(", "mode", "samples"))), stu));
        assertTrue(java.util.Set.of("CE", "RE").contains(broken.get("verdict").asText()));
        assertEquals(400, status(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content("{\"source\":\"  \"}"), stu)), "empty code");
        assertEquals(404, status(auth(post("/api/assignments/" + assignmentId + "/run").contentType(json).content(good), otherProf)));
        assertEquals(0, ok(auth(get("/api/assignments/" + assignmentId + "/submissions/me"), stu)).size(), "running stores nothing");
        assertFalse(r.toString().contains("\"10\""), "hidden expected output is never returned to students");
    }

    @Test @Order(31)
    void codeQuestionsInQuizzesAreJudgedAndHiddenTestsStayHidden() throws Exception {
        assumeTrue(docker.available() && docker.imageExists("codesentinel/runner-python:1"), "Docker or runner image unavailable");
        var json = MediaType.APPLICATION_JSON;
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"CQ\",\"title\":\"Code quiz\"}"), prof));
        String stu = ok(post("/api/auth/register").contentType(json).content(om.writeValueAsString(java.util.Map.of("email", "coder@example.test", "displayName", "Coder", "password", "student-pass-1", "enrollmentCode", c.get("enrollmentCode").asText())))).get("token").asText();
        long aid = ok(auth(post("/api/courses/" + c.get("id").asLong() + "/assignments").contentType(json).content("{\"title\":\"Mixed\",\"description\":\"d\",\"deadline\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\",\"kind\":\"QUIZ\"}"), prof)).get("id").asLong();
        String q = """
                [{"type":"CODE","prompt":"Double the number","marks":10,"settings":{"language":"python","starter":"n = int(input())\\n"},
                  "answer":{"tests":[{"input":"2\\n","expected":"4","sample":true},{"input":"21\\n","expected":"42","sample":false}]}},
                 {"type":"TRUE_FALSE","prompt":"Python is compiled to bytecode.","marks":1,"answer":{"value":true}}]""";
        assertEquals(400, status(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content("[{\"type\":\"CODE\",\"prompt\":\"x\",\"marks\":1,\"settings\":{\"language\":\"cobol\"},\"answer\":{\"tests\":[]}}]"), prof)));
        JsonNode qs = ok(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content(q), prof));
        long codeId = qs.get(0).get("id").asLong();
        ok(auth(post("/api/assignments/" + aid + "/publish"), prof));
        JsonNode att = ok(auth(post("/api/assignments/" + aid + "/attempts"), stu));
        long tid = att.get("id").asLong();
        JsonNode view = att.get("questions").get(0);
        assertEquals(1, view.get("settings").get("samples").size());
        assertEquals(1, view.get("settings").get("hidden").asInt());
        assertFalse(view.toString().contains("42"), "hidden expected output is not sent");
        String src = "print(int(input()) * 2)";
        JsonNode run = ok(auth(post("/api/questions/" + codeId + "/run?attempt=" + tid).contentType(json).content(om.writeValueAsString(java.util.Map.of("source", src, "mode", "samples"))), stu));
        assertEquals("AC", run.get("verdict").asText());
        assertEquals(1, run.get("total").asInt(), "samples only");
        assertEquals(404, status(auth(post("/api/questions/" + codeId + "/run").contentType(json).content(om.writeValueAsString(java.util.Map.of("source", src))), stu)), "students run inside their attempt");
        ok(auth(put("/api/attempts/" + tid + "/responses").contentType(json).content("{\"answers\":{\"" + codeId + "\":" + om.writeValueAsString(java.util.Map.of("code", src)) + ",\"" + qs.get(1).get("id").asLong() + "\":{\"value\":true}}}"), stu));
        JsonNode sub = ok(auth(post("/api/attempts/" + tid + "/submit"), stu));
        assertEquals("SUBMITTED", sub.get("status").asText(), "waiting for the judge");
        assertEquals("QUEUED", sub.get("questions").get(0).get("judge").get("status").asText());
        while (codeMarker.processOne()) { /* drain */ }
        JsonNode done = ok(auth(get("/api/attempts/" + tid), stu));
        assertEquals("GRADED", done.get("status").asText());
        assertEquals(11.0, done.get("score").asDouble(), "10 for both tests, 1 for true/false");
        JsonNode jr = done.get("questions").get(0).get("judge").get("result");
        assertEquals(2, jr.get("tests").size());
        for (JsonNode t : jr.get("tests")) if (!t.get("sample").asBoolean()) assertFalse(t.has("actual"), "no hidden output");
        assertEquals(1, done.get("questions").get(0).get("answer").get("tests").size(), "released key shows sample tests only");
        assertFalse(done.toString().contains("\"42\""));
        JsonNode staffRun = ok(auth(post("/api/questions/" + codeId + "/run").contentType(json).content(om.writeValueAsString(java.util.Map.of("source", "print(0)", "mode", "all"))), prof));
        assertEquals(0, staffRun.get("passed").asInt());
        assertEquals(2, staffRun.get("total").asInt());
    }

    @Test @Order(32)
    void materialsDiscussionsNotificationsAndCalendar() throws Exception {
        var json = MediaType.APPLICATION_JSON;
        JsonNode c = ok(auth(post("/api/courses").contentType(json).content("{\"code\":\"COM\",\"title\":\"Community\",\"startsOn\":\"" + java.time.LocalDate.now() + "\"}"), prof));
        long cid = c.get("id").asLong();
        String stu = ok(post("/api/auth/register").contentType(json).content(om.writeValueAsString(java.util.Map.of("email", "member@example.test", "displayName", "Member", "password", "student-pass-1", "enrollmentCode", c.get("enrollmentCode").asText())))).get("token").asText();
        String other = ok(post("/api/auth/register").contentType(json).content(om.writeValueAsString(java.util.Map.of("email", "member2@example.test", "displayName", "Member Two", "password", "student-pass-1", "enrollmentCode", c.get("enrollmentCode").asText())))).get("token").asText();
        String outsider = registerStudent("community-outsider@example.test", "Outsider");

        // materials
        ok(auth(post("/api/courses/" + cid + "/materials").contentType(json).content("{\"module\":\"Week 1\",\"title\":\"Docs\",\"kind\":\"LINK\",\"url\":\"https://example.org\"}"), prof));
        assertEquals(400, status(auth(post("/api/courses/" + cid + "/materials").contentType(json).content("{\"title\":\"Bad\",\"kind\":\"LINK\",\"url\":\"javascript:alert(1)\"}"), prof)), "only http(s) links");
        assertEquals(403, status(auth(post("/api/courses/" + cid + "/materials").contentType(json).content("{\"title\":\"x\",\"kind\":\"NOTE\",\"body\":\"y\"}"), stu)));
        JsonNode f = ok(auth(multipart("/api/courses/" + cid + "/materials/file").file(new MockMultipartFile("file", "slides.pdf", "application/pdf", "%PDF-1.4 demo".getBytes())).param("module", "Week 1"), prof));
        assertEquals(400, status(auth(multipart("/api/courses/" + cid + "/materials/file").file(new MockMultipartFile("file", "evil.html", "text/html", "<script>".getBytes())), prof)), "no HTML uploads");
        var dl = mvc.perform(auth(get("/api/materials/" + f.get("id").asLong() + "/file"), stu)).andReturn().getResponse();
        assertEquals(200, dl.getStatus());
        assertEquals("application/pdf", dl.getContentType());
        assertEquals(2, ok(auth(get("/api/courses/" + cid + "/materials"), stu)).size());
        assertEquals(404, status(auth(get("/api/courses/" + cid + "/materials"), outsider)));
        assertEquals(404, status(auth(get("/api/materials/" + f.get("id").asLong() + "/file"), outsider)));

        // discussions and their notifications
        JsonNode th = ok(auth(post("/api/courses/" + cid + "/threads").contentType(json).content("{\"title\":\"Help with lab 1\",\"body\":\"Stuck on input parsing\"}"), stu));
        long tid = th.get("id").asLong();
        assertTrue(ok(auth(get("/api/notifications"), prof)).get("items").toString().contains("Help with lab 1"), "staff hear about new questions");
        ok(auth(post("/api/threads/" + tid + "/posts").contentType(json).content("{\"body\":\"Use input().split()\"}"), prof));
        JsonNode mine = ok(auth(get("/api/notifications"), stu));
        assertTrue(mine.get("unread").asInt() >= 1);
        assertTrue(mine.get("items").get(0).get("title").asText().startsWith("Your instructor replied"));
        assertEquals(0, ok(auth(get("/api/courses/" + cid + "/threads?filter=unanswered"), stu)).size(), "a staff reply answers the thread");
        JsonNode reply = ok(auth(post("/api/threads/" + tid + "/posts").contentType(json).content("{\"body\":\"something rude\"}"), other));
        long rude = reply.get("posts").get(1).get("id").asLong();
        assertEquals(204, status(auth(post("/api/threads/" + tid + "/report").contentType(json).content("{\"postId\":" + rude + ",\"reason\":\"rude\"}"), stu)));
        assertEquals(1, ok(auth(get("/api/courses/" + cid + "/reports"), prof)).size());
        assertEquals(403, status(auth(get("/api/courses/" + cid + "/reports"), stu)));
        assertEquals(403, status(auth(post("/api/posts/" + rude + "/hide"), stu)), "students cannot hide others' posts");
        ok(auth(post("/api/posts/" + rude + "/hide"), prof));
        JsonNode seen = ok(auth(get("/api/threads/" + tid), stu));
        assertEquals("", seen.get("posts").get(1).get("body").asText(), "hidden posts are blanked for students");
        assertTrue(seen.get("posts").get(1).get("hidden").asBoolean());
        ok(auth(post("/api/threads/" + tid + "/moderate").contentType(json).content("{\"locked\":true}"), prof));
        assertEquals(409, status(auth(post("/api/threads/" + tid + "/posts").contentType(json).content("{\"body\":\"more\"}"), stu)), "locked");
        assertEquals(403, status(auth(post("/api/threads/" + tid + "/moderate").contentType(json).content("{\"pinned\":true}"), stu)));
        assertEquals(404, status(auth(get("/api/threads/" + tid), outsider)));

        // notification preferences and read state
        JsonNode prefs = ok(auth(put("/api/notifications/preferences").contentType(json).content("{\"ANNOUNCEMENT\":false}"), stu));
        assertFalse(prefs.get("ANNOUNCEMENT").asBoolean());
        assertEquals(400, status(auth(put("/api/notifications/preferences").contentType(json).content("{\"NOPE\":false}"), stu)));
        ok(auth(post("/api/courses/" + cid + "/announcements").contentType(json).content("{\"title\":\"Quiet please\",\"body\":\"b\"}"), prof));
        assertFalse(ok(auth(get("/api/notifications"), stu)).toString().contains("Quiet please"), "muted kinds are not delivered");
        assertTrue(ok(auth(get("/api/notifications"), other)).toString().contains("Quiet please"));
        ok(auth(post("/api/notifications/read-all"), stu));
        assertEquals(0, ok(auth(get("/api/notifications?unread=true"), stu)).get("unread").asInt());

        // publishing notifies once; the calendar shows it with the student's status
        String due = Instant.now().plus(20, ChronoUnit.HOURS).toString();
        long aid = ok(auth(post("/api/courses/" + cid + "/assignments").contentType(json).content("{\"title\":\"Cal quiz\",\"description\":\"d\",\"deadline\":\"" + due + "\",\"kind\":\"QUIZ\"}"), prof)).get("id").asLong();
        ok(auth(put("/api/assignments/" + aid + "/questions").contentType(json).content("[{\"type\":\"TRUE_FALSE\",\"prompt\":\"ok?\",\"marks\":1,\"answer\":{\"value\":true}}]"), prof));
        ok(auth(post("/api/assignments/" + aid + "/publish"), prof));
        ok(auth(post("/api/assignments/" + aid + "/unpublish"), prof));
        ok(auth(post("/api/assignments/" + aid + "/publish"), prof));
        JsonNode n2 = ok(auth(get("/api/notifications"), other));
        long newCount = 0;
        for (JsonNode x : n2.get("items")) if (x.get("title").asText().contains("Cal quiz") && x.get("kind").asText().equals("ASSIGNMENT")) newCount++;
        assertEquals(1, newCount, "re-publishing does not notify twice");
        boolean reminder = false;
        for (JsonNode x : n2.get("items")) if (x.get("kind").asText().equals("DEADLINE") && x.get("title").asText().contains("Cal quiz")) reminder = true;
        assertTrue(reminder, "work due within 48 hours produces a reminder");
        String from = Instant.now().minus(1, ChronoUnit.DAYS).toString(), to = Instant.now().plus(3, ChronoUnit.DAYS).toString();
        JsonNode cal = ok(auth(get("/api/calendar").param("from", from).param("to", to), other));
        boolean dueFound = false, start = false;
        for (JsonNode e : cal) {
            if (e.get("kind").asText().equals("DUE") && e.get("assignmentId").asLong() == aid) { dueFound = true; assertFalse(e.get("done").asBoolean()); }
            if (e.get("kind").asText().equals("COURSE_START") && e.get("courseId").asLong() == cid) start = true;
        }
        assertTrue(dueFound);
        assertTrue(start);
        assertFalse(ok(auth(get("/api/calendar").param("from", from).param("to", to), outsider)).toString().contains("Cal quiz"));
        assertEquals(400, status(auth(get("/api/calendar").param("from", from).param("to", Instant.now().plus(400, ChronoUnit.DAYS).toString()), other)));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (Files.exists(STORAGE)) try (var w = Files.walk(STORAGE)) {
            w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
