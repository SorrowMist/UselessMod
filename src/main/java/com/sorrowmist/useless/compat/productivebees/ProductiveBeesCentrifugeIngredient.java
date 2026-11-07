package com.sorrowmist.useless.compat.productivebees;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.compat.forge1201.recipe.CodecIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.CodecIngredientSerializer;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Arrays;
import java.util.List;

/**
 * The 12.6.0 centrifuge requires BOTH Ingredient.test and canItemStacksStack with a native
 * ingredient member. StrictNBTIngredient alone compares share tags, not full tags/capabilities.
 * Keep that extra native predicate in JSON and packets as well as in the server-side recipe.
 */
public final class ProductiveBeesCentrifugeIngredient
        extends CodecIngredient<ProductiveBeesCentrifugeIngredient> {
    public static final ResourceLocation ID = new ResourceLocation("useless_mod", "productivebees_centrifuge_input");
    private static final int MAX_MEMBERS = 4096;
    // ItemStack.save/of also retain ForgeCaps; the common recipe display-stack codec does not.
    private static final Codec<ItemStack> MEMBER_CODEC = Codec.STRING.comapFlatMap(
            ProductiveBeesCentrifugeIngredient::readMember,
            stack -> stack.save(new CompoundTag()).toString());
    public static final MapCodec<ProductiveBeesCentrifugeIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RecipeCodecs.INGREDIENT.fieldOf("predicate").forGetter(value -> value.predicate),
                    MEMBER_CODEC.listOf().fieldOf("stackable_with").forGetter(value -> value.members)
            ).apply(instance, ProductiveBeesCentrifugeIngredient::new));
    public static final CodecIngredientSerializer<ProductiveBeesCentrifugeIngredient> TYPE =
            new CodecIngredientSerializer<>(CODEC.codec());

    private final Ingredient predicate;
    private final List<ItemStack> members;

    private ProductiveBeesCentrifugeIngredient(Ingredient predicate, List<ItemStack> members) {
        if (!isStaticPredicate(predicate) || members == null || members.isEmpty() || members.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("Unsupported centrifuge ingredient predicate/member list");
        }
        this.predicate = predicate;
        this.members = members.stream().map(stack -> {
            if (stack == null || stack.isEmpty()) {
                throw new IllegalArgumentException("Empty centrifuge ingredient member");
            }
            return stack.copyWithCount(1);
        }).toList();
    }

    /** Register on both logical sides during common setup, before converted recipes are loaded. */
    public static synchronized void register() {
        if (CraftingHelper.getID(TYPE) == null) {
            CraftingHelper.register(ID, TYPE);
        }
    }

    public static boolean isRegistered() {
        return ID.equals(CraftingHelper.getID(TYPE));
    }

    /** Unknown/count-sensitive predicates cannot be batched without their native inventory. */
    public static boolean isStaticPredicate(Ingredient ingredient) {
        return ingredient != null && (ingredient.getClass() == Ingredient.class
                || ingredient.getClass() == StrictNBTIngredient.class
                || ingredient.getClass() == PartialNBTIngredient.class);
    }

    public static ProductiveBeesCentrifugeIngredient of(Ingredient predicate) {
        if (!isStaticPredicate(predicate) || predicate.isEmpty()) {
            throw new IllegalArgumentException("Unknown, count-sensitive or empty centrifuge ingredient");
        }
        return new ProductiveBeesCentrifugeIngredient(predicate, Arrays.asList(predicate.getItems()));
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !predicate.test(stack)) return false;
        for (ItemStack member : members) {
            if (ItemHandlerHelper.canItemStacksStack(stack, member)) return true;
        }
        return false;
    }

    @Override
    public ItemStack[] getItems() {
        return members.stream().filter(predicate::test).map(ItemStack::copy).toArray(ItemStack[]::new);
    }

    @Override
    public CodecIngredientSerializer<ProductiveBeesCentrifugeIngredient> getSerializer() {
        return TYPE;
    }

    private static DataResult<ItemStack> readMember(String value) {
        try {
            ItemStack stack = ItemStack.of(TagParser.parseTag(value));
            return stack.isEmpty() ? DataResult.error(() -> "Empty centrifuge member") : DataResult.success(stack);
        } catch (Exception exception) {
            return DataResult.error(() -> "Invalid centrifuge member: " + exception.getMessage());
        }
    }
}
