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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EntropyTransmutationJeiCategory
        implements IRecipeCategory<EntropyTransmutationRecipe> {

    public static final RecipeType<EntropyTransmutationRecipe> TYPE =
            RecipeType.create(UselessMod.MODID, "entropy_transmutation", EntropyTransmutationRecipe.class);

    private static final ResourceLocation ENTROPY_MANIPULATOR_TEXTURE =
            AppEng.makeId("textures/item/entropy_manipulator.png");

    private static final int WIDTH = 160;
    private static final int HEIGHT = 74;

    private static final int STEP1_Y = 1;
    private static final int ME_CHEST_X = 5;
    private static final int ME_CHEST_Y = 28;
    private static final int INPUT_START_X = 26;
    private static final int INPUT_STEP = 18;
    private static final int INPUT_ROW1_Y = 18;
    private static final int INPUT_ROW_STEP = 18;
    private static final int MAX_SLOTS_PER_ROW = 4;
    private static final int ARROW_X = 100;
    private static final int ARROW_Y = 27;

    private static final float MANIPULATOR_ICON_SCALE = 0.8f;
    private static final int MANIPULATOR_ICON_X = 101;
    private static final int MANIPULATOR_ICON_Y = 12;
    private static final int MANIPULATOR_ICON_SIZE = 16;

    private static final int OUTPUT_X = 126;
    private static final int OUTPUT_Y = 27;
    private static final int STEP2_TEXT_Y = 58;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final IDrawable icon;
    private final IDrawable manipulatorIcon;
    private final IDrawable meChestIcon;
    private final Component title;

    public EntropyTransmutationJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.drawableBuilder(ENTROPY_MANIPULATOR_TEXTURE, 0, 0, 16, 16)
                .setTextureSize(16, 16)
                .build();
        this.manipulatorIcon = this.icon;
        this.meChestIcon = guiHelper.createDrawableItemStack(AEBlocks.CHEST.stack());
        this.title = Component.translatable("jei.useless_mod.entropy_transmutation.title");
    }

    @Override
    public @NotNull RecipeType<EntropyTransmutationRecipe> getRecipeType() {
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
                          @NotNull EntropyTransmutationRecipe recipe,
                          @NotNull IFocusGroup focuses) {

        int count = recipe.inputs().size();
        for (int i = 0; i < count; i++) {
            int slotX = INPUT_START_X + (i % MAX_SLOTS_PER_ROW) * INPUT_STEP;
            int slotY = INPUT_ROW1_Y + (i / MAX_SLOTS_PER_ROW) * INPUT_ROW_STEP;
            CountedIngredient counted = recipe.inputs().get(i);
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, slotX, slotY)
                    .setStandardSlotBackground();
            ItemStack[] candidates = counted.ingredient().getItems();
            if (candidates.length > 0) {
                int displayCount = (int) Math.min(counted.count(), Integer.MAX_VALUE);
                List<ItemStack> displays = new ArrayList<>(candidates.length);
                for (ItemStack candidate : candidates) {
                    displays.add(candidate.copyWithCount(displayCount));
                }
                slot.addItemStacks(displays)
                        .setCustomRenderer(VanillaTypes.ITEM_STACK, countRenderer(counted.count()));
            }
            slot.addRichTooltipCallback((view, tooltip) -> {
                tooltip.add(Component.translatable("jei.useless_mod.entropy_transmutation.input")
                        .withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("jei.useless_mod.tooltip.amount", Long.toString(counted.count()))
                        .withStyle(ChatFormatting.GRAY));
            });
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
                                   @NotNull EntropyTransmutationRecipe recipe,
                                   @NotNull IFocusGroup focuses) {
        builder.addText(Component.translatable("jei.useless_mod.entropy_transmutation.step1"), WIDTH, 10)
                .setPosition(0, STEP1_Y)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setColor(TEXT_COLOR)
                .setTooltip(Component.translatable("jei.useless_mod.entropy_transmutation.step1.detail"));
        builder.addDrawableWidget(this.meChestIcon)
                .setPosition(ME_CHEST_X, ME_CHEST_Y)
                .setTooltip(Component.translatable("jei.useless_mod.entropy_transmutation.step1.detail"));

        builder.addRecipeArrowWidget().setPosition(ARROW_X, ARROW_Y);
        builder.addWidget(new ScaledIconWidget(this.manipulatorIcon,
                new ScreenPosition(MANIPULATOR_ICON_X, MANIPULATOR_ICON_Y),
                MANIPULATOR_ICON_SIZE, MANIPULATOR_ICON_SCALE,
                Component.translatable("jei.useless_mod.entropy_transmutation.step2.detail")));
        builder.addText(Component.translatable("jei.useless_mod.entropy_transmutation.step2"), WIDTH, 10)
                .setPosition(0, STEP2_TEXT_Y)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setColor(TEXT_COLOR);
    }

    @Override
    public @NotNull ResourceLocation getRegistryName(
            @NotNull EntropyTransmutationRecipe recipe) {
        return recipe.id();
    }

    private static mezz.jei.api.ingredients.IIngredientRenderer<ItemStack> countRenderer(long amount) {
        if (amount <= Integer.MAX_VALUE) return ItemStackCountRenderer.INSTANCE;
        return new mezz.jei.api.ingredients.IIngredientRenderer<>() {
            @Override public void render(GuiGraphics graphics, ItemStack stack) {
                if (stack == null || stack.isEmpty()) return;
                graphics.renderFakeItem(stack, 0, 0);
                var font = net.minecraft.client.Minecraft.getInstance().font;
                String text = compactCount(amount);
                float scale = Math.min(0.666f, 16.0f / Math.max(1, font.width(text)));
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(16.0 - font.width(text) * scale, 16.0 - font.lineHeight * scale, 200);
                    graphics.pose().scale(scale, scale, 1);
                    graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
                } finally {
                    graphics.pose().popPose();
                }
            }
            @Override public List<Component> getTooltip(ItemStack stack, net.minecraft.world.item.TooltipFlag flag) {
                return ItemStackCountRenderer.INSTANCE.getTooltip(stack, flag);
            }
        };
    }

    private static String compactCount(long count) {
        long divisor = 1;
        String[] suffixes = {"", "k", "M", "G", "T", "P", "E"};
        int suffix = 0;
        while (suffix + 1 < suffixes.length && count / divisor >= 1000) {
            divisor *= 1000;
            suffix++;
        }
        return String.format(java.util.Locale.ROOT, "%.2f%s", (double) count / divisor, suffixes[suffix]);
    }

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
            graphics.pose().translate(this.size * (1.0f - this.scale) / 2.0f,
                    this.size * (1.0f - this.scale) / 2.0f, 0.0f);
            graphics.pose().scale(this.scale, this.scale, 1.0f);
            this.drawable.draw(graphics, 0, 0);
            graphics.pose().popPose();
        }

        @Override
        public void getTooltip(@NotNull ITooltipBuilder tooltip, double mouseX, double mouseY) {
            if (mouseX < 0 || mouseY < 0 || mouseX >= this.size || mouseY >= this.size) {
                return;
            }
            tooltip.add(this.tooltip);
        }
    }
}