package dev.codesentinel.engine.ast;

import dev.codesentinel.engine.lexer.Language;
import org.treesitter.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Language parser that produces normalized Abstract Syntax Tree (AST) representations
 * using Tree-sitter grammars with cross-platform native bindings.
 */
public class AstLanguageParser {

    private static final Set<String> TRIVIA_TYPES = Set.of(
            "comment", "line_comment", "block_comment",
            ";", "{", "}", "(", ")", "[", "]", ",", ".", ":",
            "\"", "'", "`", "\\n"
    );

    private static final Set<String> IDENTIFIER_TYPES = Set.of(
            "identifier", "type_identifier", "field_identifier", "property_identifier",
            "variable_name", "type_name", "method_name"
    );

    private static final Set<String> LITERAL_TYPES = Set.of(
            "number_literal", "integer_literal", "decimal_integer_literal", "hex_integer_literal",
            "float_literal", "decimal_floating_point_literal",
            "string_literal", "character_literal", "boolean_literal", "true", "false", "null", "nil"
    );

    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean available = false;
        try {
            TSParser p = new TSParser();
            p.setLanguage(new TreeSitterJava());
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        NATIVE_AVAILABLE = available;
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * Parses source code into a normalized AST root node.
     */
    public Optional<AstNode> parse(String fileName, String sourceCode, Language language) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return Optional.empty();
        }

        if (NATIVE_AVAILABLE) {
            try {
                TSLanguage tsLang = resolveLanguage(language);
                if (tsLang != null) {
                    TSParser parser = new TSParser();
                    parser.setLanguage(tsLang);
                    TSTree tree = parser.parseString(null, sourceCode);
                    if (tree != null) {
                        TSNode root = tree.getRootNode();
                        return Optional.ofNullable(convertNode(root, fileName));
                    }
                }
            } catch (Throwable t) {
                // Fall back to resilient token structural fallback
            }
        }

        return fallbackParse(fileName, sourceCode);
    }

    private TSLanguage resolveLanguage(Language language) {
        if (language == null) return null;
        return switch (language) {
            case JAVA -> new TreeSitterJava();
            case PYTHON -> new TreeSitterPython();
            case C -> new TreeSitterC();
            case CPP -> new TreeSitterCpp();
            case JAVASCRIPT -> new TreeSitterJavascript();
        };
    }

    private AstNode convertNode(TSNode node, String file) {
        if (node == null || node.isNull()) return null;

        String rawType = node.getType();
        if (TRIVIA_TYPES.contains(rawType)) {
            return null;
        }

        String normalizedType = normalizeType(rawType);
        TSPoint start = node.getStartPoint();
        TSPoint end = node.getEndPoint();

        int startLine = start != null ? start.getRow() + 1 : 1;
        int startCol = start != null ? start.getColumn() + 1 : 1;
        int endLine = end != null ? end.getRow() + 1 : startLine;
        int endCol = end != null ? end.getColumn() + 1 : startCol;

        List<AstNode> children = new ArrayList<>();
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode c = node.getChild(i);
            AstNode childNode = convertNode(c, file);
            if (childNode != null) {
                children.add(childNode);
            }
        }

        long hash = computeHash(normalizedType, children);
        int totalSize = 1;
        for (AstNode c : children) {
            totalSize += c.subtreeSize();
        }

        return new AstNode(normalizedType, file, startLine, endLine, startCol, endCol, hash, totalSize, children);
    }

    private String normalizeType(String rawType) {
        if (IDENTIFIER_TYPES.contains(rawType)) return "ID";
        if (LITERAL_TYPES.contains(rawType)) return "LIT";
        return rawType;
    }

    private long computeHash(String type, List<AstNode> children) {
        long h = 0xcbf29ce484222325L;
        byte[] bytes = type.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            h ^= b;
            h *= 0x100000001b3L;
        }
        for (AstNode c : children) {
            h ^= c.structuralHash();
            h *= 0x100000001b3L;
        }
        return h;
    }

    /**
     * Resilient structural fallback when native Tree-sitter libraries cannot be loaded.
     */
    private Optional<AstNode> fallbackParse(String fileName, String sourceCode) {
        String[] lines = sourceCode.split("\\R");
        List<AstNode> statementNodes = new ArrayList<>();
        long fileHash = 0xcbf29ce484222325L;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("/*")) {
                continue;
            }
            String kind = "stmt";
            if (trimmed.startsWith("if ") || trimmed.startsWith("if(")) kind = "if_stmt";
            else if (trimmed.startsWith("for ") || trimmed.startsWith("for(")) kind = "for_stmt";
            else if (trimmed.startsWith("while ") || trimmed.startsWith("while(")) kind = "while_stmt";
            else if (trimmed.startsWith("return ") || trimmed.startsWith("return;")) kind = "return_stmt";
            else if (trimmed.contains("=") && !trimmed.contains("==")) kind = "assign_stmt";
            else if (trimmed.contains("(") && trimmed.endsWith("{")) kind = "func_stmt";

            long lineHash = computeHash(kind, List.of());
            statementNodes.add(new AstNode(kind, fileName, i + 1, i + 1, 1, trimmed.length(), lineHash, 1, List.of()));
            fileHash ^= lineHash;
            fileHash *= 0x100000001b3L;
        }

        if (statementNodes.isEmpty()) return Optional.empty();
        return Optional.of(new AstNode("root", fileName, 1, lines.length, 1, 1, fileHash, statementNodes.size() + 1, statementNodes));
    }
}
