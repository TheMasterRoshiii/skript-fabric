package dev.me.master.skript.bridge;

import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CommandEventHook {

	private CommandEventHook() {
	}

	public static boolean intercept(ServerCommandSource source, String rawCommand) {
		if (!EventDispatch.hasListeners(ScriptEvent.Command.class))
			return false;
		String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
		ScriptEvent.Command event = new ScriptEvent.Command(command, new View(source));
		return EventDispatch.fire(event).isCancelled();
	}

	private record View(ServerCommandSource source) implements ScriptEvent.ServerCommandSourceView {

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
}
