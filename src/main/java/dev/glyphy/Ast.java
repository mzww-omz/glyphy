package dev.glyphy;

import java.util.List;

final class Ast {
    private Ast() {}

    sealed interface Statement permits VarDecl, ExprStmt { }

    record VarDecl(Token name, Expression initializer) implements Statement { }
    record ExprStmt(Expression expression) implements Statement { }

    sealed interface Expression permits Literal, Variable, Unary, Binary, Get, Call { }

    record Literal(Object value) implements Expression { }
    record Variable(Token name) implements Expression { }
    record Unary(Token operator, Expression right) implements Expression { }
    record Binary(Expression left, Token operator, Expression right) implements Expression { }
    record Get(Expression object, Token name) implements Expression { }
    record Call(Expression callee, Token paren, List<Expression> arguments) implements Expression { }
}
