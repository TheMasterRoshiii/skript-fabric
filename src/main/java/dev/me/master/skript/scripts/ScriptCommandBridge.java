package dev.me.master.skript.scripts;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.me.master.skript.command.ScriptCommand;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.mixin.CommandNodeAccessMixin;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.util.SkriptLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ScriptCommandBridge {

	private record Registered(ScriptCommand command, LiteralCommandNode<ServerCommandSource> node) {
	}

	private record SourceView(ServerCommandSource source) implements ScriptEvent.ServerCommandSourceView {

		@Override
		public String name() {
			return source.getName();
		}

		@Override
		public boolean isPlayer() {
			return source.getPlayer() != null;
		}

		@Override
		public ServerPlayerEntity asPlayer() {
			return source.getPlayer();
		}
	}

	private static final Map<String, Registered> REGISTERED = new HashMap<>();

	private ScriptCommandBridge() {
	}

	public static void verifyShapeAssumptions() {
		RootCommandNode<ServerCommandSource> root = new RootCommandNode<>();
		try {
			CommandNodeAccessMixin access = (CommandNodeAccessMixin) (Object) root;
			if (access.skript$getChildren() == null
					|| access.skript$getLiterals() == null
					|| access.skript$getArguments() == null)
				throw new AssertionError("Brigadier CommandNode maps are unavailable");
		} catch (ClassCastException e) {
			throw new AssertionError("Brigadier CommandNode accessor mixin was not applied", e);
		}
	}

	public static void syncAll(List<SkriptScript> scripts) {
		MinecraftServer server = CurrentServer.get();
		if (server == null)
			return;
		CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
		RootCommandNode<ServerCommandSource> root = dispatcher.getRoot();
		Map<String, ScriptCommand> wanted = new HashMap<>();
		for (SkriptScript script : scripts) {
			if (!script.isEnabled())
				continue;
			for (ScriptCommand command : script.commands()) {
				ScriptCommand previous = wanted.putIfAbsent(command.name(), command);
				if (previous != null && previous != command)
					SkriptLogger.warn("Cannot register duplicate script command /" + command.name()
							+ "; keeping the first definition");
			}
		}

		boolean changed = false;
		for (String name : new ArrayList<>(REGISTERED.keySet())) {
			Registered registered = REGISTERED.get(name);
			ScriptCommand desired = wanted.get(name);
			if (desired == null || registered.command() != desired || root.getChild(name) != registered.node()) {
				unregister(dispatcher, name);
				changed = true;
			}
		}
		for (Map.Entry<String, ScriptCommand> entry : wanted.entrySet()) {
			if (!REGISTERED.containsKey(entry.getKey())
					&& register(dispatcher, entry.getKey(), entry.getValue()))
				changed = true;
		}
		if (changed)
			sendCommandTrees(server);
	}

	public static void registerAllNow(CommandDispatcher<ServerCommandSource> dispatcher,
			List<SkriptScript> scripts) {
		for (SkriptScript script : scripts) {
			if (!script.isEnabled())
				continue;
			for (ScriptCommand command : script.commands()) {
				if (!REGISTERED.containsKey(command.name()))
					register(dispatcher, command.name(), command);
			}
		}
	}

	private static boolean register(CommandDispatcher<ServerCommandSource> dispatcher, String name,
			ScriptCommand command) {
		if (dispatcher.getRoot().getChild(name) != null) {
			SkriptLogger.warn("Cannot register script command /" + name
					+ "; another command already owns that root literal");
			return false;
		}
		LiteralArgumentBuilder<ServerCommandSource> builder = LiteralArgumentBuilder.literal(name);
		builder.requires(source -> hasPermission(source, command));
		appendArguments(builder, command, 0);
		LiteralCommandNode<ServerCommandSource> node = dispatcher.register(builder);
		REGISTERED.put(name, new Registered(command, node));
		return true;
	}

	private static void appendArguments(ArgumentBuilder<ServerCommandSource, ?> parent,
			ScriptCommand command, int index) {
		if (index >= command.arguments().size()) {
			parent.executes(executor(command));
			return;
		}
		ScriptCommand.Arg argument = command.arguments().get(index);
		RequiredArgumentBuilder<ServerCommandSource, String> child =
				RequiredArgumentBuilder.argument(argument.name(), StringArgumentType.string());
		if (argument.optional())
			parent.executes(executor(command));
		appendArguments(child, command, index + 1);
		parent.then(child);
	}

	private static Command<ServerCommandSource> executor(ScriptCommand command) {
		return context -> {
			ServerCommandSource source = context.getSource();
			if (command.isPlayerOnly() && source.getPlayer() == null) {
				source.sendError(Text.literal("This command can only be used by players."));
				return 0;
			}
			if (!hasPermission(source, command)) {
				source.sendError(Text.literal("You don't have permission to use this command."));
				return 0;
			}
			ExecContext execution = new ExecContext(new ScriptEvent.Command(
					withoutLeadingSlash(context.getInput()), new SourceView(source)));
			if (!bindArguments(execution, command, context))
				return 0;
			command.trigger().run(execution);
			return 1;
		};
	}

	private static boolean hasPermission(ServerCommandSource source, ScriptCommand command) {
		if (command.permission().isEmpty())
			return true;
		return source.hasPermissionLevel(2)
				|| source.getPlayer() != null
						&& source.getPlayer().getCommandTags().contains(permissionTag(command.permission()));
	}

	private static void unregister(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
		Registered registered = REGISTERED.remove(name);
		if (registered == null)
			return;
		RootCommandNode<ServerCommandSource> root = dispatcher.getRoot();
		if (root.getChild(name) != registered.node())
			return;
		CommandNodeAccessMixin access = (CommandNodeAccessMixin) (Object) root;
		access.skript$getChildren().remove(name);
		access.skript$getLiterals().remove(name);
		access.skript$getArguments().remove(name);
	}

	private static void sendCommandTrees(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
			server.getPlayerManager().sendCommandTree(player);
	}

	private static String permissionTag(String permission) {
		return "skript.perm." + permission.toLowerCase(Locale.ROOT).replace(' ', '.');
	}

	private static boolean bindArguments(ExecContext execution,
			ScriptCommand command, CommandContext<ServerCommandSource> context) {
		int ordinal = 0;
		for (ScriptCommand.Arg argument : command.arguments()) {
			String raw;
			try {
				raw = context.getArgument(argument.name(), String.class);
			} catch (IllegalArgumentException e) {
				if (argument.optional())
					break;
				context.getSource().sendError(Text.literal("Missing command argument: " + argument.name()));
				return false;
			}
			Object value = resolveArgument(argument, raw);
			if (value == null) {
				context.getSource().sendError(Text.literal("Invalid value for command argument: " + argument.name()));
				return false;
			}
			ordinal++;
			execution.setLocal("\0arg:" + ordinal, value);
			execution.setLocal("\0arg:" + argument.name(), value);
		}
		execution.setLocal("\0arg:count", ordinal);
		return true;
	}

	private static Object resolveArgument(ScriptCommand.Arg argument, String rawValue) {
		String cleaned = unquote(rawValue.trim());
		return argument.type().parse(cleaned);
	}

	private static String unquote(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
			return value.substring(1, value.length() - 1);
		return value;
	}

	private static String withoutLeadingSlash(String input) {
		return input.startsWith("/") ? input.substring(1) : input;
	}
}
