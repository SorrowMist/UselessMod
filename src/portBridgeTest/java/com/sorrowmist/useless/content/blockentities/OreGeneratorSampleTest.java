package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;
import net.minecraftforge.items.SlotItemHandler;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OreGeneratorSampleTest {
    private Map<TagKey<Item>, List<Holder<Item>>> previousTags;

    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @BeforeEach void bindNativeAndCommonTags() {
        previousTags = BuiltInRegistries.ITEM.getTags().collect(Collectors.toMap(
                pair -> pair.getFirst(), pair -> pair.getSecond().stream().toList()));
        Map<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>(previousTags);
        tags.put(Tags.Items.ORES, List.of(Items.IRON_ORE.builtInRegistryHolder(),
                Items.DEEPSLATE_IRON_ORE.builtInRegistryHolder()));
        tags.put(Tags.Items.RAW_MATERIALS, List.of(Items.RAW_IRON.builtInRegistryHolder()));
        tags.put(ItemTags.create(new ResourceLocation("c", "ores")),
                List.of(Items.GOLD_ORE.builtInRegistryHolder()));
        tags.put(ItemTags.create(new ResourceLocation("c", "raw_materials")),
                List.of(Items.RAW_GOLD.builtInRegistryHolder()));
        BuiltInRegistries.ITEM.bindTags(tags);
    }

    @AfterEach void restoreTags() {
        BuiltInRegistries.ITEM.resetTags();
        BuiltInRegistries.ITEM.bindTags(previousTags);
    }

    @Test void forgeOnlyOresAndRawMaterialsAreAccepted() {
        for (Item item : List.of(Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE, Items.RAW_IRON)) {
            assertTrue(OreGeneratorSampleHandler.isValidSample(new ItemStack(item)), item.toString());
        }
    }

    @Test void commonDatapackTagsRemainSupportedWithoutForgeMembership() {
        assertTrue(OreGeneratorSampleHandler.isValidSample(new ItemStack(Items.GOLD_ORE)));
        assertTrue(OreGeneratorSampleHandler.isValidSample(new ItemStack(Items.RAW_GOLD)));
    }

    @Test void emptyStacksIngotsAndStorageBlocksAreRejected() {
        for (ItemStack stack : List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.RAW_IRON_BLOCK), new ItemStack(Items.COBBLESTONE))) {
            assertFalse(OreGeneratorSampleHandler.isValidSample(stack));
        }
    }

    @Test void nativeMenuSlotAcceptsOneSampleAndLeavesTheRemainingItemsWithThePlayer() {
        AtomicInteger changes = new AtomicInteger();
        var handler = new OreGeneratorSampleHandler(() -> 9,
                OreGeneratorSampleHandler::isValidSample, changes::incrementAndGet);
        var slot = new SlotItemHandler(handler, 0, 0, 0);
        ItemStack carried = new ItemStack(Items.IRON_ORE, 64);
        assertTrue(slot.mayPlace(carried));
        assertEquals(63, handler.insertItem(0, carried, true).getCount());
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertEquals(0, changes.get());
        ItemStack remainder = handler.insertItem(0, carried, false);
        assertEquals(63, remainder.getCount());
        assertEquals(64, carried.getCount());
        assertEquals(1, handler.getStackInSlot(0).getCount());
        assertEquals(1, changes.get());
        assertEquals(63, handler.insertItem(0, remainder, false).getCount());
    }

    @Test void sampleSurvivesSaveLoadAndConfigurationReductionAllowsRecoveryOnly() {
        AtomicInteger slots = new AtomicInteger(10);
        var handler = new OreGeneratorSampleHandler(slots::get,
                OreGeneratorSampleHandler::isValidSample, () -> {});
        handler.setStackInSlot(9, new ItemStack(Items.RAW_IRON, 32));
        assertEquals(1, handler.getStackInSlot(9).getCount());
        var saved = handler.serializeNBT();
        slots.set(9);
        var loaded = new OreGeneratorSampleHandler(slots::get,
                OreGeneratorSampleHandler::isValidSample, () -> {});
        loaded.deserializeNBT(saved);
        assertTrue(loaded.getStackInSlot(9).is(Items.RAW_IRON));
        assertFalse(new SlotItemHandler(loaded, 9, 0, 0).mayPlace(new ItemStack(Items.IRON_ORE)));
        assertTrue(loaded.extractItem(9, 1, false).is(Items.RAW_IRON));
        assertTrue(loaded.getStackInSlot(9).isEmpty());
    }

    @Test void validationTracksDatapackTagReloadInsteadOfCachingItemMembership() {
        ItemStack sample = new ItemStack(Items.IRON_ORE);
        assertTrue(OreGeneratorSampleHandler.isValidSample(sample));
        BuiltInRegistries.ITEM.resetTags();
        BuiltInRegistries.ITEM.bindTags(previousTags);
        assertFalse(OreGeneratorSampleHandler.isValidSample(sample));
    }
}
