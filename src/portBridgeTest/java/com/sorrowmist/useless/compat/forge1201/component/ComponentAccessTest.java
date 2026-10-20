package com.sorrowmist.useless.compat.forge1201.component;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComponentAccessTest {
    private static <T> DataComponentType<T> key(String name, Codec<T> codec) {
        DataComponentType<T> key=DataComponentType.<T>builder().persistent(codec).build();
        key.bind(new ResourceLocation("useless_mod",name));
        return key;
    }

    @Test void customStateRoundTripsAsVanillaNbt() {
        var enabled=key("acceleration",Codec.BOOL);
        var molds=key("selected_molds",Codec.STRING.listOf());
        CompoundTag root=new CompoundTag();
        root.putString("OtherModState","keep");
        ComponentAccess.writeTag(root,enabled,true);
        ComponentAccess.writeTag(root,molds,List.of("mekanism:crusher","create:mixing"));
        CompoundTag restored=root.copy();
        assertEquals(true,ComponentAccess.readTag(restored,enabled));
        assertEquals(List.of("mekanism:crusher","create:mixing"),ComponentAccess.readTag(restored,molds));
        assertEquals("keep",restored.getString("OtherModState"));
    }

    @Test void removingOneValuePreservesOtherKeysAndForeignNbt() {
        var first=key("first",Codec.INT);
        var second=key("second",Codec.INT);
        CompoundTag root=new CompoundTag();root.putInt("Count",64);
        ComponentAccess.writeTag(root,first,4096);
        ComponentAccess.writeTag(root,second,1024);
        ComponentAccess.writeTag(root,first,null);
        assertNull(ComponentAccess.readTag(root,first));
        assertEquals(1024,ComponentAccess.readTag(root,second));
        ComponentAccess.writeTag(root,second,null);
        assertFalse(root.contains(ComponentAccess.ROOT_KEY));
        assertEquals(64,root.getInt("Count"));
    }

    @Test void unboundOrReboundKeysCannotSilentlyCollide() {
        var key=DataComponentType.<Integer>builder().persistent(Codec.INT).build();
        assertThrows(NullPointerException.class, key::id);
        key.bind(new ResourceLocation("useless_mod","first"));
        assertThrows(IllegalStateException.class,()->key.bind(new ResourceLocation("useless_mod","second")));
    }
    @Test void deferredKeysResolveAtUseTimeAndDoNotTouchForeignNbt() {
        var key=key("deferred",Codec.INT);
        java.util.function.Supplier<DataComponentType<Integer>> supplier=()->key;
        com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap.initialize();
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        CompoundTag tag=new CompoundTag(); tag.putString("foreign","keep");
        stack.setTag(tag);
        assertNull(ComponentAccess.get(stack,supplier));
        ComponentAccess.set(stack,supplier,42);
        assertTrue(ComponentAccess.has(stack,supplier));
        ComponentAccess.update(stack,supplier,0,n->n+1);
        assertEquals(43,ComponentAccess.getOrDefault(stack,supplier,0));
        assertEquals(43,ComponentAccess.remove(stack,supplier));
        assertFalse(ComponentAccess.has(stack,supplier));
        assertEquals("keep",tag.getString("foreign"));
    }

    private static net.minecraft.world.item.ItemStack prototypeStack(DataComponentType<Boolean> key,boolean value) {
        com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap.initialize();
        var item=org.mockito.Mockito.mock(net.minecraft.world.item.Item.class,
                org.mockito.Mockito.withSettings().extraInterfaces(ComponentDefaults.class));
        org.mockito.Mockito.when(((ComponentDefaults)item).componentDefault(key)).thenReturn(value);
        var stack=org.mockito.Mockito.spy(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        org.mockito.Mockito.doReturn(item).when(stack).getItem();
        return stack;
    }

    @Test void prototypePresenceExplicitFalseAndRemovalAreDistinct() {
        var enabled=key("prototype_enabled",Codec.BOOL);
        var stack=prototypeStack(enabled,true);
        assertTrue(ComponentAccess.has(stack,enabled));
        assertEquals(true,ComponentAccess.get(stack,enabled));
        assertNull(stack.getTag()); // querying never dirties the stack
        ComponentAccess.set(stack,enabled,false);
        assertEquals(false,ComponentAccess.get(stack,enabled));
        assertTrue(ComponentAccess.has(stack,enabled));
        assertEquals(false,ComponentAccess.remove(stack,enabled));
        assertFalse(ComponentAccess.has(stack,enabled));
        assertNull(ComponentAccess.get(stack,enabled));
        assertEquals(true,ComponentAccess.getOrDefault(stack,enabled,true));
        var restored=prototypeStack(enabled,true);
        restored.setTag(stack.getTag().copy());
        assertNull(ComponentAccess.get(restored,enabled)); // removal survives native stack NBT
        ComponentAccess.set(restored,enabled,true);
        assertTrue(ComponentAccess.has(restored,enabled));
        assertFalse(restored.getTag().contains(ComponentAccess.REMOVED_KEY));
    }

    @Test void nullSetMasksAPrototypeButNotAnUnrelatedNamespace() {
        var enabled=key("prototype_null",Codec.BOOL);
        var foreign=DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build();
        foreign.bind(new ResourceLocation("another_mod","prototype_null"));
        var stack=prototypeStack(enabled,false);
        assertTrue(ComponentAccess.has(stack,enabled));
        assertNull(ComponentAccess.get(stack,foreign));
        assertEquals(false,ComponentAccess.set(stack,enabled,null));
        assertNull(ComponentAccess.get(stack,enabled));
        assertFalse(ComponentAccess.has(stack,enabled));
        ComponentAccess.set(stack,enabled,false);
        assertEquals(false,ComponentAccess.get(stack,enabled));
    }

    @Test void copyingNativeStatePreservesForeignNbtAndDoesNotAliasTheSource() {
        com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap.initialize();
        var key=key("copied_enabled",Codec.BOOL);
        var source=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        source.getOrCreateTag().putString("foreign", "keep");
        ComponentAccess.set(source,key,false);
        var target=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD);
        ComponentAccess.copyState(source,target,List.of(()->key));
        assertEquals(net.minecraft.world.item.Items.EMERALD,target.getItem());
        assertEquals(false,ComponentAccess.get(target,key));
        assertEquals("keep",target.getTag().getString("foreign"));
        target.getTag().putString("foreign","changed");
        assertEquals("keep",source.getTag().getString("foreign"));
    }
}
