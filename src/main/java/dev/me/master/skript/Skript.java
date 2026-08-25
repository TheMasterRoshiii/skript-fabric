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

public class Skript implements ModInitializer {

	private static final java.util.concurrent.atomic.AtomicBoolean BOOTED =
			new java.util.concurrent.atomic.AtomicBoolean(false);

	@Override
	public void onInitialize() {
		bootEngine();
		FabricEventBridge.register();
		Scheduler.init();
		ServerLifecycleEvents.SERVER_STARTED.register(Skript::startSession);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopSession());
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
		boolean hasExecuteWithPrefix = false;
		for (java.lang.reflect.Method method : CommandManager.class.getMethods()) {
			if (!method.getName().equals("executeWithPrefix"))
				continue;
			Class<?>[] parameters = method.getParameterTypes();
			hasExecuteWithPrefix = parameters.length == 2
					&& parameters[0] == ServerCommandSource.class
					&& parameters[1] == String.class;
			break;
		}
		if (!hasExecuteWithPrefix)
			throw new AssertionError(
					"CommandManager.executeWithPrefix(ServerCommandSource, String) is missing; "
							+ "the on-command mixin target has moved and must be updated.");
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
		PeriodicEvents.startSession();
		fireLoadEvents();
	}

	private static void fireLoadEvents() {
		for (SkriptScript script : ScriptManager.all()) {
			EventDispatch.fire(
					new ScriptEvent.ScriptLoad(script));
		}
	}

	private static void stopSession() {
		Variables.saveNow();
		Scheduler.clearQueue();
	}
}
