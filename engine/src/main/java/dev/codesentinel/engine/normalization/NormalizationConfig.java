package dev.codesentinel.engine.normalization;

/**
 * Normalization policy (FR-PLAG-05, FR-PLAG-07). Recorded in every report.
 *
 * @param literalMode           how numeric/char/string literals are represented
 * @param dropCommonIncludes    suppress standard includes/imports (stdio.h, java.util.*, import sys ...)
 * @param identifierMode        how user-defined identifiers are renamed
 */
public record NormalizationConfig(LiteralMode literalMode, boolean dropCommonIncludes, IdentifierMode identifierMode) {

    public enum IdentifierMode {
        /** Strict FR-PLAG-04: first distinct identifier of the whole file becomes ID_1, the next ID_2 ... */
        FIRST_OCCURRENCE_FILE,
        /**
         * Default. Same first-occurrence rule, but numbering restarts at every top-level unit (a C/C++/JS function or
         * struct, a Java method, a Python top-level def/class). Inserting a variable then only shifts numbers inside
         * that one unit, and reordering functions no longer renumbers anything.
         */
        FIRST_OCCURRENCE_UNIT,
        /** Every user identifier becomes the single token ID (most robust to renaming, least discriminating). */
        BLIND
    }

    public enum LiteralMode {
        /** Keep exact values of every literal (strictest; defeated by changing a constant). */
        KEEP_ALL,
        /** Collapse every literal to its category only (most permissive; risks erasing logic distinctions). */
        CATEGORY_ONLY,
        /**
         * Default. Integers with |v| &lt;= 2 keep their value (loop bounds, base cases), other numbers keep only their
         * category (int/float); char literals keep their value; strings of &lt;= 3 content characters keep their
         * value (separators, format specifiers), longer strings become a single STR category.
         */
        SMART
    }

    public static NormalizationConfig defaults() {
        return new NormalizationConfig(LiteralMode.SMART, true, IdentifierMode.FIRST_OCCURRENCE_UNIT);
    }
}
