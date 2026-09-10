package dev.me.master.skript.mixin;

import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {

	@Unique
	private BlockPos skript$placementPos;
	@Unique
	private BlockState skript$placementBefore;
	@Unique
	private ServerWorld skript$placementWorld;

	@Inject(method = "interactBlock", at = @At("HEAD"))
	private void skript$capturePlacement(
			ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
		if (!(world instanceof ServerWorld serverWorld)) {
			skript$clearPlacement();
			return;
		}
		skript$placementWorld = serverWorld;
		skript$placementPos = hitResult.getBlockPos().offset(hitResult.getSide());
		skript$placementBefore = serverWorld.getBlockState(skript$placementPos);
	}

	@Inject(method = "interactBlock", at = @At("RETURN"))
	private void skript$dispatchPlacement(
			ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
		ServerWorld placementWorld = skript$placementWorld;
		BlockPos placementPos = skript$placementPos;
		BlockState before = skript$placementBefore;
		try {
			if (placementWorld == null || placementPos == null || before == null
					|| !cir.getReturnValue().isAccepted()
					|| !EventDispatch.hasListeners(ScriptEvent.BlockPlace.class))
				return;
			BlockState placed = placementWorld.getBlockState(placementPos);
			if (placed.equals(before) || placed.isAir())
				return;
			EventDispatch.fire(new ScriptEvent.BlockPlace(player, placementPos, placed, placementWorld));
		} finally {
			skript$clearPlacement();
		}
	}

	@Unique
	private void skript$clearPlacement() {
		skript$placementWorld = null;
		skript$placementPos = null;
		skript$placementBefore = null;
	}
}
