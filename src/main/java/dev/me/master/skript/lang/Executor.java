package dev.me.master.skript.lang;

import java.util.ArrayList;
import java.util.List;

public final class Executor {

	private final ExecContext context;
	private final List<Frame> frames;

	Executor(ExecContext context, List<Frame> frames) {
		this.context = context;
		this.frames = frames;
	}

	public static Executor start(Trigger trigger, ExecContext context) {
		Executor executor = new Executor(context, new ArrayList<>());
		executor.frames.add(new Frame(new RootSection(trigger)));
		return executor;
	}

	public ExecContext context() {
		return context;
	}

	void push(TriggerItem.Section section) {
		frames.add(new Frame(section));
	}

	public void runAll() {
		while (!frames.isEmpty()) {
			step();
		}
	}

	private void step() {
		Frame frame = frames.getLast();
		TriggerItem.Section section = frame.section;
		if (!frame.entered) {
			frame.entered = true;
			frame.children = section.selectChildren(context);
			if (!section.shouldEnter(context, frame)) {
				popFrame();
				return;
			}
		}
		List<TriggerItem> children = frame.children;
		if (frame.pc < children.size()) {
			TriggerItem child = children.get(frame.pc);
			frame.pc++;
			Flow flow = child.executeStep(this, context);
			switch (flow) {
				case NORMAL -> {
				}
				case EXIT_LOOP -> unwindLoops(1);
				case EXIT_SECTION -> popFrame();
				case STOP_TRIGGER, RETURN -> clearAll();
			}
			return;
		}
		if (section.advance(context, frame)) {
			frame.pc = 0;
			return;
		}
		popFrame();
	}

	private void popFrame() {
		Frame frame = frames.removeLast();
		frame.section.onFramePopped(context);
	}

	private void unwindLoops(int count) {
		int remaining = count;
		while (!frames.isEmpty() && remaining > 0) {
			boolean wasLoop = frames.getLast().section.isLoop();
			popFrame();
			if (wasLoop)
				remaining--;
		}
	}

	private void clearAll() {
		while (!frames.isEmpty()) {
			popFrame();
		}
	}

	public Continuation suspend() {
		return new Continuation(context, frames);
	}

	private static final class RootSection extends TriggerItem.Section {

		RootSection(Trigger trigger) {
			super(trigger.line(), trigger.body());
		}
	}
}
