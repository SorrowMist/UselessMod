package com.sorrowmist.useless.content.recipe.adapters.hostilenetworks;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import dev.shadowsoffire.hostilenetworks.HostileConfig;
import dev.shadowsoffire.hostilenetworks.data.DataModel;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostileNetworksPortTest {
    @BeforeAll static void bootstrap() {
        RecipeTestBootstrap.initialize();
        CraftingHelper.register(new ResourceLocation("useless_mod:data_model_range"),DataModelRangeIngredient.TYPE);
    }
    @Test void modelRangeJsonAndPacketKeepModelIdAndInclusiveDataBounds() {
        var source=new DataModelRangeIngredient(new ResourceLocation("custom:model"),6,53);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeCodecs.INGREDIENT_STREAM.encode(buffer,source);
            var wire=RecipeCodecs.INGREDIENT_STREAM.decode(buffer);
            var json=Ingredient.fromJson(source.toJson());
            assertEquals(source,wire); assertEquals(source,json);
            assertEquals(6,((DataModelRangeIngredient)wire).minData());
            assertEquals(53,((DataModelRangeIngredient)wire).maxData());
            assertEquals(new ResourceLocation("custom:model"),((DataModelRangeIngredient)wire).modelId());
            assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
    }
    @Test void disabledSimulationUpgradeDoesNotGenerateAProgressionBypass() throws Exception {
        int original=HostileConfig.simModelUpgrade;
        try {
            HostileConfig.simModelUpgrade=0;
            Method training=HostileNetworksRecipeAdapter.class.getDeclaredMethod("addTrainingRecipes",List.class,DataModel.class);
            training.setAccessible(true); var recipes=new ArrayList<>();
            // Gate must run before any registry/model access; original pack explicitly sets this to 0.
            training.invoke(null,recipes,null); assertTrue(recipes.isEmpty());
        } finally {HostileConfig.simModelUpgrade=original;}
    }
    @Test void impossibleDataIntervalsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new DataModelRangeIngredient(new ResourceLocation("custom:model"),54,53));
    }

    @Test void continuousAccuracyMatchesInstalledHnnBytecodeAtIntermediateData() throws Exception {
        boolean original = HostileConfig.continuousAccuracy;
        try (var items = mockStatic(dev.shadowsoffire.hostilenetworks.item.DataModelItem.class)) {
            // CachedModel.EMPTY initializes from ItemStack.EMPTY. Supply an unbound holder only
            // for that bootstrap; this unit JVM deliberately has no global Forge ModList.
            var unbound = mock(dev.shadowsoffire.placebo.reload.DynamicHolder.class);
            items.when(() -> dev.shadowsoffire.hostilenetworks.item.DataModelItem.getStoredModel(any())).thenReturn(unbound);
            var model = mock(DataModel.class);
            var tier = dev.shadowsoffire.hostilenetworks.data.ModelTier.BASIC;
            when(model.getTierData(tier)).thenReturn(6);
            when(model.getTierData(tier.next())).thenReturn(54);
            var nativeModel = mock(dev.shadowsoffire.hostilenetworks.data.CachedModel.class, CALLS_REAL_METHODS);
            // Mockito's inline maker preserves the concrete native class. Fields remain on CachedModel.
            var clazz = dev.shadowsoffire.hostilenetworks.data.CachedModel.class;
            var tierMember = clazz.getDeclaredField("tier"); tierMember.setAccessible(true); tierMember.set(nativeModel, tier);
            var dataMember = clazz.getDeclaredField("data"); dataMember.setAccessible(true);
            doReturn(6).when(nativeModel).getTierData();
            doReturn(54).when(nativeModel).getNextTierData();
            for (boolean continuous : List.of(false, true)) {
                HostileConfig.continuousAccuracy = continuous;
                for (int data : new int[]{6, 7, 30, 53}) {
                    dataMember.setInt(nativeModel, data);
                    assertEquals(nativeModel.getAccuracy(), HostileNetworksRecipeAdapter.accuracyAt(model, tier, data),
                            "Mismatch at data=" + data + ", continuous=" + continuous);
                }
            }
            assertTrue(HostileNetworksRecipeAdapter.accuracyAt(model, tier, 30) > tier.accuracy());
            assertTrue(Float.isNaN(HostileNetworksRecipeAdapter.accuracyAt(model, tier, 54)));
            assertEquals(0F, HostileNetworksRecipeAdapter.accuracyAt(model,
                    dev.shadowsoffire.hostilenetworks.data.ModelTier.FAULTY, 0));
        } finally { HostileConfig.continuousAccuracy = original; }
    }

    @Test void exactDataPredicateDoesNotWidenToWholeTierDuringPacketRoundTrip() {
        var source = new DataModelRangeIngredient(new ResourceLocation("custom:continuous"), 30, 30);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeCodecs.INGREDIENT_STREAM.encode(buf, source);
            var result = (DataModelRangeIngredient)RecipeCodecs.INGREDIENT_STREAM.decode(buf);
            assertEquals(30, result.minData()); assertEquals(30, result.maxData());
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
}
