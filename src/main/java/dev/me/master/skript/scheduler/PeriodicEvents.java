package dev.me.master.skript.scheduler;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.util.TimeSpan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public final class PeriodicEvents {

	private record Entry(Trigger trigger, long periodTicks, long nextRunTick) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final Map<Trigger, Long> DEFINITIONS = new HashMap<>();
	private static final List<Entry> DEFERRED_REGISTRATIONS = new ArrayList<>();
	private static final Set<Trigger> DEFERRED_UNREGISTRATIONS = new HashSet<>();
	private static boolean registered;
	private static boolean sessionActive;
	private static boolean ticking;

	private PeriodicEvents() {
	}

	public static void register(Trigger trigger, TimeSpan period) {
		if (period.ticks() < 1)
			return;
		DEFINITIONS.put(trigger, period.ticks());
		if (!registered) {
			try {
				ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
				ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopSession());
			} catch (LinkageError e) {
				SkriptLogger.warn("Fabric lifecycle events unavailable; every-timers disabled (" + e + ")");
			}
			registered = true;
		}
		if (sessionActive) {
			Entry entry = new Entry(trigger, period.ticks(), currentTick() + period.ticks());
			if (ticking) {
				DEFERRED_REGISTRATIONS.removeIf(existing -> existing.trigger() == trigger);
				DEFERRED_REGISTRATIONS.add(entry);
			} else {
				ENTRIES.removeIf(existing -> existing.trigger() == trigger);
				ENTRIES.add(entry);
			}
		}
	}

	public static void startSession(MinecraftServer server) {
		ENTRIES.clear();
		DEFERRED_REGISTRATIONS.clear();
		DEFERRED_UNREGISTRATIONS.clear();
		sessionActive = true;
		long now = server.getTicks();
		for (Map.Entry<Trigger, Long> definition : DEFINITIONS.entrySet()) {
			ENTRIES.add(new Entry(definition.getKey(), definition.getValue(), now));
		}
	}

	public static void unregister(Trigger trigger) {
		DEFINITIONS.remove(trigger);
		if (ticking) {
			DEFERRED_REGISTRATIONS.removeIf(entry -> entry.trigger() == trigger);
			DEFERRED_UNREGISTRATIONS.add(trigger);
			return;
		}
		ENTRIES.removeIf(entry -> entry.trigger() == trigger);
	}

	private static void tick(MinecraftServer server) {
		if (!sessionActive)
			return;
		long now = server.getTicks();
		ticking = true;
		try {
			for (int i = 0; i < ENTRIES.size(); i++) {
				Entry entry = ENTRIES.get(i);
				if (DEFERRED_UNREGISTRATIONS.contains(entry.trigger()) || now < entry.nextRunTick())
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
		} finally {
			ticking = false;
			applyDeferredChanges();
		}
	}

	private static void applyDeferredChanges() {
		for (Trigger trigger : DEFERRED_UNREGISTRATIONS)
			ENTRIES.removeIf(entry -> entry.trigger() == trigger);
		for (Entry entry : DEFERRED_REGISTRATIONS) {
			if (!sessionActive || !DEFINITIONS.containsKey(entry.trigger()))
				continue;
			ENTRIES.removeIf(existing -> existing.trigger() == entry.trigger());
			ENTRIES.add(entry);
		}
		DEFERRED_UNREGISTRATIONS.clear();
		DEFERRED_REGISTRATIONS.clear();
	}

	private static void stopSession() {
		sessionActive = false;
		ENTRIES.clear();
		DEFERRED_REGISTRATIONS.clear();
		DEFERRED_UNREGISTRATIONS.clear();
	}

	private static long currentTick() {
		MinecraftServer server = CurrentServer.get();
		return server == null ? 0 : server.getTicks();
	}
}
