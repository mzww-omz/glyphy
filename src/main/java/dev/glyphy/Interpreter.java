package dev.glyphy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class Interpreter {
    private final Map<String, Object> globals = new HashMap<>();

    Interpreter() {
        // First Java interop surface. Imports will replace/extend these aliases later.
        globals.put("System", System.class);
    }

    void execute(List<Ast.Statement> statements) {
        for (Ast.Statement statement : statements) execute(statement);
    }

    private void execute(Ast.Statement statement) {
        switch (statement) {
            case Ast.VarDecl var -> globals.put(var.name().lexeme(), evaluate(var.initializer()));
            case Ast.ExprStmt expr -> evaluate(expr.expression());
        }
    }

    private Object evaluate(Ast.Expression expression) {
        return switch (expression) {
            case Ast.Literal literal -> literal.value();
            case Ast.Variable variable -> read(variable.name());
            case Ast.Unary unary -> evalUnary(unary);
            case Ast.Binary binary -> evalBinary(binary);
            case Ast.Get get -> evalGet(get);
            case Ast.Call call -> evalCall(call);
        };
    }

    private Object read(Token name) {
        if (!globals.containsKey(name.lexeme())) {
            throw runtimeError(name, "Undefined variable '" + name.lexeme() + "'.");
        }
        return globals.get(name.lexeme());
    }

    private Object evalUnary(Ast.Unary unary) {
        Object right = evaluate(unary.right());
        return switch (unary.operator().type()) {
            case MINUS -> negate(unary.operator(), right);
            case BANG -> !truthy(right);
            default -> throw runtimeError(unary.operator(), "Unsupported unary operator.");
        };
    }

    private Object evalBinary(Ast.Binary binary) {
        Object left = evaluate(binary.left());
        Object right = evaluate(binary.right());
        return switch (binary.operator().type()) {
            case PLUS -> plus(binary.operator(), left, right);
            case MINUS -> numeric(binary.operator(), left, right, '-');
            case STAR -> numeric(binary.operator(), left, right, '*');
            case SLASH -> divide(binary.operator(), left, right);
            case EQUAL_EQUAL -> equal(left, right);
            case BANG_EQUAL -> !equal(left, right);
            default -> throw runtimeError(binary.operator(), "Unsupported binary operator.");
        };
    }

    private Object evalGet(Ast.Get get) {
        Object target = evaluate(get.object());
        String name = get.name().lexeme();

        if (target == null) {
            throw runtimeError(get.name(), "Cannot access member '" + name + "' on nil.");
        }

        if (target instanceof Class<?> type) {
            try {
                Field field = type.getField(name);
                if (Modifier.isStatic(field.getModifiers())) {
                    return field.get(null);
                }
            } catch (NoSuchFieldException ignored) {
                // It may be a method; resolve it when called.
            } catch (IllegalAccessException e) {
                throw runtimeError(get.name(), "Java field '" + type.getName() + "." + name + "' is not accessible.");
            }
            return new JavaMethod(null, type, name, true, get.name());
        }

        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (NoSuchFieldException ignored) {
            return new JavaMethod(target, target.getClass(), name, false, get.name());
        } catch (IllegalAccessException e) {
            throw runtimeError(get.name(), "Java field '" + name + "' is not accessible.");
        }
    }

    private Object evalCall(Ast.Call call) {
        Object callee = evaluate(call.callee());
        List<Object> arguments = call.arguments().stream().map(this::evaluate).toList();

        if (callee instanceof JavaMethod method) {
            return invokeJava(method, arguments, call.paren());
        }

        throw runtimeError(call.paren(), "Expression is not callable.");
    }

    private Object invokeJava(JavaMethod member, List<Object> arguments, Token callSite) {
        Method best = null;
        int bestScore = Integer.MAX_VALUE;

        for (Method method : member.owner().getMethods()) {
            if (!method.getName().equals(member.name())) continue;
            if (Modifier.isStatic(method.getModifiers()) != member.isStatic()) continue;
            if (method.isVarArgs()) continue;
            if (method.getParameterCount() != arguments.size()) continue;

            int score = score(method.getParameterTypes(), arguments);
            if (score >= 0 && score < bestScore) {
                best = method;
                bestScore = score;
            }
        }

        if (best == null) {
            throw runtimeError(member.token(), "No matching Java method '" + member.owner().getName() + "." + member.name() + "'.");
        }

        try {
            Object[] converted = convert(best.getParameterTypes(), arguments);
            return best.invoke(member.receiver(), converted);
        } catch (IllegalAccessException e) {
            throw runtimeError(callSite, "Java method is not accessible: " + member.name() + ".");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            String message = cause == null ? e.getMessage() : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            throw runtimeError(callSite, "Java method threw " + message);
        }
    }

    private int score(Class<?>[] parameters, List<Object> arguments) {
        int total = 0;
        for (int i = 0; i < parameters.length; i++) {
            int score = score(parameters[i], arguments.get(i));
            if (score < 0) return -1;
            total += score;
        }
        return total;
    }

    private int score(Class<?> parameter, Object value) {
        if (value == null) return parameter.isPrimitive() ? -1 : 5;

        Class<?> boxed = box(parameter);
        if (boxed == value.getClass()) return 0;
        if (boxed.isAssignableFrom(value.getClass())) return 2;

        if (value instanceof Number && Number.class.isAssignableFrom(boxed)) {
            return 1;
        }
        return -1;
    }

    private Object[] convert(Class<?>[] parameters, List<Object> arguments) {
        List<Object> converted = new ArrayList<>(arguments.size());
        for (int i = 0; i < parameters.length; i++) {
            converted.add(convert(parameters[i], arguments.get(i)));
        }
        return converted.toArray();
    }

    private Object convert(Class<?> parameter, Object value) {
        if (!(value instanceof Number number)) return value;
        Class<?> boxed = box(parameter);
        if (boxed == Byte.class) return number.byteValue();
        if (boxed == Short.class) return number.shortValue();
        if (boxed == Integer.class) return number.intValue();
        if (boxed == Long.class) return number.longValue();
        if (boxed == Float.class) return number.floatValue();
        if (boxed == Double.class) return number.doubleValue();
        return value;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private Object negate(Token operator, Object value) {
        Number number = number(operator, value);
        if (number instanceof Long n) return -n;
        return -number.doubleValue();
    }

    private Object plus(Token operator, Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return numeric(operator, left, right, '+');
        if (left instanceof String || right instanceof String) return format(left) + format(right);
        throw runtimeError(operator, "'+' expects numbers or at least one string operand.");
    }

    private Object numeric(Token operator, Object left, Object right, char operation) {
        Number a = number(operator, left);
        Number b = number(operator, right);

        if (a instanceof Long la && b instanceof Long lb) {
            return switch (operation) {
                case '+' -> la + lb;
                case '-' -> la - lb;
                case '*' -> la * lb;
                default -> throw new IllegalStateException("Unknown numeric operation.");
            };
        }

        double da = a.doubleValue();
        double db = b.doubleValue();
        return switch (operation) {
            case '+' -> da + db;
            case '-' -> da - db;
            case '*' -> da * db;
            default -> throw new IllegalStateException("Unknown numeric operation.");
        };
    }

    private Object divide(Token operator, Object left, Object right) {
        Number a = number(operator, left);
        Number b = number(operator, right);
        double divisor = b.doubleValue();
        if (divisor == 0.0) throw runtimeError(operator, "Division by zero.");
        return a.doubleValue() / divisor;
    }

    private Number number(Token operator, Object value) {
        if (value instanceof Number number) return number;
        throw runtimeError(operator, "Operator expects a number.");
    }

    private boolean equal(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        return true;
    }

    private String format(Object value) {
        if (value == null) return "nil";
        if (value instanceof Double number && number == Math.rint(number)) {
            return Long.toString(number.longValue());
        }
        return value.toString();
    }

    private GlyphyException runtimeError(Token token, String message) {
        return new GlyphyException("[line " + token.line() + ", col " + token.column() + "] " + message);
    }

    private record JavaMethod(Object receiver, Class<?> owner, String name, boolean isStatic, Token token) { }
}
