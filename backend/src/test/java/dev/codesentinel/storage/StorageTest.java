package dev.codesentinel.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class StorageTest {
    private static byte[] zip(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (var e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    private static Map<String, byte[]> one(String name, String body) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put(name, body.getBytes(StandardCharsets.UTF_8));
        return m;
    }

    private static void rejects(byte[] z, Path dest, String fragment) {
        var ex = assertThrows(SafeZipExtractor.UnsafeArchiveException.class,
                () -> SafeZipExtractor.extract(new ByteArrayInputStream(z), dest, SafeZipExtractor.Limits.defaults()));
        assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
    }

    @Test
    void extractsNormalArchive(@TempDir Path d) throws Exception {
        Map<String, byte[]> m = one("src/Main.java", "class A{}");
        m.put("README.md", "hi".getBytes());
        m.put("__MACOSX/x", "junk".getBytes());
        m.put(".DS_Store", "junk".getBytes());
        m.put("proj/__pycache__/m.cpython-311.pyc", "junk".getBytes());
        m.put("Main.class", "junk".getBytes());
        m.put("node_modules/x/index.js", "junk".getBytes());
        List<String> files = SafeZipExtractor.extract(new ByteArrayInputStream(zip(m)), d.resolve("o"), SafeZipExtractor.Limits.defaults());
        assertEquals(List.of("README.md", "src/Main.java"), files);
        assertEquals("class A{}", Files.readString(d.resolve("o/src/Main.java")));
    }

    @Test
    void pathTraversalAbsoluteAndBackslashAreRejectedAndNothingEscapes(@TempDir Path d) throws Exception {
        rejects(zip(one("../evil.c", "x")), d.resolve("o"), "traversal");
        rejects(zip(one("a/../../evil.c", "x")), d.resolve("o"), "traversal");
        rejects(zip(one("/etc/evil.c", "x")), d.resolve("o"), "Unsafe");
        rejects(zip(one("C:/evil.c", "x")), d.resolve("o"), "Unsafe");
        rejects(zip(one("..\\evil.c", "x")), d.resolve("o"), "Unsafe");
        assertFalse(Files.exists(d.resolve("evil.c")));
    }

    @Test
    void nestedArchivesAreRejected(@TempDir Path d) throws Exception {
        rejects(zip(one("inner.zip", "PK")), d.resolve("o"), "Nested");
        rejects(zip(one("lib/a.jar", "PK")), d.resolve("o2"), "Nested");
    }

    @Test
    void tooManyEntriesTooDeepAndDuplicatesAreRejected(@TempDir Path d) throws Exception {
        Map<String, byte[]> many = new LinkedHashMap<>();
        for (int i = 0; i < 201; i++) many.put("f" + i + ".c", new byte[]{1});
        rejects(zip(many), d.resolve("a"), "Too many");
        rejects(zip(one("a/b/c/d/e/f/g/h/i/x.c", "x")), d.resolve("b"), "depth");
        Map<String, byte[]> dup = one("A.c", "1");
        dup.put("a.c", "2".getBytes());
        rejects(zip(dup), d.resolve("c"), "Duplicate");
    }

    @Test
    void decompressionBombStoppedByByteBudgetNotByHeader(@TempDir Path d) throws Exception {
        byte[] big = new byte[3 << 20]; // 3 MiB of zeros compresses to ~3 KiB, over the 2 MiB per-file cap
        byte[] z = zip(one("zeros.txt", new String(big, StandardCharsets.ISO_8859_1)));
        assertTrue(z.length < 50_000, "sanity: the archive itself is tiny");
        rejects(z, d.resolve("o"), "too large");
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) m.put("p" + i + ".txt", new byte[1_900_000]);
        rejects(zip(m), d.resolve("o2"), "expands");
    }

    @Test
    void rejectionRemovesPartialOutput(@TempDir Path d) throws Exception {
        Map<String, byte[]> m = one("ok.c", "int x;");
        m.put("../bad.c", "x".getBytes());
        assertThrows(SafeZipExtractor.UnsafeArchiveException.class, () -> SafeZipExtractor.extract(new ByteArrayInputStream(zip(m)), d.resolve("o"), SafeZipExtractor.Limits.defaults()));
        try (var s = Files.walk(d.resolve("o"))) {
            assertEquals(1, s.count(), "only the (empty) destination directory remains");
        }
    }

    @Test
    void corruptAndEmptyArchivesAreRejected(@TempDir Path d) {
        var ex = assertThrows(SafeZipExtractor.UnsafeArchiveException.class,
                () -> SafeZipExtractor.extract(new ByteArrayInputStream("not a zip".getBytes()), d.resolve("o"), SafeZipExtractor.Limits.defaults()));
        assertNotNull(ex.getMessage());
    }

    @Test
    void shellMetacharactersInFileNamesAreRejected(@TempDir Path d) throws Exception {
        rejects(zip(one("a;rm -rf x.c", "x")), d.resolve("o"), "Unsupported file name");
        rejects(zip(one("$(id).c", "x")), d.resolve("o2"), "Unsupported file name");
    }

    // ---- local storage -----------------------------------------------------------------------------------------------

    @Test
    void localStorageRoundTripWithHash(@TempDir Path d) throws Exception {
        var s = new LocalFileStorageProvider(d.resolve("store"));
        StoredObject o = s.store(new StoreRequest("sub/1/file.zip", new ByteArrayInputStream("hello".getBytes())));
        assertEquals(5, o.sizeBytes());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", o.sha256());
        assertTrue(s.exists("sub/1/file.zip"));
        assertEquals("hello", new String(s.read("sub/1/file.zip").readAllBytes()));
        s.delete("sub/1/file.zip");
        assertFalse(s.exists("sub/1/file.zip"));
    }

    @Test
    void localStorageRejectsHostileKeys(@TempDir Path d) {
        var s = new LocalFileStorageProvider(d.resolve("store"));
        for (String k : new String[]{"../x", "/etc/passwd", "a/../../x", "a//b", ".hidden", "a\\b", "", null, "x y"}) {
            assertThrows(StorageException.class, () -> s.store(new StoreRequest(k, new ByteArrayInputStream(new byte[0]))), String.valueOf(k));
        }
    }


}
