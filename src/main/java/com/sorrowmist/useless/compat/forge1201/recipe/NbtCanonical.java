package com.sorrowmist.useless.compat.forge1201.recipe;

import net.minecraft.nbt.*;
import java.util.TreeSet;
import java.util.StringJoiner;

/** Parseable SNBT with sorted compound keys, preserving numeric types and ordered lists/arrays. */
public final class NbtCanonical {
    private NbtCanonical() {}
    public static String snbt(Tag value) {
        if(value==null) return "null";
        if(value instanceof CompoundTag compound) {
            StringJoiner entries=new StringJoiner(",","{","}");
            for(String key:new TreeSet<>(compound.getAllKeys()))
                entries.add(StringTag.quoteAndEscape(key)+":"+snbt(compound.get(key)));
            return entries.toString();
        }
        if(value instanceof ListTag list) {
            StringJoiner entries=new StringJoiner(",","[","]");
            for(Tag entry:list) entries.add(snbt(entry));
            return entries.toString();
        }
        return value.toString();
    }
}
