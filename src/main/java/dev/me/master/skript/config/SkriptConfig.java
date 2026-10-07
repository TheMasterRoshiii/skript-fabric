package dev.me.master.skript.config;

import dev.me.master.skript.util.LoaderBridge;
import dev.me.master.skript.util.SkriptLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;

public final class SkriptConfig {

	public static final SkriptConfig INSTANCE = load();

	public final Path baseDir;
	public final Path scriptsDir;
	public final Path variablesFile;
	public final int variableSaveIntervalTicks;
	public final int maxScheduledResumesPerTick;
	public final int maxSpawnCount;
	public final boolean verboseErrors;
	public final boolean itemConsumeEnabled;
    public final boolean totemPopEnabled;

	private SkriptConfig(Path baseDir, Path scriptsDir, Path variablesFile, int variableSaveIntervalTicks,
            int maxScheduledResumesPerTick, int maxSpawnCount, boolean verboseErrors,
            boolean itemConsumeEnabled, boolean totemPopEnabled) {
		this.baseDir = baseDir;
		this.scriptsDir = scriptsDir;
		this.variablesFile = variablesFile;
		this.variableSaveIntervalTicks = variableSaveIntervalTicks;
		this.maxScheduledResumesPerTick = maxScheduledResumesPerTick;
		this.maxSpawnCount = maxSpawnCount;
		this.verboseErrors = verboseErrors;
		this.itemConsumeEnabled = itemConsumeEnabled;
        this.totemPopEnabled = totemPopEnabled;
	}

	private static SkriptConfig load() {
		Path baseDir = resolveBaseDir();
		Properties properties = new Properties();
		Path configFile = baseDir.resolve("config.properties").toAbsolutePath().normalize();
		if (Files.exists(configFile)) {
			try (InputStream in = Files.newInputStream(configFile)) {
				properties.load(in);
			} catch (IOException | IllegalArgumentException e) {
				properties.clear();
				SkriptLogger.error(configFile + ": cannot read configuration: " + e + "; using defaults", e);
			}
		}
		Path scriptsDir = getPath(properties, configFile, baseDir, "scripts.folder", "scripts");
		Path variablesFile = getPath(properties, configFile, baseDir, "variables.file", "variables.json");
		int saveInterval = parsePositiveInt(properties, configFile,
				"variables.save-interval-seconds", 120, Integer.MAX_VALUE / 20);
		int maxResumes = parsePositiveInt(properties, configFile,
				"scheduler.max-resumes-per-tick", 1024, Integer.MAX_VALUE);
		int maxSpawnCount = parsePositiveInt(properties, configFile,
				"entities.max-spawn-count", 64, Integer.MAX_VALUE);
		boolean verbose = parseBoolean(properties, configFile, "log.verbose", false);
		boolean itemConsumeEnabled = parseBoolean(properties, configFile, "patches.itemconsume", true);
        boolean totemPopEnabled = parseBoolean(properties, configFile, "patches.totempop", true);
		return new SkriptConfig(baseDir, scriptsDir, variablesFile, saveInterval * 20, maxResumes,
                maxSpawnCount, verbose, itemConsumeEnabled, totemPopEnabled);
	}

	private static Path resolveBaseDir() {
		try {
			return LoaderBridge.gameDir().resolve("skript");
		} catch (LinkageError | RuntimeException unavailable) {
			return Path.of("skript");
		}
	}

	private static Path getPath(Properties properties, Path configFile, Path baseDir, String key, String fallback) {
		String value = properties.getProperty(key);
		if (value == null) {
			return baseDir.resolve(fallback);
		}
		if (!value.isBlank()) {
			try {
				return baseDir.resolve(value.trim());
			} catch (InvalidPathException e) {
				invalidValue(configFile, key, value, "a valid filesystem path (" + e.getReason() + ")", fallback);
				return baseDir.resolve(fallback);
			}
		}
		invalidValue(configFile, key, value, "a non-empty filesystem path", fallback);
		return baseDir.resolve(fallback);
	}

	private static int parsePositiveInt(Properties properties, Path configFile, String key, int fallback, int maximum) {
		String raw = properties.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		try {
			int parsed = Integer.parseInt(raw.trim());
			if (parsed > 0 && parsed <= maximum) {
				return parsed;
			}
		} catch (NumberFormatException e) {
			invalidValue(configFile, key, raw, "an integer from 1 to " + maximum, Integer.toString(fallback));
			return fallback;
		}
		invalidValue(configFile, key, raw, "an integer from 1 to " + maximum, Integer.toString(fallback));
		return fallback;
	}

	private static void invalidValue(Path file, String key, String raw, String expected, String fallback) {
		SkriptLogger.error(file + ": invalid " + key + "='" + raw + "'; expected " + expected
				+ "; using " + fallback);
	}

	private static boolean parseBoolean(Properties properties, Path file, String key, boolean fallback) {
		String raw = properties.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		if (raw.trim().equalsIgnoreCase("true")) {
			return true;
		}
		if (raw.trim().equalsIgnoreCase("false")) {
			return false;
		}
		invalidValue(file, key, raw, "true or false", Boolean.toString(fallback));
		return fallback;
	}
}
