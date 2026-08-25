package dev.me.master.skript.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class EventExpression<T> extends SingleExpression<T> {

	private final boolean optional;

	public EventExpression(Class<? extends T> type) {
		this(type, false);
	}

	public EventExpression(Class<? extends T> type, boolean optional) {
		super(type);
		this.optional = optional;
	}

	@Override
	protected @Nullable T compute(ExecContext context) {
		if (!context.hasEvent())
			return null;
		return EventValues.get(context.event(), returnType());
	}

	public boolean isOptional() {
		return optional;
	}
}
