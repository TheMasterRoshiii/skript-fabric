package dev.me.master.skript.lang;

import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;

public final class Continuation {

	private final ExecContext context;
	private final List<Frame> frames;
	private final Trigger trigger;

	Continuation(Trigger trigger, ExecContext context, List<Frame> frames) {
		this.trigger = trigger;
		this.context = context;
		this.frames = new ArrayList<>(frames);
	}

	public boolean belongsTo(SkriptScript script) {
		return trigger.script() == script;
	}

	public void resume() {
		if (frames.isEmpty() || !trigger.script().isEnabled())
			return;
		Executor executor = new Executor(trigger, context, new ArrayList<>(frames));
		try {
			executor.runAll();
		} catch (DelaySignal signal) {
			if (trigger.script().isEnabled())
				Scheduler.scheduleResume(executor.suspend(), signal.delayTicks());
		}
	}
}
