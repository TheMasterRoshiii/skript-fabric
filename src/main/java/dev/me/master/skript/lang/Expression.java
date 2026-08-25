package dev.me.master.skript.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Expression<T> {

	List<T> getValues(ExecContext context);

	default @Nullable T getValue(ExecContext context) {
		List<T> values = getValues(context);
		return values.isEmpty() ? null : values.getFirst();
	}

	@SuppressWarnings("unchecked")
	default List<Object> getObjectValues(ExecContext context) {
		return (List<Object>) (List<?>) getValues(context);
	}

	default Object getObjectValue(ExecContext context) {
		List<Object> values = getObjectValues(context);
		return values.isEmpty() ? null : values.getFirst();
	}

	boolean isSingle();

	Class<? extends T> returnType();

	@Override
	String toString();
}
