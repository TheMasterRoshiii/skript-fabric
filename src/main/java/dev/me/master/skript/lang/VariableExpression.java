package dev.me.master.skript.lang;

import dev.me.master.skript.variables.Variables;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class VariableExpression implements Expression<Object> {

	private final String name;
	private final boolean local;
	private final boolean allIndices;
	@Nullable
	private final String indexSuffix;
	@Nullable
	private Expression<?> indexExpression;

	private VariableExpression(String name, boolean local, @Nullable String indexSuffix) {
		this.name = name;
		this.local = local;
		this.indexSuffix = indexSuffix;
		this.allIndices = indexSuffix != null && indexSuffix.equals("*");
	}

	public static @Nullable VariableExpression tryParse(String source) {
		String trimmed = source.trim();
		if (trimmed.length() < 3 || !trimmed.startsWith("{") || !trimmed.endsWith("}"))
			return null;
		if (trimmed.indexOf('{') != 0 || trimmed.lastIndexOf('}') != trimmed.length() - 1)
			return null;
		String inner = trimmed.substring(1, trimmed.length() - 1).trim();
		if (inner.isEmpty() || inner.contains("{"))
			return null;
		boolean local = inner.startsWith("_");
		if (local)
			inner = inner.substring(1).trim();
		if (inner.isEmpty())
			return null;
		int separator = inner.indexOf("::");
		if (separator >= 0) {
			String list = inner.substring(0, separator).trim();
			String index = inner.substring(separator + 2).trim();
			if (list.isEmpty())
				return null;
			if (!index.equals("*") && !isPositiveInt(index) && !index.contains("%"))
				return null;
			return new VariableExpression(list.toLowerCase(java.util.Locale.ROOT), local, index);
		}
		if (inner.contains("%"))
			return null;
		return new VariableExpression(inner.toLowerCase(java.util.Locale.ROOT), local, null);
	}

	private static boolean isPositiveInt(String raw) {
		for (int i = 0; i < raw.length(); i++) {
			if (!Character.isDigit(raw.charAt(i)))
				return false;
		}
		return !raw.isEmpty();
	}

	public String name() {
		return name;
	}

	public boolean isLocal() {
		return local;
	}

	public boolean isListAll() {
		return allIndices;
	}

	public String storageKey(int runtimeIndex) {
		if (indexSuffix == null)
			return name;
		return name + "::" + effectiveSuffix(runtimeIndex);
	}

	public String storageKey(ExecContext context) {
		if (indexSuffix == null)
			return name;
		if (!allIndices && indexExpression == null)
			return name + "::" + indexSuffix;
		return name + "::" + effectiveSuffix(context);
	}

	private String effectiveSuffix(int runtimeIndex) {
		if (allIndices)
			return String.valueOf(runtimeIndex);
		if (indexExpression == null)
			return indexSuffix;
		throw new AssertionError("Dynamic index requires an execution context");
	}

	private String effectiveSuffix(ExecContext context) {
		if (indexExpression == null)
			return indexSuffix == null ? "" : indexSuffix;
		Object raw = indexExpression.getObjectValue(context);
		if (raw instanceof Number number)
			return String.valueOf(number.longValue());
		return raw == null ? "" : Classes.toStringValue(raw);
	}

	public String keyPrefix() {
		return name + "::";
	}

	@Override
	public List<Object> getValues(ExecContext context) {
		if (!allIndices) {
			String key = indexExpression != null
					? name + "::" + effectiveSuffix(context)
					: (indexSuffix == null ? name : name + "::" + indexSuffix);
			Object value = resolve(context, key);
			return value == null ? List.of() : List.of(value);
		}
		List<String> keys = keys(context);
		List<Object> values = new ArrayList<>(keys.size());
		for (String key : keys)
			values.add(resolve(context, key));
		values.removeIf(java.util.Objects::isNull);
		return values;
	}

	public List<String> keys(ExecContext context) {
		if (!allIndices && local) {
			List<String> matches = context.localNamesMatching(keyPrefix());
			matches.sort((a, b) -> Long.compare(suffix(a), suffix(b)));
			return matches;
		}
		if (local) {
			List<String> matches = context.localNamesMatching(keyPrefix());
			matches.sort((a, b) -> Long.compare(suffix(a), suffix(b)));
			return matches;
		}
		if (!allIndices && indexSuffix == null)
			return List.of(name);
		return Variables.matchingKeys(keyPrefix());
	}

	private static long suffix(String key) {
		int sep = key.lastIndexOf("::");
		if (sep < 0)
			return Long.MAX_VALUE;
		try {
			return Long.parseLong(key.substring(sep + 2));
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	private Object resolve(ExecContext context, String storageKey) {
		return local ? context.getLocal(storageKey) : Variables.get(storageKey);
	}

	@Override
	public @Nullable Object getValue(ExecContext context) {
		List<Object> values = getValues(context);
		return values.isEmpty() ? null : values.getFirst();
	}

	void bindIndexExpression(Expression<?> expression) {
		this.indexExpression = expression;
	}

	public boolean isDynamic() {
		return indexExpression != null || (indexSuffix != null && !allIndices && indexSuffix.contains("%"));
	}

	@Override
	public boolean isSingle() {
		return !allIndices;
	}

	@Override
	public Class<? extends Object> returnType() {
		return Object.class;
	}

	@Override
	public String toString() {
		return "{" + (local ? "_" : "") + name + (indexSuffix == null ? "" : "::" + indexSuffix) + "}";
	}
}
