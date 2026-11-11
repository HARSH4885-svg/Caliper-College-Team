package dev.codesentinel.engine.normalization;

import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.lexer.SourceToken;
import dev.codesentinel.engine.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalization filter of the pipeline (FR-PLAG-02..05, FR-PLAG-07).
 *
 * <ul>
 *   <li>Comments/whitespace are already gone (the tokenizer never emits them).</li>
 *   <li>Keywords, operators, punctuation and token order are preserved.</li>
 *   <li>User-defined identifiers are renamed by first occurrence <b>within a file</b>: ID_1, ID_2 ... and a repeated
 *       identifier reuses its ID. Well-known library names (printf, System, len ...) are not renamed.</li>
 *   <li>Types: built-in types are keywords and are kept; user-defined types are identifiers and are renamed
 *       (documented limitation: two different user classes with the same usage pattern look alike).</li>
 *   <li>Literals follow {@link NormalizationConfig.LiteralMode}.</li>
 *   <li>Common standard includes/imports are dropped when configured; other includes are kept as INC tokens.</li>
 * </ul>
 */
public final class TokenNormalizer {

    /** Result of normalizing one file. */
    public record Result(List<NormalizedToken> tokens, int droppedIncludes) {
    }

    public Result normalize(Language lang, List<SourceToken> tokens, NormalizationConfig cfg) {
        return normalize(lang, tokens, cfg, null);
    }

    /**
     * @param suppressedByOutputIndex optional starter-code mask aligned with the <em>output</em> list of a first pass
     *                                (dropped includes are dropped identically in both passes). Identifiers at
     *                                suppressed positions do not consume ID numbers, so starter identifiers can never
     *                                shift the numbering of the student's own code.
     */
    public Result normalize(Language lang, List<SourceToken> tokens, NormalizationConfig cfg,
                            boolean[] suppressedByOutputIndex) {
        Map<String, Integer> ids = new HashMap<>();
        List<NormalizedToken> out = new ArrayList<>(tokens.size());
        int dropped = 0;
        int depth = 0;
        int pyLevel = 0;
        int boundaryDepth = lang == Language.JAVA ? 1 : 0;
        boolean unitScope = cfg.identifierMode() == NormalizationConfig.IdentifierMode.FIRST_OCCURRENCE_UNIT;
        for (SourceToken t : tokens) {
            String v;
            switch (t.type()) {
                case INCLUDE -> {
                    if (cfg.dropCommonIncludes() && CommonNames.isCommonInclude(lang, t.text())) {
                        dropped++;
                        continue;
                    }
                    v = "INC:" + t.text();
                }
                case IDENTIFIER -> {
                    if (CommonNames.isStandardName(lang, t.text())) {
                        v = t.text();
                    } else if (cfg.identifierMode() == NormalizationConfig.IdentifierMode.BLIND) {
                        v = "ID";
                    } else if (suppressedByOutputIndex != null && out.size() < suppressedByOutputIndex.length
                            && suppressedByOutputIndex[out.size()]) {
                        v = "ID_S";
                    } else {
                        Integer id = ids.get(t.text());
                        if (id == null) {
                            id = ids.size() + 1;
                            ids.put(t.text(), id);
                        }
                        v = "ID_" + id;
                    }
                }
                case NUMBER -> v = number(t.text(), cfg.literalMode());
                case STRING -> v = string(t.text(), cfg.literalMode());
                case CHAR -> v = cfg.literalMode() == NormalizationConfig.LiteralMode.CATEGORY_ONLY ? "CHR" : "CHR:" + t.text();
                case REGEX -> v = cfg.literalMode() == NormalizationConfig.LiteralMode.KEEP_ALL ? "REGEX:" + t.text() : "REGEX";
                case NEWLINE -> v = "NL";
                case INDENT -> v = "INDENT";
                case DEDENT -> v = "DEDENT";
                default -> v = t.text(); // KEYWORD, OPERATOR, PUNCT
            }
            out.add(new NormalizedToken(v, t));

            // top-level unit tracking for FIRST_OCCURRENCE_UNIT
            if (unitScope) {
                if (lang == Language.PYTHON) {
                    if (t.type() == TokenType.INDENT) pyLevel++;
                    else if (t.type() == TokenType.DEDENT) {
                        pyLevel = Math.max(0, pyLevel - 1);
                        if (pyLevel == 0) ids.clear();
                    }
                } else if (t.type() == TokenType.PUNCT) {
                    if (t.text().equals("{")) depth++;
                    else if (t.text().equals("}")) {
                        depth = Math.max(0, depth - 1);
                        if (depth <= boundaryDepth) ids.clear();
                    }
                }
            }
        }
        return new Result(out, dropped);
    }

    static String number(String text, NormalizationConfig.LiteralMode mode) {
        String clean = text.replace("_", "").replace("'", "");
        boolean isFloat = !isHexOrBin(clean) && (clean.indexOf('.') >= 0 || clean.indexOf('e') >= 0 || clean.indexOf('E') >= 0);
        if (mode == NormalizationConfig.LiteralMode.KEEP_ALL) return "NUM:" + clean;
        if (mode == NormalizationConfig.LiteralMode.SMART && !isFloat) {
            String digits = clean.replaceAll("[uUlLnN]+$", "");
            if (digits.matches("[0-9]+")) {
                try {
                    long v = Long.parseLong(digits);
                    if (v <= 2) return "NUM:" + v;
                } catch (NumberFormatException ignored) {
                    // too large for long: category only
                }
            }
        }
        return isFloat ? "NUMF" : "NUM";
    }

    private static boolean isHexOrBin(String s) {
        return s.startsWith("0x") || s.startsWith("0X") || s.startsWith("0b") || s.startsWith("0B");
    }

    static String string(String text, NormalizationConfig.LiteralMode mode) {
        switch (mode) {
            case KEEP_ALL:
                return "STR:" + text;
            case CATEGORY_ONLY:
                return "STR";
            default:
                String body = contentOf(text);
                return body.length() <= 3 ? "STR:" + body : "STR";
        }
    }

    /** Strips string prefix and quotes to estimate the content length. */
    private static String contentOf(String text) {
        int q = 0;
        while (q < text.length() && text.charAt(q) != '"' && text.charAt(q) != '\'' && text.charAt(q) != '`') q++;
        if (q >= text.length()) return text;
        String rest = text.substring(q);
        for (String tq : new String[]{"\"\"\"", "'''"}) {
            if (rest.startsWith(tq) && rest.endsWith(tq) && rest.length() >= 6) return rest.substring(3, rest.length() - 3);
        }
        if (rest.length() >= 2 && rest.charAt(rest.length() - 1) == rest.charAt(0)) return rest.substring(1, rest.length() - 1);
        return rest.substring(1);
    }

    /** Whether a token type is counted as informative program content (not include/structure). */
    public static boolean isInformative(TokenType type) {
        return !type.isStructuralOnly();
    }
}
