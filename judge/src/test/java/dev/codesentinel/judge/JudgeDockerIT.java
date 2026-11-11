package dev.codesentinel.judge;

import dev.codesentinel.judge.sandbox.DockerRunner;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import dev.codesentinel.judge.verdict.Verdict;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.codesentinel.judge.sandbox.ContainerSpec;
import dev.codesentinel.judge.sandbox.SandboxException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real containers. Skipped (not passed) when Docker or the runner images are missing. */
class JudgeDockerIT {
    static DockerRunner runner;
    static boolean ready;

    @BeforeAll
    static void init() {
        runner = new DockerRunner(System.getenv().getOrDefault("DOCKER_BIN", "docker"));
        ready = runner.available() && runner.imageExists("codesentinel/runner-native:1") && runner.imageExists("codesentinel/runner-java:1")
                && runner.imageExists("codesentinel/runner-python:1") && runner.imageExists("codesentinel/runner-node:1");
    }

    private JudgeResult run(Path dir, String lang, String file, String code, TestCase... tests) throws Exception {
        return run(dir, lang, file, code, 2000, tests);
    }

    private JudgeResult run(Path dir, String lang, String file, String code, int timeLimitMs, TestCase... tests) throws Exception {
        assumeTrue(ready, "Docker or sandbox images unavailable");
        Path src = dir.resolve("s");
        Files.createDirectories(src.resolve(file).getParent());
        Files.writeString(src.resolve(file), code);
        Judge j = new Judge(runner, new JudgeConfig(dir.resolve("work"), 120_000, 768, 240_000, 6_000, 64, 1.0, 16, 100, 20_000, 16, 1024, 2048));
        return j.judge(new JudgeRequest("it", lang, src, List.of(file), List.of(tests), timeLimitMs, 128, 65536));
    }

    private static final TestCase SUM = new TestCase("sum", "1 2 3\n", "6", true);

