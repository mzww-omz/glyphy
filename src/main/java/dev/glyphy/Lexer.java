package dev.glyphy;

import java.util.ArrayList;
import java.util.List;

final class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;
    private int startColumn = 1;

    Lexer(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            startColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '#' -> add(TokenType.HASH);
            case '(' -> add(TokenType.LEFT_PAREN);
            case ')' -> add(TokenType.RIGHT_PAREN);
            case ',' -> add(TokenType.COMMA);
            case '.' -> add(TokenType.DOT);
            case '+' -> add(TokenType.PLUS);
            case '-' -> add(TokenType.MINUS);
            case '*' -> add(TokenType.STAR);
            case '!' -> add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
            case '=' -> add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
            case '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    add(TokenType.SLASH);
                }
            }
            case ' ', '\r', '\t' -> { }
            case '\n' -> {
                add(TokenType.NEWLINE);
                line++;
                column = 1;
            }
            case '"' -> string();
            default -> {
                if (isDigit(c)) number();
                else if (isIdentifierStart(c)) identifier();
                else throw error("Unexpected character '" + c + "'.");
            }
        }
    }

    private void identifier() {
        while (isIdentifierPart(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = switch (text) {
            case "true" -> TokenType.TRUE;
            case "false" -> TokenType.FALSE;
            case "nil" -> TokenType.NIL;
            default -> TokenType.IDENTIFIER;
        };
        add(type);
    }

    private void number() {
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        String text = source.substring(start, current);
        Object value;
        if (text.contains(".")) {
            value = Double.parseDouble(text);
        } else {
            value = Long.parseLong(text);
        }
        add(TokenType.NUMBER, value);
    }

    private void string() {
        StringBuilder value = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            char c = advance();
            if (c == '\n') {
                line++;
                column = 1;
            }
            if (c == '\\' && !isAtEnd()) {
                char escaped = advance();
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> escaped;
                });
            } else {
                value.append(c);
            }
        }
        if (isAtEnd()) throw error("Unterminated string.");
        advance();
        add(TokenType.STRING, value.toString());
    }

    private char advance() {
        char c = source.charAt(current++);
        column++;
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        column++;
        return true;
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }

    private void add(TokenType type) {
        add(type, null);
    }

    private void add(TokenType type, Object literal) {
        tokens.add(new Token(type, source.substring(start, current), literal, line, startColumn));
    }

    private GlyphyException error(String message) {
        return new GlyphyException("[line " + line + ", col " + startColumn + "] " + message);
    }
}
