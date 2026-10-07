package dev.me.master.skript.lang;

import dev.me.master.skript.events.ScriptEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventValues {

	private record Key(Class<? extends ScriptEvent> eventClass, Class<?> type) {
	}

	private static final Map<Key, Extractor<?, ?>> EXTRACTORS = new HashMap<>();

	@FunctionalInterface
	public interface Extractor<E extends ScriptEvent, T> {
		@Nullable T extract(E event);
	}

	private EventValues() {
	}

	public static <E extends ScriptEvent, T> void register(Class<E> eventClass, Class<T> type, Extractor<E, T> extractor) {
		EXTRACTORS.put(new Key(eventClass, type), extractor);
	}

	public static boolean supports(Class<? extends ScriptEvent> eventClass, Class<?> type) {
		return find(eventClass, type) != null;
	}

	@SuppressWarnings("unchecked")
	public static <T> @Nullable T get(@Nullable ScriptEvent event, Class<T> type) {
		if (event == null) {
			return null;
		}
		Extractor<ScriptEvent, T> extractor = (Extractor<ScriptEvent, T>) find(event.getClass(), type);
		return extractor == null ? null : extractor.extract(event);
	}

	private static Extractor<?, ?> find(Class<? extends ScriptEvent> eventClass, Class<?> type) {
		Extractor<?, ?> exact = EXTRACTORS.get(new Key(eventClass, type));
		if (exact != null)
			return exact;
		for (Map.Entry<Key, Extractor<?, ?>> entry : EXTRACTORS.entrySet()) {
			if (entry.getKey().eventClass().isAssignableFrom(eventClass) && entry.getKey().type() == type)
				return entry.getValue();
		}
		return null;
	}

	public static List<Class<?>> supportedTypes(Class<? extends ScriptEvent> eventClass) {
		List<Class<?>> types = new ArrayList<>();
		for (Key key : EXTRACTORS.keySet()) {
			if (key.eventClass().isAssignableFrom(eventClass))
				types.add(key.type());
		}
		return types;
	}
}
