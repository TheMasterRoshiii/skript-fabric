package dev.me.master.skript.command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.me.master.skript.config.SkriptConfig;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.scheduler.Scheduler;
import dev.me.master.skript.scripts.ScriptCommandBridge;
import dev.me.master.skript.scripts.ScriptManager;
import java.util.Locale;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class SkriptCommand {

	private SkriptCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		LiteralArgumentBuilder<ServerCommandSource> root = LiteralArgumentBuilder.literal("skript");
		root.requires(source -> source.hasPermissionLevel(2));

		LiteralArgumentBuilder<ServerCommandSource> reload = LiteralArgumentBuilder.literal("reload");
		reload.executes(context -> {
			ScriptManager.reloadAll(SkriptConfig.INSTANCE.scriptsDir);
			feedback(context.getSource(), "Reloaded all scripts");
			return 1;
		});
		reload.then(argumentScript("script", (source, name) -> {
			SkriptScript script = findByName(name);
			if (script == null) {
				error(source, "Unknown or unloaded script: " + name);
				return 0;
			}
			boolean ok = ScriptManager.reload(script);
			if (ok)
				feedback(source, "Reloaded " + name);
			else
				error(source, "Failed to reload " + name + "; see server log");
			return ok ? 1 : 0;
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
						error(source, "Unknown or unloaded script: " + name);
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
						error(source, "Unknown or unloaded script: " + name);
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
						context.getSource().sendFeedback(() -> Text.literal("No scripts loaded. Put .sk files in "
								+ SkriptConfig.INSTANCE.scriptsDir), false);
						return 1;
					}
					StringBuilder listing = new StringBuilder();
					for (SkriptScript script : scripts) {
						listing.append(script.isEnabled() ? "[x] " : "[ ] ")
								.append(script.name())
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

	private static RequiredArgumentBuilder<ServerCommandSource, String> argumentScript(
			String argumentName, NamedAction action) {
		return RequiredArgumentBuilder
				.<ServerCommandSource, String>argument(argumentName,
						StringArgumentType.word())
				.executes(context -> action.run(context.getSource(),
						StringArgumentType.getString(context, argumentName)));
	}

	private static SkriptScript findByName(String loweredName) {
		for (SkriptScript script : ScriptManager.all()) {
			if (script.name().toLowerCase(Locale.ROOT).equals(loweredName))
				return script;
		}
		return null;
	}

	private static void feedback(ServerCommandSource source, String message) {
		source.sendFeedback(() -> Text.literal("[Skript] " + message), true);
	}

	private static void error(ServerCommandSource source, String message) {
		source.sendError(Text.literal("[Skript] " + message));
	}
}
