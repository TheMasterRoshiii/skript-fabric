package dev.me.master.skript.lang;

import java.util.ArrayList;
import java.util.List;

public final class Continuation {

	private final ExecContext context;
	private final List<Frame> frames;

	Continuation(ExecContext context, List<Frame> frames) {
		this.context = context;
		this.frames = new ArrayList<>(frames);
	}

	public void resume() {
		if (frames.isEmpty())
			return;
		Executor executor = new Executor(context, new ArrayList<>(frames));
		executor.runAll();
	}
}
