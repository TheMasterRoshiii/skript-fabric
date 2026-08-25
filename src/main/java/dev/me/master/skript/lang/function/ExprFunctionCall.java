package dev.me.master.skript.lang.function;

import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;

import java.util.ArrayList;
import java.util.List;

public final class ExprFunctionCall implements Expression<Object> {

	private final FunctionDefinition definition;
	private final List<Expression<?>> arguments;

	public ExprFunctionCall(FunctionDefinition definition, List<Expression<?>> arguments) {
		this.definition = definition;
		this.arguments = List.copyOf(arguments);
	}

	@Override
	public List<Object> getValues(ExecContext context) {
		List<Object> evaluated = new ArrayList<>(arguments.size());
		for (Expression<?> argument : arguments)
			evaluated.addAll(argument.getValues(context));
		Object result = definition.invoke(evaluated);
		return result == null ? List.of() : List.of(result);
	}

	@Override
	public boolean isSingle() {
		return definition.returnType() != null;
	}

	@Override
	public Class<? extends Object> returnType() {
		ClassInfo<?> type = definition.returnType();
		return type == null ? Object.class : type.type();
	}

	@Override
	public String toString() {
		return definition.name() + "(...)";
	}
}
