package dev.me.master.skript.lang;

import java.util.List;

public final class Frame {

	public final TriggerItem.Section section;
	public List<TriggerItem> children;
	public int pc;
	public boolean entered;
	public java.util.Iterator<?> iterator;

	public Frame(TriggerItem.Section section) {
		this.section = section;
	}
}
