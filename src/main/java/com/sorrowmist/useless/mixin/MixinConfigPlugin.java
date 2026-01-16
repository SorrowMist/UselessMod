package com.sorrowmist.useless.mixin;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinConfigPlugin implements IMixinConfigPlugin {
    
    private static final String MODID_GTOCORE = "gtocore";
    
    @Override
    public void onLoad(String mixinPackage) {
    }
    
    @Override
    public String getRefMapperConfig() {
        return null;
    }
    
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 检查这两个mixin是否需要gtocore模组
        if (mixinClassName.endsWith("jei.ForgePluginFinderMixin") || 
            mixinClassName.endsWith("emi.EmiAgnosMixin")) {
            return isModLoaded(MODID_GTOCORE);
        }
        return true;
    }
    
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }
    
    @Override
    public List<String> getMixins() {
        return null;
    }
    
    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
    
    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
    
    private static boolean isModLoaded(String modId) {
        if (ModList.get() == null) {
            return LoadingModList.get().getMods().stream()
                    .map(ModInfo::getModId)
                    .anyMatch(modId::equals);
        } else {
            return ModList.get().isLoaded(modId);
        }
    }
}