package dev.me.master.skript.lang.function;

import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;
import dev.me.master.skript.lang.Parser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FunctionRegistry {

	private static final Map<String, FunctionDefinition> FUNCTIONS = new HashMap<>();

	private FunctionRegistry() {
	}

	public static void register(FunctionDefinition definition) {
		FUNCTIONS.put(definition.name(), definition);
	}

	public static void unregisterScript(String scriptName) {
		FUNCTIONS.keySet().removeIf(name -> FUNCTIONS.get(name) != null && FUNCTIONS.get(name).script().name().equals(scriptName));
	}

	public static boolean exists(String name) {
		return FUNCTIONS.containsKey(name.toLowerCase(Locale.ROOT));
	}

	public static @Nullable FunctionDefinition get(String name) {
		return FUNCTIONS.get(name.toLowerCase(Locale.ROOT));
	}

	public static void clear() {
		FUNCTIONS.clear();
	}

	public static Expression<?> tryParseCall(String source, Parser parser) {
		int open = source.indexOf('(');
		if (open <= 0 || !source.endsWith(")"))
			return null;
		String name = source.substring(0, open).trim();
		if (!isIdentifier(name) || !exists(name))
			return null;
		String argumentSource = source.substring(open + 1, source.length() - 1).trim();
		List<String> chunks = argumentSource.isEmpty()
				? List.of()
				: Parser.splitTopLevel(argumentSource, ',', false);
		FunctionDefinition definition = get(name);
		if (chunks.size() > definition.parameters().size())
			return null;
		List<Expression<?>> arguments = new ArrayList<>(chunks.size());
		for (String chunk : chunks) {
			Expression<?> argument = parser.parseExpression(chunk, Object.class, true);
			if (argument == null)
				return null;
			arguments.add(argument);
		}
		return new ExprFunctionCall(definition, arguments);
	}

	private static boolean isIdentifier(String name) {
		if (name.isEmpty())
			return false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '-')
				return false;
		}
		return Character.isLetter(name.charAt(0)) || name.charAt(0) == '_';
	}
}
