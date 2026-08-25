package dev.me.master.skript.lang;

public final class LoopState {

	public final TriggerItem.Section owner;
	public java.util.Iterator<?> iterator;
	public Object currentValue;
	public int currentIndex;

	public LoopState(TriggerItem.Section owner, java.util.Iterator<?> iterator, Object firstValue) {
		this.owner = owner;
		this.iterator = iterator;
		this.currentValue = firstValue;
		this.currentIndex = 1;
	}
}
