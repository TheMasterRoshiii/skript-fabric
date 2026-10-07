package dev.me.master.skript.bridge;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

public final class FabricEventBridge {

	private FabricEventBridge() {
	}

	public static void register() {
		connections();
		messages();
		combat();
		interaction();
        items();
        entities();
        sleeping();
	}

    private static void items() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld)
                    || !EventDispatch.hasListeners(ScriptEvent.ItemUse.class)) {
                return TypedActionResult.pass(ItemStack.EMPTY);
            }
            ItemStack held = player.getStackInHand(hand);
            ScriptEvent.ItemUse event = new ScriptEvent.ItemUse(serverPlayer, held.copyWithCount(1), hand);
            return EventDispatch.fire(event).isCancelled()
                    ? TypedActionResult.fail(held) : TypedActionResult.pass(ItemStack.EMPTY);
        });
    }

    private static void entities() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (EventDispatch.hasListeners(ScriptEvent.EntityLoad.class)) {
                EventDispatch.fire(new ScriptEvent.EntityLoad(entity, world));
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (EventDispatch.hasListeners(ScriptEvent.EntityUnload.class)) {
                EventDispatch.fire(new ScriptEvent.EntityUnload(entity, world));
            }
        });
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
            if (EventDispatch.hasListeners(ScriptEvent.EquipmentChange.class)) {
                EventDispatch.fire(new ScriptEvent.EquipmentChange(
                        entity, slot, previous.copy(), current.copy()));
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (EventDispatch.hasListeners(ScriptEvent.WorldChange.class)) {
                EventDispatch.fire(new ScriptEvent.WorldChange(player, origin, destination));
            }
        });
        ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((original, entity, origin, destination) -> {
            if (EventDispatch.hasListeners(ScriptEvent.WorldChange.class)) {
                EventDispatch.fire(new ScriptEvent.WorldChange(entity, origin, destination));
            }
        });
    }

    private static void sleeping() {
        EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayerEntity player && entity.getWorld() instanceof ServerWorld world
                    && EventDispatch.hasListeners(ScriptEvent.SleepStart.class)) {
                EventDispatch.fire(new ScriptEvent.SleepStart(player, pos.toImmutable(), world));
            }
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayerEntity player && entity.getWorld() instanceof ServerWorld world
                    && EventDispatch.hasListeners(ScriptEvent.SleepStop.class)) {
                EventDispatch.fire(new ScriptEvent.SleepStop(player, pos.toImmutable(), world));
            }
        });
    }

	private static void connections() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.PlayerJoin.class))
				return;
			EventDispatch.fire(new ScriptEvent.PlayerJoin(handler.player));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.PlayerQuit.class))
				return;
			EventDispatch.fire(new ScriptEvent.PlayerQuit(handler.player));
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.Respawn.class))
				return;
			EventDispatch.fire(new ScriptEvent.Respawn(newPlayer));
		});
	}

	private static void messages() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.Chat.class))
				return true;
			String content = message.getSignedContent();
			return !EventDispatch.fire(new ScriptEvent.Chat(sender, content)).isCancelled();
		});
	}

	private static void combat() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.Damage.class))
				return true;
			return !EventDispatch.fire(new ScriptEvent.Damage(victim, source, amount)).isCancelled();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.Death.class))
				return;
			EventDispatch.fire(new ScriptEvent.Death(victim, source));
		});
	}

	private static void interaction() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.BlockBreak.class))
				return true;
			if (!(player instanceof ServerPlayerEntity serverPlayer)
					|| !(world instanceof ServerWorld serverWorld))
				return true;
			ScriptEvent.BlockBreak event = new ScriptEvent.BlockBreak(
					serverPlayer, pos, state, serverPlayer.getMainHandStack());
			return !EventDispatch.fire(event).isCancelled();
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.RightClickBlock.class))
				return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer)
					|| !(world instanceof ServerWorld serverWorld))
				return ActionResult.PASS;
			BlockPos pos = hitResult.getBlockPos();
			ScriptEvent.RightClickBlock event = new ScriptEvent.RightClickBlock(
					serverPlayer, pos, serverWorld.getBlockState(pos), hand, serverWorld);
			return EventDispatch.fire(event).isCancelled() ? ActionResult.FAIL : ActionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.LeftClickBlock.class))
				return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer)
					|| !(world instanceof ServerWorld serverWorld))
				return ActionResult.PASS;
			ScriptEvent.LeftClickBlock event = new ScriptEvent.LeftClickBlock(
					serverPlayer, pos, direction, serverWorld);
			return EventDispatch.fire(event).isCancelled() ? ActionResult.FAIL : ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.RightClickEntity.class))
				return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer))
				return ActionResult.PASS;
			ScriptEvent.RightClickEntity event = new ScriptEvent.RightClickEntity(serverPlayer, entity, hand);
			return EventDispatch.fire(event).isCancelled() ? ActionResult.FAIL : ActionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!EventDispatch.hasListeners(ScriptEvent.LeftClickEntity.class))
				return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer))
				return ActionResult.PASS;
			ScriptEvent.LeftClickEntity event = new ScriptEvent.LeftClickEntity(serverPlayer, entity);
			return EventDispatch.fire(event).isCancelled() ? ActionResult.FAIL : ActionResult.PASS;
		});
	}
}
