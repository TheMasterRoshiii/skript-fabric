package dev.me.master.skript.lang;

import java.util.Iterator;

public final class LoopState {

	public final TriggerItem.Section owner;
	public Iterator<?> iterator;
	public Object currentValue;
	public int currentIndex;

	public LoopState(TriggerItem.Section owner, Iterator<?> iterator, Object firstValue) {
		this.owner = owner;
		this.iterator = iterator;
		this.currentValue = firstValue;
		this.currentIndex = 1;
	}
}
