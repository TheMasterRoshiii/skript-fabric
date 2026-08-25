package dev.me.master.skript.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Literal<T> implements Expression<T> {

	private final List<T> values;
	private final boolean single;
	private final Class<? extends T> type;

	public Literal(T value, Class<? extends T> type) {
		this.values = List.of(value);
		this.single = true;
		this.type = type;
	}

	public Literal(List<T> values, Class<? extends T> type) {
		this.values = List.copyOf(values);
		this.single = values.size() == 1;
		this.type = type;
	}

	public T value() {
		return values.getFirst();
	}

	public List<T> values() {
		return values;
	}

	@Override
	public List<T> getValues(ExecContext context) {
		return values;
	}

	@Override
	public @Nullable T getValue(ExecContext context) {
		return values.isEmpty() ? null : values.getFirst();
	}

	@Override
	public boolean isSingle() {
		return single;
	}

	@Override
	public Class<? extends T> returnType() {
		return type;
	}

	@Override
	public String toString() {
		if (values.size() != 1)
			return values.toString();
		T value = values.getFirst();
		if (value instanceof String s)
			return "\"" + s + "\"";
		return Classes.toStringValue(value);
	}
}
