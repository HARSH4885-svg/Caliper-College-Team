package dev.codesentinel.engine.lexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hand-written scanner (state machine) for C, C++, Java and JavaScript. It deliberately does not use a comment-stripping
 * regex: comments, string/char literals, Java text blocks, C++ raw strings, JS template literals and regex literals are
 * all recognised by the scanner, so "//" inside a string is not a comment and an escaped quote does not end a string
 * (FR-PLAG-06). Preprocessor lines are tokenized, not deleted (FR-PLAG-07).
 */
public final class CFamilyTokenizer implements LanguageTokenizer {

    public enum Dialect { C, CPP, JAVA, JAVASCRIPT }

    private static final Set<String> C_KW = setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern",
            "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "_Bool");
    private static final Set<String> CPP_KW = setOf(
            "alignas", "alignof", "and", "asm", "auto", "bool", "break", "case", "catch", "char", "class", "const",
            "constexpr", "const_cast", "continue", "decltype", "default", "delete", "do", "double", "dynamic_cast",
            "else", "enum", "explicit", "export", "extern", "false", "final", "float", "for", "friend", "goto", "if",
            "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not", "nullptr", "operator", "or",
            "override", "private", "protected", "public", "register", "reinterpret_cast", "return", "short", "signed",
            "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "template", "this", "thread_local",
            "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
            "volatile", "while");
    private static final Set<String> JAVA_KW = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "instanceof", "int", "interface", "long", "native", "new", "null", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "void", "volatile", "while", "true", "false", "var", "record");
    private static final Set<String> JS_KW = setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
            "do", "else", "export", "extends", "finally", "for", "function", "if", "import", "in", "instanceof", "let",
            "new", "of", "return", "static", "super", "switch", "this", "throw", "try", "typeof", "var", "void",
            "while", "with", "yield", "null", "true", "false", "undefined");

    /** Keywords after which a '/' starts a regex literal rather than a division (JavaScript heuristic). */
    private static final Set<String> JS_REGEX_AFTER_KW = setOf(
            "return", "typeof", "case", "in", "of", "delete", "void", "throw", "new", "else", "do", "instanceof", "yield");
    private static final String JS_REGEX_AFTER_PUNCT = "(,=:[!&|?{;";

    private static final String[] OPS_COMMON = {
            "<<=", ">>=", "...", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "+=", "-=", "*=", "/=",
            "%=", "&=", "|=", "^="};
    private static final String[] OPS_C = {"->"};
    private static final String[] OPS_CPP = {"->*", "<=>", "->", "::", ".*"};
    private static final String[] OPS_JAVA = {">>>=", ">>>", "->", "::"};
    private static final String[] OPS_JS = {">>>=", ">>>", "===", "!==", "**=", "??=", "&&=", "||=", "=>", "**", "??", "?."};
    private static final String SINGLE_OPS = "+-*/%=<>!&|^~?:";
    private static final String BRACKETS = "(){}[];,.#";

    private final Dialect dialect;
    private final Language language;
    private final Set<String> keywords;
    private final String[] operators; // sorted longest first

    public CFamilyTokenizer(Dialect dialect) {
        this.dialect = dialect;
        List<String> ops = new ArrayList<>(Arrays.asList(OPS_COMMON));
        switch (dialect) {
            case C -> {
                this.language = Language.C;
                this.keywords = C_KW;
                ops.addAll(Arrays.asList(OPS_C));
            }
            case CPP -> {
                this.language = Language.CPP;
                this.keywords = CPP_KW;
                ops.addAll(Arrays.asList(OPS_CPP));
            }
            case JAVA -> {
                this.language = Language.JAVA;
                this.keywords = JAVA_KW;
                ops.addAll(Arrays.asList(OPS_JAVA));
            }
            default -> {
                this.language = Language.JAVASCRIPT;
                this.keywords = JS_KW;
                ops.addAll(Arrays.asList(OPS_JS));
            }
        }
        ops.sort((a, b) -> b.length() - a.length());
        this.operators = ops.toArray(new String[0]);
    }

    @Override
    public Language language() {
        return language;
    }

    @Override
    public List<SourceToken> tokenize(String source, String fileName) {
        return new Scan(source == null ? "" : source, fileName).run();
    }

    private static Set<String> setOf(String... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    private boolean preprocessor() {
        return dialect == Dialect.C || dialect == Dialect.CPP;
    }

    // ---------------------------------------------------------------------------------------------------------------

    private final class Scan {
        final String s;
        final int n;
        final String file;
        int i = 0;
        int line = 1;
        int col = 1;
        boolean lineStart = true; // only whitespace/comments seen so far on this line
        final List<SourceToken> out = new ArrayList<>();

        Scan(String s, String file) {
            this.s = s;
            this.n = s.length();
            this.file = file;
        }

        char peek(int off) {
            return i + off < n ? s.charAt(i + off) : '\0';
        }

        boolean starts(String t) {
            return s.startsWith(t, i);
        }

        void adv() {
            char c = s.charAt(i++);
            if (c == '\n') {
                line++;
                col = 1;
                lineStart = true;
            } else if (c != '\r') {
                col++;
            }
        }

        void adv(int k) {
            for (int j = 0; j < k && i < n; j++) adv();
        }

        void emit(TokenType type, String text, int startIdx, int sLine, int sCol) {
            out.add(new SourceToken(type, text, sLine, sCol, line, col, out.size(), file));
            lineStart = false;
            // 'startIdx' retained for readability at call sites
            assert startIdx >= 0;
        }

        List<SourceToken> run() {
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\n' || c == ' ' || c == '\t' || c == '\r' || c == '\f' || c == '\u000b') {
                    adv();
                    continue;
                }
                if (c == '\\' && (peek(1) == '\n' || (peek(1) == '\r' && peek(2) == '\n'))) {
                    adv(peek(1) == '\r' ? 3 : 2);
                    continue;
                }
                if (c == '/' && peek(1) == '/') {
                    skipLineComment();
                    continue;
                }
                if (c == '/' && peek(1) == '*') {
                    skipBlockComment();
                    continue;
                }
                if (c == '#' && preprocessor() && lineStart) {
                    directive();
                    continue;
                }
                int si = i, sl = line, sc = col;
                if (c == '"') {
                    if (dialect == Dialect.JAVA && starts("\"\"\"")) {
                        textBlock(si, sl, sc);
                    } else {
                        quoted('"', TokenType.STRING, si, sl, sc);
                    }
                    continue;
                }
                if (c == '\'') {
                    quoted('\'', dialect == Dialect.JAVASCRIPT ? TokenType.STRING : TokenType.CHAR, si, sl, sc);
                    continue;
                }
                if (c == '`' && dialect == Dialect.JAVASCRIPT) {
                    adv();
                    templateBody();
                    emit(TokenType.STRING, s.substring(si, i), si, sl, sc);
                    continue;
                }
                if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) {
                    number(si, sl, sc);
                    continue;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    identifier(si, sl, sc);
                    continue;
                }
                if (dialect == Dialect.JAVA && c == '@' && Character.isJavaIdentifierStart(peek(1))
                        && !starts("@interface")) {
                    adv();
                    while (i < n && (Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) == '.')) adv();
                    emit(TokenType.KEYWORD, s.substring(si, i), si, sl, sc);
                    continue;
                }
                if (c == '/' && dialect == Dialect.JAVASCRIPT && regexAllowed()) {
                    regex(si, sl, sc);
                    continue;
                }
                String op = matchOperator();
                if (op != null) {
                    adv(op.length());
                    emit(TokenType.OPERATOR, op, si, sl, sc);
                    continue;
                }
                adv();
                boolean single = SINGLE_OPS.indexOf(c) >= 0;
                emit(single ? TokenType.OPERATOR : TokenType.PUNCT, String.valueOf(c), si, sl, sc);
            }
            return out;
        }

        String matchOperator() {
            for (String op : operators) {
                if (s.startsWith(op, i)) {
                    // C++/Java: "<<" etc are fine; a lone "?." in JS must be followed by non-digit
                    if (op.equals("?.") && Character.isDigit(peek(2))) continue;
                    return op;
                }
            }
            return null;
        }

        void skipLineComment() {
            while (i < n && s.charAt(i) != '\n') {
                if (s.charAt(i) == '\\' && preprocessor()
                        && (peek(1) == '\n' || (peek(1) == '\r' && peek(2) == '\n'))) {
                    adv(peek(1) == '\r' ? 3 : 2); // C/C++: a backslash-newline continues a // comment
                    continue;
                }
                adv();
            }
        }

        void skipBlockComment() {
            boolean wasLineStart = lineStart;
            adv(2);
            while (i < n && !(s.charAt(i) == '*' && peek(1) == '/')) adv();
            if (i < n) adv(2);
            // A comment counts as whitespace: a directive may still follow it if only whitespace preceded it.
            lineStart = wasLineStart;
        }

        void directive() {
            int si = i, sl = line, sc = col;
            adv(); // '#'
            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) adv();
            int ws = i;
            while (i < n && Character.isLetter(s.charAt(i))) adv();
            String word = s.substring(ws, i);
            if (word.isEmpty()) {
                emit(TokenType.PUNCT, "#", si, sl, sc);
                return;
            }
            if (word.equals("include") || word.equals("include_next") || word.equals("import")) {
                while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) adv();
                int hs = i;
                if (i < n && (s.charAt(i) == '<' || s.charAt(i) == '"')) {
                    char close = s.charAt(i) == '<' ? '>' : '"';
                    adv();
                    while (i < n && s.charAt(i) != close && s.charAt(i) != '\n') adv();
                    if (i < n && s.charAt(i) == close) adv();
                    String header = s.substring(hs + 1, Math.max(hs + 1, i - 1)).trim();
                    emit(TokenType.INCLUDE, header, si, sl, sc);
                    return;
                }
                // computed include (macro): fall through and tokenize the rest as ordinary tokens
            }
            emit(TokenType.KEYWORD, "#" + word, si, sl, sc);
        }

        void quoted(char q, TokenType type, int si, int sl, int sc) {
            adv(); // opening quote
            while (i < n) {
                char ch = s.charAt(i);
                if (ch == '\\') {
                    adv();
                    if (i < n) adv();
                    continue;
                }
                if (ch == q) {
                    adv();
                    break;
                }
                if (ch == '\n') break; // unterminated: stop at end of line, do not swallow the program
                adv();
            }
            emit(type, s.substring(si, i), si, sl, sc);
        }

        void textBlock(int si, int sl, int sc) {
            adv(3);
            while (i < n) {
                if (s.charAt(i) == '\\') {
                    adv();
                    if (i < n) adv();
                } else if (starts("\"\"\"")) {
                    adv(3);
                    break;
                } else {
                    adv();
                }
            }
            emit(TokenType.STRING, s.substring(si, i), si, sl, sc);
        }

        /** Consumes a template literal body after the opening backtick, including nested ${ ... } expressions. */
        void templateBody() {
            while (i < n) {
                char ch = s.charAt(i);
                if (ch == '\\') {
                    adv();
                    if (i < n) adv();
                } else if (ch == '`') {
                    adv();
                    return;
                } else if (ch == '$' && peek(1) == '{') {
                    adv(2);
                    templateExpr();
                } else {
                    adv();
                }
            }
        }

        void templateExpr() {
            int depth = 1;
            while (i < n && depth > 0) {
                char ch = s.charAt(i);
                if (ch == '{') {
                    depth++;
                    adv();
                } else if (ch == '}') {
                    depth--;
                    adv();
                } else if (ch == '`') {
                    adv();
                    templateBody();
                } else if (ch == '"' || ch == '\'') {
                    adv();
                    while (i < n && s.charAt(i) != ch && s.charAt(i) != '\n') {
                        if (s.charAt(i) == '\\') adv();
                        if (i < n) adv();
                    }
                    if (i < n && s.charAt(i) == ch) adv();
                } else {
                    adv();
                }
            }
        }

        boolean regexAllowed() {
            if (out.isEmpty()) return true;
            SourceToken last = out.get(out.size() - 1);
            return switch (last.type()) {
                case OPERATOR -> true;
                case PUNCT -> JS_REGEX_AFTER_PUNCT.indexOf(last.text().charAt(0)) >= 0;
                case KEYWORD -> JS_REGEX_AFTER_KW.contains(last.text());
                default -> false;
            };
        }

        void regex(int si, int sl, int sc) {
            adv(); // '/'
            boolean inClass = false;
            while (i < n) {
                char ch = s.charAt(i);
                if (ch == '\n') break;
                if (ch == '\\') {
                    adv();
                    if (i < n) adv();
                    continue;
                }
                if (ch == '[') inClass = true;
                else if (ch == ']') inClass = false;
                else if (ch == '/' && !inClass) {
                    adv();
                    break;
                }
                adv();
            }
            while (i < n && Character.isLetter(s.charAt(i))) adv(); // flags
            emit(TokenType.REGEX, s.substring(si, i), si, sl, sc);
        }

        void number(int si, int sl, int sc) {
            char c = s.charAt(i);
            char c1 = peek(1);
            if (c == '0' && (c1 == 'x' || c1 == 'X')) {
                adv(2);
                while (i < n && (Character.digit(s.charAt(i), 16) >= 0 || s.charAt(i) == '_' || s.charAt(i) == '\'')) adv();
            } else if (c == '0' && (c1 == 'b' || c1 == 'B')) {
                adv(2);
                while (i < n && (s.charAt(i) == '0' || s.charAt(i) == '1' || s.charAt(i) == '_' || s.charAt(i) == '\'')) adv();
            } else {
                digits();
                if (i < n && s.charAt(i) == '.' && peek(1) != '.' && !Character.isJavaIdentifierStart(peek(1))) {
                    adv();
                    digits();
                }
                char e = peek(0);
                if ((e == 'e' || e == 'E')
                        && (Character.isDigit(peek(1)) || ((peek(1) == '+' || peek(1) == '-') && Character.isDigit(peek(2))))) {
                    adv(2);
                    digits();
                }
            }
            while (i < n && Character.isLetter(s.charAt(i))) adv(); // suffixes: u, l, f, d, n ...
            emit(TokenType.NUMBER, s.substring(si, i), si, sl, sc);
        }

        void digits() {
            while (i < n) {
                char ch = s.charAt(i);
                if (Character.isDigit(ch) || ch == '_') adv();
                else if (ch == '\'' && dialect == Dialect.CPP && Character.isDigit(peek(1))) adv(); // C++14 separator
                else break;
            }
        }

        void identifier(int si, int sl, int sc) {
            while (i < n && Character.isJavaIdentifierPart(s.charAt(i)) && s.charAt(i) != '\0') adv();
            String word = s.substring(si, i);

            // String/char prefixes and C++ raw strings
            if ((dialect == Dialect.C || dialect == Dialect.CPP)) {
                char nx = peek(0);
                if ((nx == '"' || nx == '\'') && (word.equals("L") || word.equals("u") || word.equals("U") || word.equals("u8"))) {
                    quoted(nx, nx == '"' ? TokenType.STRING : TokenType.CHAR, si, sl, sc);
                    return;
                }
                if (dialect == Dialect.CPP && nx == '"' && word.endsWith("R")
                        && (word.equals("R") || word.equals("uR") || word.equals("UR") || word.equals("u8R") || word.equals("LR"))) {
                    rawString(si, sl, sc);
                    return;
                }
            }
            if (dialect == Dialect.JAVA && (word.equals("import") || word.equals("package"))) {
                while (i < n && s.charAt(i) != ';' && s.charAt(i) != '\n') adv();
                String text = s.substring(si, i).replaceAll("\\s+", " ").trim();
                if (i < n && s.charAt(i) == ';') adv();
                emit(TokenType.INCLUDE, text, si, sl, sc);
                return;
            }
            emit(keywords.contains(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER, word, si, sl, sc);
        }

        void rawString(int si, int sl, int sc) {
            adv(); // opening quote
            int ds = i;
            while (i < n && s.charAt(i) != '(' && s.charAt(i) != '\n') adv();
            String delim = s.substring(ds, i);
            String close = ")" + delim + "\"";
            if (i < n && s.charAt(i) == '(') adv();
            while (i < n && !starts(close)) adv();
            if (i < n) adv(close.length());
            emit(TokenType.STRING, s.substring(si, i), si, sl, sc);
        }
    }
}
