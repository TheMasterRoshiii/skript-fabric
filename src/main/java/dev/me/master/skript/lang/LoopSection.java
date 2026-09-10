package dev.me.master.skript.lang;

import java.util.List;
import java.util.Iterator;

public final class LoopSection extends TriggerItem.Section {

	private final Expression<?> iterable;
	private final boolean indexNumbers;

	public LoopSection(int line, Expression<?> iterable, List<TriggerItem> body) {
		super(line, body);
		this.iterable = iterable;
		this.indexNumbers = iterable instanceof VariableExpression variable && variable.isListAll();
	}

	public boolean providesIndex() {
		return indexNumbers;
	}

	@Override
	protected boolean shouldEnter(ExecContext context, Frame frame) {
		Iterator<?> iterator = iterable.getValues(context).iterator();
		if (!iterator.hasNext())
			return false;
		Object first = iterator.next();
		context.loopStack().add(new LoopState(this, iterator, first));
		return true;
	}

	@Override
	protected boolean advance(ExecContext context, Frame frame) {
		List<LoopState> stack = context.loopStack();
		if (stack.isEmpty())
			return false;
		LoopState state = stack.getLast();
		if (!state.iterator.hasNext())
			return false;
		state.currentValue = state.iterator.next();
		state.currentIndex++;
		return true;
	}

	@Override
	protected void onFramePopped(ExecContext context) {
		removeOwnState(context);
	}

	private void removeOwnState(ExecContext context) {
		List<LoopState> stack = context.loopStack();
		for (int i = stack.size() - 1; i >= 0; i--) {
			if (stack.get(i).owner == this) {
				stack.remove(i);
				return;
			}
		}
	}

	@Override
	protected boolean isLoop() {
		return true;
	}
}
