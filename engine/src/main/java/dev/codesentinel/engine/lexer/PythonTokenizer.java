package dev.codesentinel.engine.lexer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Python scanner: tracks indentation (INDENT/DEDENT), logical lines (NEWLINE, suppressed inside brackets), triple-quoted
 * and prefixed strings, and comments. f-strings are kept as one STRING token (their embedded expressions are not parsed).
 * Import lines are collapsed into one INCLUDE token so common-import policy can suppress them.
 */
public final class PythonTokenizer implements LanguageTokenizer {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
            "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"));
    private static final String[] OPS = {"**=", "//=", ">>=", "<<=", "...", "**", "//", "==", "!=", "<=", ">=", "->", ":=",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "@=", "<<", ">>"};
    private static final String SINGLE_OPS = "+-*/%=<>&|^~@";

    @Override
    public Language language() {
        return Language.PYTHON;
    }

    @Override
    public List<SourceToken> tokenize(String source, String fileName) {
        return new Scan(source == null ? "" : source, fileName).run();
    }

    private static final class Scan {
        final String s;
        final int n;
        final String file;
        int i = 0, line = 1, col = 1, depth = 0;
        boolean atLineStart = true;
        final Deque<Integer> indents = new ArrayDeque<>();
        final List<SourceToken> out = new ArrayList<>();

        Scan(String s, String file) {
            this.s = s;
            this.n = s.length();
            this.file = file;
            indents.push(0);
        }

        char peek(int off) {
            return i + off < n ? s.charAt(i + off) : '\0';
        }

        void adv() {
            char c = s.charAt(i++);
            if (c == '\n') {
                line++;
                col = 1;
            } else if (c != '\r') {
                col++;
            }
        }

        void adv(int k) {
            for (int j = 0; j < k && i < n; j++) adv();
        }

        void emit(TokenType t, String text, int sl, int sc) {
            out.add(new SourceToken(t, text, sl, sc, line, col, out.size(), file));
        }

        boolean lastIsLogicalBreak() {
            if (out.isEmpty()) return true;
            TokenType t = out.get(out.size() - 1).type();
            return t == TokenType.NEWLINE || t == TokenType.INDENT || t == TokenType.DEDENT;
        }

        List<SourceToken> run() {
            while (i < n) {
                if (atLineStart && depth == 0) {
                    if (!handleIndentation()) continue;
                }
                char c = s.charAt(i);
                if (c == '\n') {
                    if (depth == 0 && !out.isEmpty() && out.get(out.size() - 1).type() != TokenType.NEWLINE
                            && !lastIsLogicalBreakOnlyIndent()) {
                        emit(TokenType.NEWLINE, "\n", line, col);
                    }
                    adv();
                    atLineStart = depth == 0;
                    continue;
                }
                if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
                    adv();
                    continue;
                }
                if (c == '#') {
                    while (i < n && s.charAt(i) != '\n') adv();
                    continue;
                }
                if (c == '\\' && (peek(1) == '\n' || (peek(1) == '\r' && peek(2) == '\n'))) {
                    adv(peek(1) == '\r' ? 3 : 2);
                    continue;
                }
                int sl = line, sc = col, si = i;
                if (c == '"' || c == '\'') {
                    string(si, sl, sc, i);
                    continue;
                }
                if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) {
                    number(si, sl, sc);
                    continue;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    word(si, sl, sc);
                    continue;
                }
                String op = matchOp();
                if (op != null) {
                    adv(op.length());
                    emit(TokenType.OPERATOR, op, sl, sc);
                    continue;
                }
                adv();
                if (c == '(' || c == '[' || c == '{') depth++;
                if ((c == ')' || c == ']' || c == '}') && depth > 0) depth--;
                emit(SINGLE_OPS.indexOf(c) >= 0 ? TokenType.OPERATOR : TokenType.PUNCT, String.valueOf(c), sl, sc);
            }
            if (!out.isEmpty() && out.get(out.size() - 1).type() != TokenType.NEWLINE && !lastIsLogicalBreakOnlyIndent()) {
                emit(TokenType.NEWLINE, "\n", line, col);
            }
            while (indents.peek() > 0) {
                indents.pop();
                emit(TokenType.DEDENT, "", line, col);
            }
            return out;
        }

        /** True if the last token is an INDENT/DEDENT with no content after it on the logical line. */
        boolean lastIsLogicalBreakOnlyIndent() {
            TokenType t = out.get(out.size() - 1).type();
            return t == TokenType.INDENT || t == TokenType.DEDENT;
        }

        /** Returns false if the line was blank/comment-only and has been consumed. */
        boolean handleIndentation() {
            int width = 0;
            int j = i;
            while (j < n) {
                char ch = s.charAt(j);
                if (ch == ' ') width++;
                else if (ch == '\t') width += 8 - (width % 8);
                else if (ch == '\f') width = 0;
                else break;
                j++;
            }
            if (j >= n) {
                adv(j - i);
                return true;
            }
            char ch = s.charAt(j);
            if (ch == '\n' || ch == '\r' || ch == '#') {
                adv(j - i);
                while (i < n && s.charAt(i) != '\n') adv(); // consume comment, keep the newline for the main loop
                if (i < n) adv();
                atLineStart = true;
                return false;
            }
            adv(j - i);
            int top = indents.peek();
            if (width > top) {
                indents.push(width);
                emit(TokenType.INDENT, "", line, col);
            } else if (width < top) {
                while (indents.peek() > width) {
                    indents.pop();
                    emit(TokenType.DEDENT, "", line, col);
                }
            }
            atLineStart = false;
            return true;
        }

        String matchOp() {
            for (String op : OPS) {
                if (s.startsWith(op, i)) return op;
            }
            return null;
        }

        void number(int si, int sl, int sc) {
            char c = s.charAt(i);
            char c1 = peek(1);
            if (c == '0' && (c1 == 'x' || c1 == 'X' || c1 == 'o' || c1 == 'O' || c1 == 'b' || c1 == 'B')) {
                adv(2);
                while (i < n && (Character.digit(s.charAt(i), 16) >= 0 || s.charAt(i) == '_')) adv();
            } else {
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '_')) adv();
                if (i < n && s.charAt(i) == '.') {
                    adv();
                    while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '_')) adv();
                }
                char e = peek(0);
                if ((e == 'e' || e == 'E')
                        && (Character.isDigit(peek(1)) || ((peek(1) == '+' || peek(1) == '-') && Character.isDigit(peek(2))))) {
                    adv(2);
                    while (i < n && Character.isDigit(s.charAt(i))) adv();
                }
                if (i < n && (s.charAt(i) == 'j' || s.charAt(i) == 'J')) adv();
            }
            emit(TokenType.NUMBER, s.substring(si, i), sl, sc);
        }

        void word(int si, int sl, int sc) {
            while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) adv();
            String w = s.substring(si, i);
            char nx = peek(0);
            if ((nx == '"' || nx == '\'') && w.length() <= 2 && isStringPrefix(w)) {
                string(si, sl, sc, i);
                return;
            }
            if ((w.equals("import") || w.equals("from")) && lastIsLogicalBreak() && depth == 0) {
                importLine(si, sl, sc);
                return;
            }
            emit(KEYWORDS.contains(w) ? TokenType.KEYWORD : TokenType.IDENTIFIER, w, sl, sc);
        }

        static boolean isStringPrefix(String w) {
            for (char ch : w.toCharArray()) {
                if ("rRuUbBfF".indexOf(ch) < 0) return false;
            }
            return true;
        }

        /** Scans the rest of a logical import line (honouring brackets, backslashes and comments). */
        void importLine(int si, int sl, int sc) {
            StringBuilder sb = new StringBuilder(s.substring(si, i));
            int d = 0;
            while (i < n) {
                char ch = s.charAt(i);
                if (ch == '\n' && d == 0) break;
                if (ch == '#') {
                    while (i < n && s.charAt(i) != '\n') adv();
                    continue;
                }
                if (ch == '\\' && (peek(1) == '\n' || (peek(1) == '\r' && peek(2) == '\n'))) {
                    adv(peek(1) == '\r' ? 3 : 2);
                    sb.append(' ');
                    continue;
                }
                if (ch == '(') d++;
                if (ch == ')' && d > 0) d--;
                sb.append(ch);
                adv();
            }
            emit(TokenType.INCLUDE, sb.toString().replaceAll("\\s+", " ").trim(), sl, sc);
        }

        /** {@code from} is the index where the literal (including any prefix) started; the cursor is at the quote. */
        void string(int si, int sl, int sc, int quotePos) {
            char q = s.charAt(quotePos);
            boolean triple = s.startsWith("" + q + q + q, quotePos);
            adv(quotePos - i + (triple ? 3 : 1));
            while (i < n) {
                char ch = s.charAt(i);
                if (ch == '\\') {
                    adv();
                    if (i < n) adv();
                    continue;
                }
                if (triple) {
                    if (ch == q && peek(1) == q && peek(2) == q) {
                        adv(3);
                        break;
                    }
                    adv();
                } else {
                    if (ch == q) {
                        adv();
                        break;
                    }
                    if (ch == '\n') break; // unterminated single-line string
                    adv();
                }
            }
            emit(TokenType.STRING, s.substring(si, i), sl, sc);
        }
    }
}
