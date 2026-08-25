package dev.me.master.skript.util;

public final class SkriptLogger {

	private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("skript");

	private SkriptLogger() {
	}

	public static void info(String message) {
		LOGGER.info(message);
	}

	public static void warn(String message) {
		LOGGER.warning(message);
	}

	public static void error(String message) {
		LOGGER.severe(message);
	}

	public static void error(String message, Throwable throwable) {
		LOGGER.log(java.util.logging.Level.SEVERE, message, throwable);
	}
}
