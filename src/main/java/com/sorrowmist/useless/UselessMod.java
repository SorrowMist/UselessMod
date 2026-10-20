package com.sorrowmist.useless;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.command.UselessCommands;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.items.BeefCropHarvest;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.utils.mining.RightClickChainer;
import com.sorrowmist.useless.compat.constructionwand.ConstructionWandLogic;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import com.sorrowmist.useless.compat.forge1201.OptionalIntegration;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(UselessMod.MODID)
public class UselessMod {
    public static final String MODID = "useless_mod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UselessMod() {
        com.sorrowmist.useless.compat.forge1201.capability.RegisterCapabilitiesEvent.initialize();
        IEventBus modEventBus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var modContext=net.minecraftforge.fml.ModLoadingContext.get();
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ModIngredientTypes.register();
            modEventBus.post(new com.sorrowmist.useless.compat.forge1201.capability.RegisterCapabilitiesEvent());
            modEventBus.post(new com.sorrowmist.useless.compat.forge1201.network.RegisterPayloadHandlersEvent(MODID));
        }));
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerBuiltinResourcePacks);
        UComponents.init(modEventBus);

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

        // 无用型紧凑 C9/F9/L9：只在 ECO 存在时注册。
        // 这里的类加载是条件性的——ECO 缺席时 NeoEcoCompactRegistry 永远不会被初始化。
        String compactEcoRegistry = "com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry";
        if (ModList.get().isLoaded("neoecoae") && OptionalIntegration.hasClass(compactEcoRegistry)) {
            OptionalIntegration.call("neoecoae", compactEcoRegistry,
                    "init", Void.class, null, new Class<?>[]{IEventBus.class}, modEventBus);
        }

        // 纬度注册
        UselessDimensions.init(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        modContext.registerConfig(ModConfig.Type.COMMON, ConfigManager.COMMON_SPEC, "useless_mod-common.toml");
        modContext.registerConfig(ModConfig.Type.CLIENT, ConfigManager.CLIENT_SPEC, "useless_mod-client.toml");
        modContext.registerConfig(ModConfig.Type.SERVER, ConfigManager.SERVER_SPEC, "useless_mod-server.toml");
    }

    // 便捷 ResourceLocation 工具
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        RecipeAdapterCompatRegistry.init(event);
        String ecoCompat = "com.sorrowmist.useless.compat.neoecoae.NeoEcoCompat";
        if (ModList.get().isLoaded("neoecoae") && OptionalIntegration.hasClass(ecoCompat)) {
            OptionalIntegration.call("neoecoae", ecoCompat,
                    "initialize", Void.class, null, new Class<?>[]{});
        }
    }

    private void registerBuiltinResourcePacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        var root = ModList.get().getModFileById(MODID).getFile().findResource("xia");
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("useless_mod:xia",
                    Component.translatable("pack.useless_mod.xia.name"), false,
                    id -> new net.minecraftforge.resource.PathPackResources(id, true, root),
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        UselessCommands.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (ConstructionWandLogic.handleRightClickBlock(event)) return;

        ItemStack stack = event.getItemStack();

        // 顺手收菜：先于其它模组的右键收菜逻辑收获成熟作物，并将种子保留在地里，
        // 避免部分整合包的收菜功能对造化杖连根拔除作物。
        // 潜行右键时不接管，以便将本次交互让给其它模组。
        // 按住连锁键（Tab）时按连锁范围整片收。
        if (!event.isCanceled()
                && stack.getItem() instanceof EndlessBeafItem
                && EndlessBeafItem.isCropHarvestEnabled(stack)
                && !event.getEntity().isShiftKeyDown()
                && BeefCropHarvest.isHarvestable(event.getLevel().getBlockState(event.getPos()))) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (RightClickChainer.shouldChain(event.getEntity(), stack)) {
                    RightClickChainer.harvestCrops(serverLevel, event.getPos(), event.getEntity(), stack);
                } else {
                    BeefCropHarvest.harvest(serverLevel, event.getPos(), event.getEntity(), stack);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            return;
        }

        if (stack.getItem() instanceof EndlessBeafItem) {
            InteractionResult teleportResult = EndlessBeafItem.tryTeleport(
                    event.getLevel(), event.getEntity(), stack);
            if (teleportResult != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(teleportResult);
                return;
            }

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

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ConstructionWandLogic.handleRightClickItem(event);
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
            return OptionalIntegration.call("occultism",
                    "com.sorrowmist.useless.content.recipe.adapters.occultism.OccultismSpiritEggHandler",
                    "trySpawn", InteractionResult.class, InteractionResult.PASS,
                    new Class<?>[]{PlayerInteractEvent.RightClickBlock.class}, event);
        }
    }

    @SubscribeEvent
    public void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        if (!(event.getItemStack().getItem() instanceof EndlessBeafItem)) return;

        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;
        event.removeAttribute(Attributes.ATTACK_DAMAGE);
        event.addModifier(Attributes.ATTACK_DAMAGE, EndlessBeafItem.createAttackDamageModifier());
        event.removeAttribute(Attributes.ATTACK_SPEED);
        event.addModifier(Attributes.ATTACK_SPEED, EndlessBeafItem.createAttackSpeedModifier());
        event.addModifier(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),
                new AttributeModifier(java.util.UUID.nameUUIDFromBytes("useless_mod:beef_entity_reach".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "useless_mod_beef_entity_reach", ConfigManager.getBeefToolEntityInteractionRange(),
                        AttributeModifier.Operation.ADDITION));
        event.addModifier(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(),
                new AttributeModifier(java.util.UUID.nameUUIDFromBytes("useless_mod:beef_block_reach".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "useless_mod_beef_block_reach", ConfigManager.getBeefToolBlockInteractionRange(),
                        AttributeModifier.Operation.ADDITION));
    }
}
