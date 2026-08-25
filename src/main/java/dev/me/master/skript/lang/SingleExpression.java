package dev.me.master.skript.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public abstract class SingleExpression<T> implements Expression<T> {

	private final Class<? extends T> type;

	protected SingleExpression(Class<? extends T> type) {
		this.type = type;
	}

	@Override
	public List<T> getValues(ExecContext context) {
		T value = compute(context);
		return value == null ? List.of() : List.of(value);
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends T> returnType() {
		return type;
	}

	protected abstract @Nullable T compute(ExecContext context);

	protected static @Nullable Object first(List<?> values) {
		return values.isEmpty() ? null : values.getFirst();
	}
}
