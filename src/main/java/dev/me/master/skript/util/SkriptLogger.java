package dev.me.master.skript.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class SkriptLogger {

	private static final Logger LOGGER = Logger.getLogger("skript");

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
		LOGGER.log(Level.SEVERE, message, throwable);
	}
}
