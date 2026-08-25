package dev.me.master.skript.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Classes {

	private static final Map<String, ClassInfo<?>> BY_NAME = new HashMap<>();
	private static final Map<Class<?>, ClassInfo<?>> BY_CLASS = new HashMap<>();
	private static final List<ClassInfo<?>> PARSE_ORDER = new ArrayList<>();
	private static final List<ComparatorEntry> COMPARATORS = new ArrayList<>();
	private static final List<ConverterEntry> CONVERTERS = new ArrayList<>();

	private Classes() {
	}

	public static <T> void register(ClassInfo<T> info) {
		BY_NAME.put(info.name(), info);
		BY_CLASS.put(info.type(), info);
		PARSE_ORDER.add(info);
	}

	public static ClassInfo<?> byName(String name) {
		return BY_NAME.get(name.toLowerCase(Locale.ROOT));
	}

	@SuppressWarnings("unchecked")
	public static <T> ClassInfo<T> byClass(Class<T> type) {
		ClassInfo<?> exact = BY_CLASS.get(type);
		if (exact != null)
			return (ClassInfo<T>) exact;
		for (Map.Entry<Class<?>, ClassInfo<?>> entry : BY_CLASS.entrySet()) {
			if (entry.getKey().isAssignableFrom(type))
				return (ClassInfo<T>) entry.getValue();
		}
		return null;
	}

	public static List<ClassInfo<?>> parseOrder() {
		return PARSE_ORDER;
	}

	@FunctionalInterface
	public interface Comparator<L, R> {
		Relation compare(L left, R right);
	}

	private record ComparatorEntry(Class<?> left, Class<?> right, Comparator<?, ?> comparator) {
	}

	public static <L, R> void registerComparator(Class<L> left, Class<R> right, Comparator<L, R> comparator) {
		COMPARATORS.add(new ComparatorEntry(left, right, comparator));
	}

	@SuppressWarnings("unchecked")
	public static Relation compare(Object left, Object right) {
		if (left == right || left.equals(right))
			return Relation.EQUAL;
		Comparator<Object, Object> direct = findComparatorByInstance(left, right);
		if (direct != null)
			return direct.compare(left, right);
		Comparator<Object, Object> reversed = findComparatorByInstance(right, left);
		if (reversed != null)
			return reversed.compare(right, left).mirrored();
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Comparator<Object, Object> cast(Comparator<?, ?> comparator) {
		return (Comparator<Object, Object>) comparator;
	}

	private static Comparator<Object, Object> findComparatorByInstance(Object left, Object right) {
		for (ComparatorEntry entry : COMPARATORS) {
			if (entry.left().isInstance(left) && entry.right().isInstance(right))
				return cast(entry.comparator());
		}
		return null;
	}

	@FunctionalInterface
	public interface Converter<F, T> {
		T convert(F from);
	}

	private record ConverterEntry(Class<?> from, Class<?> to, Converter<?, ?> converter) {
	}

	public static <F, T> void registerConverter(Class<F> from, Class<T> to, Converter<F, T> converter) {
		CONVERTERS.add(new ConverterEntry(from, to, converter));
	}

	@SuppressWarnings("unchecked")
	public static <T> T convert(Object value, Class<T> target) {
		if (value == null)
			return null;
		if (target.isInstance(value))
			return (T) value;
		List<Object> frontier = new ArrayList<>();
		frontier.add(value);
		int depth = 0;
		while (!frontier.isEmpty() && depth < 3) {
			List<Object> next = new ArrayList<>();
			for (Object current : frontier) {
				for (ConverterEntry entry : CONVERTERS) {
					if (!entry.from().isInstance(current) || !entry.to().isAssignableFrom(target))
						continue;
					Object converted = ((Converter<Object, Object>) entry.converter()).convert(current);
					if (converted == null)
						continue;
					if (target.isInstance(converted))
						return (T) converted;
					next.add(converted);
				}
			}
			frontier = next;
			depth++;
		}
		return null;
	}

	public static String toStringValue(Object value) {
		if (value instanceof String s)
			return s;
		for (ClassInfo<?> info : PARSE_ORDER) {
			if (info.type().isInstance(value)) {
				@SuppressWarnings("unchecked")
				ClassInfo<Object> typed = (ClassInfo<Object>) info;
				return typed.toDisplayString(value);
			}
		}
		return String.valueOf(value);
	}
}
