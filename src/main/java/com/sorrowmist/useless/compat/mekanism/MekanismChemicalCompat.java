package com.sorrowmist.useless.compat.mekanism;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.*;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.compat.forge1201.capability.CapabilityAccess;
import com.sorrowmist.useless.compat.forge1201.capability.RegisterCapabilitiesEvent;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.*;
import mekanism.api.chemical.infuse.*;
import mekanism.api.chemical.pigment.*;
import mekanism.api.chemical.slurry.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tier.ChemicalTankTier;
import mekanism.common.util.ChemicalUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Native split capabilities share the furnace's three long-sized storage slots. */
public final class MekanismChemicalCompat {
    public static final String MOD_ID="mekanism";
    public static final int TANK_COUNT=3;
    private MekanismChemicalCompat() {}
    public static FurnaceChemicalStorage createStorage(long capacity,Runnable changed) {
        return new MekanismChemicalStorage(TANK_COUNT,capacity,changed);
    }
    public static GenericStack toGenericStack(ChemicalStack<?> stack) {
        return stack == null || stack.isEmpty() ? null : ChemicalKeyProviders.get().toGenericStack(new MekanismChemicalStackView(stack));
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        ChemicalCompatProviders.register(new Provider());
        var type=ModBlockEntities.ADVANCED_ALLOY_FURNACE.get();
        event.registerBlockEntity(Capabilities.GAS_HANDLER,type,(be,side)->new NativeFurnaceChemicalHandlers.GasHandler(be.getInputChemicalStorage(),be.getOutputChemicalStorage(),side,be));
        event.registerBlockEntity(Capabilities.INFUSION_HANDLER,type,(be,side)->new NativeFurnaceChemicalHandlers.InfusionHandler(be.getInputChemicalStorage(),be.getOutputChemicalStorage(),side,be));
        event.registerBlockEntity(Capabilities.PIGMENT_HANDLER,type,(be,side)->new NativeFurnaceChemicalHandlers.PigmentHandler(be.getInputChemicalStorage(),be.getOutputChemicalStorage(),side,be));
        event.registerBlockEntity(Capabilities.SLURRY_HANDLER,type,(be,side)->new NativeFurnaceChemicalHandlers.SlurryHandler(be.getInputChemicalStorage(),be.getOutputChemicalStorage(),side,be));
    }
    private static final class Provider implements ChemicalCompatProvider {
        public FurnaceChemicalStorage createStorage(long capacity,Runnable changed) { return MekanismChemicalCompat.createStorage(capacity,changed); }
        public ChemicalHandlerView getAdjacentHandler(Level level,BlockPos pos,BlockState state,BlockEntity entity,Direction side) {
            return MekanismChemicalCompat.getAdjacentHandler(level,pos,state,entity,side);
        }
        public ChemicalStackView chemicalInItem(ItemStack item) { return MekanismChemicalCompat.chemicalInItem(item); }
        public ItemStack markerForChemical(Object ingredient) { return MekanismChemicalCompat.markerForChemical(ingredient); }
        public ResourceLocation chemicalIdOf(Object type) { return MekanismChemicalCompat.chemicalIdOf(type); }
    }
    public static ResourceLocation chemicalIdOf(Object type) {
        return type instanceof Chemical<?> chemical ? chemical.getRegistryName() : null;
    }
    public static ChemicalStackView chemicalInItem(ItemStack item) {
        if(item == null || item.isEmpty()) return null;
        List<IChemicalHandler<?,?>> handlers=new ArrayList<>();
        add(handlers,CapabilityAccess.get(item,Capabilities.GAS_HANDLER));
        add(handlers,CapabilityAccess.get(item,Capabilities.INFUSION_HANDLER));
        add(handlers,CapabilityAccess.get(item,Capabilities.PIGMENT_HANDLER));
        add(handlers,CapabilityAccess.get(item,Capabilities.SLURRY_HANDLER));
        for(var handler:handlers) for(int slot=0;slot<handler.getTanks();slot++) {
            var stack=handler.getChemicalInTank(slot);
            if(!stack.isEmpty()) return new MekanismChemicalStackView(stack);
        }
        return null;
    }
    public static ItemStack markerForChemical(Object ingredient) {
        return ingredient instanceof ChemicalStack<?> stack && !stack.isEmpty()
                ? ChemicalUtil.getFullChemicalTank(ChemicalTankTier.BASIC,stack.getType()) : ItemStack.EMPTY;
    }
    public static ChemicalHandlerView getAdjacentHandler(Level level,BlockPos pos,BlockState state,BlockEntity entity,Direction side) {
        if(level == null || entity == null) return null;
        List<IChemicalHandler<?,?>> handlers=new ArrayList<>();
        add(handlers,CapabilityAccess.get(level,Capabilities.GAS_HANDLER,pos,state,entity,side));
        add(handlers,CapabilityAccess.get(level,Capabilities.INFUSION_HANDLER,pos,state,entity,side));
        add(handlers,CapabilityAccess.get(level,Capabilities.PIGMENT_HANDLER,pos,state,entity,side));
        add(handlers,CapabilityAccess.get(level,Capabilities.SLURRY_HANDLER,pos,state,entity,side));
        return handlers.isEmpty() ? null : new HandlerView(handlers);
    }
    private static void add(List<IChemicalHandler<?,?>> handlers,IChemicalHandler<?,?> handler) {
        if(handler != null) handlers.add(handler);
    }

    /** Route only to the matching native handler; never reinterpret a chemical form. */
    private static final class HandlerView implements ChemicalHandlerView {
        final List<IChemicalHandler<?,?>> handlers;
        HandlerView(List<IChemicalHandler<?,?>> handlers) { this.handlers=List.copyOf(handlers); }
        private static boolean supports(IChemicalHandler<?,?> h,ChemicalStack<?> stack) {
            return h.getEmptyStack().getClass().isInstance(stack);
        }
        @SuppressWarnings({"rawtypes","unchecked"})
        public ChemicalStackView insertChemical(ChemicalStackView view,boolean simulate) {
            if(!(view instanceof MekanismChemicalStackView mek)) return view;
            ChemicalStack<?> remaining=mek.stack();
            for(IChemicalHandler handler:handlers) if(supports(handler,remaining))
                remaining=handler.insertChemical(remaining,simulate?Action.SIMULATE:Action.EXECUTE);
            return new MekanismChemicalStackView(remaining);
        }
        public ChemicalStackView extractChemical(long amount,boolean simulate) {
            for(var handler:handlers) {
                var stack=handler.extractChemical(amount,simulate?Action.SIMULATE:Action.EXECUTE);
                if(!stack.isEmpty()) return new MekanismChemicalStackView(stack);
            }
            return ChemicalStackView.EMPTY;
        }
        @SuppressWarnings({"rawtypes","unchecked"})
        public ChemicalStackView extractChemical(ChemicalStackView view,long amount,boolean simulate) {
            if(!(view instanceof MekanismChemicalStackView mek)) return ChemicalStackView.EMPTY;
            for(IChemicalHandler handler:handlers) if(supports(handler,mek.stack())) {
                var extracted=handler.extractChemical(ChemicalUtil.copyWithAmount(mek.stack(),amount),simulate?Action.SIMULATE:Action.EXECUTE);
                if(!extracted.isEmpty()) return new MekanismChemicalStackView(extracted);
            }
            return ChemicalStackView.EMPTY;
        }
    }
}
