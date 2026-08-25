package dev.me.master.skript.scheduler;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.util.TimeSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public final class PeriodicEvents {

	private record Entry(Trigger trigger, long periodTicks, long nextRunTick) {
		private Entry advance(long now) {
			return new Entry(trigger, periodTicks, now + periodTicks);
		}
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final Map<Trigger, Long> DEFINITIONS = new java.util.HashMap<>();
	private static boolean registered;

	private PeriodicEvents() {
	}

	public static void register(Trigger trigger, TimeSpan period) {
		if (period.ticks() < 1)
			return;
		DEFINITIONS.put(trigger, period.ticks());
		if (!registered) {
			try {
				ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
				ServerLifecycleEvents.SERVER_STOPPING.register(server -> ENTRIES.clear());
			} catch (LinkageError e) {
				SkriptLogger.warn("Fabric lifecycle events unavailable; every-timers disabled (" + e + ")");
			}
			registered = true;
		}
	}

	public static void startSession() {
		ENTRIES.clear();
		long now = 0;
		for (Map.Entry<Trigger, Long> definition : DEFINITIONS.entrySet()) {
			ENTRIES.add(new Entry(definition.getKey(), definition.getValue(), now));
		}
	}

		public static void unregister(Trigger trigger) {
		DEFINITIONS.remove(trigger);
		ENTRIES.removeIf(entry -> entry.trigger() == trigger);
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTicks();
		for (int i = 0; i < ENTRIES.size(); i++) {
			Entry entry = ENTRIES.get(i);
			if (now < entry.nextRunTick())
				continue;
			ENTRIES.set(i, new Entry(entry.trigger(), entry.periodTicks(), now + entry.periodTicks()));
			if (!entry.trigger().script().isEnabled())
				continue;
			try {
				entry.trigger().run(new ExecContext(
						new ScriptEvent.Periodic(entry.periodTicks())));
			} catch (RuntimeException e) {
				SkriptLogger.error("Error in periodic trigger '" + entry.trigger().debugName() + "'", e);
			}
		}
	}
}
