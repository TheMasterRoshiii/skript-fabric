package dev.me.master.skript.lang;

import dev.me.master.skript.events.ScriptEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExecContext {

	private final @Nullable ScriptEvent event;
	private final Map<String, Object> locals;
	private final List<LoopState> loopStack = new ArrayList<>();
	private boolean cancelled;
	private @Nullable Object returnValue;
	private boolean returned;

	public ExecContext(@Nullable ScriptEvent event) {
		this.event = event;
		this.locals = new HashMap<>();
	}

	public @Nullable ScriptEvent event() {
		return event;
	}

	public boolean hasEvent() {
		return event != null;
	}

	public void setLocal(String name, @Nullable Object value) {
		if (value == null)
			locals.remove(name);
		else
			locals.put(name, value);
	}

	public @Nullable Object getLocal(String name) {
		return locals.get(name);
	}

	public boolean hasLocal(String name) {
		return locals.containsKey(name);
	}

	public List<String> localNamesMatching(String prefix) {
		List<String> matches = new ArrayList<>();
		for (String key : locals.keySet()) {
			if (key.startsWith(prefix))
				matches.add(key);
		}
		return matches;
	}

	public List<LoopState> loopStack() {
		return loopStack;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void cancel() {
		cancelled = true;
	}

	public void setReturnValue(@Nullable Object value) {
		this.returnValue = value;
		this.returned = true;
	}

	public boolean returned() {
		return returned;
	}

	public @Nullable Object returnValue() {
		return returnValue;
	}
}
