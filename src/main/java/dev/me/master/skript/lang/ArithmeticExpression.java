package dev.me.master.skript.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BinaryOperator;

public final class ArithmeticExpression implements Expression<Number> {

	final Expression<?> left;
	final Expression<?> right;
	private final Op op;

	public enum Op {
		ADD("+", Double::sum),
		SUBTRACT("-", (a, b) -> a - b),
		MULTIPLY("*", (a, b) -> a * b),
		DIVIDE("/", (a, b) -> b == 0 ? Double.NaN : a / b),
		POWER("^", Math::pow);

		private final String symbol;
		private final BinaryOperator<Double> fn;

		Op(String symbol, BinaryOperator<Double> fn) {
			this.symbol = symbol;
			this.fn = fn;
		}
	}

	public ArithmeticExpression(Expression<?> left, Expression<?> right, Op op) {
		this.left = left;
		this.right = right;
		this.op = op;
	}

	@Override
	public List<Number> getValues(ExecContext context) {
		List<Number> result = new java.util.ArrayList<>(1);
		for (Number l : numeric(left, context)) {
			for (Number r : numeric(right, context)) {
				result.add(op.fn.apply(l.doubleValue(), r.doubleValue()));
			}
		}
		return result;
	}

	private static List<Number> numeric(Expression<?> expression, ExecContext context) {
		List<Number> numbers = new java.util.ArrayList<>();
		for (Object value : expression.getValues(context)) {
			if (value instanceof Number number)
				numbers.add(number);
		}
		return numbers;
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends Number> returnType() {
		return Number.class;
	}

	@Override
	public String toString() {
		return left + " " + op.symbol + " " + right;
	}

	static final class ArithmeticParser {

		private final Parser parser;
		private final String source;
		private int pos;

		private ArithmeticParser(Parser parser, String source) {
			this.parser = parser;
			this.source = source;
		}

		private static final int MAX_DEPTH = 64;

		static @Nullable Expression<?> tryParse(Parser parser, String source) {
			ArithmeticParser arithmetic = new ArithmeticParser(parser, source);
			try {
				Expression<?> parsed = arithmetic.parseSum(0);
				arithmetic.skipSpaces();
				if (arithmetic.pos != source.length())
					return null;
				if (!numericTree(parsed))
					return null;
				return parsed;
			} catch (ArithmeticSyntaxException e) {
				return null;
			}
		}

		private static boolean numericTree(Expression<?> expression) {
			if (expression instanceof Literal<?> literal)
				return literal.value() instanceof Number;
			if (expression instanceof ArithmeticExpression arithmetic)
				return numericTree(arithmetic.left) && numericTree(arithmetic.right);
			if (expression instanceof UnaryMinus minus)
				return numericTree(minus.operand);
			return Number.class.isAssignableFrom(expression.returnType());
		}

		private static final class ArithmeticSyntaxException extends RuntimeException {
		}

		private Expression<?> parseSum(int depth) {
			if (depth > MAX_DEPTH)
				throw new ArithmeticSyntaxException();
			Expression<?> left = parseProduct(depth);
			while (true) {
				skipSpaces();
				int save = pos;
				if (peek('+')) {
					pos++;
					left = new ArithmeticExpression(left, parseProduct(depth + 1), ArithmeticExpression.Op.ADD);
				} else if (peek('-') && !previousCharIsOperatorOrStart(save)) {
					pos++;
					left = new ArithmeticExpression(left, parseProduct(depth + 1), ArithmeticExpression.Op.SUBTRACT);
				} else {
					pos = save;
					return left;
				}
			}
		}

		private Expression<?> parseProduct(int depth) {
			if (depth > MAX_DEPTH)
				throw new ArithmeticSyntaxException();
			Expression<?> left = parseUnary(depth);
			while (true) {
				skipSpaces();
				int save = pos;
				if (peek('*')) {
					pos++;
					left = new ArithmeticExpression(left, parseUnary(depth + 1), ArithmeticExpression.Op.MULTIPLY);
				} else if (peek('/')) {
					pos++;
					left = new ArithmeticExpression(left, parseUnary(depth + 1), ArithmeticExpression.Op.DIVIDE);
				} else {
					pos = save;
					return left;
				}
			}
		}

		private Expression<?> parseUnary(int depth) {
			if (depth > MAX_DEPTH)
				throw new ArithmeticSyntaxException();
			skipSpaces();
			if (peek('-')) {
				pos++;
				Expression<?> operand = parseUnary(depth + 1);
				return new UnaryMinus(operand);
			}
			if (peek('(')) {
				pos++;
				Expression<?> inner = parseSum(depth + 1);
				skipSpaces();
				if (!peek(')'))
					throw new ArithmeticSyntaxException();
				pos++;
				return inner;
			}
			Expression<?> atom = parseAtom();
			if (atom == null)
				throw new ArithmeticSyntaxException();
			return atom;
		}

		private @Nullable Expression<?> parseAtom() {
			int start = pos;
			int depthParens = 0;
			int depthBraces = 0;
			boolean inQuotes = false;
			while (pos < source.length()) {
				char c = source.charAt(pos);
				if (inQuotes) {
					if (c == '"' && source.charAt(pos - 1) != '\\')
						inQuotes = false;
					pos++;
					continue;
				}
				switch (c) {
					case '"' -> inQuotes = true;
					case '(' -> depthParens++;
					case '{' -> depthBraces++;
					case '}' -> depthBraces--;
					case ')' -> {
						if (depthParens == 0)
							return finishAtom(start);
						depthParens--;
					}
					default -> {
						if ("+-*/^".indexOf(c) >= 0 && depthParens == 0 && depthBraces == 0 && !isUnaryAt(pos))
							return finishAtom(start);
					}
				}
				pos++;
			}
			return finishAtom(start);
		}

		private @Nullable Expression<?> finishAtom(int start) {
			String chunk = source.substring(start, pos).trim();
			if (chunk.isEmpty())
				return null;
			return parser.parseSingle(chunk, Number.class);
		}

		private boolean isUnaryAt(int position) {
			for (int i = position - 1; i >= 0; i--) {
				char previous = source.charAt(i);
				if (!Character.isWhitespace(previous))
					return "+-*/^(".indexOf(previous) >= 0 || previous == '{';
			}
			return true;
		}

		private void skipSpaces() {
			while (pos < source.length() && Character.isWhitespace(source.charAt(pos)))
				pos++;
		}

		private boolean peek(char expected) {
			return pos < source.length() && source.charAt(pos) == expected;
		}

		private boolean previousCharIsOperatorOrStart(int position) {
			for (int i = position - 1; i >= 0; i--) {
				char c = source.charAt(i);
				if (!Character.isWhitespace(c))
					return "+-*/^(".indexOf(c) >= 0 || i == 0;
			}
			return true;
		}
	}

	private static final class UnaryMinus implements Expression<Number> {

		final Expression<?> operand;

		UnaryMinus(Expression<?> operand) {
			this.operand = operand;
		}

		@Override
		public List<Number> getValues(ExecContext context) {
			List<Number> values = new java.util.ArrayList<>();
			for (Object value : operand.getValues(context)) {
				if (value instanceof Number number)
					values.add(-number.doubleValue());
			}
			return values;
		}

		@Override
		public boolean isSingle() {
			return true;
		}

		@Override
		public Class<? extends Number> returnType() {
			return Number.class;
		}

		@Override
		public String toString() {
			return "-" + operand;
		}
	}
}
