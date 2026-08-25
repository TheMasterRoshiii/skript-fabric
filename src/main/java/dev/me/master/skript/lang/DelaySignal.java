package dev.me.master.skript.lang;

public final class DelaySignal extends RuntimeException {

	private final long delayTicks;

	private DelaySignal(long delayTicks) {
		super(null, null, false, false);
		this.delayTicks = Math.max(1, delayTicks);
	}

	public static DelaySignal of(long delayTicks) {
		return new DelaySignal(delayTicks);
	}

	public long delayTicks() {
		return delayTicks;
	}
}
