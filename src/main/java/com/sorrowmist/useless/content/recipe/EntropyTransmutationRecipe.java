package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A disk-local, event-driven ME chest transformation, not a crafting-grid recipe. */
public record EntropyTransmutationRecipe(ResourceLocation id, List<CountedIngredient> inputs,
                                         ItemStack output) implements Recipe<Container> {
    public static final ResourceLocation TYPE_ID = new ResourceLocation("useless_mod", "entropy_transmutation");
    public static final RecipeType<EntropyTransmutationRecipe> TYPE = new RecipeType<>() {
        @Override public String toString() { return TYPE_ID.toString(); }
    };
    public static final int MAX_INPUTS = 256;
    public static final MapCodec<EntropyTransmutationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(EntropyTransmutationRecipe::id),
                    CountedIngredient.CODEC.codec().listOf().fieldOf("inputs")
                            .forGetter(EntropyTransmutationRecipe::inputs),
                    RecipeCodecs.ITEM_STACK.fieldOf("output").forGetter(EntropyTransmutationRecipe::output)
            ).apply(instance, EntropyTransmutationRecipe::new));

    /** Use Forge's native ingredient wire format, including custom ingredient serializers. */
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                buf.writeResourceLocation(recipe.id());
                buf.writeVarInt(recipe.inputs().size());
                for (CountedIngredient input : recipe.inputs()) {
                    input.ingredient().toNetwork(buf);
                    buf.writeVarLong(input.count());
                    buf.writeBoolean(input.consumed());
                }
                buf.writeItemStack(recipe.output(), false);
            }, buf -> {
                ResourceLocation id = buf.readResourceLocation();
                int size = buf.readVarInt();
                if (size < 1 || size > MAX_INPUTS) throw new IllegalArgumentException("Invalid entropy input count: " + size);
                List<CountedIngredient> inputs = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    inputs.add(new CountedIngredient(Ingredient.fromNetwork(buf), buf.readVarLong(), buf.readBoolean()));
                }
                return new EntropyTransmutationRecipe(id, inputs, buf.readItem());
            });

    public EntropyTransmutationRecipe {
        Objects.requireNonNull(id, "recipe id");
        if (inputs == null || inputs.isEmpty() || inputs.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("Entropy recipes need 1.." + MAX_INPUTS + " inputs");
        }
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient() == Ingredient.EMPTY || input.count() <= 0) {
                throw new IllegalArgumentException("Entropy inputs must have an ingredient and a positive long count");
            }
        }
        if (output == null || output.isEmpty() || !(output.getItem() instanceof BlockItem)
                || output.getCount() != 1 || output.hasTag()) {
            // A block state cannot represent a stack count or arbitrary item/BE NBT.
            throw new IllegalArgumentException("Entropy output must be one untagged block item");
        }
        inputs = List.copyOf(inputs);
        output = output.copy();
    }

    @Override public ItemStack output() { return output.copy(); }
    @Override public boolean matches(Container input, net.minecraft.world.level.Level level) { return false; }
    @Override public ItemStack assemble(Container input, RegistryAccess registries) { return output(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return output(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeType<?> getType() { return TYPE; }
    @Override public RecipeSerializer<?> getSerializer() { return EntropyTransmutationRecipeSerializer.INSTANCE; }
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        inputs.forEach(input -> ingredients.add(input.ingredient()));
        return ingredients;
    }
}
