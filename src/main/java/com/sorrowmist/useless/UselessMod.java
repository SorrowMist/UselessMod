package com.sorrowmist.useless;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.content.recipe.adapters.RecipeAdapterCompatRegistry;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModCreativeTabs;
import com.sorrowmist.useless.init.ModEntities;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.init.ModIngredientTypes;
import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.init.ModNetwork;
import com.sorrowmist.useless.init.ModPOIs;
import com.sorrowmist.useless.init.ModRecipeSerializers;
import com.sorrowmist.useless.init.ModRecipeTypes;
import com.sorrowmist.useless.init.ModSounds;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(UselessMod.MODID)
public class UselessMod {
    public static final String MODID = "useless_mod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UselessMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerBuiltinResourcePacks);
        UComponents.init(modEventBus);
        ModIngredientTypes.INGREDIENT_TYPES.register(modEventBus);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuType.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModPOIs.POI_TYPES.register(modEventBus);

        ModCreativeTabs.CREATIVE_TAB.register(modEventBus);

        modEventBus.addListener(ModNetwork::registerPayloadHandlers);

        GlowPlasticBlock.BLOCKS.register(modEventBus);
        GlowPlasticBlock.ITEMS.register(modEventBus);

        // 纬度注册
        UselessDimensions.init(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, ConfigManager.COMMON_SPEC, "useless_mod-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, ConfigManager.CLIENT_SPEC, "useless_mod-client.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ConfigManager.SERVER_SPEC, "useless_mod-server.toml");
    }

    // 便捷 ResourceLocation 工具
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        RecipeAdapterCompatRegistry.init(event);
    }

    private void registerBuiltinResourcePacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(MODID, "xia"),
                PackType.CLIENT_RESOURCES,
                Component.translatable("pack.useless_mod.xia.name"),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof EndlessBeafItem) {
            InteractionResult timeAccelerationResult = BeefTimeAcceleration.tryUse(
                    new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec()));
            if (timeAccelerationResult != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(timeAccelerationResult);
                return;
            }
        }

        InteractionResult occultismResult = trySpawnMarkedOccultismSpirit(event);
        if (occultismResult != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(occultismResult);
            return;
        }
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;

        InteractionResult result = EndlessBeafItem.trySummonLightningForCollector(event.getLevel(), event.getPos(), event.getEntity());
        if (result == InteractionResult.PASS) return;

        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    private static InteractionResult trySpawnMarkedOccultismSpirit(PlayerInteractEvent.RightClickBlock event) {
        if (!ModList.get().isLoaded("occultism")) {
            return InteractionResult.PASS;
        }
        return OccultismCompat.trySpawnMarkedSpirit(event);
    }

    /** Isolates the optional Occultism class reference from the main mod entrypoint. */
    private static final class OccultismCompat {
        private static InteractionResult trySpawnMarkedSpirit(PlayerInteractEvent.RightClickBlock event) {
            return com.sorrowmist.useless.content.recipe.adapters.occultism.OccultismSpiritEggHandler
                    .trySpawn(event);
        }
    }

    @SubscribeEvent
    public void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        if (!(event.getItemStack().getItem() instanceof EndlessBeafItem)) return;

        event.replaceModifier(Attributes.ATTACK_DAMAGE,
                              EndlessBeafItem.createAttackDamageModifier(),
                              EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.ENTITY_INTERACTION_RANGE,
                          new AttributeModifier(id("beef_tool_entity_interaction_range"),
                                                ConfigManager.getBeefToolEntityInteractionRange(),
                                                AttributeModifier.Operation.ADD_VALUE),
                          EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.BLOCK_INTERACTION_RANGE,
                          new AttributeModifier(id("beef_tool_block_interaction_range"),
                                                ConfigManager.getBeefToolBlockInteractionRange(),
                                                AttributeModifier.Operation.ADD_VALUE),
                          EquipmentSlotGroup.MAINHAND);
    }
}
