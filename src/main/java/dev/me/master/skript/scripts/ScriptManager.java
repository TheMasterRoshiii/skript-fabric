package dev.me.master.skript.scripts;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.lang.ParseState;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.lang.function.FunctionRegistry;
import dev.me.master.skript.loader.ScriptLoader;
import dev.me.master.skript.loader.ScriptFiles;
import dev.me.master.skript.scheduler.PeriodicEvents;
import dev.me.master.skript.scheduler.Scheduler;
import dev.me.master.skript.util.SkriptLogger;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class ScriptManager {

    private static final List<SkriptScript> SCRIPTS = new ArrayList<>();
    private static final Map<Path, SkriptScript> BY_FILE = new HashMap<>();

    public record LoadResult(int loaded, int failed, List<String> errors) {
        public LoadResult {
            errors = List.copyOf(errors);
        }

        public boolean successful() {
            return this.failed == 0;
        }
    }

    public sealed interface PreparedScript permits Source, ReadFailure {
        Path file();
    }

    public record Source(Path file, List<String> lines) implements PreparedScript {
        public Source {
            lines = List.copyOf(lines);
        }
    }

    public record ReadFailure(Path file, String diagnostic) implements PreparedScript {
    }

    public record PreparedReload(List<PreparedScript> scripts) {
        public PreparedReload {
            scripts = List.copyOf(scripts);
        }
    }

    private ScriptManager() {
    }

    public static List<SkriptScript> all() {
        return List.copyOf(SCRIPTS);
    }

    public static @Nullable SkriptScript find(Path directory, String name) {
        SkriptScript exact = BY_FILE.get(directory.resolve(name).toAbsolutePath().normalize());
        if (exact != null) {
            return exact;
        }
        SkriptScript found = null;
        for (SkriptScript script : SCRIPTS) {
            if (script.name().equalsIgnoreCase(name)) {
                if (found != null) {
                    return null;
                }
                found = script;
            }
        }
        return found;
    }

    public static void unloadAll() {
        for (SkriptScript script : SCRIPTS) {
            unbind(script);
        }
        SCRIPTS.clear();
        BY_FILE.clear();
    }

    public static LoadResult loadAll(Path directory) {
        List<String> errors = new ArrayList<>();
        List<Path> files;
        try {
            Files.createDirectories(directory);
            files = ScriptFiles.list(directory);
        } catch (IOException e) {
            String message = "scripts.folder=" + directory.toAbsolutePath().normalize()
                    + ": cannot scan scripts: " + e + ". Check the directory path and permissions.";
            errors.add(message);
            SkriptLogger.error(message, e);
            ScriptCommandBridge.syncAll(SCRIPTS);
            return new LoadResult(0, 1, errors);
        }
        if (files.isEmpty()) {
            SkriptLogger.info("Scripts folder is empty: put .sk or .sk.txt files in " + directory.toAbsolutePath());
        }
        int loaded = 0;
        for (Path file : files) {
            if (load(file, false, errors)) {
                loaded++;
            }
        }
        ScriptCommandBridge.syncAll(SCRIPTS);
        return new LoadResult(loaded, files.size() - loaded, errors);
    }

    public static LoadResult load(Path file) {
        List<String> errors = new ArrayList<>();
        boolean loaded = load(file, true, errors);
        return new LoadResult(loaded ? 1 : 0, loaded ? 0 : 1, errors);
    }

    private static boolean load(Path file, boolean syncCommands, List<String> errors) {
        Path normalized = file.toAbsolutePath().normalize();
        SkriptScript existing = BY_FILE.get(normalized);
        if (existing != null) {
            return reload(existing, syncCommands, errors);
        }
        SkriptScript script = parse(normalized, errors);
        if (script == null) {
            return false;
        }
        SCRIPTS.add(script);
        BY_FILE.put(normalized, script);
        if (syncCommands) {
            ScriptCommandBridge.syncAll(SCRIPTS);
        }
        logLoaded(script);
        return true;
    }

    private static @Nullable SkriptScript parse(Path file, List<String> errors) {
        List<String> lines;
        try {
            lines = ScriptFiles.read(file);
        } catch (IOException e) {
            String message = readDiagnostic(file, e);
            errors.add(message);
            SkriptLogger.error(message, e);
            return null;
        }
        return parse(file, lines, errors);
    }

    private static @Nullable SkriptScript parse(Path file, List<String> lines, List<String> errors) {
        SkriptScript script = new SkriptScript(file);
        ParseState state = new ParseState(script, message -> {
            errors.add(message);
            SkriptLogger.error(message);
        });
        boolean success = new ScriptLoader(state).load(script, lines);
        if (!success || state.hasErrors()) {
            SkriptLogger.error("Script '" + file + "' contains errors and was not enabled");
            return null;
        }
        return script;
    }

    public static LoadResult reload(SkriptScript script) {
        List<String> errors = new ArrayList<>();
        boolean loaded = reload(script, true, errors);
        return new LoadResult(loaded ? 1 : 0, loaded ? 0 : 1, errors);
    }

    private static boolean reload(SkriptScript script, boolean syncCommands, List<String> errors) {
        SkriptScript replacement = parse(script.file(), errors);
        if (replacement == null) {
            SkriptLogger.warn("Script '" + script.file() + "' was not reloaded; previous version remains loaded");
            return false;
        }
        replacement.setEnabled(script.isEnabled());
        unbind(script);
        SCRIPTS.remove(script);
        SCRIPTS.add(replacement);
        BY_FILE.put(script.file().toAbsolutePath().normalize(), replacement);
        if (syncCommands) {
            ScriptCommandBridge.syncAll(SCRIPTS);
        }
        logLoaded(replacement);
        return true;
    }

    public static LoadResult reloadAll(Path directory) {
        try {
            return applyReload(readReload(directory, true), true);
        } catch (IOException exception) {
            String message = "Cannot read scripts folder " + directory.toAbsolutePath().normalize() + ": " + exception
                    + ". Previous scripts remain loaded. Check the directory path and permissions.";
            SkriptLogger.error(message, exception);
            return new LoadResult(0, 1, List.of(message));
        }
    }

    public static PreparedReload readReload(Path target, boolean directory) throws IOException {
        List<Path> files = directory ? ScriptFiles.list(target)
                : List.of(target.toAbsolutePath().normalize());
        List<PreparedScript> sources = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                sources.add(new Source(file, ScriptFiles.read(file)));
            } catch (IOException e) {
                sources.add(new ReadFailure(file, readDiagnostic(file, e)));
            }
        }
        return new PreparedReload(sources);
    }

    public static LoadResult applyReload(PreparedReload prepared, boolean replaceAll) {
        if (replaceAll) {
            unloadAll();
        }
        List<String> errors = new ArrayList<>();
        int loaded = 0;
        for (PreparedScript input : prepared.scripts()) {
            Path file = input.file().toAbsolutePath().normalize();
            SkriptScript existing = BY_FILE.get(file);
            if (input instanceof ReadFailure failure) {
                errors.add(failure.diagnostic());
                SkriptLogger.error(failure.diagnostic());
                if (existing != null) {
                    SkriptLogger.warn("Script '" + file + "' was not reloaded; previous version remains loaded");
                }
                continue;
            }
            Source source = (Source) input;
            SkriptScript script = parse(file, source.lines(), errors);
            if (script == null) {
                if (existing != null) {
                    SkriptLogger.warn("Script '" + file + "' was not reloaded; previous version remains loaded");
                }
                continue;
            }
            if (existing != null) {
                script.setEnabled(existing.isEnabled());
                unbind(existing);
                SCRIPTS.remove(existing);
            }
            SCRIPTS.add(script);
            BY_FILE.put(file, script);
            logLoaded(script);
            loaded++;
        }
        if (replaceAll && prepared.scripts().isEmpty()) {
            SkriptLogger.info("Scripts folder is empty");
        }
        ScriptCommandBridge.syncAll(SCRIPTS);
        return new LoadResult(loaded, prepared.scripts().size() - loaded, errors);
    }

    private static String readDiagnostic(Path file, IOException exception) {
        String reason = exception instanceof CharacterCodingException
                ? "invalid text encoding; save as UTF-8 or UTF-16 with a BOM"
                : "cannot read script; check the file path and permissions";
        return file + ": " + reason + ": " + exception;
    }

    private static void unbind(SkriptScript script) {
        Scheduler.cancelScript(script);
        script.setEnabled(false);
        for (Trigger trigger : script.triggers()) {
            EventDispatch.unbindAll(trigger);
            PeriodicEvents.unregister(trigger);
        }
        script.triggers().clear();
        FunctionRegistry.unregisterScript(script);
        script.functions().clear();
        script.commands().clear();
    }

    private static void logLoaded(SkriptScript script) {
        if (script.triggers().isEmpty() && script.commands().isEmpty() && script.functions().isEmpty()) {
            SkriptLogger.info("Loaded empty script " + script.file());
            return;
        }
        SkriptLogger.info("Loaded script " + script.file() + " (" + script.triggers().size()
                + " triggers, " + script.commands().size() + " commands, " + script.functions().size() + " functions)");
    }
}
