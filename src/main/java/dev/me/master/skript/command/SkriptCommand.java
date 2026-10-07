package dev.me.master.skript.command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.me.master.skript.config.SkriptConfig;
import dev.me.master.skript.loader.ScriptFiles;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.scheduler.Scheduler;
import dev.me.master.skript.scripts.ScriptCommandBridge;
import dev.me.master.skript.scripts.ScriptManager;
import dev.me.master.skript.scripts.AsyncReload;
import dev.me.master.skript.util.SkriptLogger;
import java.nio.file.Path;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class SkriptCommand {

    private SkriptCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = LiteralArgumentBuilder.literal("skript");
        root.requires(source -> source.hasPermissionLevel(2));

        LiteralArgumentBuilder<ServerCommandSource> reload = LiteralArgumentBuilder.literal("reload");
        reload.executes(context -> queueReload(context.getSource(), SkriptConfig.INSTANCE.scriptsDir, true));
        reload.then(argumentScript("script", (source, name) -> {
            SkriptScript script = findByName(name);
            if (script == null) {
                String matches = matchingPaths(name);
                if (!matches.isEmpty()) {
                    error(source, "Ambiguous script name '" + name + "'; use one of these paths: " + matches);
                    return 0;
                }
                Path file = SkriptConfig.INSTANCE.scriptsDir.resolve(name).toAbsolutePath().normalize();
                if (!ScriptFiles.isScriptFile(file)) {
                    error(source, "Unsupported or disabled script filename: " + file
                            + "; use .sk or .sk.txt and remove any leading '-'");
                    return 0;
                }
                return queueReload(source, file, false);
            }
            return queueReload(source, script.file(), false);
        }));
        root.then(reload);

        root.then(LiteralArgumentBuilder.<ServerCommandSource>literal("disable")
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("all")
                        .executes(context -> {
                            for (SkriptScript script : ScriptManager.all()) {
                                script.setEnabled(false);
                                Scheduler.cancelScript(script);
                            }
                            ScriptCommandBridge.syncAll(ScriptManager.all());
                            feedback(context.getSource(), "Disabled all scripts");
                            return 1;
                        }))
                .then(argumentScript("script", (source, name) -> {
                    SkriptScript script = findByName(name);
                    if (script == null) {
                        reportMissing(source, name);
                        return 0;
                    }
                    script.setEnabled(false);
                    Scheduler.cancelScript(script);
                    ScriptCommandBridge.syncAll(ScriptManager.all());
                    feedback(source, "Disabled " + name);
                    return 1;
                })));

        root.then(LiteralArgumentBuilder.<ServerCommandSource>literal("enable")
                .then(argumentScript("script", (source, name) -> {
                    SkriptScript script = findByName(name);
                    if (script == null) {
                        reportMissing(source, name);
                        return 0;
                    }
                    script.setEnabled(true);
                    ScriptCommandBridge.syncAll(ScriptManager.all());
                    feedback(source, "Enabled " + name);
                    return 1;
                })));

        root.then(LiteralArgumentBuilder.<ServerCommandSource>literal("list")
                .executes(context -> {
                    var scripts = ScriptManager.all();
                    if (scripts.isEmpty()) {
                        context.getSource().sendFeedback(() -> Text.literal(
                                "No scripts loaded. Put .sk or .sk.txt files in " + SkriptConfig.INSTANCE.scriptsDir), false);
                        return 1;
                    }
                    StringBuilder listing = new StringBuilder();
                    for (SkriptScript script : scripts) {
                        listing.append(script.isEnabled() ? "[x] " : "[ ] ")
                                .append(displayName(script))
                                .append(" (").append(script.triggers().size()).append(" triggers)")
                                .append('\n');
                    }
                    context.getSource().sendFeedback(() -> Text.literal(listing.toString()), false);
                    return 1;
                }));

        dispatcher.register(root);
    }

    @FunctionalInterface
    private interface NamedAction {
        int run(ServerCommandSource source, String name);
    }

    private static int queueReload(ServerCommandSource source, Path target, boolean all) {
        boolean queued;
        try {
            queued = AsyncReload.INSTANCE.request(target, all, result -> {
                report(source, result);
                if (!all && !result.successful()
                        && ScriptManager.find(target.getParent(), target.getFileName().toString()) != null) {
                    error(source, "Previous version of " + target + " remains loaded");
                }
            });
        } catch (IllegalStateException exception) {
            SkriptLogger.error("Cannot queue reload of " + target, exception);
            error(source, exception.getMessage());
            return 0;
        }
        if (!queued) {
            error(source, "Another script reload is in progress; wait for its result before retrying");
            return 0;
        }
        feedback(source, "Reload queued: reading " + target + "; the result will follow");
        return 1;
    }

    private static void report(ServerCommandSource source, ScriptManager.LoadResult result) {
        for (String diagnostic : result.errors()) {
            error(source, diagnostic);
        }
        String summary = "Loaded " + result.loaded() + " script file(s); " + result.failed() + " failure(s)";
        if (result.successful()) {
            feedback(source, summary);
        } else {
            error(source, summary);
        }
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> argumentScript(
            String argumentName, NamedAction action) {
        return RequiredArgumentBuilder
                .<ServerCommandSource, String>argument(argumentName,
                        StringArgumentType.greedyString())
                .executes(context -> action.run(context.getSource(),
                        StringArgumentType.getString(context, argumentName)));
    }

    private static SkriptScript findByName(String name) {
        return ScriptManager.find(SkriptConfig.INSTANCE.scriptsDir, name);
    }

    private static String matchingPaths(String name) {
        StringBuilder matches = new StringBuilder();
        for (SkriptScript script : ScriptManager.all()) {
            if (script.name().equalsIgnoreCase(name)) {
                if (!matches.isEmpty()) {
                    matches.append(", ");
                }
                matches.append(displayName(script));
            }
        }
        return matches.toString();
    }

    private static void reportMissing(ServerCommandSource source, String name) {
        String matches = matchingPaths(name);
        if (!matches.isEmpty()) {
            error(source, "Ambiguous script name '" + name + "'; use one of these paths: " + matches);
            return;
        }
        error(source, "Script is not loaded: " + SkriptConfig.INSTANCE.scriptsDir.resolve(name)
                .toAbsolutePath().normalize() + "; use /skript reload " + name + " to load it and see any errors");
    }

    private static String displayName(SkriptScript script) {
        Path directory = SkriptConfig.INSTANCE.scriptsDir.toAbsolutePath().normalize();
        Path file = script.file().toAbsolutePath().normalize();
        return file.startsWith(directory) ? directory.relativize(file).toString().replace('\\', '/') : file.toString();
    }

    private static void feedback(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal("[Skript] " + message), true);
    }

    private static void error(ServerCommandSource source, String message) {
        source.sendError(Text.literal("[Skript] " + message));
    }
}
