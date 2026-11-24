package com.sorrowmist.useless.compat.neoecoae.compact.block;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactL9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowStorageDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompactMachineDropsTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void compactL9TooltipUsesTheControllerDropDomainAndDoesNotCreateOneForFreshItems() {
        var item = mock(CompactBlockItem.class, CALLS_REAL_METHODS);
        doReturn(mock(CompactL9Block.class)).when(item).getBlock();
        doReturn("block.useless_mod.useless_compact_l9").when(item).getDescriptionId();
        var stack = new ItemStack(Items.STONE);
        var tooltip = new ArrayList<Component>();
        item.appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
        assertFalse(tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents text
                && text.getKey().equals("tooltip.useless_mod.compact.l9.domain")));
        assertNull(stack.get(DataComponents.CUSTOM_DATA));

        UUID domain = UUID.randomUUID();
        var data = new CompoundTag();
        data.putUUID("neoecoae_infinite_controller_domain", domain);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        tooltip.clear();
        item.appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
        var contents = assertInstanceOf(TranslatableContents.class, tooltip.getLast().getContents());
        assertEquals("tooltip.useless_mod.compact.l9.domain", contents.getKey());
        assertArrayEquals(new Object[]{domain.toString()}, contents.getArgs());
    }

    @Test
    @SuppressWarnings("unchecked")
    void builtInL9MemberCannotBeReplacedExtractedOrDropped() {
        var member = new ItemStack(Items.PAPER);
        UUID domain = UUID.randomUUID();
        try (var grid = mockStatic(GridHelper.class); var cells = mockStatic(ECOStorageCells.class)) {
            grid.when(() -> GridHelper.createManagedNode(any(), any()))
                    .thenReturn(mock(IManagedGridNode.class, RETURNS_SELF));
            cells.when(() -> ECOStorageCells.isCellHandled(member)).thenReturn(true);
            BlockEntityType<ECODriveBlockEntity> type = mock(BlockEntityType.class);
            when(type.isValid(any())).thenReturn(true);
            var drive = new ShadowStorageDriveBlockEntity(type, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), member, domain);

            CompoundTag tag = drive.getCellStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            assertTrue(tag.getBoolean("neoecoae_infinite_member"));
            assertEquals(domain, tag.getUUID("neoecoae_infinite_domain"));
            assertFalse(drive.canExtractCell());
            assertTrue(drive.HANDLER.extractItem(0, 1, true).isEmpty());
            assertTrue(drive.HANDLER.extractItem(0, 1, false).isEmpty());
            drive.setCellStack(null);
            assertSame(member, drive.getCellStack());
            assertNull(drive.getCellInventory());

            var drops = new ArrayList<ItemStack>();
            drive.addAdditionalDrops(null, BlockPos.ZERO, drops);
            assertTrue(drops.isEmpty());
        }
    }

    @Test
    void compactL9ComponentSlotDisplaysItsContentsButRejectsHandlerMutations() throws ReflectiveOperationException {
        var host = mock(CompactL9BlockEntity.class, CALLS_REAL_METHODS);
        var restoreType = Class.forName(ECOStorageSystemBlockEntity.class.getPackageName() + ".ECOStorageInfiniteRestore");
        var constructor = restoreType.getDeclaredConstructor(ECOStorageSystemBlockEntity.class);
        constructor.setAccessible(true);
        var factory = restoreType.getDeclaredMethod("componentItemHandler", IItemHandlerModifiable.class);
        factory.setAccessible(true);
        var delegate = new ItemStackHandler(1);
        delegate.setStackInSlot(0, new ItemStack(Items.PAPER, 64));
        var slot = (IItemHandlerModifiable) factory.invoke(constructor.newInstance(host), delegate);

        assertEquals(64, slot.getStackInSlot(0).getCount());
        assertFalse(host.canExtractInfiniteComponents());
        assertTrue(slot.extractItem(0, 64, true).isEmpty());
        assertTrue(slot.extractItem(0, 64, false).isEmpty());
        assertSame(Items.PAPER, slot.getStackInSlot(0).getItem());
        slot.setStackInSlot(0, ItemStack.EMPTY);
        assertEquals(64, delegate.getStackInSlot(0).getCount());
        delegate.setStackInSlot(0, ItemStack.EMPTY);
        var replacement = new ItemStack(Items.PAPER);
        assertSame(replacement, slot.insertItem(0, replacement, false));
        assertTrue(delegate.getStackInSlot(0).isEmpty());
    }

    @Test
    void creativeBreakUsesExistingLootPathWithTheLiveBlockEntity() {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        BlockState state = mock(BlockState.class);
        BlockEntity blockEntity = mock(BlockEntity.class);
        BlockPos pos = new BlockPos(12, 64, -8);
        when(player.isCreative()).thenReturn(true);
        when(level.getBlockEntity(pos)).thenReturn(blockEntity);

        try (var drops = mockStatic(Block.class)) {
            CompactMachineDrops.dropForCreativePlayer(level, pos, state, player);

            drops.verify(() -> Block.dropResources(state, level, pos, blockEntity, player, ItemStack.EMPTY));
            drops.verifyNoMoreInteractions();
            verify(level).isClientSide();
            verify(level).getBlockEntity(pos);
            verifyNoMoreInteractions(level);
        }
    }

    @Test
    void survivalBreakLeavesDropsToTheNormalHarvestPath() {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        BlockState state = mock(BlockState.class);

        try (var drops = mockStatic(Block.class)) {
            CompactMachineDrops.dropForCreativePlayer(level, BlockPos.ZERO, state, player);

            drops.verifyNoInteractions();
            verify(level).isClientSide();
            verifyNoMoreInteractions(level);
        }
    }

    @Test
    void clientDoesNotReadTheBlockEntityOrSpawnDuplicateDrops() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        Player player = mock(Player.class);
        BlockState state = mock(BlockState.class);

        try (var drops = mockStatic(Block.class)) {
            CompactMachineDrops.dropForCreativePlayer(level, BlockPos.ZERO, state, player);

            drops.verifyNoInteractions();
            verify(level).isClientSide();
            verifyNoMoreInteractions(level);
            verifyNoInteractions(player);
        }
    }
}
