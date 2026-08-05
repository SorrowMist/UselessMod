package com.sorrowmist.useless.mixin;

import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Keeps optional Mekanism mixins from being resolved when Mekanism is absent. */
public final class UselessMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String MEKANISM_MIXIN_PREFIX =
            "com.sorrowmist.useless.mixin.mek.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MEKANISM_MIXIN_PREFIX)) {
            return true;
        }
        try {
            return ModList.get().isLoaded("mekanism");
        } catch (RuntimeException ignored) {
            // Mixin can be initialized while the mod list is still being assembled.
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
