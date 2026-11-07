package com.sorrowmist.useless.compat.occultism;

import appeng.api.config.Actionable;
import com.klikli_dev.modonomicon.api.ModonomiconAPI;
import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.modonomicon.multiblock.DenseMultiblock;
import com.klikli_dev.modonomicon.multiblock.SimulateResultImpl;
import com.klikli_dev.modonomicon.multiblock.matcher.BlockMatcher;
import com.klikli_dev.modonomicon.multiblock.matcher.BlockStateMatcher;
import com.klikli_dev.modonomicon.multiblock.matcher.DisplayOnlyMatcher;
import com.klikli_dev.modonomicon.multiblock.matcher.Matchers;
import com.klikli_dev.occultism.common.block.ChalkGlyphBlock;
import com.klikli_dev.occultism.common.item.tool.ChalkItem;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismSounds;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real 1.158.0 chalk, 1.79.3 simulation and Forge ItemStack placement/rollback, without starting FML. */
class RitualSatchelCompatNativeForge1201Test {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final BlockPos ANCHOR = POS.below();
    private static final ResourceLocation PENTACLE = new ResourceLocation("occultism", "satchel_test");
    private static Item toolItem;
    private static ChalkItem white;
    private static ChalkItem gold;
    private static ChalkGlyphBlock whiteGlyph;
    private static ChalkGlyphBlock goldGlyph;
    private static Field soundValue;
    private static Object previousSound;

    @BeforeAll static void bootstrap() throws ReflectiveOperationException {
        InventoryTestBootstrap.initialize();
        toolItem = InventoryTestBootstrap.registerItem("satchel_native_tool", EndlessBeafItem::new);
        var whiteRef = new AtomicReference<Item>();
        var goldRef = new AtomicReference<Item>();
        whiteGlyph = registerGlyph("satchel_white_glyph", whiteRef::get);
        goldGlyph = registerGlyph("satchel_gold_glyph", goldRef::get);
        white = InventoryTestBootstrap.registerItem("satchel_white", () ->
                new ChalkItem(new Item.Properties().durability(8), () -> whiteGlyph));
        gold = InventoryTestBootstrap.registerItem("satchel_gold", () ->
                new ChalkItem(new Item.Properties().durability(8), () -> goldGlyph));
        whiteRef.set(white);
        goldRef.set(gold);
        // Plain JUnit does not fire Occultism's deferred sound registration. Only substitute the sound;
        // ChalkItem.useOn, glyph selection, durability and ForgeHooks.onPlaceItemIntoWorld remain real.
        soundValue = RegistryObject.class.getDeclaredField("value");
        soundValue.setAccessible(true);
        previousSound = soundValue.get(OccultismSounds.CHALK);
        soundValue.set(OccultismSounds.CHALK, SoundEvents.STONE_PLACE);
    }

    @AfterAll static void restoreSoundFixture() throws IllegalAccessException {
        if (soundValue != null) soundValue.set(OccultismSounds.CHALK, previousSound);
    }

