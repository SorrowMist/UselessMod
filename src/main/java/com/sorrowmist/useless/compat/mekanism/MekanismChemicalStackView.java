package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Mekanism-backed implementation of the core chemical view. */
public final class MekanismChemicalStackView implements ChemicalStackView {
    private final ChemicalStack stack;

    public MekanismChemicalStackView(ChemicalStack stack) {
        this.stack = stack == null ? ChemicalStack.EMPTY : stack.copy();
    }

    public ChemicalStack stack() {
        return this.stack;
    }

    @Override
    public Object typeKey() {
        return this.stack.isEmpty() ? ChemicalStackView.EmptyType.INSTANCE : this.stack.getChemicalHolder();
    }

    @Override
    public long amount() {
        return this.stack.getAmount();
    }

    @Override
    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    @Override
    public ChemicalStackView copyWithAmount(long amount) {
        return amount <= 0L || this.stack.isEmpty()
                ? ChemicalStackView.EMPTY
                : new MekanismChemicalStackView(this.stack.copyWithAmount(amount));
    }

    @Override
    public Component displayName() {
        return this.stack.isEmpty() ? Component.empty() : this.stack.getChemical().getTextComponent();
    }

    @Override
    public @Nullable ResourceLocation icon() {
        return this.stack.isEmpty() ? null : this.stack.getChemical().getIcon();
    }

    @Override
    public int tintColor() {
        return this.stack.isEmpty() ? 0xFFFFFFFF : this.stack.getChemicalTint();
    }
}
