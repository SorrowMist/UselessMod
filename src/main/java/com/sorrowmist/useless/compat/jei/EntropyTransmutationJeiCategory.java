package com.sorrowmist.useless.compat.jei;

import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 展示「熵变机械臂潜行右键 ME 箱子」转化配方（{@code useless_mod:entropy_transmutation}）的 JEI 类别。
 *
 * <p>AE2JEI 的 {@code EntropyManipulatorCategory} 绑定的是 AE2 原生 {@code AERecipeTypes.ENTROPY}，
 * 只装载方块状态与流体状态层面的熵变配方，看不到本模组「按磁盘内物品合计」的转化配方，
 * 故本模组另建一个绑定 {@link EntropyTransmutationRecipe} 的类别，以熵变机械臂作为催化剂，
 * 让玩家在 JEI 中能查到万象合金炉等方块的获取方式。</p>
 *
 * <p>本机制不能仅靠槽位表达——它需要玩家先把物品放进 ME 箱子的存储磁盘、再手持熵变机械臂
 * 潜行右键该箱子，最后箱子原地变成产出方块。因此画布直接用图标与文字把这三步画出来，
 * 而不是只摆输入输出槽。</p>
 */
public class EntropyTransmutationJeiCategory
        implements IRecipeCategory<RecipeHolder<EntropyTransmutationRecipe>> {

    /** 本类别绑定的 JEI 配方类型，配方类为 {@link RecipeHolder}（与 AE2JEI 的做法一致）。 */
    public static final RecipeType<RecipeHolder<EntropyTransmutationRecipe>> TYPE = createRecipeType();

    /** 熵变机械臂的物品贴图；直接取原始贴图，避免物品渲染器叠加充能条。 */
    private static final ResourceLocation ENTROPY_MANIPULATOR_TEXTURE =
            AppEng.makeId("textures/item/entropy_manipulator.png");

    /** 布局尺寸与槽位参数。 */
    private static final int WIDTH = 160;
    private static final int HEIGHT = 74;

    private static final int STEP1_Y = 1;

    // ME 箱子与输入区两行槽位的中线对齐，避免偏居上方。
    // 输入区占 18..54，中线为 36；图标高 16，故顶边取 36 - 8 = 28。
    private static final int ME_CHEST_X = 5;
    private static final int ME_CHEST_Y = 28;
    // 输入槽按每行 4 个换行排布：8 项输入正好排成 4 列 2 行。
    private static final int INPUT_START_X = 26;
    private static final int INPUT_STEP = 18;
    private static final int INPUT_ROW1_Y = 18;
    private static final int INPUT_ROW_STEP = 18;
    private static final int MAX_SLOTS_PER_ROW = 4;

    // 箭头左移并让出输出槽的占位，避免箭身被产物方块遮挡。
    private static final int ARROW_X = 100;
    private static final int ARROW_Y = 27;

    /** 熵变机械臂图标相对原贴图的缩放系数，用于在箭头上方放置动作标记。 */
    private static final float MANIPULATOR_ICON_SCALE = 0.5f;
    /** 占位左边界取箭头中心减去半个占位宽度，使缩小后的图标在箭头上方水平居中。 */
    private static final int MANIPULATOR_ICON_X = 103;
    private static final int MANIPULATOR_ICON_Y = 12;
    /** 缩放后仍按 16×16 申报占位，使悬浮判定覆盖整个图标而不是缩小的实际绘制区域。 */
    private static final int MANIPULATOR_ICON_SIZE = 16;

    private static final int OUTPUT_X = 126;
    private static final int OUTPUT_Y = 27;

    // 机械臂图标移到箭头上方后，底部仅保留居中的步骤说明。
    private static final int STEP2_TEXT_X = 5;
    private static final int STEP2_TEXT_Y = 58;

    /** 画布文字颜色；JEI 配方区域为深色背景，用白色保证可读。 */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final IDrawable icon;
    private final IDrawable manipulatorIcon;
    private final IDrawable meChestIcon;
    private final Component title;

    EntropyTransmutationJeiCategory(IGuiHelper guiHelper) {
        // 与 AE2JEI 的熵变类别保持一致：用原始物品贴图而非物品渲染器，否则会显示充能条。
        this.icon = guiHelper.drawableBuilder(ENTROPY_MANIPULATOR_TEXTURE, 0, 0, 16, 16)
                .setTextureSize(16, 16)
                .build();
        this.manipulatorIcon = this.icon;
        this.meChestIcon = guiHelper.createDrawableItemStack(AEBlocks.ME_CHEST.stack());
        this.title = Component.translatable("jei.useless_mod.entropy_transmutation.title");
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<RecipeHolder<EntropyTransmutationRecipe>> createRecipeType() {
        // 泛型在运行时擦除，RecipeHolder 的实际类即足够；与 AE2JEI 用 createFromVanilla 得到的类型同构。
        return RecipeType.create(UselessMod.MODID, "entropy_transmutation",
                (Class<RecipeHolder<EntropyTransmutationRecipe>>) (Class<?>) RecipeHolder.class);
    }

    @Override
    public @NotNull RecipeType<RecipeHolder<EntropyTransmutationRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return this.title;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public @NotNull IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder,
                          @NotNull RecipeHolder<EntropyTransmutationRecipe> holder,
                          @NotNull IFocusGroup focuses) {
        EntropyTransmutationRecipe recipe = holder.value();

        int count = recipe.inputs().size();
        for (int i = 0; i < count; i++) {
            // 每行 MAX_SLOTS_PER_ROW 个换行，以容纳一阶锭加七种基础材料共 8 项输入。
            int slotX = INPUT_START_X + (i % MAX_SLOTS_PER_ROW) * INPUT_STEP;
            int slotY = INPUT_ROW1_Y + (i / MAX_SLOTS_PER_ROW) * INPUT_ROW_STEP;
            CountedIngredient counted = recipe.inputs().get(i);
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, slotX, slotY)
                    .setStandardSlotBackground();
            ItemStack[] candidates = counted.ingredient().getItems();
            if (candidates.length > 0) {
                // 角标数值即该输入在配方中的所需数量，不做截断，否则会显示错误的配方用量。
                int displayCount = (int) Math.min(counted.count(), Integer.MAX_VALUE);
                List<ItemStack> displays = new ArrayList<>(candidates.length);
                for (ItemStack candidate : candidates) {
                    displays.add(candidate.copyWithCount(displayCount));
                }
                slot.addItemStacks(displays)
                        .setCustomRenderer(VanillaTypes.ITEM_STACK, ItemStackCountRenderer.INSTANCE);
            }
            slot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable("jei.useless_mod.entropy_transmutation.input")
                            .withStyle(ChatFormatting.GRAY)));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setStandardSlotBackground()
                .addItemStack(recipe.output())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.useless_mod.entropy_transmutation.output")
                            .withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.useless_mod.entropy_transmutation.hint")
                            .withStyle(ChatFormatting.DARK_GRAY));
                });
    }

    @Override
    public void createRecipeExtras(@NotNull IRecipeExtrasBuilder builder,
                                   @NotNull RecipeHolder<EntropyTransmutationRecipe> holder,
                                   @NotNull IFocusGroup focuses) {
        // 第一步：画布只留一句提示，完整说明挂在悬停文本上，避免文字挤满配方区域。
        builder.addText(Component.translatable("jei.useless_mod.entropy_transmutation.step1"), WIDTH, 10)
                .setPosition(0, STEP1_Y)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setColor(TEXT_COLOR)
                .setTooltip(Component.translatable("jei.useless_mod.entropy_transmutation.step1.detail"));

        // ME 箱子图标：点明这些物品是放进「这个」容器的磁盘里，而不是直接放进工作台。
        builder.addDrawableWidget(this.meChestIcon)
                .setPosition(ME_CHEST_X, ME_CHEST_Y)
                .setTooltip(Component.translatable("jei.useless_mod.entropy_transmutation.step1.detail"));

        builder.addRecipeArrowWidget().setPosition(ARROW_X, ARROW_Y);

        // 熵变机械臂图标缩至半尺寸后置于箭头上方，作为「用此工具执行」的动作标记；
        // IDrawable 不支持缩放，故用自定义控件在绘制时施加 PoseStack 缩放。
        builder.addWidget(new ScaledIconWidget(this.manipulatorIcon,
                new ScreenPosition(MANIPULATOR_ICON_X, MANIPULATOR_ICON_Y),
                MANIPULATOR_ICON_SIZE, MANIPULATOR_ICON_SCALE,
                Component.translatable("jei.useless_mod.entropy_transmutation.step2.detail")));

        // 第二步：底部提示同样精简，详细操作说明在悬停时显示。
        builder.addText(Component.translatable("jei.useless_mod.entropy_transmutation.step2"),
                        WIDTH - STEP2_TEXT_X, 10)
                .setPosition(STEP2_TEXT_X, STEP2_TEXT_Y)
                .setColor(TEXT_COLOR)
                .setTooltip(Component.translatable("jei.useless_mod.entropy_transmutation.step2.detail"));
    }

    @Override
    public @NotNull ResourceLocation getRegistryName(
            @NotNull RecipeHolder<EntropyTransmutationRecipe> holder) {
        return holder.id();
    }

    /**
     * 按缩放系数绘制图标、并以未缩放的占位尺寸响应悬浮的配方控件。
     *
     * <p>JEI 的 {@link IDrawable} 只能按原始尺寸绘制，无法缩小；而缩小图标后若按实际绘制
     * 区域申报占位，鼠标悬浮判定会小到难以命中。因此这里把图标从占位区域中心等比缩小绘制，
     * 悬浮判定仍沿用 16×16 的原始占位。</p>
     */
    private static final class ScaledIconWidget implements IRecipeWidget {

        private final IDrawable drawable;
        private final ScreenPosition position;
        private final int size;
        private final float scale;
        private final Component tooltip;

        ScaledIconWidget(IDrawable drawable, ScreenPosition position, int size, float scale,
                         Component tooltip) {
            this.drawable = drawable;
            this.position = position;
            this.size = size;
            this.scale = scale;
            this.tooltip = tooltip;
        }

        @Override
        public @NotNull ScreenPosition getPosition() {
            return this.position;
        }

        @Override
        public void drawWidget(@NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            graphics.pose().pushPose();
            // 先平移到占位区域左上角，再按占位区域中心为缩放原点，使缩小后的图标居中于原 16×16 位置。
            graphics.pose().translate(this.position.x() + this.size * (1.0f - this.scale) / 2.0f,
                    this.position.y() + this.size * (1.0f - this.scale) / 2.0f, 0.0f);
            graphics.pose().scale(this.scale, this.scale, 1.0f);
            this.drawable.draw(graphics, 0, 0);
            graphics.pose().popPose();
        }

        @Override
        public void getTooltip(@NotNull ITooltipBuilder tooltip, double mouseX, double mouseY) {
            tooltip.add(this.tooltip);
        }
    }
}