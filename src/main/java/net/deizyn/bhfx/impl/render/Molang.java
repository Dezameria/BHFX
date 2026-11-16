package net.deizyn.bhfx.impl.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The small part of Molang that animation keyframes use, compiled once into a tree that evaluates without allocating:
 * numbers, {@code + - * /}, unary {@code - !}, comparisons, {@code && ||}, {@code ?:}, parentheses;
 * {@code query.anim_time} / {@code q.anim_time} / {@code query.life_time} / {@code q.life_time} (the clip's time in
 * seconds), {@code math.pi}; {@code math.sin / cos} (degrees, as in Bedrock), {@code abs floor ceil round trunc sqrt exp
 * ln pow min max clamp lerp mod}. Anything else (other variables, {@code math.random}, statements) is an error at
 * compile time, so the caller can warn and use 0.
 */
public final class Molang {
    private Molang() {
    }

    /** A compiled expression of the clip's time. */
    @FunctionalInterface
    public interface Expr {
        double eval(double time);
    }

    /** Compile {@code text}; throws {@link IllegalArgumentException} for what this subset doesn't have. */
    public static Expr compile(String text) {
        Parser p = new Parser(tokens(text));
        Expr e = p.ternary();
        if (p.at < p.tokens.size()) {
            throw new IllegalArgumentException("unexpected '" + p.tokens.get(p.at) + "' in " + text);
        }
        return e;
    }

