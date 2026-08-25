package dev.me.master.skript.scripts;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.command.ScriptCommand;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.types.CurrentServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class ScriptCommandBridge {

	private static final Map<String, Object> REGISTERED = new HashMap<>();

	private ScriptCommandBridge() {
	}

	public static void syncAll(List<SkriptScript> scripts) {
		MinecraftServer server = CurrentServer.get();
		if (server == null)
			return;
		CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
		Map<String, ScriptCommand> wanted = new HashMap<>();
		for (SkriptScript script : scripts) {
			if (!script.isEnabled())
				continue;
			for (ScriptCommand command : script.commands())
				wanted.put(command.name(), command);
		}
		List<String> removed = new ArrayList<>();
		for (String name : REGISTERED.keySet()) {
			if (!wanted.containsKey(name))
				removed.add(name);
		}
		for (String name : removed)
			unregister(dispatcher, name);
		for (Map.Entry<String, ScriptCommand> entry : wanted.entrySet()) {
			String name = entry.getKey();
			if (REGISTERED.containsKey(name))
				continue;
			register(dispatcher, name, entry.getValue());
		}
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

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher, String name,
			ScriptCommand command) {
		LiteralArgumentBuilder<ServerCommandSource> builder = LiteralArgumentBuilder.literal(name);
		builder.requires(source -> hasPermission(source, command));
		builder.executes(context -> {
			ServerCommandSource source = context.getSource();
			if (command.isPlayerOnly() && source.getPlayer() == null) {
				source.sendError(Text.literal("This command can only be used by players."));
				return 0;
			}
			if (!hasPermission(source, command)) {
				source.sendError(Text.literal("You don't have permission to use this command."));
				return 0;
			}
			ExecContext execution =
					new ExecContext(null);
			bindArguments(execution, command, context.getInput());
			command.trigger().run(execution);
			return 1;
		});
		Object node = dispatcher.register(builder);
		REGISTERED.put(name, node);
	}

	private static boolean hasPermission(ServerCommandSource source, ScriptCommand command) {
		if (command.permission().isEmpty())
			return true;
		return source.hasPermissionLevel(2)
				|| source.getPlayer() != null
						&& source.getPlayer().getCommandTags().contains(permissionTag(command.permission()));
	}

	@SuppressWarnings("unchecked")
	private static void unregister(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
		if (REGISTERED.remove(name) == null)
			return;
		RootCommandNode<ServerCommandSource> root = dispatcher.getRoot();
		removeChild(root, "children", name);
		removeChild(root, "literals", name);
		removeChild(root, "arguments", name);
	}

	private static void removeChild(RootCommandNode<ServerCommandSource> root, String mapField, String name) {
		try {
			java.lang.reflect.Field field = CommandNode.class.getDeclaredField(mapField);
			field.setAccessible(true);
			Object raw = field.get(root);
			if (!(raw instanceof java.util.Map<?, ?>))
				throw new AssertionError("CommandNode." + mapField + " is no longer a Map");
			((java.util.Map<String, CommandNode<ServerCommandSource>>) raw).remove(name);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Brigadier CommandNode." + mapField + " shape changed; "
					+ "script command removal must be updated", e);
		}
	}

	private static String permissionTag(String permission) {
		return "skript.perm." + permission.toLowerCase(Locale.ROOT).replace(' ', '.');
	}

	private static void bindArguments(ExecContext execution,
			ScriptCommand command, String input) {
		String argumentsPart = input.startsWith("/") ? input.substring(1) : input;
		int nameEnd = argumentsPart.indexOf(' ');
		if (nameEnd < 0)
			return;
		String rest = argumentsPart.substring(nameEnd + 1).trim();
		if (rest.isEmpty())
			return;
		List<String> raw = splitArguments(rest);
		int ordinal = 0;
		int argumentCount = Math.min(command.arguments().size(), raw.size());
		for (int i = 0; i < argumentCount; i++) {
			ScriptCommand.Arg argument = command.arguments().get(i);
			ordinal++;
			Object value = resolveArgument(argument, raw.get(i));
			execution.setLocal("\0arg:" + ordinal, value);
			execution.setLocal("\0arg:" + argument.name(), value);
		}
		execution.setLocal("\0arg:count", ordinal);
	}

	private static List<String> splitArguments(String input) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		char openQuote = 0;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (openQuote != 0) {
				current.append(c);
				if (c == openQuote && current.charAt(current.length() - 2) != '\\')
					openQuote = 0;
				continue;
			}
			if (c == '"') {
				openQuote = c;
				current.append(c);
				continue;
			}
			if (Character.isWhitespace(c)) {
				if (!current.isEmpty()) {
					parts.add(current.toString());
					current.setLength(0);
				}
				continue;
			}
			current.append(c);
		}
		if (!current.isEmpty())
			parts.add(current.toString());
		return parts;
	}

	private static Object resolveArgument(ScriptCommand.Arg argument, String rawValue) {
		if (rawValue == null)
			return null;
		String cleaned = unquote(rawValue.trim());
		return argument.type().parse(cleaned);
	}

	private static String unquote(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
			return value.substring(1, value.length() - 1);
		return value;
	}
}
