package com.azuki.calculatorfloat;

import java.util.Locale;

public final class ExpressionEngine {
    private ExpressionEngine() { }

    public static String calculate(String expression) {
        Parser parser = new Parser(expression);
        double value = parser.parse();
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("Math error");
        if (Math.abs(value) < 0.00000000005d) value = 0d;
        String result = String.format(Locale.US, "%.10f", value);
        while (result.contains(".") && result.endsWith("0")) result = result.substring(0, result.length() - 1);
        if (result.endsWith(".")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static final class Parser {
        private final String s;
        private int pos;
        Parser(String input) { s = input.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", ""); }
        double parse() {
            if (s.length() == 0) throw new IllegalArgumentException("Empty");
            double value = expression();
            if (pos != s.length()) throw new IllegalArgumentException("Invalid expression");
            return value;
        }
        double expression() {
            double value = term();
            while (true) {
                if (eat('+')) value += term();
                else if (eat('-')) value -= term();
                else return value;
            }
        }
        double term() {
            double value = power();
            while (true) {
                if (eat('*')) value *= power();
                else if (eat('/')) {
                    double divisor = power();
                    if (divisor == 0d) throw new IllegalArgumentException("Cannot divide by zero");
                    value /= divisor;
                } else return value;
            }
        }
        double power() {
            double value = unary();
            if (eat('^')) value = Math.pow(value, power());
            return value;
        }
        double unary() {
            if (eat('+')) return unary();
            if (eat('-')) return -unary();
            double value = atom();
            while (eat('%')) value /= 100d;
            return value;
        }
        double atom() {
            if (eat('(')) {
                double value = expression();
                if (!eat(')')) throw new IllegalArgumentException("Missing )");
                return value;
            }
            if (s.startsWith("sqrt(", pos)) {
                pos += 5;
                double value = expression();
                if (!eat(')') || value < 0) throw new IllegalArgumentException("Invalid square root");
                return Math.sqrt(value);
            }
            int start = pos;
            boolean dot = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') pos++;
                else if (c == '.' && !dot) { dot = true; pos++; }
                else break;
            }
            if (start == pos) throw new IllegalArgumentException("Number expected");
            return Double.parseDouble(s.substring(start, pos));
        }
        boolean eat(char c) {
            if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; }
            return false;
        }
    }
}
