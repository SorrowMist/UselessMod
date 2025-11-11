package com.sorrowmist.useless.recipes.advancedalloyfurnace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.recipes.ModRecipeSerializers;
import com.sorrowmist.useless.recipes.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdvancedAlloyFurnaceRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final List<Ingredient> inputItems;
    private final List<Integer> inputItemCounts;
    private final FluidStack inputFluid;
    private final List<ItemStack> outputItems;
    private final FluidStack outputFluid;
    private final int energy;
    private final int processTime;

    // 新增字段：催化剂和模具
    private final Ingredient catalyst;
    private final int catalystCount;
    private final Ingredient mold;

    public AdvancedAlloyFurnaceRecipe(ResourceLocation id, List<Ingredient> inputItems,
                                      List<Integer> inputItemCounts, FluidStack inputFluid,
                                      List<ItemStack> outputItems, FluidStack outputFluid,
                                      int energy, int processTime) {
        this(id, inputItems, inputItemCounts, inputFluid, outputItems, outputFluid,
                energy, processTime, Ingredient.EMPTY, 0, Ingredient.EMPTY);
    }

    // 新增构造函数
    public AdvancedAlloyFurnaceRecipe(ResourceLocation id, List<Ingredient> inputItems,
                                      List<Integer> inputItemCounts, FluidStack inputFluid,
                                      List<ItemStack> outputItems, FluidStack outputFluid,
                                      int energy, int processTime,
                                      Ingredient catalyst, int catalystCount, Ingredient mold) {
        this.id = id;
        this.inputItems = inputItems;
        this.inputItemCounts = inputItemCounts;
        this.inputFluid = inputFluid;
        this.outputItems = outputItems;
        this.outputFluid = outputFluid;
        this.energy = energy;
        this.processTime = processTime;
        this.catalyst = catalyst;
        this.catalystCount = catalystCount;
        this.mold = mold;

        UselessMod.LOGGER.debug("Created recipe {} with catalyst: {}, mold: {}", id,
                catalyst.isEmpty() ? "none" : "present", mold.isEmpty() ? "none" : "present");
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }


    // 在 AdvancedAlloyFurnaceRecipe.java 中添加黑名单检查方法
    public boolean isCatalystAllowed() {
        // 检查输出物品是否在黑名单中
        for (ItemStack output : outputItems) {
            if (com.sorrowmist.useless.registry.BlacklistManager.isOutputBlacklisted(output)) {
                return false;
            }
        }
        return true;
    }

    // 修复匹配逻辑，添加详细调试
    public boolean matches(List<ItemStack> inputSlots, FluidStack inputTank) {
        UselessMod.LOGGER.debug("  Matching recipe: {}", id);

        // 检查流体
        if (!inputFluid.isEmpty()) {
            UselessMod.LOGGER.debug("    Recipe requires fluid: {} x {}", inputFluid.getFluid(), inputFluid.getAmount());
            if (inputTank.isEmpty() || inputTank.getAmount() < inputFluid.getAmount() ||
                    !inputTank.getFluid().isSame(inputFluid.getFluid())) {
                UselessMod.LOGGER.debug("    Fluid mismatch - Have: {} x {}, Need: {} x {}",
                        inputTank.getFluid(), inputTank.getAmount(), inputFluid.getFluid(), inputFluid.getAmount());
                return false;
            }
            UselessMod.LOGGER.debug("    Fluid matches");
        }

        // 创建可用物品的副本用于匹配计算
        List<ItemStack> availableItems = new ArrayList<>();
        for (ItemStack stack : inputSlots) {
            if (!stack.isEmpty()) {
                availableItems.add(stack.copy());
            }
        }

        UselessMod.LOGGER.debug("    Available items count: {}", availableItems.size());

        // 为每个配方输入创建需求映射
        Map<Ingredient, Integer> requiredItems = new HashMap<>();
        for (int i = 0; i < inputItems.size(); i++) {
            requiredItems.put(inputItems.get(i), inputItemCounts.get(i));
            UselessMod.LOGGER.debug("    Requires: {} x {}", inputItems.get(i), inputItemCounts.get(i));
        }

        // 尝试匹配所有要求的物品
        for (Map.Entry<Ingredient, Integer> entry : requiredItems.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int requiredCount = entry.getValue();
            int foundCount = 0;

            // 在所有可用物品中查找匹配
            for (ItemStack available : availableItems) {
                if (!available.isEmpty() && ingredient.test(available)) {
                    int takeAmount = Math.min(available.getCount(), requiredCount - foundCount);
                    foundCount += takeAmount;
                    available.shrink(takeAmount);
                    UselessMod.LOGGER.debug("    Found match: {} x {}", available.getItem(), takeAmount);

                    if (foundCount >= requiredCount) {
                        break;
                    }
                }
            }

            // 如果没有找到足够的物品，返回不匹配
            if (foundCount < requiredCount) {
                UselessMod.LOGGER.debug("    Ingredient insufficient - Needed: {}, Found: {}", requiredCount, foundCount);
                return false;
            }
            UselessMod.LOGGER.debug("    Ingredient satisfied - Needed: {}, Found: {}", requiredCount, foundCount);
        }

        UselessMod.LOGGER.debug("    All ingredients satisfied");
        return true;
    }

    // 修复匹配逻辑，使催化剂真正成为可选项
    public boolean matches(List<ItemStack> inputSlots, FluidStack inputTank,
                           ItemStack catalystSlot, ItemStack moldSlot) {
        UselessMod.LOGGER.debug("  Matching recipe with catalyst and mold: {}", id);

        // 检查模具匹配（如果配方需要模具）- 模具仍然是必须的
        if (requiresMold()) {
            UselessMod.LOGGER.debug("    Recipe requires mold");
            if (moldSlot.isEmpty() || !mold.test(moldSlot)) {
                UselessMod.LOGGER.debug("    Mold not present or not matching");
                return false;
            }
            UselessMod.LOGGER.debug("    Mold matches");
        }

        // 修改：催化剂现在是可选的，不检查催化剂状态
        // 即使配方需要催化剂但没有放入，也允许匹配（并行数为1）
        if (requiresCatalyst()) {
            UselessMod.LOGGER.debug("    Recipe requires catalyst (optional)");
            // 如果有催化剂且匹配，会使用催化剂的并行数
            // 如果没有催化剂，仍然允许配方运行（并行数为1）
        }

        // 然后检查输入物品和流体
        return matches(inputSlots, inputTank);
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return outputItems.isEmpty() ? ItemStack.EMPTY : outputItems.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return outputItems.isEmpty() ? ItemStack.EMPTY : outputItems.get(0).copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ADVANCED_ALLOY_FURNACE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(inputItems);
        return ingredients;
    }

    // 新增：检查配方是否需要催化剂
    public boolean requiresCatalyst() {
        return !catalyst.isEmpty() && catalystCount > 0;
    }

    // 新增：检查配方是否需要模具
    public boolean requiresMold() {
        return !mold.isEmpty();
    }

    // 新增：获取催化剂
    public Ingredient getCatalyst() {
        return catalyst;
    }

    // 新增：获取催化剂数量
    public int getCatalystCount() {
        return catalystCount;
    }

    // 新增：获取模具
    public Ingredient getMold() {
        return mold;
    }

    // 修改消耗逻辑，考虑催化剂和并行数
    public void consumeInputs(List<ItemStack> inputSlots, FluidStack inputTank, ItemStack catalystSlot, int parallel) {
        UselessMod.LOGGER.debug("Consuming inputs for recipe {} with parallel {}", id, parallel);

        // 消耗流体，乘以并行数
        if (!inputFluid.isEmpty()) {
            int fluidToConsume = inputFluid.getAmount() * parallel;
            int consumed = Math.min(inputTank.getAmount(), fluidToConsume);
            inputTank.shrink(consumed);
            UselessMod.LOGGER.debug("Consumed {} fluid ({} required)", consumed, fluidToConsume);
        }

        // 修改：只有当有催化剂且匹配时才消耗催化剂
        if (requiresCatalyst() && !catalystSlot.isEmpty() && catalyst.test(catalystSlot) && catalystSlot.getCount() >= catalystCount) {
            int consumed = Math.min(catalystSlot.getCount(), catalystCount);
            catalystSlot.shrink(consumed);
            UselessMod.LOGGER.debug("Consumed {} catalyst", consumed);
        } else if (requiresCatalyst()) {
            UselessMod.LOGGER.debug("No catalyst consumed (not present or not matching)");
        }

        // 为每个配方输入创建消耗计数器，乘以并行数
        int[] remainingToConsume = new int[inputItems.size()];
        for (int i = 0; i < inputItems.size(); i++) {
            remainingToConsume[i] = inputItemCounts.get(i) * parallel;
            UselessMod.LOGGER.debug("Need to consume {} of ingredient {}", remainingToConsume[i], inputItems.get(i));
        }

        // 消耗物品输入 - 遍历所有输入槽位
        for (int slotIndex = 0; slotIndex < inputSlots.size(); slotIndex++) {
            ItemStack slotStack = inputSlots.get(slotIndex);
            if (slotStack.isEmpty()) continue;

            // 对于当前槽位，检查所有需要消耗的原料
            for (int ingredientIndex = 0; ingredientIndex < inputItems.size(); ingredientIndex++) {
                if (remainingToConsume[ingredientIndex] <= 0) continue;

                Ingredient ingredient = inputItems.get(ingredientIndex);
                if (ingredient.test(slotStack)) {
                    int consumeAmount = Math.min(slotStack.getCount(), remainingToConsume[ingredientIndex]);
                    slotStack.shrink(consumeAmount);
                    remainingToConsume[ingredientIndex] -= consumeAmount;

                    UselessMod.LOGGER.debug("Consumed {} from slot {}, remaining: {}",
                            consumeAmount, slotIndex, remainingToConsume[ingredientIndex]);

                    // 如果槽位物品被消耗完，设置为空并跳出内层循环
                    if (slotStack.isEmpty()) {
                        inputSlots.set(slotIndex, ItemStack.EMPTY);
                        break;
                    }
                }
            }
        }

        // 检查是否全部消耗完
        for (int i = 0; i < remainingToConsume.length; i++) {
            if (remainingToConsume[i] > 0) {
                UselessMod.LOGGER.warn("Could not consume all required items for recipe {}: ingredient {} needed {} more",
                        getId(), i, remainingToConsume[i]);
            }
        }
    }

    // 原有的consumeInputs方法，用于向后兼容
    public void consumeInputs(List<ItemStack> inputSlots, FluidStack inputTank, ItemStack catalystSlot) {
        consumeInputs(inputSlots, inputTank, catalystSlot, 1);
    }

    public void consumeInputs(List<ItemStack> inputSlots, FluidStack inputTank) {
        consumeInputs(inputSlots, inputTank, ItemStack.EMPTY, 1);
    }

    // Getters
    public List<Ingredient> getInputItems() { return inputItems; }
    public List<Integer> getInputItemCounts() { return inputItemCounts; }
    public FluidStack getInputFluid() { return inputFluid; }
    public List<ItemStack> getOutputItems() { return outputItems; }
    public FluidStack getOutputFluid() { return outputFluid; }
    public int getEnergy() { return energy; }
    public int getProcessTime() { return processTime; }

    public static class Serializer implements RecipeSerializer<AdvancedAlloyFurnaceRecipe> {
        @Override
        public AdvancedAlloyFurnaceRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            UselessMod.LOGGER.debug("Parsing recipe: {}", recipeId);

            // 解析输入物品
            List<Ingredient> inputItems = new ArrayList<>();
            List<Integer> inputCounts = new ArrayList<>();

            if (json.has("ingredients")) {
                JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
                for (JsonElement element : ingredients) {
                    JsonObject ingredientObj = element.getAsJsonObject();

                    Ingredient ingredient;
                    int count;

                    // 检查是否存在嵌套的 ingredient 对象
                    if (ingredientObj.has("ingredient")) {
                        // 格式1: {"ingredient": {"item": "...", "count": N}}
                        JsonObject innerIngredient = ingredientObj.getAsJsonObject("ingredient");
                        ingredient = Ingredient.fromJson(innerIngredient);
                        count = GsonHelper.getAsInt(innerIngredient, "count", 1);
                    } else {
                        // 格式2: 直接是原料对象，count作为属性
                        ingredient = Ingredient.fromJson(ingredientObj);
                        count = GsonHelper.getAsInt(ingredientObj, "count", 1);
                    }

                    inputItems.add(ingredient);
                    inputCounts.add(count);
                    UselessMod.LOGGER.debug("Parsed ingredient: count={}, ingredient={}", count, ingredient);
                }
            }

            // 解析输入流体
            FluidStack inputFluid = FluidStack.EMPTY;
            if (json.has("input_fluid")) {
                JsonObject fluidObj = json.getAsJsonObject("input_fluid");
                ResourceLocation fluidId = ResourceLocation.parse(GsonHelper.getAsString(fluidObj, "fluid"));
                int amount = GsonHelper.getAsInt(fluidObj, "amount", 1000);
                inputFluid = new FluidStack(ForgeRegistries.FLUIDS.getValue(fluidId), amount);
                UselessMod.LOGGER.debug("Parsed input fluid: {} x {}", fluidId, amount);
            }

            // 解析输出物品
            List<ItemStack> outputItems = new ArrayList<>();
            if (json.has("results")) {
                JsonArray results = GsonHelper.getAsJsonArray(json, "results");
                for (JsonElement element : results) {
                    JsonObject resultObj = element.getAsJsonObject();
                    ResourceLocation itemId = ResourceLocation.parse(GsonHelper.getAsString(resultObj, "item"));
                    int count = GsonHelper.getAsInt(resultObj, "count", 1);
                    ItemStack output = new ItemStack(ForgeRegistries.ITEMS.getValue(itemId), count);
                    outputItems.add(output);
                    UselessMod.LOGGER.debug("Parsed output: {} x {}", itemId, count);
                }
            }

            // 解析输出流体
            FluidStack outputFluid = FluidStack.EMPTY;
            if (json.has("output_fluid")) {
                JsonObject fluidObj = json.getAsJsonObject("output_fluid");
                ResourceLocation fluidId = ResourceLocation.parse(GsonHelper.getAsString(fluidObj, "fluid"));
                int amount = GsonHelper.getAsInt(fluidObj, "amount", 1000);
                outputFluid = new FluidStack(ForgeRegistries.FLUIDS.getValue(fluidId), amount);
            }

            // 解析催化剂
            Ingredient catalyst = Ingredient.EMPTY;
            int catalystCount = 0;
            if (json.has("catalyst")) {
                JsonElement catalystElement = json.get("catalyst");
                if (catalystElement.isJsonObject()) {
                    JsonObject catalystObj = catalystElement.getAsJsonObject();
                    catalyst = Ingredient.fromJson(catalystObj);
                    catalystCount = GsonHelper.getAsInt(catalystObj, "count", 1);
                    UselessMod.LOGGER.debug("Parsed catalyst: count={}", catalystCount);
                } else if (catalystElement.isJsonPrimitive()) {
                    // 简写格式：直接是物品ID
                    String itemId = catalystElement.getAsString();
                    catalyst = Ingredient.of(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId)));
                    catalystCount = 1;
                    UselessMod.LOGGER.debug("Parsed catalyst (simple): {}", itemId);
                }
            }

            // 解析模具
            Ingredient mold = Ingredient.EMPTY;
            if (json.has("mold")) {
                JsonElement moldElement = json.get("mold");
                if (moldElement.isJsonPrimitive()) {
                    String itemId = moldElement.getAsString();
                    mold = Ingredient.of(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId)));
                    UselessMod.LOGGER.debug("Parsed mold: {}", itemId);
                } else if (moldElement.isJsonObject()) {
                    JsonObject moldObj = moldElement.getAsJsonObject();
                    mold = Ingredient.fromJson(moldObj);
                    UselessMod.LOGGER.debug("Parsed mold (object)");
                }
            }

            int energy = GsonHelper.getAsInt(json, "energy", 2000);
            int processTime = GsonHelper.getAsInt(json, "process_time", 200);

            UselessMod.LOGGER.debug("Recipe {}: inputs={}, catalyst={}, mold={}, outputs={}, energy={}, processTime={}",
                    recipeId, inputItems.size(), catalyst.isEmpty() ? "none" : "present",
                    mold.isEmpty() ? "none" : "present", outputItems.size(), energy, processTime);

            return new AdvancedAlloyFurnaceRecipe(recipeId, inputItems, inputCounts, inputFluid,
                    outputItems, outputFluid, energy, processTime, catalyst, catalystCount, mold);
        }

        @Nullable
        @Override
        public AdvancedAlloyFurnaceRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int inputCount = buffer.readVarInt();
            List<Ingredient> inputItems = new ArrayList<>();
            List<Integer> inputCounts = new ArrayList<>();

            for (int i = 0; i < inputCount; i++) {
                inputItems.add(Ingredient.fromNetwork(buffer));
                inputCounts.add(buffer.readVarInt());
            }

            FluidStack inputFluid = buffer.readFluidStack();

            int outputCount = buffer.readVarInt();
            List<ItemStack> outputItems = new ArrayList<>();
            for (int i = 0; i < outputCount; i++) {
                outputItems.add(buffer.readItem());
            }

            FluidStack outputFluid = buffer.readFluidStack();

            // 读取催化剂和模具
            Ingredient catalyst = Ingredient.fromNetwork(buffer);
            int catalystCount = buffer.readVarInt();
            Ingredient mold = Ingredient.fromNetwork(buffer);

            int energy = buffer.readVarInt();
            int processTime = buffer.readVarInt();

            return new AdvancedAlloyFurnaceRecipe(recipeId, inputItems, inputCounts, inputFluid,
                    outputItems, outputFluid, energy, processTime, catalyst, catalystCount, mold);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AdvancedAlloyFurnaceRecipe recipe) {
            buffer.writeVarInt(recipe.inputItems.size());
            for (int i = 0; i < recipe.inputItems.size(); i++) {
                recipe.inputItems.get(i).toNetwork(buffer);
                buffer.writeVarInt(recipe.inputItemCounts.get(i));
            }

            buffer.writeFluidStack(recipe.inputFluid);
            buffer.writeVarInt(recipe.outputItems.size());
            for (ItemStack stack : recipe.outputItems) {
                buffer.writeItem(stack);
            }

            buffer.writeFluidStack(recipe.outputFluid);

            // 写入催化剂和模具
            recipe.catalyst.toNetwork(buffer);
            buffer.writeVarInt(recipe.catalystCount);
            recipe.mold.toNetwork(buffer);

            buffer.writeVarInt(recipe.energy);
            buffer.writeVarInt(recipe.processTime);
        }
    }
}