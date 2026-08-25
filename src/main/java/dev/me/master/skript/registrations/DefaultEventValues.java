package dev.me.master.skript.registrations;

import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.EventValues;
import dev.me.master.skript.types.BlockRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class DefaultEventValues {

	private DefaultEventValues() {
	}

	public static void register() {
		playerEvents();
		combat();
		blocks();
	}

	private static void playerEvents() {
		EventValues.register(ScriptEvent.PlayerJoin.class, ServerPlayerEntity.class,
				(ScriptEvent.PlayerJoin event) -> event.player());
		EventValues.register(ScriptEvent.PlayerQuit.class, ServerPlayerEntity.class,
				(ScriptEvent.PlayerQuit event) -> event.player());
		EventValues.register(ScriptEvent.Respawn.class, ServerPlayerEntity.class,
				(ScriptEvent.Respawn event) -> event.player());
		EventValues.register(ScriptEvent.Chat.class, ServerPlayerEntity.class,
				(ScriptEvent.Chat event) -> event.player());
		EventValues.register(ScriptEvent.Chat.class, String.class,
				(ScriptEvent.Chat event) -> event.message());
		EventValues.register(ScriptEvent.PlayerJoin.class, ServerWorld.class,
				(ScriptEvent.PlayerJoin event) -> (ServerWorld) event.player().getWorld());
		EventValues.register(ScriptEvent.Respawn.class, ServerWorld.class,
				(ScriptEvent.Respawn event) -> (ServerWorld) event.player().getWorld());
		EventValues.register(ScriptEvent.Command.class, ServerPlayerEntity.class,
				(ScriptEvent.Command event) -> event.source().isPlayer() ? event.source().asPlayer() : null);
		EventValues.register(ScriptEvent.Command.class, String.class,
				(ScriptEvent.Command event) -> event.command());
	}

	private static void combat() {
		EventValues.register(ScriptEvent.Damage.class, LivingEntity.class,
				(ScriptEvent.Damage event) -> event.victim());
		EventValues.register(ScriptEvent.Damage.class, Entity.class,
				(ScriptEvent.Damage event) -> event.source().getAttacker());
		EventValues.register(ScriptEvent.Damage.class, Number.class,
				(ScriptEvent.Damage event) -> event.amount());
		EventValues.register(ScriptEvent.Damage.class, ServerWorld.class,
				(ScriptEvent.Damage event) -> victimWorld(event.victim()));
		EventValues.register(ScriptEvent.Death.class, LivingEntity.class,
				(ScriptEvent.Death event) -> event.victim());
		EventValues.register(ScriptEvent.Death.class, Entity.class,
				(ScriptEvent.Death event) -> event.source().getAttacker());
		EventValues.register(ScriptEvent.Death.class, ServerWorld.class,
				(ScriptEvent.Death event) -> victimWorld(event.victim()));
	}

	private static ServerWorld victimWorld(LivingEntity victim) {
		return victim.getWorld() instanceof ServerWorld world ? world : null;
	}

	private static void blocks() {
		EventValues.register(ScriptEvent.BlockBreak.class, BlockRef.class,
				(ScriptEvent.BlockBreak event) -> new BlockRef((ServerWorld) event.player().getWorld(), event.pos()));
		EventValues.register(ScriptEvent.BlockBreak.class, ServerPlayerEntity.class,
				(ScriptEvent.BlockBreak event) -> event.player());
		EventValues.register(ScriptEvent.BlockPlace.class, BlockRef.class,
				(ScriptEvent.BlockPlace event) -> new BlockRef(event.world(), event.pos()));
		EventValues.register(ScriptEvent.BlockPlace.class, ServerPlayerEntity.class,
				(ScriptEvent.BlockPlace event) -> event.player());
		EventValues.register(ScriptEvent.RightClickBlock.class, BlockRef.class,
				(ScriptEvent.RightClickBlock event) -> new BlockRef(event.world(), event.pos()));
		EventValues.register(ScriptEvent.RightClickBlock.class, ServerPlayerEntity.class,
				(ScriptEvent.RightClickBlock event) -> event.player());
		EventValues.register(ScriptEvent.LeftClickBlock.class, BlockRef.class,
				(ScriptEvent.LeftClickBlock event) -> new BlockRef(event.world(), event.pos()));
		EventValues.register(ScriptEvent.LeftClickBlock.class, ServerPlayerEntity.class,
				(ScriptEvent.LeftClickBlock event) -> event.player());
		EventValues.register(ScriptEvent.RightClickEntity.class, ServerPlayerEntity.class,
				(ScriptEvent.RightClickEntity event) -> event.player());
		EventValues.register(ScriptEvent.RightClickEntity.class, Entity.class,
				(ScriptEvent.RightClickEntity event) -> event.target());
		EventValues.register(ScriptEvent.LeftClickEntity.class, ServerPlayerEntity.class,
				(ScriptEvent.LeftClickEntity event) -> event.player());
		EventValues.register(ScriptEvent.LeftClickEntity.class, Entity.class,
				(ScriptEvent.LeftClickEntity event) -> event.target());
	}
}
