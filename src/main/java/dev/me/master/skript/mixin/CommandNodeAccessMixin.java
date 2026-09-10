package dev.me.master.skript.mixin;

import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccessMixin {

	@Accessor(value = "children", remap = false)
	Map<String, CommandNode<?>> skript$getChildren();

	@Accessor(value = "literals", remap = false)
	Map<String, CommandNode<?>> skript$getLiterals();

	@Accessor(value = "arguments", remap = false)
	Map<String, CommandNode<?>> skript$getArguments();
}
