package dev.me.master.skript.mixin.totempop;

import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "tryUseTotem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;sendEntityStatus(Lnet/minecraft/entity/Entity;B)V",
            shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void skript$totemPop(DamageSource source, CallbackInfoReturnable<Boolean> callback, ItemStack totem) {
        if (!EventDispatch.hasListeners(ScriptEvent.TotemPop.class)) {
            return;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.getWorld() instanceof ServerWorld) {
            EventDispatch.fire(new ScriptEvent.TotemPop(entity, totem.copyWithCount(1), source));
        }
    }
}
