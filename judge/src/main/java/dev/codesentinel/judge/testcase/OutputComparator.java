package dev.codesentinel.judge.testcase;

/**
 * Documented comparison policy (FR-JUDGE-07). Both sides are normalized the same way, then compared exactly:
 * <ol>
 *   <li>CRLF is converted to LF (a lone CR is left alone).</li>
 *   <li>Spaces and tabs at the END of every line are removed.</li>
 *   <li>Empty lines at the END of the output are removed.</li>
 * </ol>
 * Everything else is significant: leading whitespace, whitespace inside a line, letter case, blank lines in the
 * middle, token order and numeric values ("1.0" differs from "1"). Nothing is "fuzzy matched".
 */
public final class OutputComparator {
    public static final String POLICY =
            "CRLF->LF; trailing spaces/tabs per line removed; trailing blank lines removed; everything else exact.";

    private OutputComparator() {
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String lf = s.replace("\r\n", "\n");
        String[] lines = lf.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            int end = l.length();
            while (end > 0 && (l.charAt(end - 1) == ' ' || l.charAt(end - 1) == '\t')) end--;
            if (i > 0) sb.append('\n');
            sb.append(l, 0, end);
        }
        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == '\n') len--;
        return sb.substring(0, len);
    }

    public static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }
}
