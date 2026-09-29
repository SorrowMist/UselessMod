package com.sorrowmist.useless.compat.jei;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.gui.ChainGroupScreen;
import com.sorrowmist.useless.client.gui.DimensionConfigScreen;
import com.sorrowmist.useless.client.gui.StaffLinkScreen;
import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.stafflink.LinkFilterSlot;
import com.sorrowmist.useless.content.stafflink.StaffLinkFilters;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JeiPlugin
public final class JEIPlugin implements IModPlugin {
    private static final ResourceLocation UID = UselessMod.id("jei_plugin");
    private static IJeiRuntime runtime;
    private static final Map<AlloyFurnaceRecipeIdentity, AlloyFurnaceRecipeCatalog.Entry>
            registeredAlloyFurnaceRecipes = new LinkedHashMap<>();

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        GenericStackJeiIngredientProviders.initialize();
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(guiHelper));
        registration.addRecipeCategories(new CatalystInfoCategory(guiHelper));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        List<AlloyFurnaceRecipeCatalog.Entry> recipes = level == null || !AlloyFurnaceRecipeCatalog.isReady(level)
                ? List.of()
                : AlloyFurnaceRecipeCatalog.entries(level);
        registeredAlloyFurnaceRecipes.clear();
        for (AlloyFurnaceRecipeCatalog.Entry recipe : recipes) {
            registeredAlloyFurnaceRecipes.put(recipe.identity(), recipe);
        }
        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, recipes);
        registration.addRecipes(CatalystInfoCategory.TYPE,
                List.of(new CatalystInfoCategory.CatalystInfo()));
    }


    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // Only this category encodes omniversal patterns. Every other page in a pattern encoding
        // terminal keeps falling through to ae2jeiintegration's universal handler, which JEI consults
        // only when no category-specific handler is registered for the open menu.
        registration.addRecipeTransferHandler(
                new OmniversalPatternJeiTransferHandler<>(
                        PatternEncodingTermMenu.class,
                        PatternEncodingTermMenu.TYPE,
                        registration.getTransferHelper()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE);
        registerWirelessTransferHandler(registration);
        registerTianshuTransferHandlers(registration);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(DimensionConfigScreen.class,
                new DimensionConfigGhostHandler());
        registration.addGhostIngredientHandler(StaffLinkScreen.class,
                new StaffLinkGhostHandler());
        registration.addGhostIngredientHandler(ChainGroupScreen.class,
                new ChainGroupGhostHandler());
        // 等价组界面继承了 AbstractContainerScreen，JEI 内置的容器屏 handler 会自动接管，
        // 不需要（也不该）再注册 IScreenHandler——两套 handler 会互相抢。
    }

    /**
     * 连锁等价组界面接受 JEI 拖拽。
     *
     * <p>每个组行右端有一个拖拽槽，把方块拖进去就作为一条精确方块 ID 追加到该组。
     * 非方块物品直接不接受——等价组判定的是方块，收一个没有方块的物品没有意义。</p>
     */
    private static final class ChainGroupGhostHandler
            implements IGhostIngredientHandler<ChainGroupScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(ChainGroupScreen screen,
                                                   ITypedIngredient<I> ingredient,
                                                   boolean doStart) {
            ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
            if (!(stack.getItem() instanceof BlockItem blockItem)) return List.of();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());

            List<Target<I>> targets = new ArrayList<>(screen.chainGroupCount());
            for (int index = 0; index < screen.chainGroupCount(); index++) {
                if (!screen.isGroupRowVisible(index)) continue;
                final int groupIndex = index;
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(screen.groupDropZoneScreenX(groupIndex),
                                screen.groupDropZoneScreenY(groupIndex),
                                screen.dropZoneSize(), screen.dropZoneSize());
                    }

                    @Override
                    public void accept(I value) {
                        screen.addEntryFromBlock(groupIndex, blockId);
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    /**
     * 无线物流的过滤槽接受 JEI 拖拽。
     *
     * <p>标记按线路的资源类型分流：物品线路收物品，流体线路收<b>流体本身</b>（不是装它的桶），
     * 化学品线路收一只装满它的储罐。类型对不上的原料直接不接受——不接，比悄悄塞进一个
     * 语义不对的东西好。</p>
     *
     * <p>拖进来的只能是<b>具体标记</b>。{@code #tag} / 通配符那类模式表达的是「一整类」，
     * 从 JEI 拖某个具体物品过来时玩家要的显然是那个物品，所以这里不替玩家猜「你是不是想要
     * 这个物品的标签」——要模式请右键格子手打。</p>
     */
    private static final class StaffLinkGhostHandler
            implements IGhostIngredientHandler<StaffLinkScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(StaffLinkScreen screen,
                                                   ITypedIngredient<I> ingredient,
                                                   boolean doStart) {
            // 过滤格住在覆盖层面板里。面板没开时那些坐标指向的是主界面别的地方，
            // 拖过去会莫名其妙地落到空处（甚至落到底下某个控件上），所以直接不给目标。
            if (!screen.isFilterPanelOpen()) {
                screen.setJeiDragActive(false);
                return List.of();
            }
            StaffLinkRoute config = screen.getMenu().getSelectedConfig();
            if (config == null || !config.filterApplies()) {
                screen.setJeiDragActive(false);
                return List.of();
            }
            LinkFilterSlot marker = StaffLinkFilters.fromIngredient(
                    config.medium(), ingredient.getIngredient());
            if (marker == null) {
                screen.setJeiDragActive(false);
                return List.of();
            }
            // 真的要开始拖了（doStart=true）才点亮拖拽高亮；doStart=false 那次只是问「有没有目标」。
            if (doStart) {
                screen.setJeiDragActive(true);
            }

            List<Target<I>> targets = new ArrayList<>(StaffLinkScreen.filterSlotCount());
            for (int index = 0; index < StaffLinkScreen.filterSlotCount(); index++) {
                final int slotIndex = index;
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(screen.filterSlotScreenX(slotIndex),
                                screen.filterSlotScreenY(slotIndex),
                                StaffLinkScreen.filterSlotSize(), StaffLinkScreen.filterSlotSize());
                    }

                    @Override
                    public void accept(I value) {
                        screen.getMenu().setFilterSlot(slotIndex, marker);
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    private static final class DimensionConfigGhostHandler
            implements IGhostIngredientHandler<DimensionConfigScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(DimensionConfigScreen screen,
                                                   ITypedIngredient<I> ingredient,
                                                   boolean doStart) {
            ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
            if (!(stack.getItem() instanceof BlockItem)) return List.of();

            List<Target<I>> targets = new ArrayList<>(DimensionConfigMenu.GHOST_SLOT_COUNT);
            for (int index = 0; index < DimensionConfigMenu.GHOST_SLOT_COUNT; index++) {
                if (!screen.getMenu().isGhostSlotActive(index)) continue;
                int slotIndex = index;
                DimensionConfigMenu.GhostSlot slot = screen.getMenu().getGhostSlot(slotIndex);
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(screen.getGuiLeft() + slot.x,
                                screen.getGuiTop() + slot.y, 16, 16);
                    }

                    @Override
                    public void accept(I value) {
                        if (value instanceof ItemStack itemStack) {
                            screen.getMenu().setGhostSlotFromClient(slotIndex, itemStack);
                        }
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    /**
     * AE2 Lightning Tech provides separate menu classes for its Tianshu terminals. JEI resolves a
     * recipe transfer handler by the exact menu class, so its universal handler would otherwise win
     * and encode a normal AE2 processing pattern for this category.
     */
    private static void registerTianshuTransferHandlers(IRecipeTransferRegistration registration) {
        if (!ModList.get().isLoaded("ae2lt")) return;
        registerReflectiveTransferHandler(registration,
                "com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu");
        registerReflectiveTransferHandler(registration,
                "com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu");
    }

    @SuppressWarnings("unchecked")
    private static void registerReflectiveTransferHandler(
            IRecipeTransferRegistration registration, String menuClassName) {
        try {
            Class<?> menuClass = Class.forName(menuClassName);
            if (!PatternEncodingTermMenu.class.isAssignableFrom(menuClass)) return;
            var menuType = (MenuType<PatternEncodingTermMenu>) menuClass.getField("TYPE").get(null);
            registration.addRecipeTransferHandler(
                    new OmniversalPatternJeiTransferHandler<>(
                            (Class<PatternEncodingTermMenu>) menuClass,
                            menuType,
                            registration.getTransferHelper()),
                    AdvancedAlloyFurnaceRecipeCategory.TYPE);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            UselessMod.LOGGER.warn(
                    "Could not register the omniversal pattern transfer handler for {}.",
                    menuClassName,
                    exception);
        }
    }

    /**
     * The wireless pattern encoding terminal is a {@link PatternEncodingTermMenu} subclass, and JEI
     * matches handlers on the menu's exact class, so it needs its own registration. ae2wtlib is only
     * a runtime dependency here, hence the reflective lookup; without it the wireless terminal would
     * silently fall back to ae2jeiintegration's universal handler and encode a plain pattern.
     */
    @SuppressWarnings("unchecked")
    private static void registerWirelessTransferHandler(IRecipeTransferRegistration registration) {
        if (!ModList.get().isLoaded("ae2wtlib")) return;
        try {
            Class<?> menuClass = Class.forName("de.mari_023.ae2wtlib.wet.WETMenu");
            if (!PatternEncodingTermMenu.class.isAssignableFrom(menuClass)) return;
            var menuType = (MenuType<PatternEncodingTermMenu>) menuClass.getField("TYPE").get(null);
            registration.addRecipeTransferHandler(
                    new OmniversalPatternJeiTransferHandler<>(
                            (Class<PatternEncodingTermMenu>) menuClass,
                            menuType,
                            registration.getTransferHelper()),
                    AdvancedAlloyFurnaceRecipeCategory.TYPE);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            UselessMod.LOGGER.warn(
                    "Could not register the omniversal pattern transfer handler for ae2wtlib's wireless "
                            + "pattern encoding terminal; it will encode plain patterns instead.",
                    exception);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack singleBlockFurnace = new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
        registration.addRecipeCatalyst(singleBlockFurnace, AdvancedAlloyFurnaceRecipeCategory.TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE);
        registration.addRecipeCatalyst(singleBlockFurnace, CatalystInfoCategory.TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get()),
                CatalystInfoCategory.TYPE);

        BuiltInRegistries.ITEM.getTag(ModTags.CATALYSTS).ifPresent(tag -> {
            for (var holder : tag) {
                registration.addRecipeCatalyst(new ItemStack(holder.value()), CatalystInfoCategory.TYPE);
            }
        });
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        refreshAlloyFurnaceRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        registeredAlloyFurnaceRecipes.clear();
    }

    /** Adds recipes generated after JEI's initial registration, such as data-driven compat data. */
    public static void refreshAlloyFurnaceRecipes() {
        // TagsUpdatedEvent is fired on the server thread during /reload, but JEI's recipe manager
        // must be mutated on the client render thread. Hop back before touching JEI.
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(JEIPlugin::refreshAlloyFurnaceRecipes);
            return;
        }

        if (runtime == null) return;

        Level level = minecraft.level;
        if (level == null || !AlloyFurnaceRecipeCatalog.isReady(level)) return;

        List<AlloyFurnaceRecipeCatalog.Entry> additions = new ArrayList<>();
        for (AlloyFurnaceRecipeCatalog.Entry recipe : AlloyFurnaceRecipeCatalog.entries(level)) {
            if (!registeredAlloyFurnaceRecipes.containsKey(recipe.identity())) {
                registeredAlloyFurnaceRecipes.put(recipe.identity(), recipe);
                additions.add(recipe);
            }
        }
        if (!additions.isEmpty()) {
            runtime.getRecipeManager().addRecipes(
                    AdvancedAlloyFurnaceRecipeCategory.TYPE, additions);
        }
    }

    public static IJeiRuntime getRuntime() {
        return runtime;
    }

    public static void showAdvancedAlloyFurnaceRecipes() {
        if (runtime != null) {
            runtime.getRecipesGui().showTypes(List.of(AdvancedAlloyFurnaceRecipeCategory.TYPE));
        }
    }

    public static boolean isAvailable() {
        return runtime != null;
    }

}
