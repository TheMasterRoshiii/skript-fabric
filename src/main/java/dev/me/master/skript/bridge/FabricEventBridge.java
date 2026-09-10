package dev.me.master.skript.bridge;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public final class FabricEventBridge {

	private FabricEventBridge() {
	}

	public static void register() {
		connections();
		messages();
		combat();
		interaction();
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
