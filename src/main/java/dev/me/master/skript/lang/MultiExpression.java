package dev.me.master.skript.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class MultiExpression<T> implements Expression<T> {

	private final Class<? extends T> type;

	protected MultiExpression(Class<? extends T> type) {
		this.type = type;
	}

	@Override
	public abstract List<T> getValues(ExecContext context);

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public Class<? extends T> returnType() {
		return type;
	}
}
