package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.*;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceFaceAccessor;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.*;
import mekanism.api.chemical.infuse.*;
import mekanism.api.chemical.pigment.*;
import mekanism.api.chemical.slurry.*;
import net.minecraft.core.Direction;
import java.util.*;

/** Split native capabilities, each enforcing form, side and shared-slot ownership. */
public final class NativeFurnaceChemicalHandlers {
    private NativeFurnaceChemicalHandlers() {}
    static abstract class TypedHandler<C extends Chemical<C>,S extends ChemicalStack<C>> implements IChemicalHandler<C,S> {
        final FurnaceChemicalStorage input,output;
        final Direction side;
        final FurnaceFaceAccessor owner;
        final Class<S> stackType;
        final S empty;
        TypedHandler(FurnaceChemicalStorage input,FurnaceChemicalStorage output,Direction side,FurnaceFaceAccessor owner,Class<S> type,S empty) {
            this.input=input; this.output=output; this.side=side; this.owner=owner; this.stackType=type; this.empty=empty;
        }
        public S getEmptyStack() { return empty; }
        public int getTanks() { return input.size()+output.size(); }
        FurnaceChemicalStorage storage(int slot) { return slot<0 || slot>=getTanks() ? null : slot<input.size()?input:output; }
        int local(int slot) { return slot<input.size()?slot:slot-input.size(); }
        S typed(ChemicalStackView view) {
            return view instanceof MekanismChemicalStackView mek && stackType.isInstance(mek.stack()) ? stackType.cast(mek.stack()) : empty;
        }
        public S getChemicalInTank(int tank) {
            var store=storage(tank); return store == null ? empty : typed(store.getStackInSlot(local(tank)));
        }
        public void setChemicalInTank(int tank,S stack) {
            var store=storage(tank);
            if(store == null) return;
            var current=store.getStackInSlot(local(tank));
            // A gas view must not clear a pigment/infusion/slurry occupying the shared slot.
            if(!current.isEmpty() && typed(current).isEmpty()) return;
            store.setStackInSlot(local(tank),new MekanismChemicalStackView(stack));
        }
        public long getTankCapacity(int tank) { var store=storage(tank); return store == null?0:store.capacity(local(tank)); }
        public boolean isValid(int tank,S stack) { return storage(tank)==input && stack!=null && !stack.isEmpty() && allows(true); }
        public S insertChemical(int tank,S stack,Action action) {
            if(!isValid(tank,stack)) return stack;
            return typed(input.insertChemical(tank,new MekanismChemicalStackView(stack),action.simulate()));
        }
        public S extractChemical(int tank,long amount,Action action) {
            if(storage(tank)!=output || !allows(false) || getChemicalInTank(tank).isEmpty()) return empty;
            return typed(output.extractChemical(local(tank),amount,action.simulate()));
        }
        boolean allows(boolean insertion) {
            if(side == null || owner == null) return true;
            var face=FurnaceFace.fromDirection(side,owner.getFacing());
            var mode=face == null ? FurnaceFaceMode.DISABLED : owner.getFaceMode(face);
            return insertion ? mode.allowsMaterialInput() : mode.allowsMaterialOutput();
        }
    }

    static final class GasHandler extends TypedHandler<Gas,GasStack> implements IGasHandler {
        GasHandler(FurnaceChemicalStorage in,FurnaceChemicalStorage out,Direction side,FurnaceFaceAccessor owner) { super(in,out,side,owner,GasStack.class,GasStack.EMPTY); }
    }

    static final class InfusionHandler extends TypedHandler<InfuseType,InfusionStack> implements IInfusionHandler {
        InfusionHandler(FurnaceChemicalStorage in,FurnaceChemicalStorage out,Direction side,FurnaceFaceAccessor owner) { super(in,out,side,owner,InfusionStack.class,InfusionStack.EMPTY); }
    }

    static final class PigmentHandler extends TypedHandler<Pigment,PigmentStack> implements IPigmentHandler {
        PigmentHandler(FurnaceChemicalStorage in,FurnaceChemicalStorage out,Direction side,FurnaceFaceAccessor owner) { super(in,out,side,owner,PigmentStack.class,PigmentStack.EMPTY); }
    }

    static final class SlurryHandler extends TypedHandler<Slurry,SlurryStack> implements ISlurryHandler {
        SlurryHandler(FurnaceChemicalStorage in,FurnaceChemicalStorage out,Direction side,FurnaceFaceAccessor owner) { super(in,out,side,owner,SlurryStack.class,SlurryStack.EMPTY); }
    }

}
