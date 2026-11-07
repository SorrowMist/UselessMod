package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.sammy.malum.common.item.spirit.SpiritShardItem;
import com.sammy.malum.core.systems.recipe.SpiritWithCount;
import com.sammy.malum.core.systems.spirit.MalumSpiritType;
import com.sammy.malum.core.systems.spirit.SpiritVisualMotif;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.registries.RegistryObject;
import team.lodestar.lodestone.systems.easing.Easing;

import java.awt.Color;
import java.lang.reflect.Field;

/**
 * Boots the small part of Forge's registry layer needed by Malum's native recipe constructors.
 *
 * <p>Malum's recipe constructors immediately call {@code RegistryObject#get()} for the recipe
 * serializer and recipe type. A plain JUnit JVM has neither the mod loading event bus nor Forge's
 * recipe registries populated, so this helper creates Forge's registries through {@code GameData}
 * and publishes the six native Malum recipe serializer/type instances. The recipe objects under
 * test are still the real 1.6.7 classes and use their real constructors.</p>
 */
final class MalumNativeTestSupport {
    private static boolean initialized;
    private static MalumSpiritType spiritType;
    private static SpiritShardItem spiritShard;
    private static com.sammy.malum.common.item.impetus.ImpetusItem impetus;

    private MalumNativeTestSupport() {
    }

    static synchronized void initialize() {
        if (initialized) {
            return;
        }
        InventoryTestBootstrap.initialize();
        try {
            ensureForgeRecipeRegistries();
            bindNativeRecipeRegistries();
            createTestSpirit();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to prepare Malum 1.6.7 native registries", exception);
        }
        initialized = true;
    }

    static SpiritWithCount spirit(int count) {
        initialize();
        return new SpiritWithCount(spiritType, count);
    }

    static ItemStack spiritStack(int count) {
        initialize();
        return new ItemStack(spiritShard, count);
    }

    static com.sammy.malum.common.item.impetus.ImpetusItem impetus() {
        initialize();
        if (impetus == null) impetus = InventoryTestBootstrap.registerItem("malum_impetus",
                () -> new com.sammy.malum.common.item.impetus.ImpetusItem(
                        new net.minecraft.world.item.Item.Properties().durability(100)));
        return impetus;
    }

    private static void ensureForgeRecipeRegistries() throws ReflectiveOperationException {
        // GameData creates the Forge registries. Calling it before ForgeRegistries is initialised
        // prevents its static fields from caching nulls.
        net.minecraftforge.registries.GameData.init();

        Class<?> forgeRegistries = Class.forName("net.minecraftforge.registries.ForgeRegistries");
        ensureRegistry(forgeRegistries, "RECIPE_SERIALIZERS", "minecraft:recipe_serializer");
        ensureRegistry(forgeRegistries, "RECIPE_TYPES", "minecraft:recipe_type");
    }

    private static void ensureRegistry(Class<?> forgeRegistries, String fieldName, String registryName)
            throws ReflectiveOperationException {
        Field field = forgeRegistries.getField(fieldName);
        Object current = field.get(null);
        if (current != null) {
            return;
        }
        IForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(new ResourceLocation(registryName));
        if (registry == null) {
            throw new IllegalStateException("Forge registry is unavailable: " + registryName);
        }
        setStatic(field, registry);
    }

