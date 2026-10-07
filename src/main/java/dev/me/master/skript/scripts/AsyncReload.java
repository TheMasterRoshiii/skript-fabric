package dev.me.master.skript.scripts;

import dev.me.master.skript.util.SkriptLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jetbrains.annotations.Nullable;

public final class AsyncReload {

    public static final AsyncReload INSTANCE = new AsyncReload();

    private sealed interface ReadResult permits Ready, Failed {
    }

    private record Ready(ScriptManager.PreparedReload prepared) implements ReadResult {
    }

    private record Failed(String diagnostic) implements ReadResult {
    }

    private record Pending(boolean replaceAll, Consumer<ScriptManager.LoadResult> callback,
            ArrayBlockingQueue<ReadResult> inbox, Thread worker) {
    }

    private @Nullable Thread owner;
    private @Nullable Pending pending;

    private AsyncReload() {
    }

    public void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> this.drain());
    }

    public void attach() {
        if (this.owner != null || this.pending != null) {
            throw new IllegalStateException("Skript async reload already has a server session");
        }
        this.owner = Thread.currentThread();
    }

    public boolean request(Path target, boolean replaceAll, Consumer<ScriptManager.LoadResult> callback) {
        this.requireOwner();
        if (this.pending != null) {
            return false;
        }
        Path file = target.toAbsolutePath().normalize();
        ArrayBlockingQueue<ReadResult> inbox = new ArrayBlockingQueue<>(1);
        Thread worker = Thread.ofVirtual().name("skript-script-reader").unstarted(() -> {
            ReadResult result;
            try {
                result = new Ready(ScriptManager.readReload(file, replaceAll));
            } catch (IOException | RuntimeException exception) {
                String diagnostic = file + ": cannot prepare reload: " + exception
                        + "; check the path and permissions. Previous scripts remain loaded.";
                SkriptLogger.error(diagnostic, exception);
                result = new Failed(diagnostic);
            }
            if (!inbox.offer(result)) {
                throw new IllegalStateException("Skript async reload completion inbox is unexpectedly full");
            }
        });
        this.pending = new Pending(replaceAll, callback, inbox, worker);
        try {
            worker.start();
        } catch (RuntimeException exception) {
            this.pending = null;
            String diagnostic = file + ": cannot start the script reader: " + exception
                    + "; check the server log. Previous scripts remain loaded.";
            throw new IllegalStateException(diagnostic, exception);
        }
        return true;
    }

    private void drain() {
        if (this.owner == null) {
            return;
        }
        this.requireOwner();
        Pending job = this.pending;
        if (job == null) {
            return;
        }
        ReadResult result = job.inbox().poll();
        if (result == null) {
            return;
        }
        this.pending = null;
        if (result instanceof Failed failure) {
            SkriptLogger.error(failure.diagnostic());
            job.callback().accept(new ScriptManager.LoadResult(0, 1, List.of(failure.diagnostic())));
            return;
        }
        Ready ready = (Ready) result;
        ScriptManager.LoadResult applied;
        try {
            applied = ScriptManager.applyReload(ready.prepared(), job.replaceAll());
        } catch (RuntimeException exception) {
            String diagnostic = "Cannot apply the prepared reload: " + exception
                    + "; some scripts may already have changed. "
                    + "Check the server log and reload after fixing the cause.";
            SkriptLogger.error(diagnostic, exception);
            applied = new ScriptManager.LoadResult(0, ready.prepared().scripts().size(), List.of(diagnostic));
        }
        job.callback().accept(applied);
    }

    public void stop() {
        this.requireOwner();
        Pending job = this.pending;
        this.pending = null;
        try {
            if (job != null) {
                job.worker().interrupt();
                String message = "Reload cancelled: the server is stopping. "
                        + "Previous scripts remain loaded until shutdown.";
                job.callback().accept(new ScriptManager.LoadResult(0, 1, List.of(message)));
            }
        } finally {
            this.owner = null;
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != this.owner) {
            throw new IllegalStateException("Skript async reload state accessed outside its owning server thread: "
                    + Thread.currentThread().getName());
        }
    }
}
