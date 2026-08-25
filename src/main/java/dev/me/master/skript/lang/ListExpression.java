package dev.me.master.skript.lang;

import java.util.ArrayList;
import java.util.List;

public final class ListExpression implements Expression<Object> {

	private final List<Expression<?>> elements;
	private final boolean single;

	public ListExpression(List<Expression<?>> elements) {
		this.elements = List.copyOf(elements);
		this.single = this.elements.size() == 1 && this.elements.getFirst().isSingle();
	}

	@Override
	public List<Object> getValues(ExecContext context) {
		List<Object> values = new ArrayList<>();
		for (Expression<?> element : elements)
			values.addAll(element.getValues(context));
		return values;
	}

	@Override
	public boolean isSingle() {
		return single;
	}

	@Override
	public Class<? extends Object> returnType() {
		return Object.class;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < elements.size(); i++) {
			if (i > 0)
				result.append(", ");
			result.append(elements.get(i));
		}
		return result.toString();
	}
}
