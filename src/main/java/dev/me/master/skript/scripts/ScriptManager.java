package dev.me.master.skript.scripts;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.lang.ParseState;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.lang.function.FunctionDefinition;
import dev.me.master.skript.lang.function.FunctionRegistry;
import dev.me.master.skript.loader.ScriptLoader;
import dev.me.master.skript.scheduler.PeriodicEvents;
import dev.me.master.skript.util.SkriptLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
			reload(existing);
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			SkriptLogger.error("Could not read script " + file, e);
			return false;
		}
		SkriptScript script = new SkriptScript(file);
		ParseState state = new ParseState(script, SkriptLogger::error);
		boolean success = new ScriptLoader(state).load(script, lines);
		if (!success || script.triggers().isEmpty() && script.commands().isEmpty() && script.functions().isEmpty()) {
			if (success)
				SkriptLogger.info("Loaded empty script " + fileName);
			else
				SkriptLogger.error("Script '" + fileName + "' contains errors and was not enabled");
			if (!state.hasErrors())
				SCRIPTS.add(script);
			return success && !state.hasErrors();
		}
		SCRIPTS.add(script);
		ScriptCommandBridge.syncAll(SCRIPTS);
		SkriptLogger.info("Loaded script " + fileName + " (" + script.triggers().size()
				+ " triggers, " + script.commands().size() + " commands)");
		return true;
	}

	public static boolean reload(SkriptScript script) {
		unbind(script);
		SCRIPTS.remove(script);
		return load(script.file());
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
		for (Trigger trigger : script.triggers())
			EventDispatch.unbindAll(trigger);
		for (Trigger trigger : script.triggers())
			PeriodicEvents.unregister(trigger);
		script.triggers().clear();
		for (FunctionDefinition function : script.functions()) {
			FunctionRegistry.unregisterScript(script.name());
		}
		script.functions().clear();
		script.commands().clear();
	}
}
