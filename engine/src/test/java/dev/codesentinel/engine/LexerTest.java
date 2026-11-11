package dev.codesentinel.engine;

import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.lexer.LexerFactory;
import dev.codesentinel.engine.lexer.SourceToken;
import dev.codesentinel.engine.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** FR-PLAG-01..07: tokenizer behaviour per language, incl. the cases a comment-stripping regex gets wrong. */
class LexerTest {

    static List<SourceToken> lex(Language l, String src) {
        return LexerFactory.forLanguage(l).tokenize(src, "t");
    }

    static String texts(List<SourceToken> t) {
        return t.stream().map(SourceToken::text).collect(Collectors.joining(" "));
    }

    static List<TokenType> types(List<SourceToken> t) {
        return t.stream().map(SourceToken::type).toList();
    }

    @Test
    void simpleDeclarationHasTypeIdentifierAssignNumberSemicolon() {
        var t = lex(Language.C, "int myVar = 10;");
        assertEquals(List.of(TokenType.KEYWORD, TokenType.IDENTIFIER, TokenType.OPERATOR, TokenType.NUMBER, TokenType.PUNCT), types(t));
        assertEquals("int myVar = 10 ;", texts(t));
    }

    @Test
    void emptyInputGivesNoTokens() {
        for (Language l : Language.values()) assertTrue(lex(l, "").isEmpty(), l.name());
    }

    @Test
    void commentsAreRemovedButDelimitersInsideStringsSurvive() {
        var t = lex(Language.C, "int a; // line\n/* block\n over lines */ char* s = \"// not /* a comment\"; int b;");
        assertEquals("int a ; char * s = \"// not /* a comment\" ; int b ;", texts(t));
        assertEquals(1, t.stream().filter(x -> x.type() == TokenType.STRING).count());
    }

    @Test
    void escapedQuoteDoesNotEndTheString() {
        var t = lex(Language.C, "printf(\"say \\\"hi\\\" // still string\"); x;");
        assertEquals(1, t.stream().filter(x -> x.type() == TokenType.STRING).count());
        assertEquals("x", t.get(t.size() - 2).text());
    }

    @Test
    void charLiteralWithEscapedQuote() {
        var t = lex(Language.C, "char c = '\\''; int d;");
        assertEquals(TokenType.CHAR, t.get(3).type());
        assertEquals("'\\''", t.get(3).text());
        assertEquals("d", t.get(t.size() - 2).text());
    }

    @Test
    void unterminatedBlockCommentConsumesToEndWithoutThrowing() {
        var t = lex(Language.C, "int a; /* never closed\nint b;");
        assertEquals("int a ;", texts(t));
    }

    @Test
    void preprocessorIsTokenizedNotDeleted() {
        var t = lex(Language.C, "#include <stdio.h>\n#define MAX 10\nint x = MAX;\n");
        assertEquals(TokenType.INCLUDE, t.get(0).type());
        assertEquals("stdio.h", t.get(0).text());
        assertEquals("#define", t.get(1).text());
        assertEquals(TokenType.KEYWORD, t.get(1).type());
        assertEquals("MAX", t.get(2).text());
        assertEquals("10", t.get(3).text());
    }

    @Test
    void lineNumbersAreTracked() {
        var t = lex(Language.C, "int a;\n\n  int b;\n");
        SourceToken b = t.stream().filter(x -> x.text().equals("b")).findFirst().orElseThrow();
        assertEquals(3, b.startLine());
        assertEquals(7, b.startCol());
    }

    @Test
    void cppRawStringAndDigitSeparator() {
        var t = lex(Language.CPP, "auto s = R\"x(a // b \" c)x\"; int n = 1'000'000;");
        assertEquals(1, t.stream().filter(x -> x.type() == TokenType.STRING).count());
        assertTrue(texts(t).contains("1'000'000"));
    }

