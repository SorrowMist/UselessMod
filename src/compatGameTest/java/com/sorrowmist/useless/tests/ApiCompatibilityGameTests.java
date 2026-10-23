package com.sorrowmist.useless.tests;

import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.ae.AeDeviceLinker;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.ae.advancedae.ReactionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.RecyclerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.mekanismmoremachine.AmbientGasCollectorRecipeAdapter;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.event.EventHandler;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.stretcher.compat.StaffRadiationProtection;
import com.sorrowmist.useless.world.ae.AeConnectLinkSavedData;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;

import java.util.UUID;
import java.util.Map;

@GameTestHolder(UselessMod.MODID)
@PrefixGameTestTemplate(false)
public final class ApiCompatibilityGameTests {
    @GameTest(template = "furnace_empty", timeoutTicks = 400)
    public static void reactionChamberConsumesExactItemsAndFluid(GameTestHelper helper) {
        var level = helper.getLevel();
        var adapter = new ReactionChamberRecipeAdapter();
        helper.assertTrue(AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters().stream()
                .anyMatch(ReactionChamberRecipeAdapter.class::isInstance), "Reaction adapter must register at startup");
        var holder = RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(), ReactionChamberRecipe.TYPE).stream()
                .filter(r -> r.id().equals(new ResourceLocation("useless_mod", "compat_reaction")))
                .findFirst().orElseThrow();
        var recipe = adapter.convert(holder, level);
        helper.assertTrue(recipe.energy() == 12000 && recipe.inputFluids().get(0).amount() == 500,
                "Energy and fluid amount must survive the new native API");
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
        var furnace = (AdvancedAlloyFurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        furnace.getItemHandler().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        furnace.getItemHandler().setStackInSlot(AdvancedAlloyFurnaceLayout.MOLD_SLOT, adapter.getMoldItem());
        furnace.getInputFluidTank(0).setFluid(new FluidStack(Fluids.WATER, 499));
        furnace.setEnergy(1_000_000L);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(furnace.getItemHandler().getStackInSlot(0).getCount() == 4
                    && furnace.getInputFluidTank(0).getFluidAmount() == 499,
                    "Insufficient fluid must not consume either duplicate item requirement");
            furnace.getInputFluidTank(0).setFluid(new FluidStack(Fluids.WATER, 500));
            furnace.setChanged();
        });
        helper.succeedWhen(() -> {
            int diamonds = 0;
            for (int slot = 9; slot < 18; slot++) {
                var stack = furnace.getItemHandler().getStackInSlot(slot);
                if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
            }
            helper.assertTrue(diamonds == 3 && furnace.getItemHandler().getStackInSlot(0).isEmpty()
                    && furnace.getInputFluidTank(0).isEmpty(), "Must consume four ingots and 500 mB exactly once");
            helper.assertTrue(ItemStack.isSameItem(furnace.getItemHandler().getStackInSlot(
                    AdvancedAlloyFurnaceLayout.MOLD_SLOT), adapter.getMoldItem()), "Mold must remain");
        });
    }

    @GameTest(template = "furnace_empty")
    public static void moreMachineAdaptersRegisterAndGenerateRecipes(GameTestHelper helper) {
        var adapters = AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters();
        var recycler = adapters.stream().filter(RecyclerRecipeAdapter.class::isInstance)
                .map(RecyclerRecipeAdapter.class::cast).findFirst().orElseThrow();
        var collector = adapters.stream().filter(AmbientGasCollectorRecipeAdapter.class::isInstance)
                .map(AmbientGasCollectorRecipeAdapter.class::cast).findFirst().orElseThrow();
        var recipes = recycler.getGeneratedRecipes(helper.getLevel());
        helper.assertTrue(!recipes.isEmpty(), "Recycler must expose its native outputs");
        helper.assertTrue(recipes.stream().allMatch(r -> r.value().convertedRecipe().energy() > 0
                && r.value().convertedRecipe().inputFluids().get(0).amount() == 1000),
                "Recycler must retain the water cost and FloatingLong energy cost");
        var gas = collector.getGeneratedRecipes(helper.getLevel());
        helper.assertTrue(gas.size() == 1 && gas.get(0).value().convertedRecipe().keyOutputs().size() == 1,
                "Ambient collector must produce the native gas key through Applied Mekanistics");
        long water = gas.get(0).value().convertedRecipe().inputFluids().get(0).amount();
        helper.assertTrue(!collector.findMatchingRecipes(helper.getLevel(), Map.of(),
                Map.of(new FluidStack(Fluids.WATER, 1), water), Map.of(), collector.getMoldItem()).isEmpty(),
                "Generated gas recipes must also be available to actual machine lookup");
        helper.succeed();
    }

    @GameTest(template = "furnace_empty", timeoutTicks = 400)
    public static void recyclerRunsInSingleBlockFurnaceWithOnlyWater(GameTestHelper helper) {
        var adapter = new RecyclerRecipeAdapter();
        var outputs = adapter.getGeneratedRecipes(helper.getLevel()).stream()
                .flatMap(r -> r.value().convertedRecipe().outputs().stream()).toList();
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
        var furnace = (AdvancedAlloyFurnaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        furnace.getItemHandler().setStackInSlot(AdvancedAlloyFurnaceLayout.MOLD_SLOT, adapter.getMoldItem());
        furnace.getInputFluidTank(0).setFluid(new FluidStack(Fluids.WATER, 1000));
        furnace.setEnergy(1_000_000L);
        helper.succeedWhen(() -> {
            boolean produced = false;
            for (int slot = 9; slot < 18; slot++) {
                var output = furnace.getItemHandler().getStackInSlot(slot);
                if (outputs.stream().anyMatch(expected -> ItemStack.matches(expected, output))) produced = true;
            }
            helper.assertTrue(produced && furnace.getInputFluidTank(0).isEmpty(),
                    "Recycler must consume exactly one bucket and produce a native output without item inputs");
        });
    }

    @GameTest(template = "furnace_empty")
    public static void staffBlocksNewRadiationAndClearsDoseBeforeEffects(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RadiationTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get()));
        var dose = player.getCapability(Capabilities.RADIATION_ENTITY).orElseThrow(IllegalStateException::new);
        StaffRadiationProtection.configure(player, false, false);
        dose.set(RadiationManager.BASELINE);
        RadiationManager.get().radiate(player, 100);
        helper.assertTrue(dose.getRadiation() > RadiationManager.BASELINE, "Positive control must receive radiation");
        StaffRadiationProtection.configure(player, false, true);
        RadiationManager.get().radiate(player, 100);
        helper.assertTrue(dose.getRadiation() == RadiationManager.BASELINE, "Enabled staff must block new radiation");
        dose.set(100);
        dose.update(player);
        helper.assertTrue(dose.getRadiation() == RadiationManager.BASELINE,
                "Native radiation update must clear an existing dose immediately, without waiting 20 ticks");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(template = "furnace_empty", timeoutTicks = 200)
    public static void staffConnectsRestoresAndDisconnectsNativeAeMachine(GameTestHelper helper) {
        var level = helper.getLevel();
        var power = new BlockPos(1, 1, 1);
        var access = new BlockPos(2, 1, 1);
        var machine = new BlockPos(6, 1, 1);
        helper.setBlock(power, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(access, AEBlocks.WIRELESS_ACCESS_POINT.block());
        helper.setBlock(access.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(machine, AEBlocks.DRIVE.block());
        helper.runAtTickTime(40, () -> {
            var accessPos = helper.absolutePos(access);
            var machinePos = helper.absolutePos(machine);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "AeLinkTest"));
            var tool = new ItemStack(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            ComponentAccess.set(tool, UComponents.AeNetworkConnectComponent.get(), true);
            ComponentAccess.set(tool, UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), accessPos));
            var ap = (WirelessAccessPointBlockEntity) level.getBlockEntity(accessPos);
            helper.assertTrue(ap.getMainNode().isOnline(), "Fixture access point must be online before connecting");
            var machineNode = ((DriveBlockEntity) level.getBlockEntity(machinePos)).getMainNode().getNode();
            helper.assertTrue(machineNode != null && !machineNode.isPowered(),
                    "Fixture machine must start outside a powered network");
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, machinePos,
                    new BlockHitResult(Vec3.atCenterOf(machinePos), Direction.UP, machinePos, false));
            EventHandler.onAeConnectInteract(event);
            helper.assertTrue(event.isCanceled(), "Staff AE mode must consume the click instead of opening the GUI");
            var link = new AeConnectLinkSavedData.Link(level.dimension(), level.dimension(), accessPos, machinePos);
            var data = AeConnectLinkSavedData.get(level.getServer());
            helper.assertTrue(data.contains(link), "Native AE machine must be linked and saved");
            var accessNode = ap.getMainNode().getNode();
            var connection = accessNode.getConnections().stream()
                    .filter(c -> !c.isInWorld() && c.getOtherSide(accessNode) == machineNode).findFirst().orElseThrow();
            connection.destroy();
            AeDeviceLinker.ensureLinks(level.getServer());
            helper.assertTrue(accessNode.getConnections().stream().anyMatch(c -> c.getOtherSide(accessNode) == machineNode),
                    "Saved links must restore using the same native host lookup");
            AeDeviceLinker.toggle(level, player, tool, machinePos);
            helper.assertTrue(!data.contains(link) && accessNode.getConnections().stream()
                    .noneMatch(c -> c.getOtherSide(accessNode) == machineNode), "Disconnect must remove only the saved link");
            helper.assertTrue(AeDeviceLinker.isLinkTarget(level, machinePos), "Disconnected device must remain a valid host");
            helper.succeed();
        });
    }
}
