package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, UselessMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HOWL = SOUNDS.register(
            "howl",
            () -> SoundEvent.createVariableRangeEvent(UselessMod.id("howl"))
    );

    private ModSounds() {
    }
}
