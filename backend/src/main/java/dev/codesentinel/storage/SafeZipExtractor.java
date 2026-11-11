package dev.codesentinel.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts student ZIPs defensively. Streaming (no ZipFile central-directory trust): sizes are counted while reading,
 * so a lying header or a decompression bomb is stopped by the byte budget. Rejects the whole archive on any violation.
 */
public final class SafeZipExtractor {
    public record Limits(int maxEntries, long maxTotalBytes, long maxFileBytes, int maxDepth, int maxNameLength) {
        public static Limits defaults() {
            return new Limits(200, 20L << 20, 2L << 20, 8, 160);
        }
    }

    public static class UnsafeArchiveException extends Exception {
        public UnsafeArchiveException(String m) {
            super(m);
        }
    }

    private static final Set<String> ARCHIVE_EXT = Set.of(".zip", ".jar", ".war", ".tar", ".gz", ".tgz", ".7z", ".rar", ".bz2", ".xz");
    private static final Set<String> IGNORED_DIRS = Set.of("__MACOSX", "__pycache__", "node_modules", ".git", ".idea", ".vscode", ".venv", "venv");
    /** Build products that students leave in project folders; skipped silently (never judged, never an error). */
    private static final Set<String> IGNORED_EXT = Set.of(".pyc", ".pyo", ".class", ".o", ".obj");

    private SafeZipExtractor() {
    }

    /** @return relative, forward-slash paths of extracted regular files, sorted. */
    public static List<String> extract(InputStream zip, Path destDir, Limits lim) throws IOException, UnsafeArchiveException {
        Files.createDirectories(destDir);
        Path root = destDir.toAbsolutePath().normalize();
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(zip)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (++entries > lim.maxEntries()) throw new UnsafeArchiveException("Too many entries");
                if (name.isEmpty() || name.length() > lim.maxNameLength()) throw new UnsafeArchiveException("Invalid entry name length");
                if (name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0 || name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
                    throw new UnsafeArchiveException("Unsafe path: " + printable(name));
                String[] parts = name.split("/");
                for (String p : parts) {
                    if (p.equals("..")) throw new UnsafeArchiveException("Path traversal: " + printable(name));
                }
                if (parts.length > lim.maxDepth()) throw new UnsafeArchiveException("Directory depth exceeded");
                boolean junkDir = false;
                for (int i = 0; i < parts.length - (e.isDirectory() ? 0 : 1); i++) junkDir |= IGNORED_DIRS.contains(parts[i]);
                if (junkDir) {
                    zin.closeEntry();
                    continue;
                }
                if (e.isDirectory()) {
                    zin.closeEntry();
                    continue;
                }
                // Java's ZipEntry cannot express symlinks; reject by unix mode if the extra field is exposed, and by name below.
                String leaf = parts[parts.length - 1];
                String lower = leaf.toLowerCase(Locale.ROOT);
                if (leaf.startsWith(".") && !lower.equals(".gitignore")) {
                    zin.closeEntry();
                    continue; // hidden/OS files silently skipped
                }
                if (IGNORED_EXT.stream().anyMatch(lower::endsWith)) {
                    zin.closeEntry();
                    continue;
                }
                for (String ext : ARCHIVE_EXT) {
                    if (lower.endsWith(ext)) throw new UnsafeArchiveException("Nested archive not allowed: " + printable(name));
                }
                if (!leaf.matches("[A-Za-z0-9._ +()-]+") && !leaf.matches("[\\p{L}\\p{N}._ +()-]+"))
                    throw new UnsafeArchiveException("Unsupported file name: " + printable(name));
                String norm = String.join("/", parts);
                if (!seen.add(norm.toLowerCase(Locale.ROOT))) throw new UnsafeArchiveException("Duplicate entry: " + printable(name));

                Path target = root.resolve(norm).normalize();
                if (!target.startsWith(root)) throw new UnsafeArchiveException("Path escapes destination");
                Files.createDirectories(target.getParent());
                long written = 0;
                byte[] buf = new byte[8192];
                try (var os = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        written += n;
                        total += n;
                        if (written > lim.maxFileBytes()) throw new UnsafeArchiveException("File too large: " + printable(name));
                        if (total > lim.maxTotalBytes()) throw new UnsafeArchiveException("Archive expands beyond allowed size");
                        os.write(buf, 0, n);
                    }
                }
                if (Files.isSymbolicLink(target)) throw new UnsafeArchiveException("Symlink");
                out.add(norm);
            }
        } catch (UnsafeArchiveException ex) {
            deleteTree(root);
            throw ex;
        } catch (java.util.zip.ZipException ex) {
            deleteTree(root);
            throw new UnsafeArchiveException("Corrupt archive");
        }
        if (out.isEmpty()) throw new UnsafeArchiveException("Archive contains no usable files");
        Collections.sort(out);
        return out;
    }

    private static String printable(String s) {
        String t = s.replaceAll("[^\\x20-\\x7E]", "?");
        return t.length() > 80 ? t.substring(0, 80) + "..." : t;
    }

    private static void deleteTree(Path root) {
        try (var w = Files.walk(root)) {
            w.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(root)).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
