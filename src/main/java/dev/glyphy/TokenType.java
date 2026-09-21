package dev.glyphy;

enum TokenType {
    HASH,
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    DOT,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    BANG,
    BANG_EQUAL,
    EQUAL,
    EQUAL_EQUAL,

    IDENTIFIER,
    STRING,
    NUMBER,

    TRUE,
    FALSE,
    NIL,

    NEWLINE,
    EOF
}
