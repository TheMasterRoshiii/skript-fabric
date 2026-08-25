package dev.me.master.skript.config;

import dev.me.master.skript.util.LoaderBridge;
import dev.me.master.skript.util.SkriptLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SkriptConfig {

	public static final SkriptConfig INSTANCE = load();

	public final Path baseDir;
	public final Path scriptsDir;
	public final Path variablesFile;
	public final int variableSaveIntervalTicks;
	public final boolean verboseErrors;

	private SkriptConfig(Path baseDir, Path scriptsDir, Path variablesFile, int variableSaveIntervalTicks, boolean verboseErrors) {
		this.baseDir = baseDir;
		this.scriptsDir = scriptsDir;
		this.variablesFile = variablesFile;
		this.variableSaveIntervalTicks = variableSaveIntervalTicks;
		this.verboseErrors = verboseErrors;
	}

	private static SkriptConfig load() {
		Path baseDir = resolveBaseDir();
		Properties properties = new Properties();
		Path configFile = baseDir.resolve("config.properties");
		if (Files.exists(configFile)) {
			try (InputStream in = Files.newInputStream(configFile)) {
				properties.load(in);
			} catch (IOException e) {
				SkriptLogger.error("Could not read skript/config.properties", e);
			}
		}
		Path scriptsDir = baseDir.resolve(get(properties, "scripts.folder", "scripts"));
		Path variablesFile = baseDir.resolve(get(properties, "variables.file", "variables.json"));
		int saveInterval = parsePositiveInt(properties.getProperty("variables.save-interval-seconds"), 120);
		boolean verbose = Boolean.parseBoolean(properties.getProperty("log.verbose", "false"));
		return new SkriptConfig(baseDir, scriptsDir, variablesFile, saveInterval * 20, verbose);
	}

	private static Path resolveBaseDir() {
		try {
			return LoaderBridge.gameDir().resolve("skript");
		} catch (Throwable unavailable) {
			return Path.of("skript");
		}
	}

	private static String get(Properties properties, String key, String fallback) {
		String value = properties.getProperty(key);
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static int parsePositiveInt(String raw, int fallback) {
		if (raw == null)
			return fallback;
		try {
			int parsed = Integer.parseInt(raw.trim());
			return parsed > 0 ? parsed : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
