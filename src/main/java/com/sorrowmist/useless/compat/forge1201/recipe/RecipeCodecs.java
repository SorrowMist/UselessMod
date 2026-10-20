package com.sorrowmist.useless.compat.forge1201.recipe;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import java.util.function.Function;

/** Native Forge JSON/packet serializers retain custom ingredients, NBT and tag references. */
public final class RecipeCodecs {
    private RecipeCodecs() {}
    public static <T> Codec<T> jsonCodec(Function<JsonElement,T> read, Function<T,JsonElement> write) {
        return Codec.PASSTHROUGH.flatXmap(dynamic -> {
            try { return DataResult.success(read.apply(dynamic.convert(JsonOps.INSTANCE).getValue())); }
            catch (RuntimeException error) { return DataResult.error(() -> "Invalid recipe data: " + error.getMessage()); }
        }, value -> {
            try { return DataResult.success(new Dynamic<>(JsonOps.INSTANCE, write.apply(value))); }
            catch (RuntimeException error) { return DataResult.error(() -> "Cannot encode recipe data: " + error.getMessage()); }
        });
    }

    public static final Codec<Ingredient> INGREDIENT = jsonCodec(Ingredient::fromJson, Ingredient::toJson);
    // Native Ingredient.toNetwork flattens item tags into a snapshot. Our named payload retains
    // source tag references across client/server identities and delegates JSON predicates to Forge.
    public static final StreamCodec<FriendlyByteBuf,Ingredient> INGREDIENT_STREAM =
            StreamCodec.of((buf,value) -> buf.writeUtf(value.toJson().toString(),1_048_576),
                    buf -> Ingredient.fromJson(JsonParser.parseString(buf.readUtf(1_048_576))));
    public static final Codec<ItemStack> ITEM_STACK = jsonCodec(RecipeCodecs::readItem, RecipeCodecs::writeItem);
    public static final Codec<FluidStack> FLUID_STACK = jsonCodec(RecipeCodecs::readFluid, RecipeCodecs::writeFluid);

    private static JsonObject object(JsonElement json) {
        if (!json.isJsonObject()) throw new JsonSyntaxException("Expected a stack object");
        JsonObject result = json.getAsJsonObject();
        if (result.has("components") && result.getAsJsonObject("components").size()>0)
            throw new JsonSyntaxException("1.21 components cannot be silently discarded; use 1.20.1 nbt");
        return result;
    }
    private static int positiveCount(JsonObject json, String primary, String fallback) {
        JsonElement number = json.has(primary) ? json.get(primary) : json.get(fallback);
        if (number == null) return 1;
        try {
            int result = number.getAsBigDecimal().intValueExact();
            if (result <= 0) throw new ArithmeticException("not positive");
            return result;
        } catch (RuntimeException error) { throw new JsonSyntaxException("Invalid positive int " + primary, error); }
    }
    private static CompoundTag readNbt(JsonObject json) {
        JsonElement nbt = json.has("nbt") ? json.get("nbt") : json.has("tag") ? json.get("tag") : json.get("Tag");
        return nbt == null ? null : CraftingHelper.getNBT(nbt);
    }
    public static ItemStack readItem(JsonElement value) {
        JsonObject json = object(value);
        String field = json.has("item") ? "item" : "id";
        ResourceLocation id = new ResourceLocation(json.get(field).getAsString());
        if (!BuiltInRegistries.ITEM.containsKey(id) || id.equals(new ResourceLocation("minecraft:air")))
            throw new JsonSyntaxException("Unknown/non-craftable item: " + id);
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(id), positiveCount(json,"count","Count"));
        CompoundTag nbt = readNbt(json);
        if (nbt != null) result.setTag(nbt.copy());
        return result;
    }
    public static JsonElement writeItem(ItemStack stack) {
        if (stack.isEmpty()) throw new JsonSyntaxException("Empty recipe item stack");
        JsonObject json = new JsonObject();
        json.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count",stack.getCount());
        if (stack.hasTag()) json.addProperty("nbt",com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical.snbt(stack.getTag()));
        return json;
    }
    public static FluidStack readFluid(JsonElement value) {
        JsonObject json = object(value);
        String field = json.has("fluid") ? "fluid" : json.has("id") ? "id" : "FluidName";
        ResourceLocation id = new ResourceLocation(json.get(field).getAsString());
        if (!BuiltInRegistries.FLUID.containsKey(id) || BuiltInRegistries.FLUID.get(id) == Fluids.EMPTY)
            throw new JsonSyntaxException("Unknown/empty fluid: " + id);
        FluidStack result = new FluidStack(BuiltInRegistries.FLUID.get(id),positiveCount(json,"amount","Amount"));
        CompoundTag nbt = readNbt(json);
        if (nbt != null) result.setTag(nbt.copy());
        return result;
    }
    public static JsonElement writeFluid(FluidStack stack) {
        if (stack.isEmpty()) throw new JsonSyntaxException("Empty recipe fluid stack");
        JsonObject json = new JsonObject();
        json.addProperty("fluid",BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
        json.addProperty("amount",stack.getAmount());
        if (stack.hasTag()) json.addProperty("nbt",com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical.snbt(stack.getTag()));
        return json;
    }
}
