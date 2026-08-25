package dev.me.master.skript.lang.function;

import dev.me.master.skript.lang.Expression;

import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.lang.TriggerItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FunctionDefinition {

	public record Parameter(String name, ClassInfo<?> type, @Nullable Expression<?> defaultValue) {
	}

	private final SkriptScript script;
	private final String name;
	private final List<Parameter> parameters;
	@Nullable
	private final ClassInfo<?> returnType;
	private final Trigger trigger;

	public FunctionDefinition(SkriptScript script, String name, List<Parameter> parameters,
			@Nullable ClassInfo<?> returnType, List<TriggerItem> body, int line) {
		this.script = script;
		this.name = name.toLowerCase(java.util.Locale.ROOT);
		this.parameters = List.copyOf(parameters);
		this.returnType = returnType;
		this.trigger = new Trigger(script, "function " + this.name, line, body);
	}

	public SkriptScript script() {
		return script;
	}

	public String name() {
		return name;
	}

	public List<Parameter> parameters() {
		return parameters;
	}

	public @Nullable ClassInfo<?> returnType() {
		return returnType;
	}

	public @Nullable Object invoke(List<Object> arguments) {
		if (arguments.size() > parameters.size())
			return null;
		ExecContext context = new ExecContext(null);
		for (int i = 0; i < parameters.size(); i++) {
			Object value = i < arguments.size() ? arguments.get(i) : null;
			if (value == null && parameters.get(i).defaultValue() != null)
				value = parameters.get(i).defaultValue().getValue(context);
			context.setLocal("\0arg:" + parameters.get(i).name(), value);
			context.setLocal("\0arg:" + (i + 1), value);
		}
		trigger.run(context);
		return context.returned() ? context.returnValue() : null;
	}
}
