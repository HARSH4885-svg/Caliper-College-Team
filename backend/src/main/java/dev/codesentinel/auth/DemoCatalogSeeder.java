package dev.codesentinel.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codesentinel.common.AppProperties;
import dev.codesentinel.quiz.QuizService;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.storage.StoreRequest;
import dev.codesentinel.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * A populated demo campus (SEED_DEMO=true only): twelve courses, five instructors, seventy-two students, coding assignments and
 * quizzes. Everything is flagged synthetic in the database and every address ends in {@code .invalid}. Nothing is a stored
 * result: coding submissions are queued for the real sandboxed judge, quiz answers are marked by the real {@link QuizService},
 * and every dashboard figure is computed from these rows. Runs once (keyed on the CS201 demo course).
 */
@Component
@Order(20)
public class DemoCatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoCatalogSeeder.class);
    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcClient jdbc;
    private final FileStorageProvider storage;
    private final QuizService quiz;
    private final ObjectMapper om;
    private final TransactionTemplate tx;
    private final Random rnd = new Random(2026);
    private final Instant now = Instant.now();

    public DemoCatalogSeeder(AppProperties props, UserRepository users, PasswordEncoder encoder, JdbcClient jdbc, FileStorageProvider storage, QuizService quiz, ObjectMapper om, TransactionTemplate tx) {
        this.tx = tx;
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.storage = storage;
        this.quiz = quiz;
        this.om = om;
    }

    // ------------------------------------------------------------------ people

    static final String[] STUDENTS = {
            "Aarav Mehta", "Diya Kapoor", "Vihaan Reddy", "Ananya Iyer", "Arjun Nair", "Ishita Banerjee", "Kabir Malhotra", "Saanvi Joshi",
            "Rohan Gupta", "Meera Pillai", "Aditya Kulkarni", "Priya Choudhary", "Krishna Menon", "Nisha Agarwal", "Siddharth Rao", "Tanvi Deshmukh",
            "Yash Saxena", "Aisha Khan", "Harsh Vardhan", "Pooja Hegde", "Rahul Bhatt", "Sneha Patil", "Varun Sinha", "Riya Sen",
            "Karthik Subramanian", "Neha Tiwari", "Aryan Chauhan", "Kavya Raghavan", "Dev Patel", "Simran Kaur", "Manav Jain", "Shruti Mishra",
            "Nikhil Dubey", "Anjali Verma", "Pranav Shetty", "Zoya Siddiqui", "Gaurav Pandey", "Isha Thakur", "Rudra Pratap", "Lakshmi Narayanan",
            "Om Prakash", "Tara Bhattacharya", "Kunal Arora", "Mansi Goel", "Abhinav Tripathi", "Ritika Sharma", "Samar Ahmed", "Divya Krishnan",
            "Atharv Pawar", "Naina Bose", "Ayaan Qureshi", "Jhanvi Desai", "Parth Shah", "Esha Mukherjee", "Laksh Bajaj", "Avni Srivastava",
            "Rehan Mirza", "Pallavi Rane", "Shaurya Yadav", "Gauri Kulshrestha", "Tushar Bansal", "Mitali Ghosh", "Advait Joshi", "Sana Fatima",
            "Ishaan Chawla", "Bhavna Rawat", "Neel Kamath", "Roshni D'Souza", "Vedant Mahajan", "Charu Ahuja", "Ayush Raj", "Kritika Negi"};

    record Staff(String email, String name) {
    }

    static final Staff[] INSTRUCTORS = {new Staff("rajesh.verma@demo.invalid", "Prof. Rajesh Verma"), new Staff("meera.iyer@demo.invalid", "Dr. Meera Iyer"),
            new Staff("vikram.singh@demo.invalid", "Prof. Vikram Singh"), new Staff("kavita.rao@demo.invalid", "Dr. Kavita Rao")};

    record CourseDef(String code, String title, String subject, String category, String lead, int size, String description) {
    }

    static final List<CourseDef> COURSES = List.of(
            new CourseDef("CS201", "Data Structures & Algorithms", "Computer Science", "Core", "demo", 40,
                    "Arrays, stacks, queues, linked lists, trees, heaps, hashing and graphs, with the algorithms that use them. Weekly coding labs are judged automatically; quizzes check the theory."),
            new CourseDef("CS202", "Database Management Systems", "Computer Science", "Core", "rajesh", 36,
                    "Relational model, SQL, normalisation, indexing, transactions and concurrency control. Includes SQL query writing practice and a schema design project."),
            new CourseDef("CS203", "Object-Oriented Programming", "Computer Science", "Core", "demo", 34,
                    "Classes, inheritance, polymorphism, interfaces and design principles in Java, with small graded programs every fortnight."),
            new CourseDef("CS204", "Web Development", "Computer Science", "Elective", "kavita", 30,
                    "HTML, CSS and modern JavaScript; the browser, HTTP and building small interactive applications."),
            new CourseDef("CS301", "Computer Networks", "Computer Science", "Core", "vikram", 32,
                    "The layered model, TCP/IP, routing, congestion control, DNS and HTTP, with packet-trace exercises."),
            new CourseDef("CS302", "Operating Systems", "Computer Science", "Core", "rajesh", 35,
                    "Processes and threads, CPU scheduling, synchronisation, memory management and file systems. Labs in C."),
            new CourseDef("CS401", "Artificial Intelligence", "Computer Science", "Elective", "meera", 28,
                    "Search, constraint satisfaction, game playing, knowledge representation and planning."),
            new CourseDef("CS402", "Machine Learning", "Computer Science", "Elective", "meera", 30,
                    "Supervised and unsupervised learning, model evaluation, regularisation and feature engineering, implemented in Python."),
            new CourseDef("CS101", "Python Programming", "Computer Science", "Foundation", "demo", 45,
                    "A first course in programming with Python: values, control flow, functions, collections, files and testing."),
            new CourseDef("CS250", "Competitive Programming", "Computer Science", "Elective", "demo", 26,
                    "Problem-solving technique for contests: complexity, prefix sums, greedy, binary search, DP and graphs, in C++."),
            new CourseDef("CS303", "Software Engineering", "Computer Science", "Core", "kavita", 33,
                    "Requirements, design, UML, testing, version control and process models, run as a team project."),
            new CourseDef("CS350", "Cybersecurity Fundamentals", "Computer Science", "Elective", "vikram", 27,
                    "Threat models, cryptography basics, authentication, web vulnerabilities and secure coding."));

    // ------------------------------------------------------------------ coding problems

    record Test(String name, String input, String expected, boolean sample) {
    }

    record Problem(String course, String title, String language, String file, String description, int dueDays, int marks, List<Test> tests, String[] variants) {
    }

    static final List<Problem> PROBLEMS = List.of(
            new Problem("CS201", "Balanced Brackets", "python", "main.py",
                    "Read an integer T, then T lines each holding a string of the characters ()[]{}. For each string print YES if every bracket is closed by the matching bracket in the right order, otherwise NO.\n\nConstraints: 1 <= T <= 100, each string has at most 10^4 characters.",
                    -12, 20, List.of(new Test("Sample", "3\n()[]{}\n([)]\n((\n", "YES\nNO\nNO", true), new Test("Nested and stray", "4\n{[()()]}\n]\n(((())))\n([]{}\n", "YES\nNO\nYES\nNO", false),
                    new Test("Order matters", "3\n)(\n[(])\n{}{}\n", "NO\nNO\nYES", false)),
                    new String[]{"""
                            import sys

                            def balanced(s):
                                stack = []
                                pairs = {')': '(', ']': '[', '}': '{'}
                                for ch in s:
                                    if ch in '([{':
                                        stack.append(ch)
                                    elif ch in pairs:
                                        if not stack or stack.pop() != pairs[ch]:
                                            return False
                                return not stack

                            data = sys.stdin.read().split()
                            t = int(data[0])
                            for s in data[1:1 + t]:
                                print("YES" if balanced(s) else "NO")
                            """, """
                            def check(line):
                                opener = {')': '(', ']': '[', '}': '{'}
                                seen = []
                                for c in line:
                                    if c in opener.values():
                                        seen.append(c)
                                    else:
                                        if len(seen) == 0 or seen[-1] != opener[c]:
                                            return "NO"
                                        seen.pop()
                                return "YES" if len(seen) == 0 else "NO"

                            n = int(input())
                            for _ in range(n):
                                print(check(input().strip()))
                            """, """
                            n = int(input())
                            for _ in range(n):
                                s = input().strip()
                                ok = s.count('(') == s.count(')') and s.count('[') == s.count(']') and s.count('{') == s.count('}')
                                print("YES" if ok else "NO")
                            """, """
                            n = int(input())
                            for _ in range(n)
                                print("YES")
                            """}),
            new Problem("CS101", "Word Frequency", "python", "main.py",
                    "Read all of standard input. Words are runs of letters a-z, compared case-insensitively. Print the three most frequent words, one per line as \"word count\". Break ties alphabetically. If there are fewer than three distinct words, print them all.",
                    -9, 15, List.of(new Test("Sample", "the cat and the hat and the bat\n", "the 3\nand 2\nbat 1", true), new Test("Case and punctuation", "Apple apple BANANA, banana; cherry!\n", "apple 2\nbanana 2\ncherry 1", false),
                    new Test("Single word", "one\n", "one 1", false)),
                    new String[]{"""
                            import re
                            import sys
                            from collections import Counter

                            words = re.findall(r"[a-z]+", sys.stdin.read().lower())
                            counts = Counter(words)
                            for word, n in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[:3]:
                                print(word, n)
                            """, """
                            import sys

                            freq = {}
                            for raw in sys.stdin.read().lower().split():
                                word = ''.join(ch for ch in raw if 'a' <= ch <= 'z')
                                if word:
                                    freq[word] = freq.get(word, 0) + 1
                            ranked = sorted(freq, key=lambda w: (-freq[w], w))
                            for w in ranked[:3]:
                                print(f"{w} {freq[w]}")
                            """, """
                            import sys
                            from collections import Counter

                            counts = Counter(sys.stdin.read().split())
                            for word, n in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[:3]:
                                print(word, n)
                            """, """
                            import re, sys
                            from collections import Counter
                            for w, n in Counter(re.findall(r"[a-z]+", sys.stdin.read().lower())).most_common(3):
                                print(w, n)
                            """}),
            new Problem("CS250", "Range Sum Queries", "cpp", "main.cpp",
                    "The first line holds n and q. The second holds n integers a1..an (|ai| <= 10^9). Each of the next q lines holds l r (1 <= l <= r <= n). For each query print a_l + ... + a_r.\n\nConstraints: n, q <= 2*10^5. Aim for O(n + q).",
                    -5, 25, List.of(new Test("Sample", "5 3\n1 2 3 4 5\n1 3\n2 5\n4 4\n", "6\n14\n4", true),
                    new Test("Large values", "4 2\n1000000000 1000000000 1000000000 1000000000\n1 4\n2 3\n", "4000000000\n2000000000", false), new Test("Negative", "1 1\n-7\n1 1\n", "-7", false)),
                    new String[]{"""
                            #include <bits/stdc++.h>
                            using namespace std;

                            int main() {
                                ios::sync_with_stdio(false);
                                cin.tie(nullptr);
                                int n, q;
                                if (!(cin >> n >> q)) return 0;
                                vector<long long> prefix(n + 1, 0);
                                for (int i = 1; i <= n; i++) {
                                    long long x;
                                    cin >> x;
                                    prefix[i] = prefix[i - 1] + x;
                                }
                                while (q--) {
                                    int l, r;
                                    cin >> l >> r;
                                    cout << prefix[r] - prefix[l - 1] << '\\n';
                                }
                            }
                            """, """
                            #include <bits/stdc++.h>
                            using namespace std;
                            int main() {
                                int n, q;
                                cin >> n >> q;
                                vector<int> pre(n + 1);
                                for (int i = 1; i <= n; ++i) { int v; cin >> v; pre[i] = pre[i - 1] + v; }
                                for (int k = 0; k < q; ++k) { int l, r; cin >> l >> r; cout << pre[r] - pre[l - 1] << endl; }
                                return 0;
                            }
                            """, """
                            #include <bits/stdc++.h>
                            using namespace std;
                            int main() {
                                int n, q;
                                cin >> n >> q
                                vector<long long> a(n);
                                for (auto &x : a) cin >> x;
                                return 0;
                            }
                            """}),
            new Problem("CS203", "Shape Areas", "java", "Main.java",
                    "Model circles, rectangles and squares as subclasses of an abstract Shape with an area() method. Read n, then n lines: \"circle r\", \"rect w h\" or \"square s\". Print each area rounded to two decimals.",
                    -7, 20, List.of(new Test("Sample", "3\ncircle 1\nrect 2 3\nsquare 4\n", "3.14\n6.00\n16.00", true), new Test("Big circle", "2\ncircle 10\nrect 0.5 0.5\n", "314.16\n0.25", false)),
                    new String[]{"""
                            import java.util.Locale;
                            import java.util.Scanner;

                            abstract class Shape {
                                abstract double area();
                            }

                            class Circle extends Shape {
                                private final double r;
                                Circle(double r) { this.r = r; }
                                double area() { return Math.PI * r * r; }
                            }

                            class Rect extends Shape {
                                private final double w, h;
                                Rect(double w, double h) { this.w = w; this.h = h; }
                                double area() { return w * h; }
                            }

                            class Square extends Rect {
                                Square(double s) { super(s, s); }
                            }

                            public class Main {
                                public static void main(String[] args) {
                                    Scanner in = new Scanner(System.in).useLocale(Locale.ROOT);
                                    int n = in.nextInt();
                                    for (int i = 0; i < n; i++) {
                                        String kind = in.next();
                                        Shape s;
                                        if (kind.equals("circle")) s = new Circle(in.nextDouble());
                                        else if (kind.equals("rect")) s = new Rect(in.nextDouble(), in.nextDouble());
                                        else s = new Square(in.nextDouble());
                                        System.out.println(String.format(Locale.ROOT, "%.2f", s.area()));
                                    }
                                }
                            }
                            """, """
                            import java.util.*;

                            public class Main {
                                public static void main(String[] args) {
                                    Scanner sc = new Scanner(System.in).useLocale(Locale.ROOT);
                                    int n = sc.nextInt();
                                    while (n-- > 0) {
                                        String t = sc.next();
                                        double area;
                                        if (t.equals("circle")) { double r = sc.nextDouble(); area = 3.14 * r * r; }
                                        else if (t.equals("rect")) area = sc.nextDouble() * sc.nextDouble();
                                        else { double s = sc.nextDouble(); area = s * s; }
                                        System.out.println(String.format(Locale.ROOT, "%.2f", area));
                                    }
                                }
                            }
                            """}),
            new Problem("CS204", "Title Case", "javascript", "main.js",
                    "Convert each non-empty input line to title case: every word capitalised and the rest lower case, except the small words a, an, the, of, and, in, on, to, which stay lower case unless they start the line. Collapse runs of spaces to one.",
                    -6, 15, List.of(new Test("Sample", "the lord of the rings\n", "The Lord of the Rings", true), new Test("Mixed case", "a TALE of two cities\nwar AND peace\n", "A Tale of Two Cities\nWar and Peace", false),
                    new Test("Extra spaces", "  harry   potter  \n", "Harry Potter", false)),
                    new String[]{"""
                            const lines = require('fs').readFileSync(0, 'utf8').split('\\n').filter(l => l.trim().length > 0);
                            const small = new Set(['a', 'an', 'the', 'of', 'and', 'in', 'on', 'to']);
                            for (const line of lines) {
                              const words = line.trim().toLowerCase().split(/\\s+/);
                              console.log(words.map((w, i) => (i > 0 && small.has(w)) ? w : w[0].toUpperCase() + w.slice(1)).join(' '));
                            }
                            """, """
                            const input = require('fs').readFileSync(0, 'utf8');
                            const small = ['a', 'an', 'the', 'of', 'and', 'in', 'on', 'to'];
                            input.split('\\n').forEach(line => {
                              if (!line.trim()) return;
                              const out = line.trim().toLowerCase().split(' ').map((w, i) => i && small.includes(w) ? w : w.charAt(0).toUpperCase() + w.substring(1));
                              console.log(out.join(' '));
                            });
                            """, """
                            const lines = require('fs').readFileSync(0, 'utf8').split('\\n');
                            for (const l of lines) if (l.trim()) console.log(l.trim().split(/\\s+/).map(w => w[0].toUpperCase() + w.slice(1)).join(' '));
                            """}),
            new Problem("CS302", "FCFS Waiting Time", "c", "main.c",
                    "All processes arrive at time 0 and run first-come, first-served. Read n and then n CPU burst times. Print the average waiting time with two decimals.",
                    -3, 20, List.of(new Test("Sample", "3\n24 3 3\n", "17.00", true), new Test("Fractional average", "4\n5 1 1 1\n", "4.50", false), new Test("One process", "1\n10\n", "0.00", false)),
                    new String[]{"""
                            #include <stdio.h>

                            int main(void) {
                                int n;
                                if (scanf("%d", &n) != 1 || n <= 0) return 0;
                                long long clock = 0, waited = 0;
                                for (int i = 0; i < n; i++) {
                                    int burst;
                                    scanf("%d", &burst);
                                    waited += clock;
                                    clock += burst;
                                }
                                printf("%.2f\\n", (double) waited / n);
                                return 0;
                            }
                            """, """
                            #include <stdio.h>
                            int main() {
                                int n, b, t = 0, total = 0;
                                scanf("%d", &n);
                                for (int i = 0; i < n; i++) { scanf("%d", &b); total += t; t += b; }
                                printf("%.2f\\n", (double)(total / n));
                                return 0;
                            }
                            """, """
                            #include <stdio.h>
                            int main() {
                                int n, b, t = 0, total = 0;
                                scanf("%d", &n);
                                for (int i = 0; i < n; i++) { scanf("%d", &b); t += b; total += t; }
                                printf("%.2f\\n", (double) total / n);
                                return 0;
                            }
                            """}),
            new Problem("CS402", "Min-Max Normalisation", "python", "main.py",
                    "Read whitespace-separated numbers and print each rescaled to [0, 1] as (x - min) / (max - min), three decimals, space separated. If all values are equal, print 0.000 for each.",
                    4, 15, List.of(new Test("Sample", "2 4 6\n", "0.000 0.500 1.000", true), new Test("Constant column", "5 5 5\n", "0.000 0.000 0.000", false),
                    new Test("Negative values", "-1 0 1 3\n", "0.000 0.250 0.500 1.000", false)),
                    new String[]{"""
                            values = [float(v) for v in open(0).read().split()]
                            lo, hi = min(values), max(values)
                            span = hi - lo
                            print(" ".join(f"{(v - lo) / span if span else 0:.3f}" for v in values))
                            """, """
                            xs = list(map(float, input().split()))
                            lo, hi = min(xs), max(xs)
                            print(" ".join("%.3f" % ((x - lo) / (hi - lo)) for x in xs))
                            """}),
            new Problem("CS201", "Kth Smallest Element", "python", "main.py",
                    "Read n and k, then n integers. Print the k-th smallest value (1-indexed, counting duplicates).\n\nConstraints: 1 <= k <= n <= 10^5.",
                    5, 20, List.of(new Test("Sample", "5 2\n7 1 5 3 9\n", "3", true), new Test("Duplicates", "6 4\n4 4 1 2 4 3\n", "4", false), new Test("Negatives", "3 1\n-5 -9 0\n", "-9", false)),
                    new String[]{"""
                            import heapq
                            n, k = map(int, input().split())
                            nums = list(map(int, input().split()))
                            print(heapq.nsmallest(k, nums)[-1])
                            """, """
                            n, k = map(int, input().split())
                            a = sorted(set(map(int, input().split())))
                            print(a[k - 1])
                            """}));

    // ------------------------------------------------------------------ quizzes

    record Q(String type, String prompt, double marks, String difficulty, List<String> options, JsonNode answer, JsonNode settings, String explanation, String[] weakAnswers) {
    }

    record QuizDef(String course, String title, String description, int dueDays, Integer minutes, String release, List<Q> questions) {
    }

    private ArrayNode arr(Object... xs) {
        ArrayNode a = om.createArrayNode();
        for (Object x : xs) {
            if (x instanceof Integer i) a.add(i);
            else if (x instanceof String[] s) {
                ArrayNode in = a.addArray();
                for (String v : s) in.add(v);
            } else a.add(String.valueOf(x));
        }
        return a;
    }

    private Q mcq(String prompt, double marks, int correct, String why, String... options) {
        return new Q("MCQ", prompt, marks, "EASY", List.of(options), om.createObjectNode().set("correct", arr(correct)), om.createObjectNode(), why, null);
    }

    private Q multi(String prompt, double marks, int[] correct, String why, String... options) {
        ArrayNode c = om.createArrayNode();
        for (int i : correct) c.add(i);
        return new Q("MULTI", prompt, marks, "MEDIUM", List.of(options), om.createObjectNode().set("correct", c), om.createObjectNode().put("partial", true), why, null);
    }

    private Q tf(String prompt, double marks, boolean value, String why) {
        return new Q("TRUE_FALSE", prompt, marks, "EASY", List.of(), om.createObjectNode().put("value", value), om.createObjectNode(), why, null);
    }

    private Q shortQ(String prompt, double marks, String why, String... accepted) {
        return new Q("SHORT", prompt, marks, "MEDIUM", List.of(), om.createObjectNode().set("accepted", arr((Object[]) accepted)), om.createObjectNode(), why, null);
    }

    private Q fill(String prompt, double marks, String why, String[]... blanks) {
        ArrayNode b = om.createArrayNode();
        for (String[] x : blanks) {
            ArrayNode in = b.addArray();
            for (String v : x) in.add(v);
        }
        return new Q("FILL_BLANK", prompt, marks, "MEDIUM", List.of(), om.createObjectNode().set("blanks", b), om.createObjectNode(), why, null);
    }

    private Q output(String prompt, double marks, String code, String lang, String expected, String why) {
        return new Q("OUTPUT", prompt, marks, "MEDIUM", List.of(), om.createObjectNode().set("accepted", arr(expected)), om.createObjectNode().put("code", code).put("codeLanguage", lang), why, null);
    }

    private Q longQ(String prompt, double marks, String guidance, String... answersByQuality) {
        return new Q("LONG", prompt, marks, "HARD", List.of(), om.createObjectNode(), om.createObjectNode(), guidance, answersByQuality);
    }

    private List<QuizDef> quizzes() {
        return List.of(
                new QuizDef("CS201", "Quiz 1: Complexity and Linear Structures", "Twenty minutes, one attempt. Covers asymptotic notation, arrays, stacks and queues.", -16, 20, "IMMEDIATE", List.of(
                        mcq("What is the worst-case time complexity of binary search on a sorted array of n elements?", 2, 1, "Each step halves the search range, so at most log2(n) + 1 comparisons.", "O(n)", "O(log n)", "O(n log n)", "O(1)"),
                        multi("Which of these operations are O(1) on a stack implemented with a dynamic array (amortised)?", 3, new int[]{0, 1, 3}, "Push (amortised), pop and peek are constant time; searching needs a scan.", "push", "pop", "search for a value", "peek"),
                        tf("A queue is a LIFO structure.", 1, false, "A queue is first-in, first-out; a stack is last-in, first-out."),
                        fill("Merge sort runs in ___ time in every case and needs ___ extra space for arrays.", 4, "Merge sort always splits in half (n log n) and the merge step needs a buffer of size n.",
                                new String[]{"O(n log n)", "n log n", "O(nlogn)"}, new String[]{"O(n)", "n", "linear"}),
                        output("What does this program print?", 3, "stack = []\nfor x in [3, 1, 4, 1, 5]:\n    stack.append(x)\nstack.pop()\nprint(stack[-1], len(stack))", "python", "1 4", "After pushing five values and popping one, the top is 1 and four remain."),
                        longQ("Explain when you would choose a linked list over a dynamic array. Give one concrete example.", 7, "Look for: O(1) insertion/removal at known positions vs O(n) shifting; poor cache locality and no random access as the trade-off; a sensible example (LRU cache, undo history, adjacency lists).",
                                "A linked list is better when we insert or delete in the middle often, because it only relinks pointers instead of shifting elements. The cost is no random access and worse cache behaviour. Example: an LRU cache keeps recently used items in a doubly linked list so moving an item to the front is O(1).",
                                "Linked lists are good for inserting because you don't need to move elements. Arrays are faster to index. For example a playlist where songs are added in between.",
                                "When the size changes."))),
                new QuizDef("CS201", "Quiz 2: Trees and Heaps", "Covers binary search trees, traversals and binary heaps. Results are released after marking.", -2, 25, "MANUAL", List.of(
                        mcq("In a binary min-heap stored in an array (0-indexed), where is the left child of index i?", 2, 2, "Children of i are at 2i+1 and 2i+2.", "i + 1", "2i", "2i + 1", "i / 2"),
                        shortQ("Which traversal of a binary search tree visits keys in sorted order?", 2, "In-order traversal visits left subtree, node, right subtree.", "inorder", "in-order", "in order"),
                        tf("Every complete binary tree is a full binary tree.", 1, false, "A complete tree may have a last level filled from the left without every node having two children."),
                        output("What is printed?", 3, "import heapq\nh = []\nfor v in [5, 2, 8, 1]:\n    heapq.heappush(h, v)\nprint(heapq.heappop(h), heapq.heappop(h))", "python", "1 2", "heappop returns the smallest remaining item each time."),
                        longQ("Describe how to delete a node with two children from a binary search tree, and why the result is still a BST.", 8, "Expect: replace with in-order successor (or predecessor), delete that node, argument based on ordering of successor.",
                                "Find the in-order successor, the smallest key in the right subtree. Copy its key into the node and delete the successor, which has at most one child. Every key in the left subtree is smaller than the successor and every remaining key on the right is larger, so ordering is preserved.",
                                "You replace the node with the smallest node on the right side and delete that one.",
                                "Just remove it and attach the children."))),
                new QuizDef("CS202", "SQL and the Relational Model", "Short quiz on keys, joins and normal forms. Two attempts allowed.", -10, null, "IMMEDIATE", List.of(
                        mcq("Which SQL clause filters groups after aggregation?", 2, 2, "WHERE filters rows before grouping; HAVING filters groups.", "WHERE", "ORDER BY", "HAVING", "LIMIT"),
                        multi("Which of the following are guaranteed by a PRIMARY KEY constraint?", 3, new int[]{0, 2}, "A primary key is unique and not null; it does not imply an ordering or a foreign key.", "Uniqueness", "Rows are stored in key order", "NOT NULL", "A foreign key to another table"),
                        fill("A relation is in ___ normal form if every non-key attribute depends on the whole key and on nothing but the key.", 2, "That is the informal definition of 3NF.", new String[]{"third", "3rd", "3"}),
                        shortQ("Write an SQL query that returns the names of students (table students(id, name, dept)) in the 'CSE' department, sorted by name.", 5, "Any equivalent query is accepted; spacing and case are ignored.",
                                "select name from students where dept = 'CSE' order by name", "select name from students where dept='CSE' order by name", "select name from students where dept = 'CSE' order by name asc"),
                        tf("A LEFT JOIN returns every row of the left table, even when no right row matches.", 1, true, "Unmatched left rows appear with NULLs for the right table's columns."))),
                new QuizDef("CS203", "Inheritance and Polymorphism", "One attempt; the order of questions is shuffled for each student.", -11, 15, "IMMEDIATE", List.of(
                        mcq("Which keyword prevents a Java class from being subclassed?", 1, 1, "A final class cannot be extended.", "static", "final", "private", "sealed-off"),
                        tf("A Java interface can declare default methods with a body.", 1, true, "Since Java 8, interfaces may contain default and static methods."),
                        output("What does this print?", 3, "class A { String hi() { return \"A\"; } }\nclass B extends A { String hi() { return \"B\"; } }\npublic class Main {\n  public static void main(String[] x) {\n    A a = new B();\n    System.out.println(a.hi() + new A().hi());\n  }\n}", "java", "BA", "Dynamic dispatch calls B's override through an A reference."),
                        multi("Which are true of method overloading in Java?", 3, new int[]{0, 2}, "Overloads differ in parameter lists and are resolved at compile time; return type alone does not distinguish them.", "Methods share a name but differ in parameters", "Return type alone can distinguish overloads", "The call is resolved at compile time", "It requires inheritance"),
                        longQ("Explain the Liskov substitution principle with an example that violates it.", 6, "Look for the definition (subtypes usable wherever the base type is) and a violation such as Square extends Rectangle with independent setters.",
                                "Objects of a subtype must be usable anywhere the base type is expected without breaking behaviour. Square extending Rectangle violates it: code that sets width then height and expects area w*h breaks because the square keeps both sides equal.",
                                "Child classes should be able to replace parent classes. Like a penguin is a bird but can't fly.",
                                "It is about inheritance."))),
                new QuizDef("CS204", "JavaScript Fundamentals", "Covers scope, closures, equality and the event loop.", -8, 15, "IMMEDIATE", List.of(
                        output("What is logged?", 3, "console.log(typeof null, [] == false, 0.1 + 0.2 === 0.3)", "javascript", "object true false", "typeof null is 'object' (a historical quirk); [] coerces to '' then 0; floating point makes 0.3 inexact."),
                        mcq("Which declaration is block-scoped and cannot be reassigned?", 1, 2, "const is block-scoped and cannot be reassigned.", "var", "let", "const", "function"),
                        tf("setTimeout(fn, 0) runs fn before the current synchronous code finishes.", 2, false, "The callback is queued and runs only after the call stack is empty."),
                        shortQ("Name the HTTP method that should be idempotent and is used to fully replace a resource.", 2, "PUT replaces; PATCH partially updates.", "PUT"))),
                new QuizDef("CS301", "Layers and Transport", "Covers the TCP/IP model, TCP vs UDP and addressing.", -9, 20, "IMMEDIATE", List.of(
                        mcq("Which layer is responsible for routing packets between networks?", 2, 1, "The network layer (IP) handles routing.", "Data link", "Network", "Transport", "Application"),
                        multi("Which features does TCP provide that UDP does not?", 3, new int[]{0, 1, 3}, "TCP offers reliable, ordered delivery with congestion control; both use ports.", "Reliable delivery", "Congestion control", "Port numbers", "In-order delivery"),
                        fill("An IPv4 address is ___ bits long; an IPv6 address is ___ bits long.", 2, "32 and 128 bits.", new String[]{"32"}, new String[]{"128"}),
                        tf("DNS normally uses UDP port 53 for queries.", 1, true, "Ordinary DNS queries use UDP 53; TCP is used for large responses and zone transfers."),
                        longQ("Explain the TCP three-way handshake and why it is needed.", 6, "SYN, SYN-ACK, ACK; synchronises sequence numbers and confirms both directions work.",
                                "The client sends SYN with its initial sequence number, the server replies SYN-ACK acknowledging it and sending its own, and the client ACKs. It lets both sides agree on sequence numbers and confirms each direction can carry data before transfer starts.",
                                "Client sends SYN, server sends SYN ACK, client sends ACK. It is to start the connection.",
                                "It is how TCP connects."))),
                new QuizDef("CS302", "Processes and Scheduling", "Covers process states, context switches and CPU scheduling policies.", -14, 20, "IMMEDIATE", List.of(
                        mcq("Which scheduling algorithm can cause starvation of long jobs?", 2, 1, "SJF always prefers shorter jobs, so long ones may wait indefinitely.", "Round robin", "Shortest job first", "FCFS", "None of these"),
                        tf("A context switch between threads of the same process is usually cheaper than between processes.", 1, true, "Threads share the address space, so no page-table switch is needed."),
                        fill("The four necessary conditions for deadlock are mutual exclusion, hold and wait, no ___, and circular ___.", 4, "Coffman conditions.", new String[]{"preemption", "pre-emption"}, new String[]{"wait", "waiting"}),
                        output("How many times is \"hi\" printed?", 3, "#include <stdio.h>\n#include <unistd.h>\nint main() {\n  fork();\n  fork();\n  printf(\"hi\\n\");\n}", "c", "4", "Two forks create four processes, each printing once. Answer with the number."),
                        longQ("Compare round robin with a small time quantum and with a very large one.", 6, "Small quantum: responsive, more context switches; large: degenerates to FCFS.",
                                "With a small quantum the system is responsive and fair but spends more time switching contexts. As the quantum grows round robin behaves like FCFS: fewer switches but poor response time for short interactive jobs.",
                                "Small quantum means more switching. Large is like FCFS.",
                                "Small is faster."))),
                new QuizDef("CS401", "Search Strategies", "Covers uninformed and informed search.", -6, 20, "IMMEDIATE", List.of(
                        mcq("A* search is optimal when its heuristic is:", 2, 0, "An admissible heuristic never overestimates the true cost.", "Admissible", "Greater than the true cost", "Zero only", "Random"),
                        multi("Which searches are complete on finite graphs?", 3, new int[]{0, 1, 2}, "BFS, uniform-cost and iterative deepening are complete; depth-first tree search can loop.", "Breadth-first search", "Uniform-cost search", "Iterative deepening", "Depth-first tree search without cycle checking"),
                        tf("Greedy best-first search always finds the shortest path.", 1, false, "It follows the heuristic only and can return suboptimal paths."))),
                new QuizDef("CS402", "Model Evaluation", "Covers overfitting, metrics and cross-validation.", -4, 20, "IMMEDIATE", List.of(
                        mcq("High training accuracy and low validation accuracy usually indicates:", 2, 1, "The model memorises the training set.", "Underfitting", "Overfitting", "Data leakage is impossible", "A perfect model"),
                        fill("Precision is TP / (TP + ___), recall is TP / (TP + ___).", 4, "False positives for precision, false negatives for recall.", new String[]{"FP", "false positives"}, new String[]{"FN", "false negatives"}),
                        tf("k-fold cross-validation trains k models.", 1, true, "One model per held-out fold."),
                        shortQ("Which regularisation (L1 or L2) tends to drive some weights exactly to zero?", 2, "L1 (lasso) produces sparse weights.", "L1", "lasso"))),
                new QuizDef("CS101", "Python Basics Check", "Covers values, lists, strings and loops. Three attempts allowed; best score counts.", -13, 15, "IMMEDIATE", List.of(
                        output("What does this print?", 2, "nums = [1, 2, 3, 4]\nprint(nums[1:3], nums[-1])", "python", "[2, 3] 4", "Slices exclude the end index; -1 is the last element."),
                        output("What does this print?", 2, "s = 'banana'\nprint(s.count('a'), s.find('n'))", "python", "3 2", "Three a's; the first n is at index 2."),
                        mcq("What is the type of the expression 7 / 2 in Python 3?", 1, 1, "True division always returns a float.", "int", "float", "str", "Fraction"),
                        tf("Lists are immutable in Python.", 1, false, "Lists are mutable; tuples are immutable."),
                        fill("To iterate over both index and value of a list, use the built-in ___ function.", 2, "enumerate yields (index, value) pairs.", new String[]{"enumerate"}),
                        shortQ("Which keyword exits a loop immediately?", 1, "break leaves the innermost loop.", "break"))),
                new QuizDef("CS250", "Complexity Warm-up", "Short quiz on complexity analysis for contest problems.", -7, 15, "IMMEDIATE", List.of(
                        mcq("With n = 2*10^5, which complexity is safely within a one-second limit?", 2, 2, "About 3.5 million operations for n log n; n^2 is 4*10^10.", "O(n^2)", "O(n^3)", "O(n log n)", "O(2^n)"),
                        tf("Binary search on the answer requires the predicate to be monotonic.", 1, true, "Otherwise halving the range can discard the answer."),
                        output("What is printed?", 3, "#include <bits/stdc++.h>\nint main() {\n  int x = 1 << 4;\n  std::cout << (x | 3) << ' ' << (x >> 2);\n}", "cpp", "19 4", "16 | 3 = 19 and 16 >> 2 = 4."))),
                new QuizDef("CS303", "Process Models and Testing", "Covers SDLC models, UML and testing levels.", -9, 20, "IMMEDIATE", List.of(
                        mcq("Which model delivers working software in short, fixed-length iterations?", 2, 2, "Scrum works in sprints.", "Waterfall", "V-model", "Scrum", "Big bang"),
                        multi("Which UML diagrams are behavioural?", 3, new int[]{1, 2}, "Sequence and activity diagrams show behaviour; class and component diagrams show structure.", "Class diagram", "Sequence diagram", "Activity diagram", "Component diagram"),
                        tf("Unit tests should depend on a real database to be meaningful.", 1, false, "Unit tests isolate the unit; integration tests exercise real dependencies."),
                        longQ("Write two acceptance criteria for a 'reset password' user story.", 5, "Criteria should be testable (Given/When/Then), cover link expiry or invalid email, and avoid implementation detail.",
                                "Given a registered email, when the user requests a reset, then a single-use link valid for 30 minutes is emailed. Given an expired or used link, when it is opened, then the user sees an error and can request a new one.",
                                "User gets an email with a link. The link lets them change the password.",
                                "It should work."))),
                new QuizDef("CS350", "Security Foundations", "Covers the CIA triad, hashing and common web vulnerabilities.", -5, 15, "IMMEDIATE", List.of(
                        mcq("Which of these is the right way to store user passwords?", 2, 3, "A slow, salted hash (bcrypt, scrypt, Argon2) resists offline guessing.", "Plain text", "Base64", "SHA-256 without salt", "bcrypt with a per-user salt"),
                        multi("Which help prevent SQL injection?", 3, new int[]{0, 2}, "Parameterised queries and least-privilege accounts; escaping by hand and hiding errors are not reliable defences.", "Parameterised queries", "Hiding database errors from users", "Least-privilege database accounts", "Removing quotes from input"),
                        fill("The CIA triad stands for confidentiality, ___ and ___.", 2, "Integrity and availability.", new String[]{"integrity"}, new String[]{"availability"}),
                        tf("HTTPS hides the domain name from network observers in all cases.", 1, false, "SNI and DNS can reveal the domain unless encrypted."))));
    }

    // ------------------------------------------------------------------ seeding

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedDemo()) return;
        // all or nothing: a failure part-way leaves the database exactly as it was
        tx.executeWithoutResult(s -> {
            try {
                seed();
            } catch (Exception e) {
                throw new IllegalStateException("Demo catalogue seeding failed", e);
            }
        });
    }

    private void seed() throws Exception {
        if (jdbc.sql("SELECT count(*) FROM courses WHERE synthetic AND code='CS201'").query(Long.class).single() > 0) return;
        Long demo = jdbc.sql("SELECT id FROM users WHERE email=?").param(DemoSeedRunner.INSTRUCTOR_EMAIL).query(Long.class).optional().orElse(null);
        if (demo == null) return;
        long started = System.currentTimeMillis();
        String hash = encoder.encode(DemoSeedRunner.PASSWORD);

        // the presenter logins keep their addresses; their display names become realistic
        jdbc.sql("UPDATE users SET display_name='Dr. Ananya Sharma' WHERE id=?").param(demo).update();
        jdbc.sql("UPDATE assignments SET title='Array statistics' WHERE title='Array statistics (synthetic)'").update();
        jdbc.sql("UPDATE courses SET title='Programming Lab', description='The original single-assignment demo, kept for the similarity walkthrough.' WHERE code='DEMO101' AND synthetic").update();
        Map<String, Long> staff = new HashMap<>();
        staff.put("demo", demo);
        for (Staff s : INSTRUCTORS) {
            long id = users.findByEmail(s.email()).map(UserRepository.UserRow::id).orElseGet(() -> users.insert(s.email(), s.name(), hash, "INSTRUCTOR", true));
            staff.put(s.email().substring(0, s.email().indexOf('.')), id);
        }

        List<Long> students = new ArrayList<>();
        Map<Long, Double> ability = new HashMap<>();
        for (int i = 0; i < STUDENTS.length; i++) {
            String name = STUDENTS[i];
            long id;
            if (i < 8) { // the eight original demo students keep their logins (student1@demo.invalid ...)
                String email = "student" + (i + 1) + "@demo.invalid";
                id = users.findByEmail(email).map(UserRepository.UserRow::id).orElseGet(() -> users.insert(email, name, hash, "STUDENT", true));
                jdbc.sql("UPDATE users SET display_name=? WHERE id=?").params(name, id).update();
            } else {
                String email = name.toLowerCase().replace("'", "").replace(' ', '.') + "@demo.invalid";
                id = users.findByEmail(email).map(UserRepository.UserRow::id).orElseGet(() -> users.insert(email, name, hash, "STUDENT", true));
            }
            students.add(id);
            ability.put(id, i == 0 ? 0.82 : 0.35 + rnd.nextDouble() * 0.6);
        }

        Map<String, Long> courseIds = new HashMap<>();
        Map<String, List<Long>> rosters = new HashMap<>();
        LocalDate termStart = LocalDate.now().minusWeeks(10);
        for (CourseDef c : COURSES) {
            long lead = staff.get(c.lead());
            long id = jdbc.sql("""
                    INSERT INTO courses(code, title, enrollment_code, instructor_id, synthetic, description, subject, category, starts_on, ends_on, created_at)
                    VALUES (?,?,?,?,TRUE,?,?,?,?,?,?) RETURNING id""")
                    .params(c.code(), c.title(), "CS-" + c.code() + "DEMO", lead, c.description(), c.subject(), c.category(), termStart, termStart.plusWeeks(16),
                            Timestamp.from(now.minus(75, ChronoUnit.DAYS))).query(Long.class).single();
            courseIds.put(c.code(), id);
            if (lead != demo) jdbc.sql("INSERT INTO course_staff(course_id, user_id, role) VALUES (?,?,'CO_INSTRUCTOR')").params(id, demo).update();
            List<Long> pool = new ArrayList<>(students.subList(1, students.size()));
            Collections.shuffle(pool, rnd);
            List<Long> roster = new ArrayList<>(pool.subList(0, c.size() - 1));
            if (List.of("CS201", "CS101", "CS203", "CS250", "CS302", "CS402").contains(c.code())) roster.add(0, students.get(0)); // the demo student's timetable
            else roster.add(pool.get(c.size() - 1));
            for (long s : roster) {
                Instant at = now.minus(40 + rnd.nextInt(30), ChronoUnit.DAYS).plus(rnd.nextInt(1440), ChronoUnit.MINUTES);
                jdbc.sql("INSERT INTO enrollments(course_id, student_id, created_at) VALUES (?,?,?)").params(id, s, Timestamp.from(at)).update();
            }
            rosters.put(c.code(), roster);
        }
        // a TA, so the staff list shows more than one role
        jdbc.sql("INSERT INTO course_staff(course_id, user_id, role) VALUES (?,?,'TA') ON CONFLICT DO NOTHING").params(courseIds.get("CS201"), staff.get("rajesh")).update();

        int jobs = 0;
        for (Problem pr : PROBLEMS) jobs += seedProblem(pr, courseIds.get(pr.course()), rosters.get(pr.course()), ability);
        int attempts = 0;
        for (QuizDef q : quizzes()) attempts += seedQuiz(q, courseIds.get(q.course()), rosters.get(q.course()), ability, staff.get(COURSES.stream().filter(c -> c.code().equals(q.course())).findFirst().orElseThrow().lead()));
        seedAnnouncements(courseIds, staff);
        seedBank(demo);
        seedDrafts(courseIds);
        log.info("Demo campus created in {} ms: {} courses, {} students, {} coding submissions queued for the judge, {} quiz attempts marked.",
                System.currentTimeMillis() - started, COURSES.size(), students.size(), jobs, attempts);
    }

    private long assignment(long course, String title, String desc, String kind, String language, int dueDays, double marks, String release, Integer minutes, int maxAttempts, String latePolicy) {
        Instant deadline = now.plus(dueDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS).plus(23, ChronoUnit.HOURS).plus(59, ChronoUnit.MINUTES);
        Instant from = deadline.minus(dueDays < 0 ? 14 : 10, ChronoUnit.DAYS);
        if (from.isAfter(now)) from = now.minus(1, ChronoUnit.DAYS);
        return jdbc.sql("""
                INSERT INTO assignments(course_id, title, description, language, deadline, time_limit_ms, memory_limit_mb, published, kind, category, difficulty, max_marks,
                    passing_marks, available_from, max_attempts, late_policy, late_penalty_pct, late_until, quiz_minutes, release_mode, created_at, shuffle_questions)
                VALUES (?,?,?,?,?,?,?,TRUE,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id""")
                .params(course, title, desc, language, Timestamp.from(deadline), 2000, language.equals("java") ? 512 : 256, kind, kind.equals("QUIZ") ? "QUIZ" : "LAB",
                        marks >= 20 ? "MEDIUM" : "EASY", BigDecimal.valueOf(marks), BigDecimal.valueOf(Math.round(marks * 0.4)), Timestamp.from(from), maxAttempts, latePolicy,
                        latePolicy.equals("PENALTY") ? 10 : 0, latePolicy.equals("BLOCK") ? null : Timestamp.from(deadline.plus(3, ChronoUnit.DAYS)), minutes, release,
                        Timestamp.from(from.minus(1, ChronoUnit.DAYS)), kind.equals("QUIZ") && title.contains("Inheritance")).query(Long.class).single();
    }

    private int seedProblem(Problem p, long course, List<Long> roster, Map<Long, Double> ability) throws Exception {
        long a = assignment(course, p.title(), p.description(), "CODING", p.language(), p.dueDays(), p.marks(), "IMMEDIATE", null, 10, p.dueDays() < -8 ? "BLOCK" : "PENALTY");
        int ord = 0;
        for (Test t : p.tests())
            jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) VALUES (?,?,?,?,?,?)").params(a, t.name(), t.input(), t.expected(), t.sample(), ord++).update();
        Instant deadline = jdbc.sql("SELECT deadline FROM assignments WHERE id=?").param(a).query(Timestamp.class).single().toInstant();
        double share = p.dueDays() < 0 ? 0.92 : 0.3;
        int n = 0;
        String[] v = p.variants();
        int good = Math.min(2, v.length - 1); // the first one or two variants are correct solutions; the rest are flawed
        for (long s : roster) {
            double ab = ability.get(s);
            if (rnd.nextDouble() > share * (0.78 + ab * 0.25)) continue;
            List<String> chain = new ArrayList<>();
            if (rnd.nextDouble() > ab) chain.add(v[good + rnd.nextInt(v.length - good)]); // a flawed first try
            if (rnd.nextDouble() < ab + 0.1 || chain.isEmpty()) chain.add(v[rnd.nextInt(good)]);
            Instant at = p.dueDays() < 0 ? deadline.minus(3 + rnd.nextInt(90), ChronoUnit.HOURS) : now.minus(1 + rnd.nextInt(20), ChronoUnit.HOURS);
            boolean lateOne = p.dueDays() < 0 && p.dueDays() >= -8 && rnd.nextDouble() < 0.08;
            if (lateOne) at = deadline.plus(2 + rnd.nextInt(30), ChronoUnit.HOURS);
            if (at.isAfter(now)) at = now.minus(1 + rnd.nextInt(5), ChronoUnit.HOURS);
            int attempt = 1;
            for (String src : chain) {
                long sub = jdbc.sql("INSERT INTO submissions(assignment_id, student_id, attempt, status, synthetic, created_at, late) VALUES (?,?,?,'QUEUED',TRUE,?,?) RETURNING id")
                        .params(a, s, attempt, Timestamp.from(at), at.isAfter(deadline)).query(Long.class).single();
                String key = "sub/" + sub + "/f0";
                StoredObject o = storage.store(new StoreRequest(key, new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8))));
                jdbc.sql("INSERT INTO submission_files(submission_id, path, storage_key, size_bytes, sha256) VALUES (?,?,?,?,?)").params(sub, p.file(), key, o.sizeBytes(), o.sha256()).update();
                jdbc.sql("INSERT INTO evaluation_jobs(submission_id, created_at) VALUES (?,?)").params(sub, Timestamp.from(at)).update();
                n++;
                attempt++;
                at = at.plus(20 + rnd.nextInt(200), ChronoUnit.MINUTES);
                if (at.isAfter(now)) at = now.minus(5, ChronoUnit.MINUTES);
            }
        }
        return n;
    }

    private int seedQuiz(QuizDef d, long course, List<Long> roster, Map<Long, Double> ability, long lead) {
        long a = assignment(course, d.title(), d.description(), "QUIZ", "none", d.dueDays(), 1, d.release(), d.minutes(),
                d.title().contains("Basics") ? 3 : d.title().contains("SQL") ? 2 : 1, "BLOCK");
        List<QuizService.QuestionInput> in = new ArrayList<>();
        for (Q q : d.questions())
            in.add(new QuizService.QuestionInput(null, q.type(), q.prompt(), BigDecimal.valueOf(q.marks()), q.difficulty(), true, om.valueToTree(q.options()), q.answer(), q.settings(),
                    q.explanation(), null));
        List<QuizService.Question> qs = quiz.replace(a, in);
        jdbc.sql("UPDATE assignments SET passing_marks=round(max_marks * 0.4), difficulty='MEDIUM' WHERE id=?").param(a).update();
        Instant deadline = jdbc.sql("SELECT deadline FROM assignments WHERE id=?").param(a).query(Timestamp.class).single().toInstant();
        boolean past = deadline.isBefore(now);
        int n = 0;
        for (long s : roster) {
            double ab = ability.get(s);
            if (rnd.nextDouble() > (past ? 0.88 : 0.35) * (0.7 + ab * 0.4)) continue;
            Instant at = past ? deadline.minus(2 + rnd.nextInt(100), ChronoUnit.HOURS) : now.minus(1 + rnd.nextInt(20), ChronoUnit.HOURS);
            var attempt = quiz.start(a, s, at);
            Map<Long, JsonNode> answers = new HashMap<>();
            Map<Long, Integer> tier = new HashMap<>();
            for (var q : qs) {
                Q def = d.questions().get(q.ord());
                boolean right = rnd.nextDouble() < ab;
                ObjectNode r = om.createObjectNode();
                switch (q.type()) {
                    case "MCQ" -> {
                        int c = q.answer().path("correct").get(0).asInt();
                        r.put("choice", right ? c : (c + 1 + rnd.nextInt(q.options().size() - 1)) % q.options().size());
                    }
                    case "MULTI" -> {
                        ArrayNode ch = r.putArray("choices");
                        for (JsonNode c : q.answer().path("correct")) if (right || rnd.nextBoolean()) ch.add(c.asInt());
                        if (!right) ch.add((q.answer().path("correct").get(0).asInt() + 1) % q.options().size());
                    }
                    case "TRUE_FALSE" -> r.put("value", right == q.answer().path("value").asBoolean());
                    case "SHORT" -> r.put("text", right ? q.answer().path("accepted").get(0).asText() : "not sure");
                    case "OUTPUT" -> r.put("text", right ? q.answer().path("accepted").get(0).asText() : q.answer().path("accepted").get(0).asText() + " 0");
                    case "FILL_BLANK" -> {
                        ArrayNode b = r.putArray("blanks");
                        for (JsonNode blank : q.answer().path("blanks")) b.add(rnd.nextDouble() < ab ? blank.get(0).asText() : "?");
                    }
                    case "LONG" -> {
                        int t = ab > 0.75 ? 0 : ab > 0.5 ? 1 : 2;
                        tier.put(q.id(), t);
                        r.put("text", def.weakAnswers()[t]);
                    }
                    default -> { }
                }
                answers.put(q.id(), r);
            }
            quiz.save(attempt.id(), answers, at);
            Instant done = at.plus(4 + rnd.nextInt(Math.max(5, d.minutes() == null ? 30 : d.minutes() - 3)), ChronoUnit.MINUTES);
            if (done.isAfter(now)) done = now;
            quiz.submit(attempt.id(), done);
            jdbc.sql("UPDATE quiz_attempts SET started_at=?, synthetic=TRUE WHERE id=?").params(Timestamp.from(at), attempt.id()).update();
            n++;
            // marked by the instructor, except the most recent DSA quiz which is left in the marking queue
            if (!tier.isEmpty() && !d.release().equals("MANUAL")) {
                List<QuizService.ManualMark> marks = new ArrayList<>();
                for (var e : tier.entrySet()) {
                    var q = qs.stream().filter(x -> x.id() == e.getKey()).findFirst().orElseThrow();
                    double f = e.getValue() == 0 ? 0.9 : e.getValue() == 1 ? 0.55 : 0.2;
                    marks.add(new QuizService.ManualMark(q.id(), q.marks().multiply(BigDecimal.valueOf(f)).setScale(1, java.math.RoundingMode.HALF_UP),
                            e.getValue() == 0 ? "Clear and complete." : e.getValue() == 1 ? "Right idea; give the trade-off and a concrete example." : "Too brief: explain the mechanism."));
                }
                quiz.grade(attempt.id(), marks, ab > 0.7 ? "Well done." : "Review the explanations attached to each question.", "", true, false);
            }
        }
        return n;
    }

    private void seedAnnouncements(Map<String, Long> courses, Map<String, Long> staff) {
        String[][] posts = {
                {"Welcome to the course", "Welcome! The syllabus, marking scheme and lab timetable are in the course description. Labs are judged automatically; read the sample tests carefully before you submit.", "true", "70"},
                {"Office hours this week", "Office hours move to Thursday 3-5 pm in Lab 2 this week. Bring the problem you are stuck on and your latest attempt.", "false", "9"},
                {"Mid-semester feedback", "Thank you for the feedback forms. From next week, every lab will include one extra sample test and a worked example in the statement.", "false", "3"}};
        for (CourseDef c : COURSES) {
            long id = courses.get(c.code());
            long author = staff.get(c.lead());
            for (String[] p : posts) {
                if (p[3].equals("3") && rnd.nextBoolean()) continue;
                jdbc.sql("INSERT INTO announcements(course_id, author_id, title, body, pinned, created_at) VALUES (?,?,?,?,?,?)")
                        .params(id, author, p[0], p[1], Boolean.parseBoolean(p[2]), Timestamp.from(now.minus(Integer.parseInt(p[3]), ChronoUnit.DAYS).minus(rnd.nextInt(600), ChronoUnit.MINUTES))).update();
            }
        }
    }

    private void seedBank(long owner) {
        for (QuizDef d : quizzes()) {
            String subject = COURSES.stream().filter(c -> c.code().equals(d.course())).findFirst().orElseThrow().title();
            for (Q q : d.questions()) {
                jdbc.sql("""
                        INSERT INTO question_bank(owner_id, type, prompt, marks, difficulty, subject, topic, options, answer, settings, explanation)
                        VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?)""")
                        .params(owner, q.type(), q.prompt(), BigDecimal.valueOf(q.marks()), q.difficulty(), subject, d.title().replaceFirst("^Quiz \\d+: ", ""),
                                om.valueToTree(q.options()).toString(), q.answer().toString(), q.settings().toString(), q.explanation()).update();
            }
        }
    }

    /** An unpublished draft and a scheduled quiz, so the instructor's planning views are not empty. */
    private void seedDrafts(Map<String, Long> courses) {
        long draft = assignment(courses.get("CS201"), "Graph Traversal Lab", "Read an undirected graph and print the BFS order from vertex 1. Draft: tests still being written.",
                "CODING", "python", 12, 25, "IMMEDIATE", null, 10, "PENALTY");
        jdbc.sql("UPDATE assignments SET published=FALSE WHERE id=?").param(draft).update();
        jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) VALUES (?,?,?,?,TRUE,0)").params(draft, "Sample", "4 3\n1 2\n1 3\n2 4\n", "1 2 3 4").update();
        long scheduled = assignment(courses.get("CS101"), "Quiz: Functions and Scope", "Opens next week.", "QUIZ", "none", 10, 1, "IMMEDIATE", 15, 1, "BLOCK");
        quiz.replace(scheduled, List.of(new QuizService.QuestionInput(null, "MCQ", "What does a Python function return when it has no return statement?", BigDecimal.valueOf(2), "EASY", true,
                arr("0", "None", "an empty string", "an error"), om.createObjectNode().set("correct", arr(1)), om.createObjectNode(), "Functions return None by default.", null)));
        jdbc.sql("UPDATE assignments SET available_from=? WHERE id=?").params(Timestamp.from(now.plus(5, ChronoUnit.DAYS)), scheduled).update();
    }
}
