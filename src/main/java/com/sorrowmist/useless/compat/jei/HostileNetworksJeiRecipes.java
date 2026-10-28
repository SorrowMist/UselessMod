package com.sorrowmist.useless.compat.jei;

import com.google.gson.JsonObject;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Display-only HNN groups. Never publishes synthetic identities or changes the server catalog. */
final class HostileNetworksJeiRecipes {
    static final String MODEL_SLOT = "hnn_model";
    static final String INPUT_SLOT = "hnn_matrix";
    private static final String OUTPUT_SLOT = "hnn_output_";
    private static final ResourceLocation PREDICTION = new ResourceLocation("hostilenetworks", "prediction");
    private static Map<AlloyFurnaceRecipeIdentity, List<Entry>> groups = Map.of();
    private static final Map<IRecipeSlotDrawable, Entry> displaySelections = new WeakHashMap<>();

    private HostileNetworksJeiRecipes() { }

    /** EMI/JEMI imports a static recipe; keep exact entries there until it supports grouped picks. */
    static List<Entry> prepare(List<Entry> entries, boolean groupingEnabled) {
        if (!groupingEnabled) {
            clear();
            return entries;
        }
        Map<String, List<Entry>> candidates = new LinkedHashMap<>();
        for (Entry entry : entries) {
            String key = groupKey(entry);
            if (key != null) candidates.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        Map<AlloyFurnaceRecipeIdentity, List<Entry>> next = new HashMap<>();
        Map<AlloyFurnaceRecipeIdentity, Entry> representatives = new HashMap<>();
        for (List<Entry> group : candidates.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparingInt(entry -> info(entry).minData()));
            // Conflicting recipes for the same data are not interchangeable display variants.
            if (group.stream().map(entry -> info(entry).minData()).distinct().count() != group.size()) continue;
            Entry representative = group.get(0);
            next.put(representative.identity(), List.copyOf(group));
            for (Entry member : group) representatives.put(member.identity(), representative);
        }
        groups = Map.copyOf(next);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            Entry representative = representatives.get(entry.identity());
            if (representative == null || representative.identity().equals(entry.identity())) result.add(entry);
        }
        return List.copyOf(result);
    }

    static void clear() {
        groups = Map.of();
        displaySelections.clear();
    }

    static List<Entry> variants(Entry entry) {
        return groups.getOrDefault(entry.identity(), List.of());
    }

    @Nullable
    private static String groupKey(Entry entry) {
        Info info = info(entry);
        if (info == null || info.minData() != info.maxData()) return null;
        AdvancedAlloyFurnaceRecipe recipe = entry.recipe();
        if (recipe.inputs().size() != 1 || recipe.outputs().isEmpty() || recipe.outputs().size() > 12
                || !recipe.inputFluids().isEmpty() || !recipe.outputFluids().isEmpty()
                || !recipe.keyInputs().isEmpty() || !recipe.keyOutputs().isEmpty()
                || !recipe.catalyst().isEmpty()) return null;
        ItemStack[] inputs = recipe.inputs().get(0).ingredient().getItems();
        ItemStack[] models = recipe.mold().getItems();
        if (inputs.length != 1 || inputs[0].isEmpty() || models.length != 1 || models[0].isEmpty()
                || info.operations() <= 0 || info.operations() > Integer.MAX_VALUE) return null;
        String path = recipe.id().getPath();
        int data = path.indexOf("/data_");
        if (data < 0) return null;
        StringBuilder key = new StringBuilder(recipe.id().getNamespace()).append(':')
                .append(path, 0, data).append('|').append(info.model()).append('|')
                .append(recipe.inputs().get(0).ingredient().toJson()).append('|')
                .append(recipe.mode()).append('|').append(recipe.tier());
        // Counts vary, but differing item/NBT shapes must remain separate searchable recipes.
        for (ItemStack output : recipe.outputs()) {
            if (output == null || output.isEmpty()) return null;
            key.append('|').append(BuiltInRegistries.ITEM.getKey(output.getItem())).append(output.getTag());
        }
        return key.toString();
    }

    /** Uses serialized predicates, not HNN classes, so an absent optional mod is safe. */
    @Nullable
    static Info info(Entry entry) {
        if (!RecipeSourceIds.HOSTILE_NETWORKS.equals(entry.sourceId())
                || !"hostilenetworks".equals(entry.recipe().id().getNamespace())
                || !entry.recipe().id().getPath().startsWith("compat/inference/")
                || entry.recipe().molds().size() != 1) return null;
        try {
            JsonObject json = entry.recipe().mold().toJson().getAsJsonObject();
            if (!json.has("type") || !"useless_mod:data_model_range".equals(json.get("type").getAsString())) return null;
            int min = json.get("min_data").getAsInt();
            int max = json.get("max_data").getAsInt();
            if (min < 0 || max < min || entry.recipe().inputs().isEmpty()) return null;
            return new Info(json.get("model").getAsString(), min, max,
                    entry.recipe().inputs().get(0).count());
        } catch (RuntimeException exception) {
            return null; // An unsupported predicate must keep its normal, ungrouped rendering.
        }
    }

    static boolean setRecipe(IRecipeLayoutBuilder builder, Entry entry) {
        List<Entry> variants = variants(entry);
        if (variants.isEmpty()) return false;
        List<IRecipeSlotBuilder> linked = new ArrayList<>();
        IRecipeSlotBuilder input = builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setSlotName(INPUT_SLOT)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, ItemStackCountRenderer.INSTANCE);
        input.addItemStacks(variants.stream().map(HostileNetworksJeiRecipes::matrix).toList());
        input.addRichTooltipCallback((slot, tooltip) -> addAmount(tooltip, slot));
        linked.add(input);
        for (int i = 0; i < entry.recipe().outputs().size(); i++) {
            final int index = i;
            IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT,
                    141 + (i % 4) * 18, 1 + (i / 4) * 18).setSlotName(OUTPUT_SLOT + i)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, ItemStackCountRenderer.INSTANCE);
            output.addItemStacks(variants.stream().map(e -> e.recipe().outputs().get(index).copy()).toList());
            output.addRichTooltipCallback((slot, tooltip) -> addAmount(tooltip, slot));
            linked.add(output);
        }
        IRecipeSlotBuilder mold = builder.addSlot(RecipeIngredientRole.CATALYST, 89, 55).setSlotName(MODEL_SLOT);
        mold.addItemStacks(variants.stream().map(HostileNetworksJeiRecipes::model).toList());
        mold.addRichTooltipCallback((slot, tooltip) -> {
            Entry current = selectedByModel(entry, slot.getDisplayedItemStack().orElse(ItemStack.EMPTY));
            if (current != null) addDetails(tooltip, current);
        });
        linked.add(mold);
        // JEI lookups must index every exact model, not just the group's representative.
        builder.createFocusLink(linked.toArray(IRecipeSlotBuilder[]::new));
        return true;
    }

    static void createExtras(IRecipeExtrasBuilder builder, Entry entry, mezz.jei.api.recipe.IFocusGroup focuses) {
        List<Entry> variants = variants(entry);
        if (variants.isEmpty()) return;
        var slots = builder.getRecipeSlots();
        IRecipeSlotDrawable model = slots.findSlotByName(MODEL_SLOT).orElse(null);
        if (model == null) return;
        // getDisplayedIngredients is missing on supported older JEI releases. Resolve exact
        // model focuses through the stable focus API instead of sampling a cycling slot.
        List<ItemStack> focusedModels = focuses.getItemStackFocuses()
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> variants.stream().anyMatch(candidate -> candidate.recipe().mold().test(stack)))
                .toList();
        List<Entry> choices = focusedModels.isEmpty() ? variants : variants.stream()
                .filter(candidate -> focusedModels.stream().anyMatch(stack -> candidate.recipe().mold().test(stack)))
                .toList();
        if (choices.isEmpty()) return;
        Entry initial = choices.get(0);
        java.util.function.Consumer<Entry> display = selected -> {
            displaySelections.put(model, selected);
            display(slots, selected);
        };
        display.accept(initial);
        // addCycleButton is unavailable on the pack's JEI 15.48. Use its public widget API.
        var selector = new HostileNetworksDataSelector(choices, display);
        builder.addWidget(selector);
        builder.addInputHandler(selector);
    }

    /** JEI clears display overrides whenever its automatic ingredient cycle advances. */
    static void refreshDisplay(Entry entry, List<IRecipeSlotDrawable> slots) {
        IRecipeSlotDrawable model = slots.stream()
                .filter(slot -> slot.getSlotName().filter(MODEL_SLOT::equals).isPresent())
                .findFirst().orElse(null);
        Entry selected = displaySelections.get(model);
        if (selected == null || variants(entry).stream()
                .noneMatch(variant -> variant.identity().equals(selected.identity()))) return;
        display(() -> slots, selected);
    }

    private static void display(IRecipeSlotDrawablesView slots, Entry selected) {
        override(slots.findSlotByName(INPUT_SLOT).orElseThrow(), matrix(selected));
        for (int i = 0; i < selected.recipe().outputs().size(); i++) {
            override(slots.findSlotByName(OUTPUT_SLOT + i).orElseThrow(),
                    selected.recipe().outputs().get(i).copy());
        }
        override(slots.findSlotByName(MODEL_SLOT).orElseThrow(), model(selected));
    }

    private static void override(IRecipeSlotDrawable slot, ItemStack stack) {
        // createDisplayOverrides returns the SAME acceptor until it is explicitly cleared.
        slot.clearDisplayOverrides();
        slot.createDisplayOverrides().addItemStack(stack);
    }

    /** Fail closed: neither a missing model nor independently edited amounts may encode a group. */
    @Nullable
    static Entry selected(Entry entry, IRecipeSlotsView slots) {
        if (variants(entry).isEmpty()) return entry;
        ItemStack model = slots.findSlotByName(MODEL_SLOT).flatMap(IRecipeSlotView::getDisplayedItemStack)
                .orElse(ItemStack.EMPTY);
        Entry selected = selectedByModel(entry, model);
        if (selected == null || !displayMatches(slots, INPUT_SLOT, matrix(selected))) return null;
        for (int i = 0; i < selected.recipe().outputs().size(); i++) {
            if (!displayMatches(slots, OUTPUT_SLOT + i, selected.recipe().outputs().get(i))) return null;
        }
        return selected;
    }

    @Nullable
    private static Entry selectedByModel(Entry entry, ItemStack model) {
        if (model.isEmpty()) return null;
        Entry match = null;
        for (Entry variant : variants(entry)) {
            if (!variant.recipe().mold().test(model)) continue;
            if (match != null) return null;
            match = variant;
        }
        return match;
    }

    private static boolean displayMatches(IRecipeSlotsView slots, String name, ItemStack expected) {
        return slots.findSlotByName(name).flatMap(IRecipeSlotView::getDisplayedItemStack)
                .filter(stack -> stack.getCount() == expected.getCount()
                        && ItemStack.isSameItemSameTags(stack, expected)).isPresent();
    }

    static ItemStack matrix(Entry entry) {
        return entry.recipe().inputs().get(0).ingredient().getItems()[0]
                .copyWithCount(Math.toIntExact(entry.recipe().inputs().get(0).count()));
    }

    private static ItemStack model(Entry entry) { return entry.recipe().mold().getItems()[0].copy(); }

    static Component dataLabel(Info info) {
        if (info.minData() == info.maxData()) return Component.translatable("jei.useless_mod.hnn.data", info.minData());
        return Component.translatable("jei.useless_mod.hnn.range", info.minData(),
                info.maxData() == Integer.MAX_VALUE ? "∞" : info.maxData());
    }

    static void addDetails(ITooltipBuilder tooltip, Entry entry) {
        Info info = info(entry);
        if (info == null) return;
        tooltip.add(Component.translatable("jei.useless_mod.hnn.model", info.model()));
        tooltip.add(dataLabel(info));
        tooltip.add(Component.translatable("jei.useless_mod.hnn.batch", info.operations()));
        long predictions = entry.recipe().outputs().stream()
                .filter(stack -> PREDICTION.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                .mapToLong(ItemStack::getCount).sum();
        if (info.operations() > 0) {
            tooltip.add(Component.translatable("jei.useless_mod.hnn.accuracy",
                    String.format(java.util.Locale.ROOT, "%.4f", 100.0 * predictions / info.operations()),
                    predictions, info.operations()));
        }
        tooltip.add(Component.translatable("jei.useless_mod.hnn.expected_batch"));
        tooltip.add(Component.translatable("jei.useless_mod.tooltip.recipe_id", entry.recipe().id().toString()));
    }

    private static void addAmount(ITooltipBuilder tooltip, IRecipeSlotView slot) {
        slot.getDisplayedItemStack().ifPresent(stack -> tooltip.add(Component.translatable(
                "jei.useless_mod.tooltip.amount", stack.getCount())));
    }

    record Info(String model, int minData, int maxData, long operations) { }
}
