package dev.me.master.skript.scheduler;
import dev.me.master.skript.lang.Continuation;
import dev.me.master.skript.util.SkriptLogger;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public final class Scheduler {

	private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>();
	private static volatile LongSupplier currentTickSupplier = () -> 0L;

	private record Task(long atTick, Continuation continuation) implements Comparable<Task> {

		@Override
		public int compareTo(Task other) {
			return Long.compare(atTick, other.atTick);
		}
	}

	private Scheduler() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearQueue());
	}

	public static void clearQueue() {
		QUEUE.clear();
	}

	public static void attach(MinecraftServer server) {
		currentTickSupplier = server::getTicks;
	}

	public static void scheduleResume(Continuation continuation, long delayTicks) {
		schedule(delayTicks, continuation);
	}

	public static void schedule(long delayTicks, Continuation continuation) {
		long delay = Math.max(1, delayTicks);
		long base = currentTickSupplier.getAsLong();
		if (!QUEUE.add(new Task(base + delay, continuation)))
			SkriptLogger.error("Failed to schedule delayed trigger continuation");
	}

	private static void tick() {
		if (QUEUE.isEmpty())
			return;
		long now = currentTickSupplier.getAsLong();
		while (!QUEUE.isEmpty() && QUEUE.peek().atTick <= now) {
			Task task = QUEUE.poll();
			task.continuation().resume();
		}
	}
}