    @Test void acC(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d, "c", "main.c", "#include <stdio.h>\nint main(){int a,b,c;scanf(\"%d %d %d\",&a,&b,&c);printf(\"%d\\n\",a+b+c);return 0;}", SUM).verdict());
    }
    @Test void acCpp(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d, "cpp", "main.cpp", "#include <iostream>\nint main(){int a,b,c;std::cin>>a>>b>>c;std::cout<<a+b+c<<std::endl;}", SUM).verdict());
    }
    @Test void acJava(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d, "java", "Main.java", "import java.util.*;public class Main{public static void main(String[] a){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt()+s.nextInt());}}", SUM).verdict());
    }
    @Test void acPython(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d, "python", "main.py", "print(sum(map(int,input().split())))", SUM).verdict());
    }
    @Test void acJs(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d, "javascript", "main.js", "const l=require('fs').readFileSync(0,'utf8').trim().split(/\\s+/).map(Number);console.log(l.reduce((a,b)=>a+b,0));", SUM).verdict());
    }
    @Test void waPython(@TempDir Path d) throws Exception {
        assertEquals(Verdict.WA, run(d, "python", "main.py", "print(7)", SUM).verdict());
    }
    @Test void crlfAndTrailingWhitespaceAccepted(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d, "python", "main.py", "import sys\nsys.stdout.write('6   \\r\\n\\r\\n')", SUM).verdict());
    }
    @Test void ceC(@TempDir Path d) throws Exception {
        JudgeResult r = run(d, "c", "main.c", "int main(){ return x }", SUM);
        assertEquals(Verdict.CE, r.verdict());
        assertFalse(r.compileLog().isBlank());
    }
    @Test void ceJava(@TempDir Path d) throws Exception {
        assertEquals(Verdict.CE, run(d, "java", "Main.java", "public class Main{public static void main(String[] a){ int x = \"s\"; }}", SUM).verdict());
    }
    @Test void cePythonSyntax(@TempDir Path d) throws Exception {
        assertEquals(Verdict.CE, run(d, "python", "main.py", "def (:\n", SUM).verdict());
    }
    @Test void reNonZeroExit(@TempDir Path d) throws Exception {
        assertEquals(Verdict.RE, run(d, "python", "main.py", "raise SystemExit(3)", SUM).verdict());
    }
    @Test void reSegfault(@TempDir Path d) throws Exception {
        assertEquals(Verdict.RE, run(d, "c", "main.c", "int main(){int*p=0;*p=1;return 0;}", SUM).verdict());
    }
    @Test void tleInfiniteLoop(@TempDir Path d) throws Exception {
        long t = System.currentTimeMillis();
        assertEquals(Verdict.TLE, run(d, "c", "main.c", "int main(){for(;;){} }", SUM).verdict());
        assertTrue(System.currentTimeMillis() - t < 60_000);
    }
    @Test void mleAllocation(@TempDir Path d) throws Exception {
        assertEquals(Verdict.MLE, run(d, "c", "main.c",
                "#include <stdlib.h>\n#include <string.h>\nint main(){for(;;){char*p=malloc(8<<20);if(!p)return 1;memset(p,1,8<<20);}}",
                6_000, SUM).verdict());
    }
    @Test void forkBombIsContained(@TempDir Path d) throws Exception {
        JudgeResult r = run(d, "c", "main.c", "#include <unistd.h>\nint main(){for(;;)fork();}", SUM);
        assertNotEquals(Verdict.AC, r.verdict());
        assertNotEquals(Verdict.IE, r.verdict(), "pids limit/timeout should classify it as a student failure, not crash the judge");
    }
    @Test void outputFloodIsBounded(@TempDir Path d) throws Exception {
        JudgeResult r = run(d, "c", "main.c", "#include <stdio.h>\nint main(){for(;;)puts(\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\");}", SUM);
        assertNotEquals(Verdict.AC, r.verdict());
        assertTrue(r.results().get(0).stdoutExcerpt().length() <= 4096, "excerpt bounded");
    }
    @Test void networkIsBlocked(@TempDir Path d) throws Exception {
        String code = "import socket,sys\ntry:\n    socket.create_connection(('1.1.1.1',53),timeout=2)\n    print('NET-OPEN')\nexcept Exception:\n    print('6')\n";
        assertEquals(Verdict.AC, run(d, "python", "main.py", code, SUM).verdict());
    }
    @Test void sourceMountIsReadOnlyAndRootFsIsReadOnly(@TempDir Path d) throws Exception {
        String code = "import os\nok=0\nfor p in ['/work/x','/usr/x','/etc/x','/work/src/x']:\n    try:\n        open(p,'w').write('a'); ok+=1\n    except Exception: pass\nprint('6' if ok==0 else 'WRITABLE')\n";
        assertEquals(Verdict.AC, run(d, "python", "main.py", code, SUM).verdict());
    }
    @Test void runsAsNonRootAndSeesNoHostSecrets(@TempDir Path d) throws Exception {
        String code = "import os\nbad=[]\nif os.getuid()==0: bad.append('root')\nfor p in ['/var/run/docker.sock','/root/.ssh']:\n    if os.path.exists(p): bad.append(p)\ntry:\n    open('/etc/shadow').read(); bad.append('shadow')\nexcept Exception: pass\nprint('6' if not bad else 'BAD:'+','.join(bad))\n";
        assertEquals(Verdict.AC, run(d, "python", "main.py", code, SUM).verdict());
    }
    @Test void hiddenExpectedOutputIsNotInTheContainer(@TempDir Path d) throws Exception {
        String code = "import os\nfound=False\nfor root,_,fs in os.walk('/work'):\n    for f in fs:\n        try:\n            if ('HIDDEN-INPUT'+'-42') in open(os.path.join(root,f),errors='ignore').read(): found=True\n        except Exception: pass\nprint('LEAK' if found else '6')\n";
        assertEquals(Verdict.AC, run(d, "python", "main.py", code, new TestCase("h", "HIDDEN-INPUT-42\n", "6", false)).verdict());
    }
    @Test void missingJavaMainIsCE(@TempDir Path d) throws Exception {
        assertEquals(Verdict.CE, run(d, "java", "A.java", "public class A{}", SUM).verdict());
    }
    @Test void unsupportedLanguageIsIE(@TempDir Path d) throws Exception {
        assertEquals(Verdict.IE, run(d, "cobol", "main.cob", "x", SUM).verdict());
    }
    @Test void utf8ByteOrderMarkDoesNotBreakPythonOrJava(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d.resolve("p"), "python", "main.py", "\uFEFFprint(sum(map(int,input().split())))", SUM).verdict());
        assertEquals(Verdict.AC, run(d.resolve("j"), "java", "Main.java", "\uFEFFimport java.util.*;public class Main{public static void main(String[] a){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt()+s.nextInt());}}", SUM).verdict());
    }
    @Test void dockerFailingToStartAContainerIsNotACompileError(@TempDir Path d) throws Exception {
        assumeTrue(ready, "Docker or sandbox images unavailable");
        Path work = Files.createDirectories(d.resolve("w"));
        ContainerSpec spec = new ContainerSpec("codesentinel/no-such-image:0", List.of("true"), new byte[0], work, false, 64, 1.0, 16, 20_000, 1024, 16, java.util.Map.of());
        SandboxException e = assertThrows(SandboxException.class, () -> runner.run(spec));
        assertTrue(e.getMessage().contains("could not start"), e.getMessage());
    }
    @Test void browserStyleFileNamesAreAccepted(@TempDir Path d) throws Exception {
        assertEquals(Verdict.AC, run(d.resolve("a"), "python", "array_statistics(1).py", "print(sum(map(int,input().split())))", SUM).verdict());
        assertEquals(Verdict.AC, run(d.resolve("b"), "python", "My Solution (final).py", "print(sum(map(int,input().split())))", SUM).verdict());
    }
}