    @Test
    void javaTextBlockAnnotationAndImport() {
        String src = "import java.util.List;\n@Override public String f() { String s = \"\"\"\n  has \"quotes\" // and slashes\n  \"\"\"; return s; }";
        var t = lex(Language.JAVA, src);
        assertEquals(TokenType.INCLUDE, t.get(0).type());
        assertEquals("import java.util.List", t.get(0).text());
        assertEquals("@Override", t.get(1).text());
        assertEquals(1, t.stream().filter(x -> x.type() == TokenType.STRING).count());
        assertEquals("return", t.get(t.size() - 4).text());
    }

    @Test
    void javascriptTemplateRegexAndDivision() {
        var t = lex(Language.JAVASCRIPT, "const s = `a ${ b + `n${c}` } // not comment`; const r = /ab+\\/c/g.test(s); const q = a / b / c;");
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.STRING || x.type() == TokenType.REGEX).count());
        assertEquals(1, t.stream().filter(x -> x.type() == TokenType.REGEX).count());
        assertEquals(2, t.stream().filter(x -> x.text().equals("/")).count()); // real divisions are operators
    }

    @Test
    void javascriptSingleQuotedStringsAreStrings() {
        var t = lex(Language.JAVASCRIPT, "let a = 'it\\'s'; // end");
        assertEquals(TokenType.STRING, t.get(3).type());
        assertEquals(5, t.size());
    }

    @Test
    void pythonIndentationProducesIndentAndDedent() {
        String src = "def f(x):\n    if x:\n        return 1\n    return 2\n";
        var t = lex(Language.PYTHON, src);
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.INDENT).count());
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.DEDENT).count());
        // INDENT/DEDENT are balanced and the stream ends with NEWLINE then DEDENT(s)
        assertEquals(TokenType.NEWLINE, t.get(t.size() - 2).type());
    }

    @Test
    void pythonMultilineStringsHashInsideStringsAndComments() {
        String src = "x = \"\"\"line one # not comment\nline two\n\"\"\"  # real comment\ny = 'a#b'\n";
        var t = lex(Language.PYTHON, src);
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.STRING).count());
        assertFalse(texts(t).contains("real"));
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.NEWLINE).count());
    }

    @Test
    void pythonNewlinesInsideBracketsAreIgnored() {
        var t = lex(Language.PYTHON, "x = [1,\n     2,\n     3]\ny = 4\n");
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.NEWLINE).count());
        assertEquals(0, t.stream().filter(x -> x.type() == TokenType.INDENT).count());
    }

    @Test
    void pythonImportsAndPrefixedStrings() {
        var t = lex(Language.PYTHON, "import sys\nfrom os import (path,\n   sep)\ns = rb'\\x00' + f\"{x}!\"\n");
        assertEquals(TokenType.INCLUDE, t.get(0).type());
        assertEquals("import sys", t.get(0).text());
        assertEquals("from os import (path, sep)", t.get(2).text());
        assertEquals(2, t.stream().filter(x -> x.type() == TokenType.STRING).count());
    }

    @Test
    void blankAndCommentOnlyLinesDoNotAffectPythonIndentation() {
        var t = lex(Language.PYTHON, "if a:\n\n    # comment\n    b = 1\n");
        assertEquals(1, t.stream().filter(x -> x.type() == TokenType.INDENT).count());
    }

    @Test
    void unsupportedExtensionIsReportedNotGuessed() {
        assertTrue(LexerFactory.forFileName("logo.png").isEmpty());
        assertTrue(LexerFactory.forFileName("Makefile").isEmpty());
        assertTrue(LexerFactory.forFileName("src/Main.java").isPresent());
        assertEquals(Language.CPP, Language.fromFileName("a/b/x.CPP").orElseThrow());
    }

    @Test
    void neverThrowsOnGarbage() {
        String junk = "\u0000\u0001 \"unterminated\n'x\n`tpl ${ { \n#\n@\\\n/*";
        for (Language l : Language.values()) assertDoesNotThrow(() -> lex(l, junk), l.name());
    }
}
