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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AdvancedAlloyFurnaceRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final List<Ingredient> inputItems;
    private final List<Long> inputItemCounts;
    private final List<FluidStack> inputFluids;
    private final List<ItemStack> outputItems;
    private final List<FluidStack> outputFluids;
    private final int energy;
    private final int processTime;

    // 新增字段：催化剂和模具
    private final Ingredient catalyst;
    private final int catalystCount;
    private final Ingredient mold;
    
    // 新增字段：标记是否为有机灌注机配方
    private final boolean isInsolatorRecipe;
    
    // 新增字段：标记是否为冲压机配方
    private final boolean isPressRecipe;

    public AdvancedAlloyFurnaceRecipe(ResourceLocation id, List<Ingredient> inputItems,
                                      List<Long> inputItemCounts, List<FluidStack> inputFluids,
                                      List<ItemStack> outputItems, List<FluidStack> outputFluids,
                                      int energy, int processTime) {
        this(id, inputItems, inputItemCounts, inputFluids, outputItems, outputFluids,
                energy, processTime, Ingredient.EMPTY, 0, Ingredient.EMPTY, false, false);
    }

    // 新增构造函数
    public AdvancedAlloyFurnaceRecipe(ResourceLocation id, List<Ingredient> inputItems, List<Long> inputItemCounts, 
                                      List<FluidStack> inputFluids, List<ItemStack> outputItems, List<FluidStack> outputFluids,
                                      int energy, int processTime, Ingredient catalyst, int catalystCount, Ingredient mold) {
        this(id, inputItems, inputItemCounts, inputFluids, outputItems, outputFluids,
                energy, processTime, catalyst, catalystCount, mold, false, false);
    }
    
    // 新增构造函数，支持有机灌注机配方标记
    public AdvancedAlloyFurnaceRecipe(ResourceLocation id, List<Ingredient> inputItems, List<Long> inputItemCounts, 
                                      List<FluidStack> inputFluids, List<ItemStack> outputItems, List<FluidStack> outputFluids,
                                      int energy, int processTime, Ingredient catalyst, int catalystCount, Ingredient mold, boolean isInsolatorRecipe) {
        this(id, inputItems, inputItemCounts, inputFluids, outputItems, outputFluids,
                energy, processTime, catalyst, catalystCount, mold, isInsolatorRecipe, false);
    }
    
    // 新增构造函数，支持冲压机配方标记
    public AdvancedAlloyFurnaceRecipe(ResourceLocation id, List<Ingredient> inputItems, List<Long> inputItemCounts, 
                                      List<FluidStack> inputFluids, List<ItemStack> outputItems, List<FluidStack> outputFluids,
                                      int energy, int processTime, Ingredient catalyst, int catalystCount, Ingredient mold, boolean isInsolatorRecipe, boolean isPressRecipe) {
        this.id = id;
        this.inputItems = inputItems;
        this.inputItemCounts = inputItemCounts;
        this.inputFluids = inputFluids;
        this.outputItems = outputItems;
        this.outputFluids = outputFluids;
        this.energy = energy;
        this.processTime = processTime;
        this.catalyst = catalyst;
        this.catalystCount = catalystCount;
        this.mold = mold;
        this.isInsolatorRecipe = isInsolatorRecipe;
        this.isPressRecipe = isPressRecipe;
    }
    
    // 新增：检查是否为有机灌注机配方
    public boolean isInsolatorRecipe() {
        return isInsolatorRecipe;
    }
    
    // 新增：检查是否为冲压机配方
    public boolean isPressRecipe() {
        return isPressRecipe;
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

    // 修复匹配逻辑 - 支持多个流体槽
    public boolean matches(List<ItemStack> inputSlots, List<FluidStack> inputFluidsList) {

        
        // 检查流体
        if (!inputFluids.isEmpty()) {
            if (inputFluidsList.isEmpty()) {
                return false;
            }
            
            // 对于多种流体输入的配方，需要检查所有配方流体是否都能在输入流体槽中找到匹配
            // 创建一个副本用于跟踪已匹配的配方流体
            List<FluidStack> remainingRequiredFluids = new ArrayList<>(inputFluids);
            
            // 遍历所有输入流体槽
            for (FluidStack inputTank : inputFluidsList) {
                if (inputTank.isEmpty()) {
                    continue;
                }
                
                // 尝试匹配剩余的配方流体
                for (Iterator<FluidStack> iterator = remainingRequiredFluids.iterator(); iterator.hasNext(); ) {
                    FluidStack requiredFluid = iterator.next();
                    if (inputTank.getAmount() >= requiredFluid.getAmount() && inputTank.getFluid().isSame(requiredFluid.getFluid())) {
                        // 匹配成功，从剩余列表中移除
                        iterator.remove();
                        break;
                    }
                }
                
                // 如果所有配方流体都已匹配，提前结束
                if (remainingRequiredFluids.isEmpty()) {
                    break;
                }
            }
            
            // 如果还有未匹配的配方流体，返回不匹配
            if (!remainingRequiredFluids.isEmpty()) {
                return false;
            }
        }

        // 创建可用物品的副本用于匹配计算
        List<ItemStack> availableItems = new ArrayList<>();
        for (int i = 0; i < inputSlots.size(); i++) {
            ItemStack stack = inputSlots.get(i);
            if (!stack.isEmpty()) {
                availableItems.add(stack.copy());
            }
        }

        // 直接遍历输入物品列表，不使用HashMap，避免重复Ingredient被覆盖
        for (int i = 0; i < inputItems.size(); i++) {
            Ingredient ingredient = inputItems.get(i);
            long requiredCount = inputItemCounts.get(i);
            long foundCount = 0;

            // 在所有可用物品中查找匹配
            for (ItemStack available : availableItems) {
                if (!available.isEmpty() && ingredient.test(available)) {
                    int takeAmount = (int) Math.min(available.getCount(), requiredCount - foundCount);
                    foundCount += takeAmount;
                    available.shrink(takeAmount);

                    if (foundCount >= requiredCount) {
                        break;
                    }
                }
            }

            // 如果没有找到足够的物品，返回不匹配
            if (foundCount < requiredCount) {
                return false;
            }
        }
        return true;
    }
    
    // 兼容旧版本的matches方法，只检查第一个流体槽
    public boolean matches(List<ItemStack> inputSlots, FluidStack inputTank) {
        List<FluidStack> inputFluidsList = new ArrayList<>();
        if (inputTank != null && !inputTank.isEmpty()) {
            inputFluidsList.add(inputTank);
        }
        return matches(inputSlots, inputFluidsList);
    }

    // 修复匹配逻辑，使催化剂真正成为可选项
    public boolean matches(List<ItemStack> inputSlots, FluidStack inputTank,
                           ItemStack catalystSlot, ItemStack moldSlot) {
        // 检查模具匹配（如果配方需要模具）- 模具仍然是必须的
        if (requiresMold()) {
            if (moldSlot.isEmpty()) {
                return false;
            }
            if (!mold.test(moldSlot)) {
                return false;
            }
        }

        // 修改：催化剂现在是可选的，不检查催化剂状态
        // 即使配方需要催化剂但没有放入，也允许匹配（并行数为1）

        // 然后检查输入物品和流体
        List<FluidStack> inputFluidsList = new ArrayList<>();
        if (inputTank != null && !inputTank.isEmpty()) {
            inputFluidsList.add(inputTank);
        }
        boolean itemsAndFluidMatch = matches(inputSlots, inputFluidsList);
        return itemsAndFluidMatch;
    }
    
    // 支持多个流体槽的matches方法 - 完整版本
    public boolean matches(List<ItemStack> inputSlots, List<FluidStack> inputFluidsList,
                           ItemStack catalystSlot, ItemStack moldSlot) {
        // 检查模具匹配（如果配方需要模具）- 模具仍然是必须的
        if (requiresMold()) {
            if (moldSlot.isEmpty()) {
                return false;
            }
            if (!mold.test(moldSlot)) {
                return false;
            }
        }

        // 修改：催化剂现在是可选的，不检查催化剂状态
        // 即使配方需要催化剂但没有放入，也允许匹配（并行数为1）

        // 然后检查输入物品和流体
        boolean itemsAndFluidMatch = matches(inputSlots, inputFluidsList);
        return itemsAndFluidMatch;
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

    // 修改消耗逻辑，考虑催化剂和并行数（支持单个流体槽）
    public void consumeInputs(List<ItemStack> inputSlots, FluidStack inputTank, ItemStack catalystSlot, int parallel) {
        // 将单个流体槽转换为列表，调用支持多种流体的消耗方法
        List<FluidStack> inputFluidsList = new ArrayList<>();
        if (inputTank != null && !inputTank.isEmpty()) {
            inputFluidsList.add(inputTank);
        }
        consumeInputs(inputSlots, inputFluidsList, catalystSlot, parallel);
    }
    
    // 添加支持多种流体输入的消耗方法
    public void consumeInputs(List<ItemStack> inputSlots, List<FluidStack> inputFluidsList, ItemStack catalystSlot, int parallel) {
        // 消耗流体，乘以并行数
        if (!inputFluids.isEmpty()) {
            // 遍历每个需要的流体
            for (FluidStack requiredFluid : inputFluids) {
                int fluidToConsume = requiredFluid.getAmount() * parallel;
                
                // 遍历所有输入流体槽，找到匹配的流体并消耗
                for (FluidStack inputTank : inputFluidsList) {
                    if (!inputTank.isEmpty() && inputTank.getFluid().isSame(requiredFluid.getFluid())) {
                        int consumed = Math.min(inputTank.getAmount(), fluidToConsume);
                        inputTank.shrink(consumed);
                        fluidToConsume -= consumed;
                        
                        // 如果已经消耗了足够的流体，跳出循环
                        if (fluidToConsume <= 0) {
                            break;
                        }
                    }
                }
            }
        }

        // 修改：如果催化剂槽有物品且是有效的催化剂，就消耗催化剂，无论配方是否明确要求
        // 但有用的锭（USEFUL_INGOT）作为终极催化剂不会被消耗
        String catalystId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(catalystSlot.getItem()).toString();
        if (!catalystSlot.isEmpty() && com.sorrowmist.useless.registry.CatalystManager.getCatalystParallel(catalystSlot) > 1 &&
            !catalystId.equals("useless_mod:useful_ingot")) {
            // 消耗一个催化剂
            catalystSlot.shrink(1);
        }
        
        // 特殊处理：有机灌注机配方不消耗输入物品
        if (isInsolatorRecipe()) {
            return;
        }

        // 为每个配方输入创建消耗计数器，乘以并行数
        long[] remainingToConsume = new long[inputItems.size()];
        for (int i = 0; i < inputItems.size(); i++) {
            // 对于冲压机配方，忽略底部输入栏对应的物品（索引为1），只消耗顶部输入栏（索引为0）
            if (isPressRecipe() && i == 1) {
                remainingToConsume[i] = 0; // 不消耗底部输入栏物品
            } else {
                remainingToConsume[i] = inputItemCounts.get(i) * parallel;
            }
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
                    int consumeAmount = (int) Math.min(slotStack.getCount(), remainingToConsume[ingredientIndex]);
                    slotStack.shrink(consumeAmount);
                    remainingToConsume[ingredientIndex] -= consumeAmount;

                    // 如果槽位物品被消耗完，设置为空并跳出内层循环
                    if (slotStack.isEmpty()) {
                        inputSlots.set(slotIndex, ItemStack.EMPTY);
                        break;
                    }
                }
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
    public List<Long> getInputItemCounts() { return inputItemCounts; }
    public List<FluidStack> getInputFluids() { return inputFluids; }
    public List<ItemStack> getOutputItems() { return outputItems; }
    public List<FluidStack> getOutputFluids() { return outputFluids; }
    public int getEnergy() { return energy; }
    public int getProcessTime() { return processTime; }

    public static class Serializer implements RecipeSerializer<AdvancedAlloyFurnaceRecipe> {
        @Override
        public AdvancedAlloyFurnaceRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            // 解析输入物品
            List<Ingredient> inputItems = new ArrayList<>();
            List<Long> inputCounts = new ArrayList<>();

            // 在 Serializer.fromJson 方法中修改这部分
            if (json.has("ingredients")) {
                JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
                for (JsonElement element : ingredients) {
                    JsonObject ingredientObj = element.getAsJsonObject();

                    Ingredient ingredient;
                    long count;

                    // 检查是否存在嵌套的 ingredient 对象
                    if (ingredientObj.has("ingredient")) {
                        JsonElement innerElement = ingredientObj.get("ingredient");

                        // 支持数组格式的 ingredient
                        if (innerElement.isJsonArray()) {
                            // 数组格式: {"ingredient": [{"tag": "a"}, {"tag": "b"}], "count": N}
                            ingredient = Ingredient.fromJson(innerElement);
                        } else {
                            // 对象格式: {"ingredient": {"tag": "a"}, "count": N}
                            ingredient = Ingredient.fromJson(innerElement.getAsJsonObject());
                        }
                        count = GsonHelper.getAsLong(ingredientObj, "count", 1L);
                    } else {
                        // 格式2: 直接是原料对象，count作为属性
                        ingredient = Ingredient.fromJson(ingredientObj);
                        count = GsonHelper.getAsLong(ingredientObj, "count", 1L);
                    }

                    inputItems.add(ingredient);
                    inputCounts.add(count);
                }
            }

            // 解析输入流体
            List<FluidStack> inputFluids = new ArrayList<>();
            if (json.has("input_fluids")) {
                JsonArray inputFluidsArray = GsonHelper.getAsJsonArray(json, "input_fluids");
                for (JsonElement element : inputFluidsArray) {
                    JsonObject fluidObj = element.getAsJsonObject();
                    ResourceLocation fluidId = ResourceLocation.parse(GsonHelper.getAsString(fluidObj, "fluid"));
                    int amount = GsonHelper.getAsInt(fluidObj, "amount", 1000);
                    inputFluids.add(new FluidStack(ForgeRegistries.FLUIDS.getValue(fluidId), amount));
                }
            } else if (json.has("input_fluid")) {
                // 向后兼容：支持单个输入流体
                JsonObject fluidObj = json.getAsJsonObject("input_fluid");
                ResourceLocation fluidId = ResourceLocation.parse(GsonHelper.getAsString(fluidObj, "fluid"));
                int amount = GsonHelper.getAsInt(fluidObj, "amount", 1000);
                inputFluids.add(new FluidStack(ForgeRegistries.FLUIDS.getValue(fluidId), amount));
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
                }
            }

            // 解析输出流体
            List<FluidStack> outputFluids = new ArrayList<>();
            if (json.has("output_fluids")) {
                JsonArray outputFluidsArray = GsonHelper.getAsJsonArray(json, "output_fluids");
                for (JsonElement element : outputFluidsArray) {
                    JsonObject fluidObj = element.getAsJsonObject();
                    ResourceLocation fluidId = ResourceLocation.parse(GsonHelper.getAsString(fluidObj, "fluid"));
                    int amount = GsonHelper.getAsInt(fluidObj, "amount", 1000);
                    outputFluids.add(new FluidStack(ForgeRegistries.FLUIDS.getValue(fluidId), amount));
                }
            } else if (json.has("output_fluid")) {
                // 向后兼容：支持单个输出流体
                JsonObject fluidObj = json.getAsJsonObject("output_fluid");
                ResourceLocation fluidId = ResourceLocation.parse(GsonHelper.getAsString(fluidObj, "fluid"));
                int amount = GsonHelper.getAsInt(fluidObj, "amount", 1000);
                outputFluids.add(new FluidStack(ForgeRegistries.FLUIDS.getValue(fluidId), amount));
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
                } else if (catalystElement.isJsonPrimitive()) {
                    // 简写格式：直接是物品ID
                    String itemId = catalystElement.getAsString();
                    catalyst = Ingredient.of(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId)));
                    catalystCount = 1;
                }
            }

            // 解析模具
            Ingredient mold = Ingredient.EMPTY;
            if (json.has("mold")) {
                JsonElement moldElement = json.get("mold");
                if (moldElement.isJsonPrimitive()) {
                    String itemId = moldElement.getAsString();
                    mold = Ingredient.of(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId)));
                } else if (moldElement.isJsonObject()) {
                    JsonObject moldObj = moldElement.getAsJsonObject();
                    mold = Ingredient.fromJson(moldObj);
                }
            }

            int energy = GsonHelper.getAsInt(json, "energy", 2000);
            int processTime = GsonHelper.getAsInt(json, "process_time", 200);

            return new AdvancedAlloyFurnaceRecipe(recipeId, inputItems, inputCounts, inputFluids,
                    outputItems, outputFluids, energy, processTime, catalyst, catalystCount, mold);
        }

        @Nullable
        @Override
        public AdvancedAlloyFurnaceRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int inputCount = buffer.readVarInt();
            List<Ingredient> inputItems = new ArrayList<>();
            List<Long> inputCounts = new ArrayList<>();

            for (int i = 0; i < inputCount; i++) {
                inputItems.add(Ingredient.fromNetwork(buffer));
                inputCounts.add(buffer.readVarLong());
            }

            int inputFluidCount = buffer.readVarInt();
            List<FluidStack> inputFluids = new ArrayList<>();
            for (int i = 0; i < inputFluidCount; i++) {
                inputFluids.add(buffer.readFluidStack());
            }

            int outputCount = buffer.readVarInt();
            List<ItemStack> outputItems = new ArrayList<>();
            for (int i = 0; i < outputCount; i++) {
                outputItems.add(buffer.readItem());
            }

            int outputFluidCount = buffer.readVarInt();
            List<FluidStack> outputFluids = new ArrayList<>();
            for (int i = 0; i < outputFluidCount; i++) {
                outputFluids.add(buffer.readFluidStack());
            }

            // 读取催化剂和模具
            Ingredient catalyst = Ingredient.fromNetwork(buffer);
            int catalystCount = buffer.readVarInt();
            Ingredient mold = Ingredient.fromNetwork(buffer);

            int energy = buffer.readVarInt();
            int processTime = buffer.readVarInt();

            return new AdvancedAlloyFurnaceRecipe(recipeId, inputItems, inputCounts, inputFluids,
                    outputItems, outputFluids, energy, processTime, catalyst, catalystCount, mold);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AdvancedAlloyFurnaceRecipe recipe) {
            buffer.writeVarInt(recipe.inputItems.size());
            for (int i = 0; i < recipe.inputItems.size(); i++) {
                recipe.inputItems.get(i).toNetwork(buffer);
                buffer.writeVarLong(recipe.inputItemCounts.get(i));
            }

            buffer.writeVarInt(recipe.inputFluids.size());
            for (FluidStack fluid : recipe.inputFluids) {
                buffer.writeFluidStack(fluid);
            }

            buffer.writeVarInt(recipe.outputItems.size());
            for (ItemStack stack : recipe.outputItems) {
                buffer.writeItem(stack);
            }

            buffer.writeVarInt(recipe.outputFluids.size());
            for (FluidStack fluid : recipe.outputFluids) {
                buffer.writeFluidStack(fluid);
            }

            // 写入催化剂和模具
            recipe.catalyst.toNetwork(buffer);
            buffer.writeVarInt(recipe.catalystCount);
            recipe.mold.toNetwork(buffer);

            buffer.writeVarInt(recipe.energy);
            buffer.writeVarInt(recipe.processTime);
        }
    }
}