package dev.me.master.skript.lang;

import dev.me.master.skript.util.TimeSpan;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeSpanLiteral implements Expression<TimeSpan> {

	private static final Pattern UNIT = Pattern.compile("(\\d+)\\s*(ticks?|seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|months?)");

	private final TimeSpan value;

	private TimeSpanLiteral(TimeSpan value) {
		this.value = value;
	}

	public TimeSpan value() {
		return value;
	}

	public static @Nullable TimeSpanLiteral tryParse(String raw) {
		String lowered = raw.toLowerCase(java.util.Locale.ROOT).replace(" and ", " ");
		if (!lowered.matches("[\\d\\s]+[\\d\\sa-z]*"))
			return null;
		Matcher matcher = UNIT.matcher(lowered);
		long ticks = 0;
		int matchedEnd = -1;
		while (matcher.find()) {
			long amount;
			try {
				amount = Long.parseLong(matcher.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
			ticks += switch (matcher.group(2)) {
				case "tick", "ticks" -> amount;
				case "second", "seconds", "sec", "secs" -> Math.multiplyExact(amount, 20);
				case "minute", "minutes", "min", "mins" -> Math.multiplyExact(amount, 20 * 60);
				case "hour", "hours", "hr", "hrs" -> Math.multiplyExact(amount, 20 * 60 * 60);
				case "day", "days" -> Math.multiplyExact(amount, 20 * 60 * 60 * 24);
				case "month", "months" -> Math.multiplyExact(Math.multiplyExact(amount, 20L), 60L * 60L * 24L * 30L);
				default -> throw new AssertionError("Unhandled time unit");
			};
			matchedEnd = matcher.end();
		}
		if (matchedEnd < 0)
			return null;
		String tail = lowered.substring(matchedEnd).trim();
		if (!tail.isEmpty())
			return null;
		return new TimeSpanLiteral(new TimeSpan(ticks));
	}

	@Override
	public List<TimeSpan> getValues(ExecContext context) {
		return List.of(value);
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends TimeSpan> returnType() {
		return TimeSpan.class;
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
