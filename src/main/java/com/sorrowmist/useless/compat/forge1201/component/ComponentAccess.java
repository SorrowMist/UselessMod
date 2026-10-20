package com.sorrowmist.useless.compat.forge1201.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.function.UnaryOperator;
import java.util.function.Supplier;

/** Uses vanilla ItemStack NBT so Forge saves and synchronizes values without extra packets. */
public final class ComponentAccess {
    public static final String ROOT_KEY="useless_mod:components";
    public static final String REMOVED_KEY="useless_mod:removed_components";
    private ComponentAccess() {}
    public static <T> T readTag(CompoundTag root,DataComponentType<T> key) {
        if(root==null || !root.contains(ROOT_KEY,Tag.TAG_COMPOUND)) return null;
        Tag value=root.getCompound(ROOT_KEY).get(key.id().toString());
        if(value==null) return null;
        return key.codec().parse(NbtOps.INSTANCE,value).result().orElse(null);
    }
    public static <T> void writeTag(CompoundTag root,DataComponentType<T> key,T value) {
        clearRemoval(root,key);
        if(value==null) { removeTag(root,key); return; }
        Tag encoded=key.codec().encodeStart(NbtOps.INSTANCE,value).getOrThrow(false,
                message->{ throw new IllegalArgumentException("Invalid component "+key.id()+": "+message); });
        CompoundTag data=root.contains(ROOT_KEY,Tag.TAG_COMPOUND)?root.getCompound(ROOT_KEY):new CompoundTag();
        data.put(key.id().toString(),encoded); root.put(ROOT_KEY,data);
    }
    private static void removeTag(CompoundTag root,DataComponentType<?> key) {
        if(root==null || !root.contains(ROOT_KEY,Tag.TAG_COMPOUND)) return;
        CompoundTag data=root.getCompound(ROOT_KEY); data.remove(key.id().toString());
        if(data.isEmpty()) root.remove(ROOT_KEY);
    }
    private static boolean isRemoved(CompoundTag root,DataComponentType<?> key) {
        return root!=null && root.getCompound(REMOVED_KEY).getBoolean(key.id().toString());
    }
    private static void clearRemoval(CompoundTag root,DataComponentType<?> key) {
        if(root==null || !root.contains(REMOVED_KEY,Tag.TAG_COMPOUND)) return;
        CompoundTag removed=root.getCompound(REMOVED_KEY);
        removed.remove(key.id().toString());
        if(removed.isEmpty()) root.remove(REMOVED_KEY);
    }
    private static Object prototype(ItemStack stack,DataComponentType<?> key) {
        return stack.getItem() instanceof ComponentDefaults defaults ? defaults.componentDefault(key) : null;
    }
    @SuppressWarnings("unchecked")
    public static <T> T get(ItemStack stack,DataComponentType<T> key) {
        if(isRemoved(stack.getTag(),key)) return null;
        T value=readTag(stack.getTag(),key);
        if(value!=null) return value;
        return (T) prototype(stack,key);
    }
    public static <T> T getOrDefault(ItemStack stack,DataComponentType<T> key,T fallback) {
        T value=get(stack,key); return value==null?fallback:value;
    }
    public static boolean has(ItemStack stack,DataComponentType<?> key) {
        return get(stack,key)!=null;
    }
    public static <T> T set(ItemStack stack,DataComponentType<T> key,T value) {
        T old=get(stack,key);
        if(value==null) remove(stack,key); else writeTag(stack.getOrCreateTag(),key,value);
        return old;
    }
    public static <T> T remove(ItemStack stack,DataComponentType<T> key) {
        T old=get(stack,key);
        removeTag(stack.getTag(),key);
        if(prototype(stack,key)!=null) {
            CompoundTag root=stack.getOrCreateTag();
            CompoundTag removed=root.contains(REMOVED_KEY,Tag.TAG_COMPOUND)?root.getCompound(REMOVED_KEY):new CompoundTag();
            removed.putBoolean(key.id().toString(),true); root.put(REMOVED_KEY,removed);
        } else clearRemoval(stack.getTag(),key);
        return old;
    }
    public static <T> T update(ItemStack stack,DataComponentType<T> key,T fallback,UnaryOperator<T> change) {
        T value=change.apply(getOrDefault(stack,key,fallback)); set(stack,key,value); return value;
    }
    /** Copies detached NBT, Forge capabilities and effective defaults across item variants. */
    public static void copyState(ItemStack source,ItemStack target,
            Iterable<? extends Supplier<? extends DataComponentType<?>>> types) {
        if(source.isEmpty() || target.isEmpty()) return;
        target.deserializeNBT(source.save(new CompoundTag()));
        for(var type:types) copyValue(source,target,type.get());
    }
    private static <T> void copyValue(ItemStack source,ItemStack target,DataComponentType<T> type) {
        set(target,type,get(source,type));
    }
    // AE keys are immutable/hash-stable. Decode from a detached stack so even a codec
    // returning mutable NBT cannot let callers mutate the key held by the crafting CPU.
    public static <T> T get(appeng.api.stacks.AEItemKey key,DataComponentType<T> type) {
        return key==null?null:get(key.toStack(1),type);
    }
    public static <T> T getOrDefault(appeng.api.stacks.AEItemKey key,DataComponentType<T> type,T fallback) {
        T value=get(key,type); return value==null?fallback:value;
    }
    public static boolean has(appeng.api.stacks.AEItemKey key,DataComponentType<?> type) {
        return key!=null && has(key.toStack(1),type);
    }
    // Deferred registry objects implement Supplier; resolve them at use time, not class loading.
    public static <T> T get(ItemStack stack,Supplier<? extends DataComponentType<T>> key) { return get(stack,key.get()); }
    public static <T> T getOrDefault(ItemStack stack,Supplier<? extends DataComponentType<T>> key,T fallback) { return getOrDefault(stack,key.get(),fallback); }
    public static boolean has(ItemStack stack,Supplier<? extends DataComponentType<?>> key) { return has(stack,key.get()); }
    public static <T> T set(ItemStack stack,Supplier<? extends DataComponentType<T>> key,T value) { return set(stack,key.get(),value); }
    public static <T> T remove(ItemStack stack,Supplier<? extends DataComponentType<T>> key) { return remove(stack,key.get()); }
    public static <T> T update(ItemStack stack,Supplier<? extends DataComponentType<T>> key,T fallback,UnaryOperator<T> change) { return update(stack,key.get(),fallback,change); }
}
