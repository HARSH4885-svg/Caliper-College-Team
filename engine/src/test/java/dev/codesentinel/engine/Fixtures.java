package dev.codesentinel.engine;

import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Loads the SYNTHETIC fixtures from src/test/resources/fixtures/quality (all test data, no real student work). */
final class Fixtures {
    static final Path ROOT;

    static {
        try {
            ROOT = Path.of(Fixtures.class.getResource("/fixtures/quality").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private Fixtures() {
    }

    static String main(String name) {
        try {
            return Files.readString(ROOT.resolve(name).resolve("main.cpp")).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static SubmissionInput sub(String name) {
        return new SubmissionInput(name, List.of(new SourceFileInput("main.cpp", main(name))));
    }

    static SubmissionInput sub(String id, String cppSource) {
        return new SubmissionInput(id, List.of(new SourceFileInput("main.cpp", cppSource)));
    }

    static List<SourceFileInput> starter() {
        return List.of(new SourceFileInput("starter.cpp", readStarter()));
    }

    static String starterText() {
        return readStarter();
    }

    private static String readStarter() {
        try {
            return Files.readString(ROOT.resolve("starter").resolve("starter.cpp")).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> allSubmissionNames() {
        try (Stream<Path> s = Files.list(ROOT)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("I") || n.startsWith("C")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
