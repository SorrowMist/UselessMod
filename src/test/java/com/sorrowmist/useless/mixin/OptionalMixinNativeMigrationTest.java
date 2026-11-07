package com.sorrowmist.useless.mixin;

import com.sammy.malum.core.handlers.SoulDataHandler;
import com.sammy.malum.core.handlers.SpiritHarvestHandler;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.itemobliterator.ItemObliteratorProtection;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.stafflink.PressureSealRegistry;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.mixin.itemobliterator.ItemObliteratorContainerMixin;
import com.sorrowmist.useless.mixin.itemobliterator.ItemObliteratorUtilsMixin;
import com.sorrowmist.useless.mixin.malum.SoulHarvestDropMixin;
import com.sorrowmist.useless.mixin.malum.SoulHarvestHandlerMixin;
import com.sorrowmist.useless.mixin.pneumaticcraft.MachineAirHandlerMixin;
import elocindev.item_obliterator.forge.utils.Utils;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.capabilities.MachineAirHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Checks native targets and callback rules; does not claim to run a transformed game. */
class OptionalMixinNativeMigrationTest {
    private static Item beef;
    private static Item protectedItem;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        beef = InventoryTestBootstrap.registerItem("optional_mixin_beef", EndlessBeafItem::new);
        protectedItem = InventoryTestBootstrap.registerItem(new net.minecraft.resources.ResourceLocation("useless_mod", "mixin_protected"),
                () -> new Item(new Item.Properties()));
    }

    @Test void allFiveOptionalMixinsNameExistingNativeMethodsWithExactDescriptors() throws Exception {
        nativeTarget(SoulHarvestDropMixin.class, SpiritHarvestHandler.class);
        nativeTarget(SoulHarvestHandlerMixin.class, SoulDataHandler.class);
        nativeTarget(MachineAirHandlerMixin.class, MachineAirHandler.class);
        nativeTarget(ItemObliteratorContainerMixin.class, elocindev.item_obliterator.forge.ItemObliterator.class);
        nativeTarget(ItemObliteratorUtilsMixin.class, Utils.class);
        assertNotNull(elocindev.item_obliterator.forge.ItemObliterator.class.getDeclaredMethod(
                "item_obliterator$removeFromInventory", PlayerContainerEvent.class));
    }

    @Test void enabledMalumModeExposesOnlyPositiveUncancelledDamage() throws Exception {
        LivingEntity victim = mock(LivingEntity.class);
        DamageSource damage = damage(true);
        try (var nativeSoul = mockStatic(SoulDataHandler.class)) {
            var positive = new LivingHurtEvent(victim, damage, 2.0F);
            CallbackInfo callback = new CallbackInfo("exposeSoul", true);
            invokeExposure(positive, callback);
            assertTrue(callback.isCancelled());
            nativeSoul.verify(() -> SoulDataHandler.exposeSoul(victim));
            for (float amount : new float[]{0.0F, -1.0F, Float.NaN}) {
                callback = new CallbackInfo("exposeSoul", true);
                invokeExposure(new LivingHurtEvent(victim, damage, amount), callback);
                assertFalse(callback.isCancelled());
            }
            var cancelled = new LivingHurtEvent(victim, damage, 2.0F);
            cancelled.setCanceled(true);
            invokeExposure(cancelled, new CallbackInfo("exposeSoul", true));
            nativeSoul.verifyNoMoreInteractions();
        }
    }

    @Test void disabledMalumModeCancelsNativeExposureAndDeathDropsWithoutCallingItsCapabilityApi() throws Exception {
        DamageSource damage = damage(false);
        try (var nativeSoul = mockStatic(SoulDataHandler.class)) {
            CallbackInfo exposure = new CallbackInfo("exposeSoul", true);
            invokeExposure(new LivingHurtEvent(mock(LivingEntity.class), damage, 1.0F), exposure);
            assertTrue(exposure.isCancelled());
            CallbackInfo death = new CallbackInfo("spawnSpiritsOnDeath", true);
            var method = SoulHarvestDropMixin.class.getDeclaredMethod("uselessMod$disableBeefSoulDrops", LivingDeathEvent.class, CallbackInfo.class);
            method.setAccessible(true);
            method.invoke(null, new LivingDeathEvent(mock(LivingEntity.class), damage), death);
            assertTrue(death.isCancelled());
            nativeSoul.verifyNoInteractions();
        }
    }

    @Test void unrelatedWeaponLeavesTheNativeMalumHandlerInCharge() throws Exception {
        Player player = mock(Player.class);
        when(player.getMainHandItem()).thenReturn(new ItemStack(Items.IRON_SWORD));
        DamageSource source = mock(DamageSource.class);
        when(source.getEntity()).thenReturn(player);
        try (var nativeSoul = mockStatic(SoulDataHandler.class)) {
            CallbackInfo callback = new CallbackInfo("exposeSoul", true);
            invokeExposure(new LivingHurtEvent(mock(LivingEntity.class), source, 3.0F), callback);
            assertFalse(callback.isCancelled());
            nativeSoul.verifyNoInteractions();
        }
    }

    @Test void pneumaticSealOnlyTouchesAnActivelyLinkedServerEndpoint() throws Exception {
        var mixin = mock(MachineAirHandlerMixin.class, withSettings().extraInterfaces(IAirHandlerMachine.class));
        var nativeAir = (IAirHandlerMachine) mixin;
        BlockEntity owner = mock(BlockEntity.class);
        Level level = mock(Level.class);
        when(owner.getLevel()).thenReturn(level);
        when(owner.getBlockPos()).thenReturn(BlockPos.ZERO);
        var callback = MachineAirHandlerMixin.class.getDeclaredMethod("uselessmod$sealWirelessLink", BlockEntity.class, CallbackInfo.class);
        callback.setAccessible(true);
        try (var seals = mockStatic(PressureSealRegistry.class)) {
            when(level.isClientSide()).thenReturn(true);
            callback.invoke(mixin, owner, new CallbackInfo("tick", false));
            seals.verifyNoInteractions();
            when(level.isClientSide()).thenReturn(false);
            seals.when(PressureSealRegistry::isEmpty).thenReturn(true);
            callback.invoke(mixin, owner, new CallbackInfo("tick", false));
            verifyNoInteractions(nativeAir);
            seals.when(PressureSealRegistry::isEmpty).thenReturn(false);
            seals.when(() -> PressureSealRegistry.isSealed(level, BlockPos.ZERO)).thenReturn(true);
            callback.invoke(mixin, owner, new CallbackInfo("tick", false));
            verify(nativeAir).setSideLeaking(null);
            verifyNoMoreInteractions(nativeAir);
        }
    }

    @Test void itemProtectionIsScopedToThisExactNamespace() {
        assertTrue(ItemObliteratorProtection.isProtectedItemId("useless_mod:part"));
        assertFalse(ItemObliteratorProtection.isProtectedItemId("useless_mod_addon:part"));
        assertFalse(ItemObliteratorProtection.isProtectedItemId(null));
        assertFalse(ItemObliteratorProtection.isProtected(new ItemStack(Items.STONE)));
        assertFalse(ItemObliteratorProtection.isProtected(ItemStack.EMPTY));
        assertTrue(ItemObliteratorProtection.isProtected(new ItemStack(protectedItem)));
    }

    @Test void obliteratorIdAndNbtStackPathsStopOnlyForProtectedItems() throws Exception {
        for (String methodName : List.of("uselessMod$protectItemId", "uselessMod$protectRecipeOutput",
                "uselessMod$protectInteraction", "uselessMod$protectAttack")) {
            Method method = ItemObliteratorUtilsMixin.class.getDeclaredMethod(methodName, String.class, CallbackInfoReturnable.class);
            method.setAccessible(true);
            var protectedResult = new CallbackInfoReturnable<Boolean>(methodName, true);
            method.invoke(null, "useless_mod:part", protectedResult);
            assertTrue(protectedResult.isCancelled());
            assertFalse(protectedResult.getReturnValue());
            var other = new CallbackInfoReturnable<Boolean>(methodName, true);
            method.invoke(null, "other:part", other);
            assertFalse(other.isCancelled());
        }
        Method stackPath = ItemObliteratorUtilsMixin.class.getDeclaredMethod("uselessMod$protectItemStack", ItemStack.class, CallbackInfoReturnable.class);
        stackPath.setAccessible(true);
        ItemStack stack = new ItemStack(protectedItem);
        stack.getOrCreateTag().putString("blocked_nbt", "retained");
        var callback = new CallbackInfoReturnable<Boolean>("stack", true);
        stackPath.invoke(null, stack, callback);
        assertTrue(callback.isCancelled());
        assertFalse(callback.getReturnValue());
        assertEquals("retained", stack.getTag().getString("blocked_nbt"));
    }

    @Test void obliteratorContainerRedirectStillRemovesOtherBlacklistedItems() throws Exception {
        var mixin = mock(ItemObliteratorContainerMixin.class);
        Method method = ItemObliteratorContainerMixin.class.getDeclaredMethod("uselessMod$keepProtectedItems", List.class, Object.class);
        method.setAccessible(true);
        var blacklist = List.of("useless_mod:part", "minecraft:stone");
        assertEquals(false, method.invoke(mixin, blacklist, "useless_mod:part"));
        assertEquals(true, method.invoke(mixin, blacklist, "minecraft:stone"));
        assertEquals(false, method.invoke(mixin, blacklist, "minecraft:dirt"));
    }

    private static DamageSource damage(boolean enabled) {
        ItemStack stack = new ItemStack(beef);
        ComponentAccess.set(stack, UComponents.BeefMalumSpiritEnabledComponent.get(), enabled);
        Player player = mock(Player.class);
        when(player.getMainHandItem()).thenReturn(stack);
        DamageSource source = mock(DamageSource.class);
        when(source.getEntity()).thenReturn(player);
        return source;
    }
    private static void invokeExposure(LivingHurtEvent event, CallbackInfo callback) throws Exception {
        Method method = SoulHarvestHandlerMixin.class.getDeclaredMethod("uselessMod$controlBeefSoulExposure", LivingHurtEvent.class, CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(null, event, callback);
    }
    private static void nativeTarget(Class<?> mixin, Class<?> target) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = mixin.getResourceAsStream("/" + mixin.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
        }
        AnnotationNode mixinAnnotation = node.invisibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        assertTrue(mixinAnnotation.values.toString().contains(target.getName()));
        assertTrue(node.invisibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")));
        for (var method : node.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")) continue;
                int index = annotation.values.indexOf("method");
                if (index < 0) continue;
                @SuppressWarnings("unchecked") List<String> selectors = (List<String>) annotation.values.get(index + 1);
                for (String selector : selectors) {
                    assertTrue(java.util.Arrays.stream(target.getDeclaredMethods()).anyMatch(nativeMethod -> selector.equals(nativeMethod.getName() + Type.getMethodDescriptor(nativeMethod))), selector);
                }
            }
        }
    }
}
