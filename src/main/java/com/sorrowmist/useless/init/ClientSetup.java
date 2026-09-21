package com.sorrowmist.useless.init;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.EnumColor;
import com.sorrowmist.useless.client.gui.AdvancedAlloyFurnaceScreen;
import com.sorrowmist.useless.client.gui.PagedRecoverableScreen;
import com.sorrowmist.useless.client.gui.PatternAssemblyScreen;
import com.sorrowmist.useless.client.gui.MoldHubScreen;
import com.sorrowmist.useless.client.gui.MultiblockAlloyFurnaceScreen;
import com.sorrowmist.useless.client.gui.PassiveCraftingHatchScreen;
import com.sorrowmist.useless.client.gui.OreGeneratorScreen;
import com.sorrowmist.useless.client.gui.DimensionConfigScreen;
import com.sorrowmist.useless.client.render.ctm.CtmModelRegistrar;
import com.sorrowmist.useless.client.render.supervisor.SupervisorModelLoader;
import com.sorrowmist.useless.compat.jei.JEIPlugin;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.client.gui.PatternConverterScreen;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientSetup {
    private static Level observedClientLevel;
    /** 客户端配方目录的待重建标记；登录、标签包与数据包重载都会置位，由 onClientTick 统一消费。 */
    private static volatile boolean recipeCatalogDirty;
    /**
     * 本次客户端会话是否已经收到过标签包。
     *
     * <p>部分可选 adapter 依赖同步下来的物品标签。配方包先于标签包到达，若此时就构建，
     * 依赖标签的 adapter 会全部产出为空，得到一份不完整的目录，并在标签到达后立刻被推翻重建。</p>
     */
    private static volatile boolean clientTagsReceived;
    /** 脏标记置位后经过的 tick 数，用于标签包迟迟不到时兜底构建。 */
    private static int recipeCatalogDirtyTicks;
    /** 等待标签包的上限（tick）。超过后不再等待，避免标签包缺失时目录永远构建不出来。 */
    private static final int TAG_WAIT_TICKS = 100;
    /**
     * 已在后台发起构建、正等待目录就绪以刷新 JEI。
     *
     * <p>构建本身已经挪到后台线程（见 {@link AlloyFurnaceRecipeCatalog#prewarmAsync}），
     * 但 JEI 的展示刷新必须在客户端线程做，因此这里记住「欠一次刷新」，在后续 tick 里
     * 等目录就绪后再补上，而不是在渲染线程上同步等构建完成。</p>
     */
    private static boolean awaitingCatalogRefresh;

    @SubscribeEvent
    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        CtmModelRegistrar.modifyBakingResult(event);
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(UselessMod.id("supervisor"), SupervisorModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void onItemColor(RegisterColorHandlersEvent.Item event) {
        for (var itemMap : GlowPlasticBlock.ALL_BLOCK_ITEM_MAPS) {
            for (EnumColor color : EnumColor.valuesInOrder()) {
                var item = itemMap.get(color).get();
                event.register((stack, tintIndex) -> tintIndex == 0 ? color.getRgb() : 0xFFFFFFFF,
                        item);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockColor(RegisterColorHandlersEvent.Block event) {
        for (var blockMap : GlowPlasticBlock.ALL_BLOCK_MAPS) {
            for (EnumColor color : EnumColor.valuesInOrder()) {
                var block = blockMap.get(color).get();
                event.register((state, world, pos, tintIndex) -> tintIndex == 0 ? color.getRgb() : 0xFFFFFFFF,
                        block);
            }
        }
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuType.ADVANCED_ALLOY_FURNACE_MENU.get(), AdvancedAlloyFurnaceScreen::new);
        event.register(ModMenuType.ME_PATTERN_ASSEMBLY_MENU.get(), PatternAssemblyScreen::new);
        event.register(ModMenuType.OMNIVERSAL_MOLD_HUB_MENU.get(), MoldHubScreen::new);
        event.register(ModMenuType.MULTIBLOCK_ALLOY_FURNACE_MENU.get(), MultiblockAlloyFurnaceScreen::new);
        event.register(ModMenuType.PASSIVE_CRAFTING_HATCH_MENU.get(), PassiveCraftingHatchScreen::new);
        event.register(ModMenuType.ORE_GENERATOR_MENU.get(), OreGeneratorScreen::new);
        event.register(ModMenuType.DIMENSION_CONFIG_MENU.get(), DimensionConfigScreen::new);
        event.register(ModMenuType.PATTERN_CONVERTER_MENU.get(), PatternConverterScreen::new);
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        // 只登记待刷新，不在这里直接重建：登录路径里本事件、标签包事件与 level 切换会在极短时间
        // 内接连触发，逐次重建等于把同一份目录完整算好几遍。
        markRecipeCatalogDirty();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level != observedClientLevel) {
            observedClientLevel = level;
            if (level != null) {
                // The initial JEI registration can happen on the title screen, before a client level
                // exists. Rebuild once after joining a world so generated compat recipes are visible.
                markRecipeCatalogDirty();
            } else {
                AlloyFurnaceRecipeCatalog.invalidate();
                recipeCatalogDirty = false;
                // 离开世界：标签会随下一次登录重新下发，必须把「已收到」重置掉，
                // 否则下次进世界会跳过等待、又在标签落地前建出残缺目录。
                clientTagsReceived = false;
                recipeCatalogDirtyTicks = 0;
                awaitingCatalogRefresh = false;
            }
        }

        if (level == null) {
            recipeCatalogDirtyTicks = 0;
            return;
        }

        // 合并点：无论脏标记来自登录、标签包还是数据包重载，都在这里统一重建一次。
        if (recipeCatalogDirty) {
            // 登录路径里配方包会先于标签包到达。若在标签包落地前就构建，依赖标签的 adapter 会
            // 整批产出为空，得到一份随即被推翻的残缺目录（实测那次白跑花了 13.8 秒）。
            // 因此这里等到标签包到达再建；标签包始终不来时按 TAG_WAIT_TICKS 兜底。
            if (!clientTagsReceived && ++recipeCatalogDirtyTicks < TAG_WAIT_TICKS) {
                return;
            }
            recipeCatalogDirty = false;
            recipeCatalogDirtyTicks = 0;
            beginRecipeCatalogRebuild(level);
        }

        // 构建在后台线程进行，主线程不再被数万条配方的转换与指纹计算阻塞。
        // 就绪后再回到客户端线程刷新 JEI 展示与物品属性。
        if (awaitingCatalogRefresh && AlloyFurnaceRecipeCatalog.isReady(level)) {
            awaitingCatalogRefresh = false;
            onRecipeCatalogReady();
        }
    }

    /** 标记客户端配方目录需要在下一个 tick 重建。 */
    private static void markRecipeCatalogDirty() {
        recipeCatalogDirty = true;
    }

    /**
     * 失效目录并把重建交给后台线程，同时记下「欠一次 JEI 刷新」。
     *
     * <p>失效必须发生在主线程、且与后续构建之间不插入读取：后台构建会按当时读到的
     * RecipeManager 内容产出快照，若这中间有人触发同步构建，就会白算一份。</p>
     */
    private static void beginRecipeCatalogRebuild(Level level) {
        AlloyFurnaceRecipeCatalog.invalidate(level);
        awaitingCatalogRefresh = true;
        AlloyFurnaceRecipeCatalog.prewarmAsync(level);
    }

    /** 目录已就绪：在客户端线程刷新依赖它的 JEI 展示与物品属性。 */
    private static void onRecipeCatalogReady() {
        JEIPlugin.refreshAlloyFurnaceRecipes();
        if (Minecraft.getInstance().player != null) {
            EndlessBeafItem.refreshAttackDamage(Minecraft.getInstance().player);
        }
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // Only react to the tag packet the client actually received. The integrated server also
        // fires this event on its own thread for data-pack loads (including /reload); touching the
        // client recipe catalog or JEI from there fails the reload.
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) return;

        // Optional recipe adapters may read synced item tags. Recipes are updated before the
        // clientbound tag packet in some login paths, so refresh the catalog after those tags bind.
        //
        // 只在「首次收到标签包」时登记重建。目录已经按完整标签建好之后，本事件还会因各种
        // 原因重复触发（例如 JEI 注册期间的数据同步），此时再置脏只会让同一份目录被完整
        // 重建一遍——实测那次重复构建白白多花了 5.7 秒。配方数据的真正变更由
        // onRecipesUpdated 负责登记，这里只负责补齐标签依赖。
        if (clientTagsReceived) return;

        // 先置位「标签已到达」再登记重建：onClientTick 的合并点据此放行，避免在标签缺失时
        // 建出一份依赖标签的 adapter 全空的残缺目录。
        clientTagsReceived = true;
        markRecipeCatalogDirty();
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModItems.OMNIVERSAL_PATTERN.get())) return;
        var data = event.getItemStack().get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null) return;
        List<OmniversalPatternData.MoldTagInputSlot> moldTags =
                resolveMoldTagInputs(event.getItemStack(), data);
        event.getToolTip().add(createRecipeTooltip(data.recipeId()));
        if (!data.requiresMold()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.useless_mod.omniversal_pattern.mold",
                    Component.translatable("tooltip.useless_mod.omniversal_pattern.no_mold"))
                    .withStyle(ChatFormatting.GOLD));
        } else if (!data.displayMolds().isEmpty()) {
            Set<Integer> tagMoldSlots = new HashSet<>();
            for (var moldTag : moldTags) {
                tagMoldSlots.add(moldTag.moldSlot());
            }
            for (int moldSlot = 0; moldSlot < data.displayMolds().size(); moldSlot++) {
                // Older metadata stored one representative item for every mold slot. A tag-backed
                // mold must be shown by its tag instead of that arbitrary representative.
                if (tagMoldSlots.contains(moldSlot)) continue;
                var mold = data.displayMolds().get(moldSlot);
                event.getToolTip().add(Component.translatable(
                        "tooltip.useless_mod.omniversal_pattern.mold",
                        mold.getDisplayName()).withStyle(ChatFormatting.GOLD));
            }
            appendMoldTagTooltips(event, moldTags);
        } else if (!moldTags.isEmpty()) {
            appendMoldTagTooltips(event, moldTags);
        } else {
            Component mold = data.displayMold().<Component>map(key -> key.getDisplayName())
                    .orElseGet(() -> Component.translatable("tooltip.useless_mod.omniversal_pattern.unknown_mold"));
            event.getToolTip().add(Component.translatable(
                    "tooltip.useless_mod.omniversal_pattern.mold", mold).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void appendMoldTagTooltips(
            ItemTooltipEvent event, List<OmniversalPatternData.MoldTagInputSlot> moldTags) {
        for (var moldTag : moldTags) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.useless_mod.omniversal_pattern.mold",
                    Component.literal("#" + moldTag.tag().location()))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * Reconstructs mold tags for old metadata that predates the mold-tag field. This also makes
     * an already encoded pattern update its tooltip without requiring the player to encode it
     * again, while the bound recipe remains the source of truth for execution.
     */
    private static List<OmniversalPatternData.MoldTagInputSlot> resolveMoldTagInputs(
            ItemStack pattern, OmniversalPatternData data) {
        if (!data.requiresMold()) return List.of();
        if (!data.moldTagInputSlots().isEmpty()) {
            return data.moldTagInputSlots();
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        try {
            var entry = AlloyFurnaceRecipeCatalog.resolve(level, data.identity());
            if (entry.isEmpty() && pattern != null && !pattern.isEmpty()) {
                AEItemKey definition = AEItemKey.of(pattern);
                if (definition != null) {
                    entry = AlloyFurnaceRecipeCatalog.resolvePattern(
                            level, data.identity(), new AEProcessingPattern(definition));
                }
            }
            if (entry.isEmpty()) {
                // 用 entriesIfReady 而不是 entries：本方法跑在物品提示框渲染路径上（渲染线程），
                // 目录尚未就绪时同步构建会把画面卡住二十多秒。未就绪就直接放弃这次兜底，
                // 提示框少显示一行远比卡住主线程可接受。
                List<AlloyFurnaceRecipeCatalog.Entry> byId =
                        AlloyFurnaceRecipeCatalog.entriesIfReady(level).stream()
                                .filter(candidate -> candidate.identity().recipeId().equals(data.recipeId()))
                                .toList();
                if (byId.size() == 1) {
                    entry = java.util.Optional.of(byId.getFirst());
                }
            }
            return entry.map(candidate ->
                            OmniversalPatternEncoding.resolveMoldTagInputSlots(candidate.recipe()))
                    .orElse(List.of());
        } catch (RuntimeException ignored) {
            // Tooltips must remain safe while the client recipe catalog is still loading.
            return List.of();
        }
    }

    static Component createRecipeTooltip(ResourceLocation recipeId) {
        return Component.translatable(
                "tooltip.useless_mod.omniversal_pattern.recipe", recipeId.toString())
                .withStyle(ChatFormatting.DARK_GRAY);
    }
}
