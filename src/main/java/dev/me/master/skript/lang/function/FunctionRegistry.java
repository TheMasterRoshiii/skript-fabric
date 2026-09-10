package dev.me.master.skript.lang.function;

import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.script.SkriptScript;
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

	public static @Nullable FunctionDefinition register(FunctionDefinition definition) {
		return FUNCTIONS.put(definition.name(), definition);
	}

	public static void restore(FunctionDefinition current, @Nullable FunctionDefinition previous) {
		if (FUNCTIONS.get(current.name()) != current)
			return;
		if (previous == null)
			FUNCTIONS.remove(current.name());
		else
			FUNCTIONS.put(current.name(), previous);
	}

	public static void unregisterScript(String scriptName) {
		FUNCTIONS.entrySet().removeIf(entry -> entry.getValue().script().name().equals(scriptName));
	}

	public static void unregisterScript(SkriptScript script) {
		FUNCTIONS.entrySet().removeIf(entry -> entry.getValue().script() == script);
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
		int required = 0;
		for (FunctionDefinition.Parameter parameter : definition.parameters()) {
			if (parameter.defaultValue() == null)
				required++;
		}
		if (chunks.size() < required || chunks.size() > definition.parameters().size())
			return null;
		List<Expression<?>> arguments = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			FunctionDefinition.Parameter parameter = definition.parameters().get(i);
			Expression<?> argument = parser.parseExpression(chunks.get(i), parameter.type().type(), false);
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
