package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceFaceAccessor;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.gas.*;
import mekanism.api.chemical.infuse.*;
import mekanism.api.chemical.pigment.*;
import mekanism.api.chemical.slurry.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeChemicalStorageTest {
    @BeforeAll static void bootstrap() throws Exception {
        RecipeTestBootstrap.initialize();
        installRegistry("GAS_REGISTRY");
        installRegistry("INFUSE_TYPE_REGISTRY");
        installRegistry("PIGMENT_REGISTRY");
        installRegistry("SLURRY_REGISTRY");
    }
    // Forge normally creates these registries before ChemicalStack's EMPTY constants.
    // Standalone tests keep native stacks and serialization, supplying only the registry lifecycle.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void installRegistry(String fieldName) throws Exception {
        var keys=new java.util.IdentityHashMap<Object,ResourceLocation>();
        var values=new java.util.HashMap<ResourceLocation,Object>();
        var delegates=new java.util.IdentityHashMap<Object,net.minecraft.core.Holder.Reference>();
        IForgeRegistry registry=mock(IForgeRegistry.class, invocation -> {
            Object[] args=invocation.getArguments();
            return switch(invocation.getMethod().getName()) {
                case "getKey" -> keys.computeIfAbsent(args[0], value -> {
                    var key=new ResourceLocation("useless_port_test",fieldName.toLowerCase()+"_"+keys.size());
                    values.put(key,value); return key;
                });
                case "getDelegateOrThrow" -> delegates.computeIfAbsent(args[0],value -> {
                    net.minecraft.core.Holder.Reference holder=mock(net.minecraft.core.Holder.Reference.class);
                    when(holder.value()).thenReturn(value); when(holder.get()).thenReturn(value); return holder;
                });
                case "getValue" -> values.get(args[0]);
                default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        var field=MekanismAPI.class.getDeclaredField(fieldName);
        field.setAccessible(true); field.set(null,registry);
    }
    static GasStack gas(long amount) { return new GasStack(new Gas(GasBuilder.builder()),amount); }
    static List<ChemicalStack<?>> forms(long amount) {
        return List.of(gas(amount),new InfusionStack(new InfuseType(InfuseTypeBuilder.builder()),amount),
                new PigmentStack(new Pigment(PigmentBuilder.builder()),amount),new SlurryStack(new Slurry(SlurryBuilder.clean()),amount));
    }
    @Test void everyNativeFormPreservesLongAmountsAndDetachedCopies() {
        long large=(long)Integer.MAX_VALUE+123456;
        for(var source:forms(large)) {
            var storage=new MekanismChemicalStorage(1,Long.MAX_VALUE,null);
            storage.setStackInSlot(0,new MekanismChemicalStackView(source));
            source.setAmount(1);
            assertEquals(large,storage.getStackInSlot(0).amount());
            var exposed=storage.getNativeStack(0); exposed.setAmount(2);
            assertEquals(large,storage.getStackInSlot(0).amount());
            assertEquals(large-5,storage.extractChemical(0,large-5,true).amount());
            assertEquals(large,storage.getStackInSlot(0).amount());
            assertEquals(large-5,storage.extractChemical(0,large-5,false).amount());
            assertEquals(5,storage.getStackInSlot(0).amount());
        }
    }
    @Test void simulationCapacityAndCrossFormRejectionConserveMaterials() {
        var changed=new AtomicInteger();
        var storage=new MekanismChemicalStorage(1,100,changed::incrementAndGet);
        var input=new MekanismChemicalStackView(gas(150));
        assertEquals(50,storage.insertChemical(0,input,true).amount());
        assertTrue(storage.getStackInSlot(0).isEmpty()); assertEquals(0,changed.get());
        assertEquals(50,storage.insertChemical(0,input,false).amount());
        assertEquals(100,storage.getStackInSlot(0).amount()); assertEquals(1,changed.get());
        var pigment=new MekanismChemicalStackView(forms(20).get(2));
        assertEquals(20,storage.insertChemical(0,pigment,false).amount());
        assertEquals(100,storage.getStackInSlot(0).amount());
        assertEquals(150,input.amount());
    }
    @Test void wrongNativeCapabilityCannotClearOrExtractAnotherForm() {
        var input=new MekanismChemicalStorage(1,100,null);
        var output=new MekanismChemicalStorage(1,100,null);
        input.setStackInSlot(0,new MekanismChemicalStackView(forms(50).get(2)));
        output.setStackInSlot(0,new MekanismChemicalStackView(forms(60).get(2)));
        var gas=new NativeFurnaceChemicalHandlers.GasHandler(input,output,null,null);
        assertTrue(gas.getChemicalInTank(0).isEmpty());
        gas.setChemicalInTank(0,GasStack.EMPTY);
        assertEquals(50,input.getStackInSlot(0).amount());
        assertTrue(gas.extractChemical(1,30,Action.EXECUTE).isEmpty());
        assertEquals(60,output.getStackInSlot(0).amount());
        var pigments=new NativeFurnaceChemicalHandlers.PigmentHandler(input,output,null,null);
        assertEquals(30,pigments.extractChemical(1,30,Action.EXECUTE).getAmount());
        assertEquals(30,output.getStackInSlot(0).amount());
    }
    @Test void nativeHandlerHonorsConfiguredFaceAndInputOutputSlots() {
        var input=new MekanismChemicalStorage(1,100,null);
        var output=new MekanismChemicalStorage(1,100,null);
        var owner=mock(FurnaceFaceAccessor.class);
        when(owner.getFacing()).thenReturn(Direction.NORTH);
        when(owner.getFaceMode(any())).thenReturn(FurnaceFaceMode.DISABLED);
        var handler=new NativeFurnaceChemicalHandlers.GasHandler(input,output,Direction.NORTH,owner);
        var stack=gas(40);
        assertEquals(40,handler.insertChemical(0,stack,Action.EXECUTE).getAmount());
        assertTrue(input.getStackInSlot(0).isEmpty());
        when(owner.getFaceMode(any())).thenReturn(FurnaceFaceMode.MATERIAL_INPUT);
        assertTrue(handler.insertChemical(0,stack,Action.EXECUTE).isEmpty());
        assertEquals(40,input.getStackInSlot(0).amount());
        assertEquals(40,handler.insertChemical(1,stack,Action.EXECUTE).getAmount());
        assertTrue(handler.extractChemical(0,20,Action.EXECUTE).isEmpty());
    }
    @Test @SuppressWarnings("unchecked") void boxedNativeNbtRestoresTypeAndLongAmountAndClearsStaleEmptySlots() {
        var source=gas((long)Integer.MAX_VALUE+42);
            var storage=new MekanismChemicalStorage(2,Long.MAX_VALUE,null);
            storage.setStackInSlot(0,new MekanismChemicalStackView(source));
            CompoundTag tag=new CompoundTag(); storage.save(tag,"tank",null);
            var restored=new MekanismChemicalStorage(2,Long.MAX_VALUE,null); restored.load(tag,"tank",null);
            assertEquals(source.getAmount(),restored.getStackInSlot(0).amount());
            assertSame(source.getType(),restored.getNativeStack(0).getType());
            storage.extractChemical(0,Long.MAX_VALUE,false); storage.save(tag,"tank",null);
            assertFalse(tag.contains("tank0"));
            restored.load(tag,"tank",null); assertTrue(restored.getStackInSlot(0).isEmpty());
    }
}
