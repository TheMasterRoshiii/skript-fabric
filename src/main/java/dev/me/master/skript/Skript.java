package dev.me.master.skript;
import dev.me.master.skript.bridge.FabricEventBridge;
import dev.me.master.skript.command.SkriptCommand;
import dev.me.master.skript.config.SkriptConfig;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.SyntaxRegistry;
import dev.me.master.skript.registrations.DefaultConditions;
import dev.me.master.skript.registrations.DefaultEffects;
import dev.me.master.skript.registrations.DefaultEventValues;
import dev.me.master.skript.registrations.DefaultEvents;
import dev.me.master.skript.registrations.DefaultExpressions;
import dev.me.master.skript.registrations.DefaultTypes;
import dev.me.master.skript.scheduler.PeriodicEvents;
import dev.me.master.skript.scheduler.Scheduler;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.scripts.ScriptCommandBridge;
import dev.me.master.skript.scripts.ScriptManager;
import dev.me.master.skript.types.AliasIndex;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.variables.Variables;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Skript implements ModInitializer {

	private static final AtomicBoolean BOOTED =
			new AtomicBoolean(false);

	@Override
	public void onInitialize() {
		bootEngine();
		FabricEventBridge.register();
		Scheduler.init();
		Variables.init();
		ServerLifecycleEvents.SERVER_STARTED.register(Skript::startSession);
		ServerLifecycleEvents.SERVER_STOPPING.register(Skript::stopSession);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SkriptCommand.register(dispatcher));
	}

	public static void bootEngine() {
		if (!BOOTED.compareAndSet(false, true))
			return;
		AliasIndex.init();
		DefaultTypes.register();
		DefaultTypes.registerComparisons();
		DefaultExpressions.register();
		DefaultConditions.register();
		DefaultEffects.register();
		DefaultEventValues.register();
		DefaultEvents.register();
		SyntaxRegistry.freeze();
		verifyShapeAssumptions();
		SkriptLogger.info("Skript engine initialized");
	}

	private static void verifyShapeAssumptions() {
		try {
			Method method = CommandManager.class.getDeclaredMethod(
					"executeWithPrefix", ServerCommandSource.class, String.class);
			if (method.getReturnType() != void.class)
				throw new AssertionError("CommandManager.executeWithPrefix return type changed");
		} catch (NoSuchMethodException e) {
			throw new AssertionError(
					"CommandManager.executeWithPrefix(ServerCommandSource, String) is missing; "
							+ "the on-command mixin target has moved and must be updated.");
		}
		try {
			Method method = ServerPlayerInteractionManager.class.getDeclaredMethod(
					"interactBlock", ServerPlayerEntity.class, World.class, ItemStack.class,
					Hand.class, BlockHitResult.class);
			if (method.getReturnType() != ActionResult.class)
				throw new AssertionError("ServerPlayerInteractionManager.interactBlock return type changed");
		} catch (NoSuchMethodException e) {
			throw new AssertionError(
					"ServerPlayerInteractionManager.interactBlock target is missing; block-place hook must be updated.", e);
		}
		ScriptCommandBridge.verifyShapeAssumptions();
	}

	private static void startSession(MinecraftServer server) {
		CurrentServer.attach(server);
		Scheduler.attach(server);
		Variables.load();
		Variables.resolvePending(server);
		int loaded = ScriptManager.loadAll(SkriptConfig.INSTANCE.scriptsDir);
		SkriptLogger.info("Loaded " + loaded + " script file(s) from "
				+ SkriptConfig.INSTANCE.scriptsDir);
		ScriptCommandBridge.syncAll(ScriptManager.all());
		PeriodicEvents.startSession(server);
		fireLoadEvents();
	}

	private static void fireLoadEvents() {
		for (SkriptScript script : ScriptManager.all()) {
			EventDispatch.fire(
					new ScriptEvent.ScriptLoad(script));
		}
	}

	private static void stopSession(MinecraftServer server) {
		ScriptCommandBridge.syncAll(List.of());
		Variables.saveNow();
		Scheduler.clearQueue();
		ScriptManager.unloadAll();
		CurrentServer.detach(server);
	}
}
