package dev.me.master.skript.lang;

import java.util.List;

public abstract sealed class TriggerItem permits TriggerItem.Statement, TriggerItem.Section {

	private final int line;

	protected TriggerItem(int line) {
		this.line = line;
	}

	public int line() {
		return line;
	}

	abstract Flow executeStep(Executor executor, ExecContext context);

	public abstract non-sealed static class Statement extends TriggerItem {

		protected Statement(int line) {
			super(line);
		}

		@Override
		final Flow executeStep(Executor executor, ExecContext context) {
			return execute(context);
		}

		protected abstract Flow execute(ExecContext context);
	}

	public abstract non-sealed static class Section extends TriggerItem {

		private final List<TriggerItem> children;

		protected Section(int line, List<TriggerItem> children) {
			super(line);
			this.children = List.copyOf(children);
		}

		public final List<TriggerItem> children() {
			return children;
		}

		@Override
		final Flow executeStep(Executor executor, ExecContext context) {
			executor.push(this);
			return Flow.NORMAL;
		}

		protected List<TriggerItem> selectChildren(ExecContext context) {
			return children;
		}

		protected boolean shouldEnter(ExecContext context, Frame frame) {
			return true;
		}

		protected boolean advance(ExecContext context, Frame frame) {
			return false;
		}

		protected void onFramePopped(ExecContext context) {
		}

		protected boolean isLoop() {
			return false;
		}
	}
}
