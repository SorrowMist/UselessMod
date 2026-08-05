package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceFaceAccessor;
import com.sorrowmist.useless.init.ModBlockEntities;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Mekanism-only storage and native capability bridge. */
public final class MekanismChemicalCompat {
    public static final String MOD_ID = "mekanism";
    public static final int TANK_COUNT = 3;

    private MekanismChemicalCompat() {
    }

    public static FurnaceChemicalStorage createStorage(long capacity, Runnable onChanged) {
        return new MekanismChemicalStorage(TANK_COUNT, capacity, onChanged);
    }

    /** Converts through the currently registered optional key provider. */
    public static @Nullable GenericStack toGenericStack(ChemicalStack stack) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= 0L) return null;
        return com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders
                .get().toGenericStack(new MekanismChemicalStackView(stack));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        ChemicalCompatProviders.register(new Provider());
        event.registerBlockEntity(
                Capabilities.CHEMICAL.block(),
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> createHandler(
                        blockEntity.getInputChemicalStorage(), blockEntity.getOutputChemicalStorage(), side, blockEntity));
    }

    private static final class Provider implements ChemicalCompatProvider {
        @Override
        public FurnaceChemicalStorage createStorage(long capacity, Runnable onChanged) {
            return MekanismChemicalCompat.createStorage(capacity, onChanged);
        }

        @Override
        public @Nullable ChemicalHandlerView getAdjacentHandler(Level level, BlockPos pos, BlockState state,
                                                                BlockEntity entity, @Nullable Direction side) {
            return MekanismChemicalCompat.getAdjacentHandler(level, pos, state, entity, side);
        }
    }

    @Nullable
    public static ChemicalHandlerView getAdjacentHandler(Level level, BlockPos pos, BlockState state,
                                                          BlockEntity entity, @Nullable Direction side) {
        if (level == null || entity == null) return null;
        IChemicalHandler handler = level.getCapability(Capabilities.CHEMICAL.block(), pos, state, entity, side);
        return handler == null ? null : new HandlerView(handler);
    }

    public static IChemicalHandler createHandler(FurnaceChemicalStorage input, FurnaceChemicalStorage output,
                                                 @Nullable Direction side, @Nullable FurnaceFaceAccessor owner) {
        return new FurnaceChemicalHandler(input, output, side, owner);
    }

    private static final class FurnaceChemicalHandler implements IChemicalHandler {
        private final FurnaceChemicalStorage input;
        private final FurnaceChemicalStorage output;
        private final Direction side;
        private final FurnaceFaceAccessor owner;

        private FurnaceChemicalHandler(FurnaceChemicalStorage input, FurnaceChemicalStorage output,
                                       @Nullable Direction side, @Nullable FurnaceFaceAccessor owner) {
            this.input = input;
            this.output = output;
            this.side = side;
            this.owner = owner;
        }

        @Override
        public int getChemicalTanks() {
            return this.input.size() + this.output.size();
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            return nativeStack(tank);
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
            FurnaceChemicalStorage target = targetStorage(tank);
            int local = localSlot(tank);
            if (target != null) {
                target.setStackInSlot(local, new MekanismChemicalStackView(stack));
            }
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            FurnaceChemicalStorage target = targetStorage(tank);
            return target == null ? 0L : target.capacity(localSlot(tank));
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            return tank >= 0 && tank < getChemicalTanks() && stack != null && !stack.isEmpty()
                    && targetStorage(tank) == this.input;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (stack == null || stack.isEmpty() || !allowsInput()) return stack;
            if (tank < 0 || tank >= this.input.size()) return stack;
            ChemicalStackView remainder = this.input.insertChemical(
                    tank, new MekanismChemicalStackView(stack), action.simulate());
            return remainder instanceof MekanismChemicalStackView mek ? mek.stack() : stack;
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            if (!allowsOutput() || tank < this.input.size()) return ChemicalStack.EMPTY;
            int local = tank - this.input.size();
            ChemicalStackView extracted = this.output.extractChemical(local, amount, action.simulate());
            return extracted instanceof MekanismChemicalStackView mek ? mek.stack() : ChemicalStack.EMPTY;
        }

        private ChemicalStack nativeStack(int tank) {
            FurnaceChemicalStorage target = targetStorage(tank);
            if (target instanceof MekanismChemicalStorage mek) {
                return mek.getNativeStack(localSlot(tank));
            }
            return ChemicalStack.EMPTY;
        }

        @Nullable
        private FurnaceChemicalStorage targetStorage(int tank) {
            if (tank >= 0 && tank < this.input.size()) return this.input;
            if (tank >= this.input.size() && tank < getChemicalTanks()) return this.output;
            return null;
        }

        private int localSlot(int tank) {
            return tank < this.input.size() ? tank : tank - this.input.size();
        }

        private boolean allowsInput() {
            FurnaceFaceMode mode = faceMode();
            return mode == null || mode.allowsMaterialInput();
        }

        private boolean allowsOutput() {
            FurnaceFaceMode mode = faceMode();
            return mode == null || mode.allowsMaterialOutput();
        }

        @Nullable
        private FurnaceFaceMode faceMode() {
            if (this.side == null || this.owner == null) return null;
            FurnaceFace face = FurnaceFace.fromDirection(this.side, this.owner.getFacing());
            return face == null ? FurnaceFaceMode.DISABLED : this.owner.getFaceMode(face);
        }
    }

    private static final class HandlerView implements ChemicalHandlerView {
        private final IChemicalHandler handler;

        private HandlerView(IChemicalHandler handler) {
            this.handler = handler;
        }

        @Override
        public ChemicalStackView insertChemical(ChemicalStackView stack, boolean simulate) {
            if (!(stack instanceof MekanismChemicalStackView mek)) return stack;
            ChemicalStack remainder = this.handler.insertChemical(
                    mek.stack(), simulate ? Action.SIMULATE : Action.EXECUTE);
            return remainder.isEmpty() ? ChemicalStackView.EMPTY : new MekanismChemicalStackView(remainder);
        }

        @Override
        public ChemicalStackView extractChemical(long amount, boolean simulate) {
            ChemicalStack extracted = this.handler.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
            return extracted.isEmpty() ? ChemicalStackView.EMPTY : new MekanismChemicalStackView(extracted);
        }

        @Override
        public ChemicalStackView extractChemical(ChemicalStackView stack, long amount, boolean simulate) {
            if (!(stack instanceof MekanismChemicalStackView mek)) return ChemicalStackView.EMPTY;
            ChemicalStack extracted = this.handler.extractChemical(
                    mek.stack().copyWithAmount(amount), simulate ? Action.SIMULATE : Action.EXECUTE);
            return extracted.isEmpty() ? ChemicalStackView.EMPTY : new MekanismChemicalStackView(extracted);
        }
    }
}
