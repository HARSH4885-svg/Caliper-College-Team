package dev.codesentinel.jobs;

import dev.codesentinel.judge.Judge;
import dev.codesentinel.judge.JudgeRequest;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * At start-up, judges one tiny known-good program per language in the real sandbox and logs the outcome, so a broken
 * Docker setup (missing image, unshared mount path, daemon down) is visible in the terminal immediately instead of
 * showing up later as confusing verdicts on student submissions. Runs in the background; never blocks start-up.
 */
@Component
@ConditionalOnProperty(name = "app.judge-self-test", havingValue = "true", matchIfMissing = true)
public class JudgeSelfTest implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(JudgeSelfTest.class);

    private static final Map<String, String[]> PROGRAMS = new java.util.LinkedHashMap<>();

    static {
        PROGRAMS.put("python", new String[]{"main.py", "print(sum(map(int, input().split())))"});
        PROGRAMS.put("javascript", new String[]{"main.js", "const l=require('fs').readFileSync(0,'utf8').trim().split(/\\s+/).map(Number);console.log(l.reduce((a,b)=>a+b,0));"});
        PROGRAMS.put("c", new String[]{"main.c", "#include <stdio.h>\nint main(){int a,b;scanf(\"%d %d\",&a,&b);printf(\"%d\\n\",a+b);return 0;}"});
        PROGRAMS.put("cpp", new String[]{"main.cpp", "#include <iostream>\nint main(){int a,b;std::cin>>a>>b;std::cout<<a+b<<std::endl;}"});
        PROGRAMS.put("java", new String[]{"Main.java", "import java.util.*;public class Main{public static void main(String[] x){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt());}}"});
    }

    private final Judge judge;

    public JudgeSelfTest(Judge judge) {
        this.judge = judge;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::check, "judge-self-test");
        t.setDaemon(true);
        t.start();
    }

    void check() {
        int failed = 0;
        for (var e : PROGRAMS.entrySet()) {
            Path dir = null;
            try {
                dir = Files.createTempDirectory("cs-selftest");
                Files.writeString(dir.resolve(e.getValue()[0]), e.getValue()[1]);
                JudgeResult r = judge.judge(new JudgeRequest("selftest-" + e.getKey(), e.getKey(), dir, List.of(e.getValue()[0]),
                        List.of(new TestCase("sum", "2 3\n", "5", true)), 2000, 128, 65536));
                if (r.verdict().name().equals("AC")) {
                    log.info("Judge self-test {}: OK", e.getKey());
                } else {
                    failed++;
                    log.error("Judge self-test {}: FAILED ({}) {} {}", e.getKey(), r.verdict(), r.message(), r.compileLog());
                }
            } catch (Exception ex) {
                failed++;
                log.error("Judge self-test {}: FAILED {}", e.getKey(), ex.toString());
            } finally {
                if (dir != null) {
                    try (var s = Files.walk(dir)) {
                        s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                    } catch (Exception ignored) {
                        // best effort
                    }
                }
            }
        }
        if (failed > 0) {
            log.error("Judge self-test: {} of {} languages FAILED. Student submissions in those languages will not be judged correctly until this is fixed (is Docker running, are the runner images built with scripts/build-sandbox-images.sh, and is the work directory shared with Docker?).", failed, PROGRAMS.size());
        } else {
            log.info("Judge self-test: all {} languages OK.", PROGRAMS.size());
        }
    }
}
