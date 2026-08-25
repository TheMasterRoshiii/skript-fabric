package dev.me.master.skript.util;

public record TimeSpan(long ticks) implements Comparable<TimeSpan> {

	public static final TimeSpan ZERO = new TimeSpan(0);
	public static final TimeSpan TICK = new TimeSpan(1);
	public static final TimeSpan SECOND = fromSeconds(1);

	private static final long TICKS_PER_SECOND = 20;
	private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
	private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
	private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
	private static final long MILLIS_PER_TICK = 50;

	public static TimeSpan fromTicks(long ticks) {
		return new TimeSpan(ticks);
	}

	public static TimeSpan fromSeconds(long seconds) {
		return new TimeSpan(Math.multiplyExact(seconds, TICKS_PER_SECOND));
	}

	public static TimeSpan ofMillis(long millis) {
		if (millis <= 0)
			return ZERO;
		long ticks = millis / MILLIS_PER_TICK;
		return new TimeSpan(ticks == 0 ? 1 : ticks);
	}

	@Override
	public int compareTo(TimeSpan other) {
		return Long.compare(ticks, other.ticks);
	}

	@Override
	public String toString() {
		long remaining = ticks;
		StringBuilder builder = new StringBuilder();
		appendUnit(builder, remaining / TICKS_PER_DAY, "day");
		remaining %= TICKS_PER_DAY;
		appendUnit(builder, remaining / TICKS_PER_HOUR, "hour");
		remaining %= TICKS_PER_HOUR;
		appendUnit(builder, remaining / TICKS_PER_MINUTE, "minute");
		remaining %= TICKS_PER_MINUTE;
		appendUnit(builder, remaining / TICKS_PER_SECOND, "second");
		remaining %= TICKS_PER_SECOND;
		appendUnit(builder, remaining, "tick");
		if (builder.isEmpty())
			return "0 seconds";
		return builder.toString();
	}

	private static void appendUnit(StringBuilder builder, long amount, String singular) {
		if (amount == 0 && !builder.isEmpty())
			return;
		if (amount != 0 || builder.isEmpty()) {
			if (!builder.isEmpty())
				builder.append(" and ");
			builder.append(amount).append(' ').append(singular);
			if (Math.abs(amount) != 1)
				builder.append('s');
		}
	}
}
