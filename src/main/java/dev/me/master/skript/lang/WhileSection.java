package dev.me.master.skript.lang;

import java.util.List;

public final class WhileSection extends TriggerItem.Section {

	private final Condition condition;

	public WhileSection(int line, Condition condition, List<TriggerItem> body) {
		super(line, body);
		this.condition = condition;
	}

	@Override
	protected boolean shouldEnter(ExecContext context, Frame frame) {
		return Boolean.TRUE.equals(condition.check(context));
	}

	@Override
	protected boolean advance(ExecContext context, Frame frame) {
		return Boolean.TRUE.equals(condition.check(context));
	}

	@Override
	protected boolean isLoop() {
		return true;
	}
}
