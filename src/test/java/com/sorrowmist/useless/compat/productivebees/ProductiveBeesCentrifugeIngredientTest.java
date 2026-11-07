package com.sorrowmist.useless.compat.productivebees;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemHandlerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ProductiveBeesCentrifugeIngredientTest {
    private static Item hiddenTagItem;
    private static Item capabilityItem;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        ProductiveBeesCentrifugeIngredient.register();
        hiddenTagItem = InventoryTestBootstrap.registerItem("pb_hidden_share_tag", () -> new Item(new Item.Properties()) {
            @Override public CompoundTag getShareTag(ItemStack stack) {
                CompoundTag tag = stack.getTag() == null ? null : stack.getTag().copy();
                if (tag != null) tag.remove("private_data");
                return tag;
            }
        });
        capabilityItem = InventoryTestBootstrap.registerItem("pb_native_capability", () -> new Item(new Item.Properties()) {
            @Override public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
                return new FixtureCaps();
            }
        });
    }

    @Test
    void registrationIsIdempotentAndThePredicateIsNeverMarkedSimple() {
        ProductiveBeesCentrifugeIngredient.register();
        assertEquals(ProductiveBeesCentrifugeIngredient.ID, CraftingHelper.getID(ProductiveBeesCentrifugeIngredient.TYPE));
        var input = ProductiveBeesCentrifugeIngredient.of(Ingredient.of(Items.HONEYCOMB));
        assertFalse(input.isSimple());
        assertFalse(input.test(null));
        assertFalse(input.test(ItemStack.EMPTY));
    }

    @Test
    void fullNbtStackabilityIsStricterThanForgeStrictNbtShareTags() {
        ItemStack required = tagged(hiddenTagItem, "required");
        required.getOrCreateTag().putString("private_data", "native-private");
        ItemStack wrong = required.copy();
        wrong.getOrCreateTag().putString("private_data", "different-private");
        Ingredient shareTagsOnly = StrictNBTIngredient.of(required);
        assertTrue(shareTagsOnly.test(wrong), "the fixture intentionally hides this NBT from share tags");
        assertFalse(ItemHandlerHelper.canItemStacksStack(wrong, required));
        var converted = ProductiveBeesCentrifugeIngredient.of(shareTagsOnly);
        assertTrue(converted.test(required));
        assertFalse(converted.test(wrong));
        assertFalse(converted.test(tagged(hiddenTagItem, "different")));
    }

    @Test
    void jsonNativeNetworkAndFurnaceNetworkRetainVanillaMembersWhoseJsonDropsNbt() {
        ItemStack required = tagged(Items.HONEYCOMB, "snapshot");
        Ingredient vanilla = Ingredient.of(required);
        var original = ProductiveBeesCentrifugeIngredient.of(vanilla);
        assertFalse(vanilla.toJson().toString().contains("snapshot"), "vanilla JSON alone would lose this native member's NBT");
        var json = ProductiveBeesCentrifugeIngredient.TYPE.parse(original.toJson().getAsJsonObject());
        FriendlyByteBuf nativePacket = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf furnacePacket = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.toNetwork(nativePacket);
            Ingredient nativeRoundTrip = Ingredient.fromNetwork(nativePacket);
            RecipeCodecs.INGREDIENT_STREAM.encode(furnacePacket, original);
            Ingredient furnaceRoundTrip = RecipeCodecs.INGREDIENT_STREAM.decode(furnacePacket);
            for (Ingredient decoded : List.of(original, json, nativeRoundTrip, furnaceRoundTrip)) {
                assertTrue(decoded.test(required));
                assertFalse(decoded.test(new ItemStack(Items.HONEYCOMB)));
                assertFalse(decoded.test(tagged(Items.HONEYCOMB, "different")));
                assertFalse(decoded.test(new ItemStack(Items.DIAMOND)));
                assertEquals("snapshot", decoded.getItems()[0].getTag().getString("payload"));
                decoded.getItems()[0].getTag().putString("payload", "changed display");
                assertTrue(decoded.test(required));
            }
        } finally {
            nativePacket.release();
            furnacePacket.release();
        }
        assertEquals("snapshot", required.getTag().getString("payload"));
    }

    @Test
    void knownNbtPredicateSurvivesSerializationAndUnknownOrCountSensitiveOnesAreRejected() {
        ItemStack required = tagged(Items.HONEYCOMB, "strict");
        var converted = ProductiveBeesCentrifugeIngredient.of(StrictNBTIngredient.of(required));
        var decoded = ProductiveBeesCentrifugeIngredient.TYPE.parse(converted.toJson().getAsJsonObject());
        assertTrue(decoded.test(required.copyWithCount(17)));
        ItemStack extra = required.copy(); extra.getOrCreateTag().putInt("extra", 1);
        assertFalse(decoded.test(extra));
        Ingredient unknown = new Ingredient(Stream.of(new Ingredient.ItemValue(required))) {
            @Override public boolean test(ItemStack stack) { return stack != null && stack.getCount() >= 12; }
        };
        assertThrows(IllegalArgumentException.class, () -> ProductiveBeesCentrifugeIngredient.of(unknown));
        assertThrows(IllegalArgumentException.class, () -> ProductiveBeesCentrifugeIngredient.of(Ingredient.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> ProductiveBeesCentrifugeIngredient.of((Ingredient) null));
    }

    @Test
    void nativeCapabilityCompatibilityAndMemberPayloadSurviveTheCodec() {
        ItemStack required = new ItemStack(capabilityItem);
        // Forge defers initCapabilities until the first capability query. ItemStack.save alone
        // preserves only existing lazy data and does not initialize a fresh native provider.
        required.getCapability(org.mockito.Mockito.mock(Capability.class));
        CompoundTag saved = required.save(new CompoundTag());
        CompoundTag caps = saved.getCompound("ForgeCaps");
        assertFalse(caps.isEmpty(), "the fixture has a real native serializable capability provider");
        CompoundTag expected = caps.copy();
        String provider = caps.getAllKeys().iterator().next();
        caps.getCompound(provider).putString("payload", "different native capability");
        ItemStack wrong = ItemStack.of(saved);
        assertFalse(ItemHandlerHelper.canItemStacksStack(wrong, required));
        var original = ProductiveBeesCentrifugeIngredient.of(Ingredient.of(required));
        var decoded = ProductiveBeesCentrifugeIngredient.TYPE.parse(original.toJson().getAsJsonObject());
        assertEquals(expected, decoded.getItems()[0].save(new CompoundTag()).getCompound("ForgeCaps"));
        assertTrue(decoded.test(required));
        assertFalse(decoded.test(wrong));
    }

    private static final class FixtureCaps implements ICapabilitySerializable<CompoundTag> {
        private CompoundTag data = initialData();
        private static CompoundTag initialData() {
            CompoundTag tag = new CompoundTag();
            tag.putString("payload", "native capability");
            return tag;
        }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return LazyOptional.empty();
        }
        @Override public CompoundTag serializeNBT() { return data.copy(); }
        @Override public void deserializeNBT(CompoundTag nbt) { data = nbt.copy(); }
    }

    private static ItemStack tagged(Item item, String payload) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("payload", payload);
        return stack;
    }
}
