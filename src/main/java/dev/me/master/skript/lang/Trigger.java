package dev.me.master.skript.lang;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.scheduler.Scheduler;
import java.util.List;

public final class Trigger {

	private final SkriptScript script;
	private final String debugName;
	private final int line;
	private final List<TriggerItem> body;

	public Trigger(SkriptScript script, String debugName, int line, List<TriggerItem> body) {
		this.script = script;
		this.debugName = debugName;
		this.line = line;
		this.body = List.copyOf(body);
	}

	public SkriptScript script() {
		return script;
	}

	public String debugName() {
		return debugName;
	}

	public int line() {
		return line;
	}

	public List<TriggerItem> body() {
		return body;
	}

	public void run(ExecContext context) {
		Executor executor = Executor.start(this, context);
		try {
			executor.runAll();
		} catch (DelaySignal signal) {
			if (script.isEnabled())
				Scheduler.scheduleResume(executor.suspend(), signal.delayTicks());
		}
	}
}