    /** The number {@code text} is, or null when it is an expression. */
    public static Double number(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int j = i + 1;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                if (j < s.length() && (s.charAt(j) == 'f' || s.charAt(j) == 'F')) {
                    j++; // 1.5f
                    out.add(s.substring(i, j - 1));
                } else {
                    out.add(s.substring(i, j));
                }
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '.')) {
                    j++;
                }
                out.add(s.substring(i, j).toLowerCase(Locale.ROOT));
                i = j;
            } else {
                String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                if (two.equals("<=") || two.equals(">=") || two.equals("==") || two.equals("!=") || two.equals("&&")
                        || two.equals("||")) {
                    out.add(two);
                    i += 2;
                } else if ("+-*/()?:,<>!".indexOf(c) >= 0) {
                    out.add(String.valueOf(c));
                    i++;
                } else {
                    throw new IllegalArgumentException("unsupported '" + c + "' in " + s);
                }
            }
        }
        return out;
    }

    private static final class Parser {
        final List<String> tokens;
        int at;

        Parser(List<String> tokens) {
            this.tokens = tokens;
        }

        String peek() {
            return at < tokens.size() ? tokens.get(at) : "";
        }

        boolean eat(String t) {
            if (peek().equals(t)) {
                at++;
                return true;
            }
            return false;
        }

        void expect(String t) {
            if (!eat(t)) {
                throw new IllegalArgumentException("expected '" + t + "' but found '" + peek() + "'");
            }
        }

        Expr ternary() {
            Expr c = or();
            if (eat("?")) {
                Expr a = ternary();
                expect(":");
                Expr b = ternary();
                return t -> c.eval(t) != 0.0 ? a.eval(t) : b.eval(t);
            }
            return c;
        }

        Expr or() {
            Expr l = and();
            while (eat("||")) {
                Expr a = l;
                Expr b = and();
                l = t -> a.eval(t) != 0.0 || b.eval(t) != 0.0 ? 1.0 : 0.0;
            }
            return l;
        }

        Expr and() {
            Expr l = compare();
            while (eat("&&")) {
                Expr a = l;
                Expr b = compare();
                l = t -> a.eval(t) != 0.0 && b.eval(t) != 0.0 ? 1.0 : 0.0;
            }
            return l;
        }

        Expr compare() {
            Expr l = sum();
            while (true) {
                String op = peek();
                if (!(op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || op.equals("==")
                        || op.equals("!="))) {
                    return l;
                }
                at++;
                Expr a = l;
                Expr b = sum();
                l = switch (op) {
                    case "<" -> t -> a.eval(t) < b.eval(t) ? 1.0 : 0.0;
                    case ">" -> t -> a.eval(t) > b.eval(t) ? 1.0 : 0.0;
                    case "<=" -> t -> a.eval(t) <= b.eval(t) ? 1.0 : 0.0;
                    case ">=" -> t -> a.eval(t) >= b.eval(t) ? 1.0 : 0.0;
                    case "==" -> t -> a.eval(t) == b.eval(t) ? 1.0 : 0.0;
                    default -> t -> a.eval(t) != b.eval(t) ? 1.0 : 0.0;
                };
            }
        }

        Expr sum() {
            Expr l = product();
            while (true) {
                if (eat("+")) {
                    Expr a = l;
                    Expr b = product();
                    l = t -> a.eval(t) + b.eval(t);
                } else if (eat("-")) {
                    Expr a = l;
                    Expr b = product();
                    l = t -> a.eval(t) - b.eval(t);
                } else {
                    return l;
                }
            }
        }

        Expr product() {
            Expr l = unary();
            while (true) {
                if (eat("*")) {
                    Expr a = l;
                    Expr b = unary();
                    l = t -> a.eval(t) * b.eval(t);
                } else if (eat("/")) {
                    Expr a = l;
                    Expr b = unary();
                    l = t -> {
                        double d = b.eval(t);
                        return d == 0.0 ? 0.0 : a.eval(t) / d;
                    };
                } else {
                    return l;
                }
            }
        }

        Expr unary() {
            if (eat("-")) {
                Expr a = unary();
                return t -> -a.eval(t);
            }
            if (eat("+")) {
                return unary();
            }
            if (eat("!")) {
                Expr a = unary();
                return t -> a.eval(t) == 0.0 ? 1.0 : 0.0;
            }
            return atom();
        }

        Expr atom() {
            String tok = peek();
            if (tok.isEmpty()) {
                throw new IllegalArgumentException("expression ends too early");
            }
            at++;
            if (tok.equals("(")) {
                Expr e = ternary();
                expect(")");
                return e;
            }
            Double n = number(tok);
            if (n != null) {
                double v = n;
                return t -> v;
            }
            if (eat("(")) {
                List<Expr> args = new ArrayList<>();
                if (!eat(")")) {
                    do {
                        args.add(ternary());
                    } while (eat(","));
                    expect(")");
                }
                return call(tok, args);
            }
            return switch (tok) {
                case "query.anim_time", "q.anim_time", "query.life_time", "q.life_time" -> t -> t;
                case "math.pi" -> t -> Math.PI;
                default -> throw new IllegalArgumentException("unsupported variable " + tok);
            };
        }

        private static Expr call(String name, List<Expr> a) {
            int n = a.size();
            Expr x = n > 0 ? a.get(0) : null;
            Expr y = n > 1 ? a.get(1) : null;
            Expr z = n > 2 ? a.get(2) : null;
            int want = switch (name) {
                case "math.sin", "math.cos", "math.abs", "math.floor", "math.ceil", "math.round", "math.trunc",
                        "math.sqrt", "math.exp", "math.ln" -> 1;
                case "math.pow", "math.min", "math.max", "math.mod" -> 2;
                case "math.clamp", "math.lerp" -> 3;
                default -> throw new IllegalArgumentException("unsupported function " + name);
            };
            if (n != want) {
                throw new IllegalArgumentException(name + " takes " + want + " values, not " + n);
            }
            return switch (name) {
                case "math.sin" -> t -> Math.sin(Math.toRadians(x.eval(t)));
                case "math.cos" -> t -> Math.cos(Math.toRadians(x.eval(t)));
                case "math.abs" -> t -> Math.abs(x.eval(t));
                case "math.floor" -> t -> Math.floor(x.eval(t));
                case "math.ceil" -> t -> Math.ceil(x.eval(t));
                case "math.round" -> t -> Math.round(x.eval(t));
                case "math.trunc" -> t -> (double) (long) x.eval(t);
                case "math.sqrt" -> t -> Math.sqrt(Math.max(0.0, x.eval(t)));
                case "math.exp" -> t -> Math.exp(x.eval(t));
                case "math.ln" -> t -> {
                    double v = x.eval(t);
                    return v > 0.0 ? Math.log(v) : 0.0;
                };
                case "math.pow" -> t -> Math.pow(x.eval(t), y.eval(t));
                case "math.min" -> t -> Math.min(x.eval(t), y.eval(t));
                case "math.max" -> t -> Math.max(x.eval(t), y.eval(t));
                case "math.mod" -> t -> {
                    double d = y.eval(t);
                    return d == 0.0 ? 0.0 : x.eval(t) % d;
                };
                case "math.clamp" -> t -> Math.max(y.eval(t), Math.min(z.eval(t), x.eval(t)));
                default -> t -> {
                    double from = x.eval(t);
                    return from + (y.eval(t) - from) * z.eval(t);
                };
            };
        }
    }
}