    @Test void nativeBlockItemConsumesOneAndRepeatDoesNotBuildAboveTheCorrectCell() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            ItemStack stone = new ItemStack(Items.STONE, 3);
            s.inventory.setItem(1, stone);
            var target = target(Blocks.STONE);
            assertTrue(s.place(target));
            assertEquals(2, stone.getCount());
            assertTrue(s.state(POS).is(Blocks.STONE));
            assertFalse(s.place(target));
            assertTrue(s.state(POS.above()).isAir());
            assertSame(s.tool, s.player.getMainHandItem());
            ae.verifyNoInteractions();
        }
    }

    @Test void occupiedOrRedirectedPlacementNeverLeaksIntoTheAdjacentCell() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            s.blocks.put(POS, Blocks.STONE.defaultBlockState());
            ItemStack dirt = new ItemStack(Items.DIRT, 2);
            s.inventory.setItem(1, dirt);
            assertFalse(s.place(target(Blocks.DIRT)));
            assertEquals(2, dirt.getCount());
            assertTrue(s.state(POS.above()).isAir());
            verify(s.level, never()).setBlock(any(), any(), anyInt());
            ae.verify(() -> AE2Compat.extractMatchingFromLinkedGrid(eq(s.tool), eq(s.player), any()));
        }
    }

    @Test void rejectedNativeBlockWriteIsNotReportedAsSuccessOrCharged() {
        try (var s = new Scenario()) {
            ItemStack stone = new ItemStack(Items.STONE, 2);
            s.rejectWrites = true;
            assertFalse(s.use(stone, target(Blocks.STONE)));
            assertEquals(2, stone.getCount());
            assertTrue(s.state(POS).isAir());
        }
    }

    @Test void aBlockConsumedByNativePlacementSideEffectsDoesNotMintARefund() {
        try (var s = new Scenario()) {
            // Wither skulls can disappear after successful placement because the wither was created.
            // Model the resulting world transition, but keep the native BlockItem and Forge item accounting.
            s.removePlacedBlock = true;
            ItemStack stone = new ItemStack(Items.STONE, 2);
            assertFalse(s.use(stone, target(Blocks.STONE)));
            assertTrue(s.state(POS).isAir());
            assertEquals(1, stone.getCount());
        }
    }

    @Test void blockStateTagAndRotationUseTheSamePredicateAsNativeSimulation() {
        try (var s = new Scenario()) {
            ItemStack log = new ItemStack(Items.OAK_LOG, 2);
            log.getOrCreateTag().put("BlockStateTag", new net.minecraft.nbt.CompoundTag());
            log.getTag().getCompound("BlockStateTag").putString("axis", "x");
            BlockState wanted = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            var target = new SimulateResultImpl(POS, BlockStateMatcher.from(wanted), 'L');
            assertTrue(s.matches(log, target));
            assertTrue(s.use(log, target));
            assertEquals(wanted, s.state(POS));
            assertEquals(1, log.getCount());

            // A candidate's world-space state must be transformed just as SimulateResultImpl.test does.
            s.blocks.remove(POS);
            var rotated = new SimulateResultImpl(POS, BlockStateMatcher.from(
                    wanted.rotate(com.klikli_dev.modonomicon.multiblock.AbstractMultiblock.fixHorizontal(Rotation.CLOCKWISE_90))), 'L');
            assertTrue(RitualSatchelCompat.matches(log, s.player, s.level, InteractionHand.MAIN_HAND,
                    hit(), rotated, Rotation.CLOCKWISE_90));
        }
    }

    @Test void nativeChalkSelectionChecksColorSupportAndEveryPossibleRandomSignWithoutUsingRng() {
        try (var s = new Scenario()) {
            assertTrue(s.matches(new ItemStack(white), target(whiteGlyph)));
            assertFalse(s.matches(new ItemStack(gold), target(whiteGlyph)));
            var exactSign = new SimulateResultImpl(POS, BlockStateMatcher.from(whiteGlyph.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                    .setValue(ChalkGlyphBlock.SIGN, 0)), 'W');
            assertFalse(s.matches(new ItemStack(white), exactSign));
            verify(s.random, never()).nextInt(anyInt());
            s.blocks.remove(ANCHOR);
            assertFalse(s.matches(new ItemStack(white), target(whiteGlyph)));
        }
    }

    @Test void nativeChalkUsesItsLastDurabilityPointAndCreativeDoesNotLoseMaterials() {
        try (var s = new Scenario()) {
            ItemStack chalk = new ItemStack(white);
            chalk.setDamageValue(7);
            assertTrue(s.use(chalk, target(whiteGlyph)));
            assertTrue(chalk.isEmpty());
            assertTrue(s.state(POS).is(whiteGlyph));
            assertEquals(5, s.state(POS).getValue(ChalkGlyphBlock.SIGN));

            s.blocks.remove(POS);
            s.abilities.instabuild = true;
            ItemStack creativeChalk = new ItemStack(gold);
            creativeChalk.setDamageValue(7);
            assertTrue(s.use(creativeChalk, target(goldGlyph)));
            assertEquals(7, creativeChalk.getDamageValue());
            assertEquals(1, creativeChalk.getCount());
            s.blocks.remove(POS);
            ItemStack stone = new ItemStack(Items.STONE);
            assertTrue(s.use(stone, target(Blocks.STONE)));
            assertEquals(1, stone.getCount());
        }
    }

    @Test void chalkSuccessWithoutAWorldChangeIsNotSuccessAndDoesNotSpendDurability() {
        try (var s = new Scenario()) {
            s.rejectWrites = true;
            ItemStack chalk = new ItemStack(white);
            assertEquals(InteractionResult.SUCCESS, chalk.useOn(new UseOnContext(
                    s.level, s.player, InteractionHand.MAIN_HAND, chalk, hit())));
            assertTrue(s.state(POS).isAir());
            assertEquals(1, chalk.getDamageValue()); // target jar's actual misleading SUCCESS behaviour
            assertFalse(s.use(chalk, target(whiteGlyph)));
            assertEquals(1, chalk.getDamageValue()); // compatibility layer does not charge another no-op
        }
    }

    @Test void forgePlaceCancellationRestoresBothNativeBlocksAndChalkWithoutAeRetry() {
        for (ItemStack material : List.of(new ItemStack(Items.STONE, 2), new ItemStack(white))) {
            try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
                s.cancelPlacement = true;
                s.inventory.setItem(1, material);
                assertFalse(s.place(target(material.getItem() == white ? whiteGlyph : Blocks.STONE)));
                assertTrue(s.state(POS).isAir());
                assertEquals(material.getItem() == white ? 1 : 2, material.getCount());
                assertEquals(0, material.getDamageValue());
                assertFalse(s.level.captureBlockSnapshots);
                assertTrue(s.level.capturedBlockSnapshots.isEmpty());
                ae.verifyNoInteractions();
            }
        }
    }

    @Test void rightClickCancellationEvenWithSuccessAndExplicitDenyCannotFallThroughToAe() {
        for (boolean cancelled : List.of(true, false)) {
            try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
                when(s.interaction.isCanceled()).thenReturn(cancelled);
                when(s.interaction.getCancellationResult()).thenReturn(InteractionResult.SUCCESS);
                when(s.interaction.getUseItem()).thenReturn(cancelled ? Event.Result.DEFAULT : Event.Result.DENY);
                ItemStack stone = new ItemStack(Items.STONE, 2);
                s.inventory.setItem(1, stone);
                s.inventory.setItem(2, new ItemStack(Items.STONE, 2));
                assertFalse(s.place(target(Blocks.STONE)));
                assertEquals(2, stone.getCount());
                verify(s.level, never()).setBlock(any(), any(), anyInt());
                ae.verifyNoInteractions();
            }
        }
    }

    @Test void eventChangingHeldToolOrPermissionIsRecheckedBeforeMaterialUse() {
        try (var s = new Scenario()) {
            ItemStack stone = new ItemStack(Items.STONE);
            when(s.interaction.getUseItem()).thenAnswer(call -> {
                s.abilities.mayBuild = false;
                return Event.Result.ALLOW;
            });
            assertFalse(s.use(stone, target(Blocks.STONE)));
            assertEquals(1, stone.getCount());
            verify(s.level, never()).setBlock(any(), any(), anyInt());
        }
    }

    @Test void aeWornChalkIsReturnedWithItsNewDamageAndOriginalNbt() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            ItemStack extracted = new ItemStack(white);
            extracted.setDamageValue(3);
            extracted.getOrCreateTag().putString("owner_marker", "retained");
            ae.when(() -> AE2Compat.extractMatchingFromLinkedGrid(eq(s.tool), eq(s.player), any()))
                    .thenAnswer(call -> call.<Predicate<ItemStack>>getArgument(2).test(extracted) ? extracted : null);
            var returned = new AtomicReference<ItemStack>();
            ae.when(() -> AE2Compat.tryInsertIntoLinkedGrid(eq(s.tool), eq(s.player), any(), eq(Actionable.MODULATE)))
                    .thenAnswer(call -> {
                        returned.set(call.<ItemStack>getArgument(2).copy());
                        return 1L;
                    });
            assertTrue(s.place(target(whiteGlyph)));
            assertEquals(4, returned.get().getDamageValue());
            assertEquals("retained", returned.get().getTag().getString("owner_marker"));
            assertEquals(1, returned.get().getCount());
            assertEquals(0, s.inventory.countItem(white));
        }
    }

    @Test void aeRejectedPlacementRefundsTheUnchangedStackAndInsertShortfallGoesToInventory() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            ItemStack extracted = new ItemStack(white);
            extracted.setDamageValue(3);
            s.cancelPlacement = true;
            ae.when(() -> AE2Compat.extractMatchingFromLinkedGrid(eq(s.tool), eq(s.player), any())).thenReturn(extracted);
            // Network disconnected / no capacity for the changed key: default insert result is zero.
            assertFalse(s.place(target(whiteGlyph)));
            ItemStack refund = s.inventory.getItem(1);
            assertEquals(white, refund.getItem());
            assertEquals(3, refund.getDamageValue());
            assertTrue(s.state(POS).isAir());

            ItemStack partial = new ItemStack(Items.DIAMOND, 3);
            ae.when(() -> AE2Compat.tryInsertIntoLinkedGrid(s.tool, s.player, partial, Actionable.MODULATE)).thenReturn(1L);
            RitualSatchelCompat.returnRemainder(s.player, s.tool, partial);
            assertEquals(2, s.inventory.countItem(Items.DIAMOND));
        }
    }

    @Test void successfulAeDrawingWithRejectedReinsertionReturnsTheDamagedChalkToInventory() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            ItemStack extracted = new ItemStack(white);
            extracted.setDamageValue(3);
            ae.when(() -> AE2Compat.extractMatchingFromLinkedGrid(eq(s.tool), eq(s.player), any())).thenReturn(extracted);
            assertTrue(s.place(target(whiteGlyph)));
            assertEquals(4, s.inventory.getItem(1).getDamageValue());
            assertEquals(white, s.inventory.getItem(1).getItem());
            assertTrue(s.state(POS).is(whiteGlyph));
        }
    }

    @Test void fullCreativeInventoryDropsUninsertedAeRemainderInsteadOfDeletingIt() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            s.abilities.instabuild = true;
            for (int i = 1; i < s.inventory.items.size(); i++) s.inventory.setItem(i, new ItemStack(Items.STONE, 64));
            ItemStack remainder = new ItemStack(white);
            remainder.setDamageValue(4);
            remainder.getOrCreateTag().putString("marker", "must_survive");
            RitualSatchelCompat.returnRemainder(s.player, s.tool, remainder);
            verify(s.player).drop(argThat(stack -> stack.getItem() == white && stack.getDamageValue() == 4
                    && "must_survive".equals(stack.getTag().getString("marker"))), eq(false));
        }
    }

    @Test void exceptionAfterAeExtractionStillReturnsTheUnspentMaterial() {
        try (var s = new Scenario(); var ae = mockStatic(AE2Compat.class)) {
            ItemStack extracted = new ItemStack(Items.STONE);
            ae.when(() -> AE2Compat.extractMatchingFromLinkedGrid(eq(s.tool), eq(s.player), any())).thenReturn(extracted);
            s.hooks.when(() -> ForgeHooks.onPlaceItemIntoWorld(any())).thenThrow(new IllegalStateException("fixture"));
            assertThrows(IllegalStateException.class, () -> s.place(target(Blocks.STONE)));
            assertEquals(1, s.inventory.countItem(Items.STONE));
        }
    }

    @Test void actualPreviewCoordinatesAreUsedAndServerSightIsRequired() {
        try (var s = new Scenario(); var api = s.installRitual(); var ae = mockStatic(AE2Compat.class)) {
            s.inventory.setItem(1, new ItemStack(Items.STONE, 2));
            RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
            assertTrue(s.state(POS).is(Blocks.STONE)); // true,false simulation places above the support anchor
            assertTrue(s.state(POS.above()).isAir());
            s.blocks.remove(POS);
            when(s.level.clip(any())).thenReturn(new BlockHitResult(Vec3.ZERO, Direction.UP, POS.east(8), false));
            RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
            assertTrue(s.state(POS).isAir());
            assertEquals(1, s.inventory.getItem(1).getCount());
        }
    }

    @Test void offhandToolIsSupportedButToolCooldownCannotBeBypassed() {
        try (var s = new Scenario(); var api = s.installRitual()) {
            s.inventory.setItem(0, ItemStack.EMPTY);
            s.inventory.offhand.set(0, s.tool);
            s.inventory.setItem(1, new ItemStack(Items.STONE, 2));
            RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
            assertTrue(s.state(POS).is(Blocks.STONE));
            s.hooks.verify(() -> ForgeHooks.onRightClickBlock(eq(s.player), eq(InteractionHand.OFF_HAND), eq(POS), any()));
            s.blocks.remove(POS);
            s.player.getCooldowns().addCooldown(toolItem, 20);
            RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
            assertTrue(s.state(POS).isAir());
            assertEquals(1, s.inventory.getItem(1).getCount());
        }
    }

    @Test void simulatedTargetsAlsoRequireLoadedChunksBuildHeightAndBorderBeforeTakingAnyMaterial() {
        for (int failure = 0; failure < 3; failure++) {
            try (var s = new Scenario(); var api = s.installRitual(); var ae = mockStatic(AE2Compat.class)) {
                switch (failure) {
                    case 0 -> when(s.level.hasChunkAt(POS)).thenReturn(false);
                    case 1 -> when(s.level.isOutsideBuildHeight(POS)).thenReturn(true);
                    case 2 -> when(s.level.getWorldBorder().isWithinBounds(POS)).thenReturn(false);
                    default -> throw new AssertionError();
                }
                RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
                ae.verifyNoInteractions();
                verify(s.level, never()).getBlockState(any());
                verify(s.level, never()).setBlock(any(), any(), anyInt());
            }
        }
    }

    @Test void forgedAdventureDeadUnheldRemoteAndUnloadedRequestsStopBeforeApiOrAeAccess() {
        for (int failure = 0; failure < 6; failure++) {
            try (var s = new Scenario(); var api = mockStatic(ModonomiconAPI.class); var ae = mockStatic(AE2Compat.class)) {
                BlockPos anchor = ANCHOR;
                switch (failure) {
                    case 0 -> s.abilities.mayBuild = false;
                    case 1 -> when(s.player.isAlive()).thenReturn(false);
                    case 2 -> s.inventory.setItem(0, ItemStack.EMPTY);
                    case 3 -> anchor = ANCHOR.east(1000);
                    case 4 -> when(s.level.hasChunkAt(ANCHOR)).thenReturn(false);
                    case 5 -> when(s.player.isSpectator()).thenReturn(true);
                    default -> throw new AssertionError();
                }
                RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, anchor, Rotation.NONE);
                api.verifyNoInteractions();
                ae.verifyNoInteractions();
                verify(s.level, never()).getBlockState(any());
            }
        }
    }

    @Test void unreferencedMultiblocksAndProtectedCellsCannotBeUsedAsArbitraryBuilders() {
        try (var s = new Scenario(); var api = s.installRitual(); var ae = mockStatic(AE2Compat.class)) {
            s.inventory.setItem(1, new ItemStack(Items.STONE));
            when(s.manager.getRecipes()).thenReturn(List.of());
            RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
            verify(s.level, never()).setBlock(any(), any(), anyInt());
            s.knownRitual();
            when(s.player.mayInteract(s.level, POS)).thenReturn(false);
            RitualSatchelCompat.placeFromAe(s.level, s.player, s.tool, PENTACLE, ANCHOR, Rotation.NONE);
            verify(s.level, never()).setBlock(any(), any(), anyInt());
            ae.verifyNoInteractions();
        }
    }

    @Test void ignoredMatchersAndOversizedServerStructuresAreBoundedBeforeSimulation() {
        assertFalse(RitualSatchelCompat.isPlaceable(Matchers.ANY));
        assertFalse(RitualSatchelCompat.isPlaceable(Matchers.AIR));
        var json = new com.google.gson.JsonObject();
        json.addProperty("display", "minecraft:stone");
        assertFalse(RitualSatchelCompat.isPlaceable(DisplayOnlyMatcher.fromJson(json)));
        Multiblock oversized = mock(Multiblock.class);
        when(oversized.getSize()).thenReturn(new Vec3i(Integer.MAX_VALUE, 2, Integer.MAX_VALUE));
        assertFalse(RitualSatchelCompat.boundedSize(oversized));
        verify(oversized, never()).simulate(any(), any(), any(), anyBoolean(), anyBoolean());
    }

    private static Multiblock.SimulateResult target(Block block) {
        return new SimulateResultImpl(POS, BlockMatcher.from(block), 'M');
    }

    private static BlockHitResult hit() {
        return new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false);
    }

    private static ChalkGlyphBlock registerGlyph(String name, Supplier<Item> chalk) {
        var registry = (ForgeRegistry<Block>) ForgeRegistries.BLOCKS;
        var wrapper = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        boolean locked = registry.isLocked();
        registry.unfreeze();
        wrapper.unfreeze();
        try {
            var block = new ChalkGlyphBlock(BlockBehaviour.Properties.of().replaceable().noCollission().instabreak(),
                    () -> 0xffffff, chalk);
            registry.register(new ResourceLocation("useless_mod_test", name), block);
            return block;
        } finally {
            wrapper.freeze();
            if (locked) registry.freeze();
        }
    }

    /** Only world storage, external event decisions and the network are doubles; placement code is native. */
    private static final class Scenario implements AutoCloseable {
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final Inventory inventory = new Inventory(player);
        final Abilities abilities = new Abilities();
        final ItemStack tool = new ItemStack(toolItem);
        final RandomSource random = mock(RandomSource.class);
        final RecipeManager manager = mock(RecipeManager.class);
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final PlayerInteractEvent.RightClickBlock interaction = mock(PlayerInteractEvent.RightClickBlock.class);
        final MockedStatic<ForgeHooks> hooks = mockStatic(ForgeHooks.class);
        final MockedStatic<ForgeEventFactory> events = mockStatic(ForgeEventFactory.class);
        boolean rejectWrites;
        boolean cancelPlacement;
        boolean removePlacedBlock;

        Scenario() {
            abilities.mayBuild = true;
            ComponentAccess.set(tool, UComponents.BeefRitualSatchelComponent.get(), true);
            inventory.setItem(0, tool);
            blocks.put(ANCHOR, Blocks.STONE.defaultBlockState());
            level.capturedBlockSnapshots = new ArrayList<>();
            when(player.level()).thenReturn(level);
            when(player.serverLevel()).thenReturn(level);
            player.connection = mock(net.minecraft.server.network.ServerGamePacketListenerImpl.class);
            when(player.isAlive()).thenReturn(true);
            when(player.getAbilities()).thenReturn(abilities);
            when(player.mayBuild()).thenAnswer(call -> abilities.mayBuild);
            when(player.isCreative()).thenAnswer(call -> abilities.instabuild);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getMainHandItem()).thenAnswer(call -> inventory.getSelected());
            when(player.getOffhandItem()).thenAnswer(call -> inventory.offhand.get(0));
            when(player.getItemInHand(any())).thenAnswer(call -> call.getArgument(0) == InteractionHand.MAIN_HAND
                    ? inventory.getSelected() : inventory.offhand.get(0));
            when(player.getCooldowns()).thenReturn(new ItemCooldowns());
            when(player.getDirection()).thenReturn(Direction.NORTH);
            when(player.getRandom()).thenReturn(random);
            when(player.mayUseItemAt(any(), any(), any())).thenReturn(true);
            when(player.mayInteract(eq(level), any())).thenReturn(true);
            when(player.distanceToSqr(any(Vec3.class))).thenAnswer(call ->
                    call.<Vec3>getArgument(0).distanceToSqr(new Vec3(0.5, 64, -3)));
            when(player.getBlockReach()).thenReturn(5.0);
            when(player.canReach(any(BlockPos.class), anyDouble())).thenReturn(true);
            when(player.getEyePosition()).thenReturn(new Vec3(0.5, 65.6, -3));
            when(player.getLookAngle()).thenReturn(new Vec3(0, -0.3, 1));
            when(level.clip(any())).thenReturn(new BlockHitResult(Vec3.atCenterOf(ANCHOR), Direction.UP, ANCHOR, false));
            when(level.getRecipeManager()).thenReturn(manager);
            var server = mock(net.minecraft.server.MinecraftServer.class);
            when(level.getServer()).thenReturn(server);
            when(level.enabledFeatures()).thenReturn(FeatureFlags.VANILLA_SET);
            when(level.getRandom()).thenReturn(random);
            when(random.nextInt(anyInt())).thenReturn(5);
            when(random.nextFloat()).thenReturn(0.5f);
            when(level.getBlockState(any())).thenAnswer(call -> state(call.getArgument(0)));
            when(level.hasChunkAt(any())).thenReturn(true);
            var border = mock(WorldBorder.class);
            when(level.getWorldBorder()).thenReturn(border);
            when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
            when(level.isUnobstructed(any(BlockState.class), any(BlockPos.class), any(CollisionContext.class))).thenReturn(true);
            when(level.setBlock(any(), any(), anyInt())).thenAnswer(call -> {
                if (rejectWrites) return false;
                BlockPos pos = call.<BlockPos>getArgument(0).immutable();
                BlockState before = state(pos);
                if (level.captureBlockSnapshots) {
                    var snapshot = mock(BlockSnapshot.class);
                    when(snapshot.getPos()).thenReturn(pos);
                    when(snapshot.getReplacedBlock()).thenReturn(before);
                    when(snapshot.getFlag()).thenReturn(call.getArgument(2));
                    when(snapshot.restore(true, false)).thenAnswer(restore -> { blocks.put(pos, before); return true; });
                    level.capturedBlockSnapshots.add(snapshot);
                }
                blocks.put(pos, call.getArgument(1));
                if (removePlacedBlock) blocks.remove(pos);
                return true;
            });
            when(level.setBlockAndUpdate(any(), any())).thenAnswer(call -> level.setBlock(call.getArgument(0), call.getArgument(1), 3));
            when(interaction.getUseItem()).thenReturn(Event.Result.DEFAULT);
            when(interaction.getUseBlock()).thenReturn(Event.Result.DEFAULT);
            hooks.when(() -> ForgeHooks.onRightClickBlock(eq(player), any(), any(), any())).thenReturn(interaction);
            hooks.when(() -> ForgeHooks.onPlaceItemIntoWorld(any())).thenCallRealMethod();
            events.when(() -> ForgeEventFactory.onBlockPlace(eq(player), any(BlockSnapshot.class), any()))
                    .thenAnswer(call -> cancelPlacement);
            events.when(() -> ForgeEventFactory.onMultiBlockPlace(eq(player), anyList(), any()))
                    .thenAnswer(call -> cancelPlacement);
        }

        BlockState state(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        boolean place(Multiblock.SimulateResult target) {
            return RitualSatchelCompat.placeForMatcher(level, player, tool, InteractionHand.MAIN_HAND, target, Rotation.NONE);
        }
        boolean use(ItemStack material, Multiblock.SimulateResult target) {
            return RitualSatchelCompat.useForMatcher(level, player, tool, material, InteractionHand.MAIN_HAND, hit(), target, Rotation.NONE);
        }
        boolean matches(ItemStack material, Multiblock.SimulateResult target) {
            return RitualSatchelCompat.matches(material, player, level, InteractionHand.MAIN_HAND, hit(), target, Rotation.NONE);
        }
        void knownRitual() {
            var ritual = new RitualRecipe(new ResourceLocation("occultism", "satchel_recipe"), PENTACLE,
                    new ResourceLocation("occultism", "craft"), new ItemStack(Items.PAPER), new ItemStack(Items.DIAMOND),
                    null, null, Ingredient.of(Items.BOOK), NonNullList.create(), 20, -1, null, null, "", Ingredient.EMPTY, "");
            when(manager.getRecipes()).thenReturn(List.of(ritual));
        }
        MockedStatic<ModonomiconAPI> installRitual() {
            knownRitual();
            var multiblock = new DenseMultiblock(new String[][]{{"0"}}, new HashMap<>(Map.of('0', BlockMatcher.from(Blocks.STONE))));
            multiblock.setId(PENTACLE);
            var api = mock(ModonomiconAPI.class);
            when(api.getMultiblock(PENTACLE)).thenReturn(multiblock);
            var access = mockStatic(ModonomiconAPI.class);
            access.when(ModonomiconAPI::get).thenReturn(api);
            return access;
        }
        @Override public void close() { events.close(); hooks.close(); }
    }
}
