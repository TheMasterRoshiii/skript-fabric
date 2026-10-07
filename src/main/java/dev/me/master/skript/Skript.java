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
import dev.me.master.skript.scripts.AsyncReload;
import dev.me.master.skript.scripts.ScriptManager;
import dev.me.master.skript.types.AliasIndex;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.variables.Variables;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
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
        AsyncReload.INSTANCE.init();
        ServerLifecycleEvents.SERVER_STARTED.register(Skript::startSession);
        ServerLifecycleEvents.SERVER_STOPPING.register(Skript::stopSession);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SkriptCommand.register(dispatcher));
    }

    public static void bootEngine() {
        if (!BOOTED.compareAndSet(false, true)) {
            return;
        }
        AliasIndex.init();
        DefaultTypes.register();
        DefaultTypes.registerComparisons();
        DefaultExpressions.register();
        DefaultConditions.register();
        DefaultEffects.register();
        DefaultEventValues.register();
        DefaultEvents.register();
        SyntaxRegistry.freeze();
        ScriptCommandBridge.verifyShapeAssumptions();
        SkriptLogger.info("Skript engine initialized");
    }

    private static void startSession(MinecraftServer server) {
        CurrentServer.attach(server);
        Scheduler.attach(server);
        AsyncReload.INSTANCE.attach();
        Variables.load();
        Variables.resolvePending(server);
        ScriptManager.LoadResult result = ScriptManager.loadAll(SkriptConfig.INSTANCE.scriptsDir);
        String summary = "Loaded " + result.loaded() + " script file(s); " + result.failed()
                + " failure(s) in " + SkriptConfig.INSTANCE.scriptsDir.toAbsolutePath().normalize();
        if (result.successful()) {
            SkriptLogger.info(summary);
        } else {
            SkriptLogger.error(summary);
        }
        PeriodicEvents.startSession(server);
        fireLoadEvents();
        if (EventDispatch.hasListeners(ScriptEvent.ServerStart.class)) {
            EventDispatch.fire(new ScriptEvent.ServerStart(server));
        }
    }

    private static void fireLoadEvents() {
        for (SkriptScript script : ScriptManager.all()) {
            EventDispatch.fire(
                    new ScriptEvent.ScriptLoad(script));
        }
    }

    private static void stopSession(MinecraftServer server) {
        if (EventDispatch.hasListeners(ScriptEvent.ServerStop.class)) {
            EventDispatch.fire(new ScriptEvent.ServerStop(server));
        }
        AsyncReload.INSTANCE.stop();
        ScriptCommandBridge.syncAll(List.of());
        Variables.saveNow();
        Scheduler.clearQueue();
        ScriptManager.unloadAll();
        CurrentServer.detach(server);
    }
}
