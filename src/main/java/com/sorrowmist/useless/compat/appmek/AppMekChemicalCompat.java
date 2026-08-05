package com.sorrowmist.useless.compat.appmek;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.compat.mekanism.MekanismChemicalCompat;
import com.sorrowmist.useless.compat.mekanism.MekanismChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.init.ModBlockEntities;
import me.ramidzkh.mekae2.MekCapabilities;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** AppMek-only native capability and AE key bridge. */
public final class AppMekChemicalCompat {
    public static final String MOD_ID = "appmek";

    private AppMekChemicalCompat() {
    }

    public static void registerKeyProvider() {
        ChemicalKeyProviders.register(new Provider());
    }

    public static void register(RegisterCapabilitiesEvent event) {
        registerKeyProvider();
        event.registerBlockEntity(
                MekCapabilities.CHEMICAL.block(),
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> MekanismChemicalCompat.createHandler(
                        blockEntity.getInputChemicalStorage(), blockEntity.getOutputChemicalStorage(), side, blockEntity));
    }

    /** Converts a concrete Mekanism stack to the AppMek AE key without exposing AppMek to callers. */
    public static @Nullable GenericStack toGenericStack(ChemicalStack stack) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= 0L) return null;
        MekanismKey key = MekanismKey.of(stack.copyWithAmount(1L));
        return key == null ? null : new GenericStack(key, stack.getAmount());
    }

    /** Creates a concrete Mekanism stack from a registered AppMek key. */
    public static @Nullable ChemicalStack toChemicalStack(GenericStack stack) {
        if (stack == null || stack.amount() <= 0L || !(stack.what() instanceof MekanismKey key)) return null;
        ChemicalStack result = key.withAmount(stack.amount());
        return result.isEmpty() ? null : result;
    }

    private static final class Provider implements ChemicalKeyProvider {
        @Override
        public @Nullable GenericStack toGenericStack(com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView stack) {
            if (!(stack instanceof MekanismChemicalStackView mek) || mek.isEmpty() || mek.amount() <= 0L) {
                return null;
            }
            return AppMekChemicalCompat.toGenericStack(mek.stack());
        }

        @Override
        public @Nullable com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView fromGenericStack(GenericStack stack) {
            if (stack == null || stack.amount() <= 0L || !(stack.what() instanceof MekanismKey key)) {
                return null;
            }
            ChemicalStack chemical = AppMekChemicalCompat.toChemicalStack(stack);
            return chemical == null ? null : new MekanismChemicalStackView(chemical);
        }

        @Override
        public boolean isChemicalKey(@Nullable AEKey key) {
            return key instanceof MekanismKey;
        }
    }
}
