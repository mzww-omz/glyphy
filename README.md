# Glyphy

Glyphy is an experimental small programming language with a symbol-oriented syntax and direct JVM/Java interoperability.

The first implementation is deliberately tiny and dependency-free. It is written in Java and currently supports:

- variable declarations with `#`
- strings, integers, decimal numbers, booleans and `nil`
- arithmetic with `+`, `-`, `*`, `/`
- equality with `==` and `!=`
- unary `-` and `!`
- variable references
- member access with `.`
- Java field access and method calls (initially exposed through `java.lang.System` as `System`)

## Example

```gly
# message = "Hello, Glyphy!"
# answer = 6 * 7

System.out.println(message)
System.out.println(answer)
```

`print(...)` is intentionally not a Glyphy builtin. The language starts by using Java interop for output instead of inventing a parallel standard-library API.

## Run

Requires Java 21+.

```sh
./scripts/run.sh examples/hello.gly
```

## Test

```sh
./scripts/test.sh
```

## Current architecture

```text
source
  -> lexer
  -> tokens
  -> parser
  -> AST
  -> interpreter
  -> Java reflection bridge
```

The goal of this first milestone is not to freeze the whole language design. It is to make Glyphy real enough that future syntax and JVM interop decisions can be tested in a working implementation.
