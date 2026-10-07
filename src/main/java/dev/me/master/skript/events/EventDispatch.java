package dev.me.master.skript.events;

import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.util.SkriptLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class EventDispatch {

	private record Binding(Class<? extends ScriptEvent> type, Predicate<ScriptEvent> filter, Trigger trigger) {
	}

	private static final List<Binding> BINDINGS = new ArrayList<>();
	private static final Map<Class<? extends ScriptEvent>, List<Binding>> BINDINGS_BY_EVENT =
			new HashMap<>();
	private static final List<Class<? extends ScriptEvent>> CONCRETE_TYPES = List.of(
			ScriptEvent.PlayerJoin.class,
			ScriptEvent.PlayerQuit.class,
			ScriptEvent.Chat.class,
			ScriptEvent.ItemConsume.class,
            ScriptEvent.ItemUse.class,
            ScriptEvent.TotemPop.class,
            ScriptEvent.EquipmentChange.class,
            ScriptEvent.SleepStart.class,
            ScriptEvent.SleepStop.class,
            ScriptEvent.WorldChange.class,
            ScriptEvent.EntityLoad.class,
            ScriptEvent.EntityUnload.class,
            ScriptEvent.ServerStart.class,
            ScriptEvent.ServerStop.class,
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
		rebuildIndex();
	}

	public static void unbindAll(Trigger trigger) {
		BINDINGS.removeIf(binding -> binding.trigger() == trigger);
		rebuildIndex();
	}

	private static void rebuildIndex() {
		BINDINGS_BY_EVENT.clear();
		for (Class<? extends ScriptEvent> concrete : CONCRETE_TYPES) {
			List<Binding> bindings = new ArrayList<>();
			for (Binding binding : BINDINGS) {
				if (binding.type().isAssignableFrom(concrete))
					bindings.add(binding);
			}
			BINDINGS_BY_EVENT.put(concrete, List.copyOf(bindings));
		}
	}

	public static boolean hasListeners(Class<? extends ScriptEvent> type) {
		List<Binding> bindings = BINDINGS_BY_EVENT.get(type);
		return bindings != null && !bindings.isEmpty();
	}

	public static ExecContext fire(ScriptEvent event) {
		ExecContext result = new ExecContext(event);
		List<Binding> bindings = BINDINGS_BY_EVENT.get(event.getClass());
		if (bindings == null)
			return result;
		boolean cancelled = false;
		for (Binding binding : bindings) {
			if (!binding.trigger().script().isEnabled())
				continue;
			ExecContext context = new ExecContext(event);
			if (cancelled)
				context.cancel();
			try {
				if (!binding.filter().test(event))
					continue;
				binding.trigger().run(context);
			} catch (RuntimeException e) {
				SkriptLogger.error("Error in trigger '" + binding.trigger().debugName()
						+ "' (" + binding.trigger().script().name() + ")", e);
			} finally {
                context.closeCancellation();
			}
			if (context.isCancelled()) {
				cancelled = true;
				result.cancel();
			}
		}
		return result;
	}
}
