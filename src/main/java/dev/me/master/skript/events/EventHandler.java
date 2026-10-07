package dev.me.master.skript.events;

import dev.me.master.skript.lang.Trigger;

@FunctionalInterface
public interface EventHandler {

	void bind(Trigger trigger);

    default boolean canCancel() {
        return true;
    }
}
