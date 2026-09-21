package dev.glyphy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: glyphy <file.gly>");
            System.exit(64);
        }

        try {
            String source = Files.readString(Path.of(args[0]));
            List<Token> tokens = new Lexer(source).scanTokens();
            List<Ast.Statement> program = new Parser(tokens).parse();
            new Interpreter().execute(program);
        } catch (IOException e) {
            System.err.println("Could not read file: " + e.getMessage());
            System.exit(66);
        } catch (GlyphyException e) {
            System.err.println("Glyphy error: " + e.getMessage());
            System.exit(65);
        }
    }
}
