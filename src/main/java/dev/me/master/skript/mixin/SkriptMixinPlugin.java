package dev.me.master.skript.mixin;

import dev.me.master.skript.config.SkriptConfig;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SkriptMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "dev.me.master.skript.mixin";
    private static final String MIXIN_PREFIX = MIXIN_PACKAGE + ".";

    @Override
    public void onLoad(String mixinPackage) {
        if (!MIXIN_PACKAGE.equals(mixinPackage)) {
            throw new IllegalArgumentException("Unexpected Skript Mixin package: " + mixinPackage);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PREFIX)) {
            throw new IllegalArgumentException("Foreign Mixin requested from Skript: " + mixinClassName);
        }
        return switch (mixinClassName.substring(MIXIN_PREFIX.length())) {
            case "CommandManagerMixin", "CommandNodeAccessMixin", "ServerPlayerInteractionManagerMixin" -> true;
            case "itemconsume.ServerPlayerEntityMixin" -> SkriptConfig.INSTANCE.itemConsumeEnabled;
            case "totempop.LivingEntityMixin" -> SkriptConfig.INSTANCE.totemPopEnabled;
            default -> throw new IllegalArgumentException("Unknown Skript patch: " + mixinClassName);
        };
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
