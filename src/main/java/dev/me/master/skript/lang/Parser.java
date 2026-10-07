package dev.me.master.skript.lang;
import dev.me.master.skript.lang.VariableExpression;
import dev.me.master.skript.lang.function.FunctionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public final class Parser {

	public final ParseState state;
	private int expressionDepth;

	public Parser(ParseState state) {
		this.state = state;
	}

	public @Nullable Expression<?> parseExpression(String source, Class<?> expected, boolean allowList) {
		String trimmed = source.trim();
		this.expressionDepth++;
		try {
			Expression<?> result = null;
			if (!trimmed.isEmpty()) {
				List<Expression<?>> elements = allowList ? this.parseList(trimmed, expected) : null;
				result = elements == null ? this.parseSingle(trimmed, expected) : new ListExpression(elements);
			}
			if (result == null) {
				this.state.rememberFailure(trimmed,
						"Cannot parse '" + trimmed + "'; expected a valid " + typeName(expected) + " expression");
			} else if (this.expressionDepth == 1) {
				this.state.clearFailure();
			}
			return result;
		} finally {
			this.expressionDepth--;
		}
	}

	private @Nullable List<Expression<?>> parseList(String source, Class<?> expected) {
		List<String> chunks = splitTopLevel(source, ',', true);
		if (chunks.size() < 2)
			return null;
		List<Expression<?>> parsed = new ArrayList<>(chunks.size());
		for (String chunk : chunks) {
			Expression<?> element = this.parseExpression(chunk, expected, false);
			if (element == null)
				return null;
			parsed.add(element);
		}
		return parsed;
	}

	Expression<?> parseSingle(String source, Class<?> expected) {
		String trimmed = source.trim();
		if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"))))
			return this.acceptExpected(
					new Literal<>(unescape(trimmed.substring(1, trimmed.length() - 1)), String.class), expected, trimmed);
		Expression<?> variable = tryParseVariable(trimmed);
		if (variable != null)
			return this.acceptExpected(variable, expected, trimmed);
		Expression<?> functionCall = FunctionRegistry.tryParseCall(trimmed, this);
		if (functionCall != null)
			return this.acceptExpected(functionCall, expected, trimmed);
		if (containsTopLevelOperator(trimmed)) {
			Expression<?> arithmetic = ArithmeticExpression.ArithmeticParser.tryParse(this, trimmed);
			if (arithmetic != null)
				return this.acceptExpected(arithmetic, expected, trimmed);
		}
		for (SyntaxRegistry.ExpressionEntry entry : SyntaxRegistry.expressions()) {
			SkriptPattern.MatchResult match = entry.pattern().match(trimmed);
			if (match == null)
				continue;
			Expression<?> built = entry.factory().create(this, entry.pattern(), match);
			Expression<?> accepted = this.acceptExpected(built, expected, trimmed);
			if (accepted != null)
				return accepted;
		}
		return this.acceptExpected(parseLiteral(trimmed, expected), expected, trimmed);
	}

	public VariableExpression parseVariable(String source) {
		Expression<?> parsed = tryParseVariable(source);
		return parsed instanceof VariableExpression variable ? variable : null;
	}

	private Expression<?> tryParseVariable(String source) {
		VariableExpression variable =
				VariableExpression.tryParse(source);
		if (variable == null)
			return null;
		String inner = source.trim();
		int separator = inner.indexOf("::");
		if (separator >= 0 && variable.isDynamic()) {
			String indexSource = inner.substring(separator + 2, inner.length() - 1).trim();
			if (indexSource.length() >= 2 && indexSource.startsWith("%") && indexSource.endsWith("%"))
				indexSource = indexSource.substring(1, indexSource.length() - 1).trim();
			Expression<?> index = parseExpression(indexSource, Object.class, false);
			if (index == null)
				return null;
			variable.bindIndexExpression(index);
		}
		return variable;
	}

	public @Nullable Expression<?> parseLiteral(String source, Class<?> expected) {
		String trimmed = source.trim();
		if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"))))
			return new Literal<>(unescape(trimmed.substring(1, trimmed.length() - 1)), String.class);
		if (expected != Object.class) {
			ClassInfo<?> info = Classes.byClass(expected);
			if (info != null) {
				Object typed = info.parse(trimmed);
				if (typed != null)
					return new Literal<>(castTo(info, typed), info.type());
			}
		}
		Number number = tryParseNumber(trimmed);
		if (number != null)
			return new Literal<>(number, Number.class);
		Boolean bool = tryParseBoolean(trimmed);
		if (bool != null)
			return new Literal<>(bool, Boolean.class);
		TimeSpanLiteral timeSpan = TimeSpanLiteral.tryParse(trimmed);
		if (timeSpan != null)
			return timeSpan;
		for (ClassInfo<?> info : Classes.parseOrder()) {
			Object parsed = info.parse(trimmed);
			if (parsed != null)
				return new Literal<>(parsed, info.type());
		}
		return null;
	}

	private @Nullable Expression<?> acceptExpected(
			@Nullable Expression<?> expression, Class<?> expected, String source) {
		if (expression == null || expected == Object.class || expression.returnType() == Object.class) {
			return expression;
		}
		if (expected.isAssignableFrom(expression.returnType())) {
			return expression;
		}
		this.state.rememberFailure(source, "Expected " + typeName(expected) + " expression, but '" + source
				+ "' produces " + typeName(expression.returnType()));
		return null;
	}

	private static String typeName(Class<?> type) {
		ClassInfo<?> info = Classes.byClass(type);
		return info == null ? "value" : info.name();
	}

	@SuppressWarnings("unchecked")
	private static Object castTo(ClassInfo<?> info, Object value) {
		return info.type().cast(value);
	}

	public static @Nullable Number tryParseNumber(String raw) {
		String trimmed = raw.trim();
		if (trimmed.isEmpty())
			return null;
		try {
			return Long.parseLong(trimmed);
		} catch (NumberFormatException ignored) {
		}
		try {
			double parsed = Double.parseDouble(trimmed);
			if (Double.isFinite(parsed))
				return parsed;
		} catch (NumberFormatException ignored) {
		}
		return null;
	}

	private static @Nullable Boolean tryParseBoolean(String raw) {
		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "true", "yes" -> Boolean.TRUE;
			case "false", "no" -> Boolean.FALSE;
			default -> null;
		};
	}

	public static String unescape(String quoted) {
		StringBuilder result = new StringBuilder(quoted.length());
		for (int i = 0; i < quoted.length(); i++) {
			char c = quoted.charAt(i);
			if (c == '\\' && i + 1 < quoted.length()) {
				result.append(quoted.charAt(i + 1));
				i++;
				continue;
			}
			result.append(c);
		}
		return result.toString();
	}

	public static List<String> splitTopLevel(String source, char delimiter, boolean alsoAnd) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int depthParens = 0;
		int depthBraces = 0;
		int depthBrackets = 0;
		char openQuote = 0;
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (openQuote != 0) {
				current.append(c);
				if (c == openQuote && source.charAt(i - 1) != '\\')
					openQuote = 0;
				continue;
			}
			switch (c) {
				case '"', '\'' -> {
					openQuote = c;
					current.append(c);
					continue;
				}
				case '(' -> depthParens++;
				case ')' -> depthParens--;
				case '{' -> depthBraces++;
				case '}' -> depthBraces--;
				case '[' -> depthBrackets++;
				case ']' -> depthBrackets--;
				default -> {
				}
			}
			boolean isDelimiter = c == delimiter && depthParens == 0 && depthBraces == 0 && depthBrackets == 0;
			if (isDelimiter) {
				parts.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		parts.add(current.toString());
		if (alsoAnd && parts.size() == 1)
			return splitOnWord(parts.getFirst(), "and");
		return normalize(parts);
	}

	private static List<String> splitOnWord(String source, String word) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int depthParens = 0;
		int depthBraces = 0;
		boolean inQuotes = false;
		int i = 0;
		while (i < source.length()) {
			char c = source.charAt(i);
			if (inQuotes) {
				current.append(c);
				if (c == '"')
					inQuotes = false;
				i++;
				continue;
			}
			if (c == '"') {
				inQuotes = true;
				current.append(c);
				i++;
				continue;
			}
			if (c == '(')
				depthParens++;
			else if (c == ')')
				depthParens--;
			else if (c == '{')
				depthBraces++;
			else if (c == '}')
				depthBraces--;
			if (depthParens == 0 && depthBraces == 0 && matchesWordAt(source, i, word)) {
				parts.add(current.toString());
				current.setLength(0);
				i += word.length();
				continue;
			}
			current.append(c);
			i++;
		}
		parts.add(current.toString());
		return normalize(parts);
	}

	private static boolean matchesWordAt(String source, int pos, String word) {
		if (pos > 0 && isWordChar(source.charAt(pos - 1)))
			return false;
		if (pos + word.length() >= source.length() && !source.substring(pos).equalsIgnoreCase(word))
			return false;
		if (!source.regionMatches(true, pos, word, 0, word.length()))
			return false;
		int end = pos + word.length();
		return end >= source.length() || !isWordChar(source.charAt(end));
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '-';
	}

	private static List<String> normalize(List<String> rawParts) {
		if (rawParts.size() == 1)
			return List.of();
		List<String> parts = new ArrayList<>(rawParts.size());
		for (String part : rawParts) {
			String trimmed = part.trim();
			if (trimmed.isEmpty())
				return List.of();
			parts.add(trimmed);
		}
		return parts;
	}

	private static boolean containsTopLevelOperator(String source) {
		int depthParens = 0;
		int depthBraces = 0;
		boolean inQuotes = false;
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (inQuotes) {
				if (c == '"' && source.charAt(i - 1) != '\\')
					inQuotes = false;
				continue;
			}
			switch (c) {
				case '"' -> inQuotes = true;
				case '(' -> depthParens++;
				case ')' -> depthParens--;
				case '{' -> depthBraces++;
				case '}' -> depthBraces--;
				default -> {
					if ((c == '+' || c == '-' || c == '*' || c == '/' || c == '^') && depthParens == 0 && depthBraces == 0
							&& !(c == '-' && (i == 0 || "+-*/^(".indexOf(source.charAt(i - 1)) >= 0)))
						return true;
				}
			}
		}
		return false;
	}
}
