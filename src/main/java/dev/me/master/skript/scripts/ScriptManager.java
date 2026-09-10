package dev.me.master.skript.scripts;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.lang.ParseState;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.lang.function.FunctionRegistry;
import dev.me.master.skript.loader.ScriptLoader;
import dev.me.master.skript.scheduler.PeriodicEvents;
import dev.me.master.skript.scheduler.Scheduler;
import dev.me.master.skript.util.SkriptLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public final class ScriptManager {

	private static final List<SkriptScript> SCRIPTS = new ArrayList<>();

	private ScriptManager() {
	}

	public static List<SkriptScript> all() {
		return List.copyOf(SCRIPTS);
	}

	public static void unloadAll() {
		for (SkriptScript script : SCRIPTS)
			unbind(script);
		SCRIPTS.clear();
	}

	public static int loadAll(Path directory) {
		if (!Files.isDirectory(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				SkriptLogger.error("Could not create scripts directory " + directory, e);
			}
			SkriptLogger.info("Scripts folder is empty: put .sk files in " + directory);
			return 0;
		}
		List<Path> files = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(directory)) {
			stream.filter(path -> path.toString().endsWith(".sk"))
					.filter(path -> !path.getFileName().toString().startsWith("-"))
					.sorted()
					.forEach(files::add);
		} catch (IOException e) {
			SkriptLogger.error("Could not list scripts in " + directory, e);
			return 0;
		}
		int loaded = 0;
		for (Path file : files) {
			if (load(file))
				loaded++;
		}
		return loaded;
	}

	public static boolean load(Path file) {
		String fileName = file.getFileName().toString();
		SkriptScript existing = byName(fileName);
		if (existing != null)
			return reload(existing);
		SkriptScript script = parse(file);
		if (script == null)
			return false;
		SCRIPTS.add(script);
		ScriptCommandBridge.syncAll(SCRIPTS);
		logLoaded(script, fileName);
		return true;
	}

	private static @Nullable SkriptScript parse(Path file) {
		String fileName = file.getFileName().toString();
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			SkriptLogger.error("Could not read script " + file, e);
			return null;
		}
		SkriptScript script = new SkriptScript(file);
		ParseState state = new ParseState(script, SkriptLogger::error);
		boolean success = new ScriptLoader(state).load(script, lines);
		if (!success || state.hasErrors()) {
			SkriptLogger.error("Script '" + fileName + "' contains errors and was not enabled");
			return null;
		}
		return script;
	}

	public static boolean reload(SkriptScript script) {
		SkriptScript replacement = parse(script.file());
		if (replacement == null) {
			SkriptLogger.warn("Script '" + script.name() + "' was not reloaded; previous version remains active");
			return false;
		}
		replacement.setEnabled(script.isEnabled());
		unbind(script);
		SCRIPTS.remove(script);
		SCRIPTS.add(replacement);
		ScriptCommandBridge.syncAll(SCRIPTS);
		logLoaded(replacement, replacement.name());
		return true;
	}

	public static void reloadAll(Path directory) {
		unloadAll();
		loadAll(directory);
		ScriptCommandBridge.syncAll(SCRIPTS);
	}

	private static SkriptScript byName(String name) {
		for (SkriptScript script : SCRIPTS) {
			if (script.name().equals(name))
				return script;
		}
		return null;
	}

	private static void unbind(SkriptScript script) {
		Scheduler.cancelScript(script);
		script.setEnabled(false);
		for (Trigger trigger : script.triggers())
			EventDispatch.unbindAll(trigger);
		for (Trigger trigger : script.triggers())
			PeriodicEvents.unregister(trigger);
		script.triggers().clear();
		FunctionRegistry.unregisterScript(script);
		script.functions().clear();
		script.commands().clear();
	}

	private static void logLoaded(SkriptScript script, String fileName) {
		if (script.triggers().isEmpty() && script.commands().isEmpty() && script.functions().isEmpty()) {
			SkriptLogger.info("Loaded empty script " + fileName);
			return;
		}
		SkriptLogger.info("Loaded script " + fileName + " (" + script.triggers().size()
				+ " triggers, " + script.commands().size() + " commands)");
	}
}
