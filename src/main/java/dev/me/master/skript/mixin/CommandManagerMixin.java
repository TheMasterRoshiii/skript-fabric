package dev.me.master.skript.mixin;

import dev.me.master.skript.bridge.CommandEventHook;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

	@Inject(
			method = "executeWithPrefix(Lnet/minecraft/server/command/ServerCommandSource;Ljava/lang/String;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void skript$onExecuteWithPrefix(ServerCommandSource source, String command, CallbackInfo ci) {
		if (CommandEventHook.intercept(source, command)) {
			ci.cancel();
		}
	}
}
