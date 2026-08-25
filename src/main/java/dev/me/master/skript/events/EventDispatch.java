package dev.me.master.skript.events;

import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.util.SkriptLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class EventDispatch {

	private record Binding(Class<? extends ScriptEvent> type, Predicate<ScriptEvent> filter, Trigger trigger) {
	}

	private static final List<Binding> BINDINGS = new ArrayList<>();
	private static final Map<Class<? extends ScriptEvent>, Integer> LISTENERS_PER_CONCRETE_TYPE =
			new java.util.HashMap<>();
	private static final List<Class<? extends ScriptEvent>> CONCRETE_TYPES = List.of(
			ScriptEvent.PlayerJoin.class,
			ScriptEvent.PlayerQuit.class,
			ScriptEvent.Chat.class,
			ScriptEvent.Damage.class,
			ScriptEvent.Death.class,
			ScriptEvent.BlockBreak.class,
			ScriptEvent.BlockPlace.class,
			ScriptEvent.RightClickBlock.class,
			ScriptEvent.LeftClickBlock.class,
			ScriptEvent.RightClickEntity.class,
			ScriptEvent.LeftClickEntity.class,
			ScriptEvent.Respawn.class,
			ScriptEvent.Command.class,
			ScriptEvent.Periodic.class,
			ScriptEvent.ScriptLoad.class);

	private EventDispatch() {
	}

	public static void bind(Class<? extends ScriptEvent> type, Predicate<ScriptEvent> filter, Trigger trigger) {
		BINDINGS.add(new Binding(type, filter, trigger));
		recount();
	}

	public static void unbindAll(Trigger trigger) {
		BINDINGS.removeIf(binding -> binding.trigger() == trigger);
		recount();
	}

	private static void recount() {
		LISTENERS_PER_CONCRETE_TYPE.clear();
		for (Class<? extends ScriptEvent> concrete : CONCRETE_TYPES) {
			int count = 0;
			for (Binding binding : BINDINGS) {
				if (binding.type().isAssignableFrom(concrete))
					count++;
			}
			LISTENERS_PER_CONCRETE_TYPE.put(concrete, count);
		}
	}

	public static boolean hasListeners(Class<? extends ScriptEvent> type) {
		Integer count = LISTENERS_PER_CONCRETE_TYPE.get(type);
		return count != null && count > 0;
	}

	public static ExecContext fire(ScriptEvent event) {
		ExecContext context = new ExecContext(event);
		for (int i = 0; i < BINDINGS.size(); i++) {
			Binding binding = BINDINGS.get(i);
			if (!binding.type().isInstance(event) || !binding.filter().test(event))
				continue;
			if (!binding.trigger().script().isEnabled())
				continue;
			try {
				binding.trigger().run(context);
			} catch (RuntimeException e) {
				SkriptLogger.error("Error in trigger '" + binding.trigger().debugName()
						+ "' (" + binding.trigger().script().name() + ")", e);
			}
		}
		return context;
	}
}