    private static void bindNativeRecipeRegistries() throws ReflectiveOperationException {
        Class<?> types = Class.forName("com.sammy.malum.registry.common.recipe.RecipeTypeRegistry");
        bindType(types, "SPIRIT_INFUSION", "spirit_infusion");
        bindType(types, "SPIRIT_FOCUSING", "spirit_focusing");
        bindType(types, "VOID_FAVOR", "favor_of_the_void");
        bindType(types, "RUNEWORKING", "runeworking");
        bindType(types, "SPIRIT_REPAIR", "spirit_repair");
        bindType(types, "SPIRIT_TRANSMUTATION", "spirit_transmutation");

        Class<?> serializers = Class.forName(
                "com.sammy.malum.registry.common.recipe.RecipeSerializerRegistry");
        bindSerializer(serializers, "INFUSION_RECIPE_SERIALIZER",
                "com.sammy.malum.common.recipe.SpiritInfusionRecipe$Serializer");
        bindSerializer(serializers, "FOCUSING_RECIPE_SERIALIZER",
                "com.sammy.malum.common.recipe.SpiritFocusingRecipe$Serializer");
        bindSerializer(serializers, "VOID_FAVOR_RECIPE_SERIALIZER",
                "com.sammy.malum.common.recipe.FavorOfTheVoidRecipe$Serializer");
        bindSerializer(serializers, "RUNEWORKING_RECIPE_SERIALIZER",
                "com.sammy.malum.common.recipe.RunicWorkbenchRecipe$Serializer");
        bindSerializer(serializers, "REPAIR_RECIPE_SERIALIZER",
                "com.sammy.malum.common.recipe.SpiritRepairRecipe$Serializer");
        bindSerializer(serializers, "SPIRIT_TRANSMUTATION_RECIPE_SERIALIZER",
                "com.sammy.malum.common.recipe.SpiritTransmutationRecipe$Serializer");
    }

    private static void bindSerializer(Class<?> holderClass, String fieldName, String serializerClass)
            throws ReflectiveOperationException {
        Field field = holderClass.getField(fieldName);
        RegistryObject<?> object = (RegistryObject<?>) field.get(null);
        Object serializer = Class.forName(serializerClass).getDeclaredConstructor().newInstance();
        setRegistryObjectValue(object, serializer);
    }

    private static void bindType(Class<?> holderClass, String fieldName, String name)
            throws ReflectiveOperationException {
        Field field = holderClass.getField(fieldName);
        RegistryObject<?> object = (RegistryObject<?>) field.get(null);
        RecipeType<net.minecraft.world.item.crafting.Recipe<?>> type = new RecipeType<>() {
            @Override
            public String toString() {
                return "malum:" + name;
            }
        };
        setRegistryObjectValue(object, type);
    }

    private static void setRegistryObjectValue(RegistryObject<?> object, Object value)
            throws ReflectiveOperationException {
        Field field = RegistryObject.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void setStatic(Field field, Object value) throws ReflectiveOperationException {
        field.setAccessible(true);
        try {
            field.set(null, value);
        } catch (IllegalAccessException exception) {
            // Java 17 can still reject a static final field through Field#set. The field is not a
            // compile-time constant, so Unsafe is sufficient for this test-only registry bridge.
            setStaticWithUnsafe(field, value);
        }
    }

    private static void setStaticWithUnsafe(Field field, Object value) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field instanceField = unsafeClass.getDeclaredField("theUnsafe");
        instanceField.setAccessible(true);
        Object unsafe = instanceField.get(null);
        long offset = (long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafe, field);
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(unsafe, field);
        unsafeClass.getMethod("putObject", Object.class, long.class, Object.class)
                .invoke(unsafe, base, offset, value);
    }

    private static void createTestSpirit() {
        SpiritShardItem[] shardRef = new SpiritShardItem[1];
        SpiritVisualMotif motif = new SpiritVisualMotif(
                Color.WHITE, Color.WHITE, 1.0F, Easing.LINEAR);
        MalumSpiritType type = new MalumSpiritType(
                "useless_mod_test", motif, () -> shardRef[0], Color.WHITE) {
            // Enum extension is a launcher transform, unrelated to recipe semantics.
            @Override public net.minecraft.world.item.Rarity getItemRarity() {
                return net.minecraft.world.item.Rarity.COMMON;
            }
        };
        spiritType = type;
        spiritShard = InventoryTestBootstrap.registerItem("malum_shard",
                () -> new SpiritShardItem(new net.minecraft.world.item.Item.Properties(), type));
        shardRef[0] = spiritShard;
    }
}
