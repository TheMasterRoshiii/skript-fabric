package dev.me.master.skript.lang;

import java.util.function.Function;

public final class ClassInfo<T> {

	private final String name;
	private final Class<T> type;
	private final Function<String, T> parser;
	private final Function<T, String> toString;

	private ClassInfo(String name, Class<T> type, Function<String, T> parser, Function<T, String> toString) {
		this.name = name;
		this.type = type;
		this.parser = parser;
		this.toString = toString;
	}

	public static <T> ClassInfo<T> of(String name, Class<T> type, Function<String, T> parser, Function<T, String> toString) {
		return new ClassInfo<>(name.toLowerCase(java.util.Locale.ROOT), type, parser, toString);
	}

	public String name() {
		return name;
	}

	public Class<T> type() {
		return type;
	}

	public T parse(String raw) {
		String trimmed = raw.trim();
		if (trimmed.isEmpty())
			return null;
		T parsed = parser.apply(trimmed);
		if (parsed == null && trimmed.endsWith("s") && trimmed.length() > 1)
			parsed = parser.apply(trimmed.substring(0, trimmed.length() - 1));
		return parsed;
	}

	public String toDisplayString(T value) {
		return toString.apply(value);
	}

	@Override
	public String toString() {
		return "ClassInfo(" + name + ")";
	}
}
