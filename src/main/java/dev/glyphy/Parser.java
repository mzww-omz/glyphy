package dev.glyphy;

import java.util.ArrayList;
import java.util.List;

final class Parser {
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Ast.Statement> parse() {
        List<Ast.Statement> statements = new ArrayList<>();
        skipNewlines();
        while (!isAtEnd()) {
            statements.add(statement());
            if (!check(TokenType.EOF) && !check(TokenType.NEWLINE)) {
                throw error(peek(), "Expected end of line.");
            }
            skipNewlines();
        }
        return statements;
    }

    private Ast.Statement statement() {
        if (match(TokenType.HASH)) return variableDeclaration();
        return new Ast.ExprStmt(expression());
    }

    private Ast.Statement variableDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expected variable name after '#'.");
        consume(TokenType.EQUAL, "Expected '=' after variable name.");
        return new Ast.VarDecl(name, expression());
    }

    private Ast.Expression expression() {
        return equality();
    }

    private Ast.Expression equality() {
        Ast.Expression expr = addition();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token op = previous();
            Ast.Expression right = addition();
            expr = new Ast.Binary(expr, op, right);
        }
        return expr;
    }

    private Ast.Expression addition() {
        Ast.Expression expr = multiplication();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            Ast.Expression right = multiplication();
            expr = new Ast.Binary(expr, op, right);
        }
        return expr;
    }

    private Ast.Expression multiplication() {
        Ast.Expression expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token op = previous();
            Ast.Expression right = unary();
            expr = new Ast.Binary(expr, op, right);
        }
        return expr;
    }

    private Ast.Expression unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token op = previous();
            return new Ast.Unary(op, unary());
        }
        return call();
    }

    private Ast.Expression call() {
        Ast.Expression expr = primary();

        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER, "Expected member name after '.'.");
                expr = new Ast.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Ast.Expression finishCall(Ast.Expression callee) {
        List<Ast.Expression> args = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                args.add(expression());
            } while (match(TokenType.COMMA));
        }
        Token paren = consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments.");
        return new Ast.Call(callee, paren, args);
    }

    private Ast.Expression primary() {
        if (match(TokenType.FALSE)) return new Ast.Literal(false);
        if (match(TokenType.TRUE)) return new Ast.Literal(true);
        if (match(TokenType.NIL)) return new Ast.Literal(null);
        if (match(TokenType.NUMBER, TokenType.STRING)) return new Ast.Literal(previous().literal());
        if (match(TokenType.IDENTIFIER)) return new Ast.Variable(previous());
        if (match(TokenType.LEFT_PAREN)) {
            Ast.Expression expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expected ')' after expression.");
            return expr;
        }
        throw error(peek(), "Expected expression.");
    }

    private void skipNewlines() {
        while (match(TokenType.NEWLINE)) { }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return type == TokenType.EOF;
        return peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private GlyphyException error(Token token, String message) {
        return new GlyphyException("[line " + token.line() + ", col " + token.column() + "] " + message);
    }
}
