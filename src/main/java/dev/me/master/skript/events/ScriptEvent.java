package dev.me.master.skript.events;

import dev.me.master.skript.script.SkriptScript;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public sealed interface ScriptEvent permits
		ScriptEvent.PlayerJoin,
		ScriptEvent.PlayerQuit,
		ScriptEvent.Chat,
		ScriptEvent.Damage,
		ScriptEvent.Death,
		ScriptEvent.BlockBreak,
		ScriptEvent.BlockPlace,
		ScriptEvent.RightClickBlock,
		ScriptEvent.LeftClickBlock,
		ScriptEvent.RightClickEntity,
		ScriptEvent.LeftClickEntity,
		ScriptEvent.Respawn,
		ScriptEvent.Command,
		ScriptEvent.Periodic,
		ScriptEvent.ScriptLoad {

	record PlayerJoin(ServerPlayerEntity player) implements ScriptEvent {
	}

	record PlayerQuit(ServerPlayerEntity player) implements ScriptEvent {
	}

	record Chat(ServerPlayerEntity player, String message) implements ScriptEvent {
	}

	record Damage(LivingEntity victim, DamageSource source, float amount) implements ScriptEvent {
	}

	record Death(LivingEntity victim, DamageSource source) implements ScriptEvent {
	}

	record BlockBreak(ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack tool) implements ScriptEvent {
	}

	record BlockPlace(ServerPlayerEntity player, BlockPos pos, BlockState placed, ServerWorld world)
			implements ScriptEvent {
	}

	record RightClickBlock(ServerPlayerEntity player, BlockPos pos, BlockState state, Hand hand, ServerWorld world)
			implements ScriptEvent {
	}

	record LeftClickBlock(ServerPlayerEntity player, BlockPos pos, Direction direction, ServerWorld world)
			implements ScriptEvent {
	}

	record RightClickEntity(ServerPlayerEntity player, Entity target, Hand hand) implements ScriptEvent {
	}

	record LeftClickEntity(ServerPlayerEntity player, Entity target) implements ScriptEvent {
	}

	record Respawn(ServerPlayerEntity player) implements ScriptEvent {
	}

	record Command(String command, ServerCommandSourceView source) implements ScriptEvent {
	}

	interface ServerCommandSourceView {
		String name();

		boolean isPlayer();

		ServerPlayerEntity asPlayer();
	}

	record Periodic(long periodTicks) implements ScriptEvent {
	}

	record ScriptLoad(SkriptScript script) implements ScriptEvent {
	}
}
