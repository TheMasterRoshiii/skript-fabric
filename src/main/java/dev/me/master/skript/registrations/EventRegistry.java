package dev.me.master.skript.registrations;

import dev.me.master.skript.events.EventHandler;
import dev.me.master.skript.lang.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class EventRegistry {

	private record Entry(Pattern pattern, EventHandlerFactory factory) {
	}

	@FunctionalInterface
	public interface EventHandlerFactory {
		EventHandler create(String matchedName, Parser parser);
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private EventRegistry() {
	}

	public static void register(String regex, EventHandlerFactory factory) {
		ENTRIES.add(new Entry(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), factory));
	}

	public static EventHandler match(String eventName, Parser parser) {
		for (Entry entry : ENTRIES) {
			if (entry.pattern().matcher(eventName).matches())
				return entry.factory().create(eventName, parser);
		}
		return null;
	}
}
