package dev.me.master.skript.mixin.itemconsume;

import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.ScriptEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "consumeItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket"
                    + "(Lnet/minecraft/network/packet/Packet;)V"),
            cancellable = true)
    private void skript$consumeItem(CallbackInfo ci) {
        if (!EventDispatch.hasListeners(ScriptEvent.ItemConsume.class)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ItemStack item = player.getActiveItem();
        Hand hand = player.getActiveHand();
        if (!item.equals(player.getStackInHand(hand))) {
            return;
        }
        ScriptEvent.ItemConsume event = new ScriptEvent.ItemConsume(player, item.copyWithCount(1), hand);
        if (EventDispatch.fire(event).isCancelled()) {
            player.clearActiveItem();
            ci.cancel();
        }
    }
}
