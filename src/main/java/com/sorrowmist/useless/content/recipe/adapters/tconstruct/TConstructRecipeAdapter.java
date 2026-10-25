package com.sorrowmist.useless.content.recipe.adapters.tconstruct;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.*;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Proxy;
import java.util.*;

/** Optional native 1.20.1 integration: no linkage to TConstruct or Mantle when absent. */
public final class TConstructRecipeAdapter implements IRecipeAdapter<Recipe<?>> {
    private static final String ROOT = "slimeknights.tconstruct.library.recipe.";
    private final String type, machine;
    private final Class<Recipe<?>> recipeClass;

    @SuppressWarnings("unchecked")
    public TConstructRecipeAdapter(String type, String machine) throws ClassNotFoundException {
        this.type = type;
        this.machine = machine;
        String name = switch (type) {
            case "melting" -> "melting.MeltingRecipe";
            case "alloy" -> "alloying.AlloyRecipe";
            default -> "casting.ItemCastingRecipe";
        };
        recipeClass = (Class<Recipe<?>>)(Class<?>)Class.forName(ROOT + name);
    }

    public static List<IRecipeAdapter<?>> createAdapters() throws ClassNotFoundException {
        return List.of(new TConstructRecipeAdapter("melting", "smeltery_controller"),
                new TConstructRecipeAdapter("melting", "foundry_controller"),
                new TConstructRecipeAdapter("alloy", "smeltery_controller"),
                new TConstructRecipeAdapter("alloy", "foundry_controller"),
                new TConstructRecipeAdapter("casting_table", "seared_table"),
                new TConstructRecipeAdapter("casting_table", "scorched_table"),
                new TConstructRecipeAdapter("casting_basin", "seared_basin"),
                new TConstructRecipeAdapter("casting_basin", "scorched_basin"));
    }
    @Override public String sourceId() { return "tconstruct"; }
    @Override public Class<Recipe<?>> getRecipeClass() { return recipeClass; }
    @Override public ItemStack getMoldItem() {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tconstruct", machine)));
    }

    /** Reject dynamic durability, material/tool and retexturing recipes rather than invent fixed outputs. */
    public static boolean safeClass(String name) {
        return Set.of(ROOT + "melting.MeltingRecipe", ROOT + "melting.OreMeltingRecipe",
                ROOT + "alloying.AlloyRecipe", ROOT + "casting.ItemCastingRecipe").contains(name);
    }

    @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<Recipe<?>> holder, Level level) {
        if (holder == null || holder.value() == null || getMoldItem().isEmpty()) return List.of();
        Recipe<?> source = holder.value();
        if (!safeClass(source.getClass().getName())) return List.of();
        var nativeType = BuiltInRegistries.RECIPE_TYPE.getKey(source.getType());
        if (!new ResourceLocation("tconstruct", type.equals("alloy") ? "alloying" : type).equals(nativeType)) return List.of();
        try {
            return switch (type) {
                case "melting" -> melt(holder.id(), source);
                case "alloy" -> alloy(holder.id(), source);
                default -> cast(holder.id(), source);
            };
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            RateLimitedLogger.warn(LogUtils.getLogger(), "Cannot convert TConstruct recipe {} for {}", holder.id(), machine, exception);
            return List.of();
        }
    }

    private List<AdvancedAlloyFurnaceRecipe> cast(ResourceLocation id, Object source) throws ReflectiveOperationException {
        Ingredient cast = (Ingredient)call(source,"getCast");
        ItemStack result = (ItemStack)call(source,"getOutput");
        boolean consumed = (Boolean)call(source,"isConsumed");
        if (result.isEmpty()) return List.of();
        var items = new ArrayList<CountedIngredient>();
        var molds = new ArrayList<Ingredient>();
        molds.add(Ingredient.of(getMoldItem()));
        addCast(cast, consumed, items, molds);
        var alternatives = fluids((JsonElement)call(call(source,"getFluid"),"serialize"));
        var recipes = new ArrayList<AdvancedAlloyFurnaceRecipe>();
        int time = ((Number)call(source,"getCoolingTime")).intValue();
        for (int i=0; i<alternatives.size(); i++) recipes.add(recipe(id,i,items,List.of(alternatives.get(i)),
                List.of(result.copy()),List.of(),molds,time));
        return recipes;
    }

    /** Preserve both the cast's native predicate and whether the original machine consumes it. */
    static void addCast(Ingredient cast, boolean consumed, List<CountedIngredient> items, List<Ingredient> molds) {
        if (cast == null || cast.isEmpty()) return;
        if (consumed) items.add(new CountedIngredient(cast,1));
        else molds.add(cast);
    }

    private List<AdvancedAlloyFurnaceRecipe> melt(ResourceLocation id, Object source) throws ReflectiveOperationException {
        Ingredient input = (Ingredient)call(source,"getInput");
        if (input == null || input.isEmpty()) return List.of();
        var outputs = new ArrayList<FluidStack>();
        if (machine.equals("foundry_controller")) {
            // Native API includes its configured ore rate AND independently rated byproducts.
            for (Object group : (List<?>)call(source,"getOutputWithByproducts")) {
                var alternatives = (List<?>)group;
                if (alternatives.size()!=1) return List.of();
                FluidStack output = (FluidStack)alternatives.get(0);
                if (!output.isEmpty()) outputs.add(output.copy());
            }
        } else {
            // Let native OreMeltingRecipe apply smelteryOreRate; do not hardcode a multiplier.
            Class<?> container = Class.forName(ROOT + "melting.IMeltingContainer");
            Object config = Class.forName("slimeknights.tconstruct.common.config.Config").getField("COMMON").get(null);
            Object rate = config.getClass().getField("smelteryOreRate").get(config);
            Object proxy = Proxy.newProxyInstance(container.getClassLoader(),new Class<?>[]{container},(p,m,a) -> {
                if (m.getName().equals("getOreRate")) return rate;
                if (m.getName().equals("getStack")) return ItemStack.EMPTY;
                throw new UnsupportedOperationException(m.getName());
            });
            FluidStack output = (FluidStack)source.getClass().getMethod("getOutput",container).invoke(source,proxy);
            if (!output.isEmpty()) outputs.add(output.copy());
        }
        if (outputs.isEmpty()) return List.of();
        return List.of(recipe(id,0,List.of(new CountedIngredient(input,1)),List.of(),List.of(),outputs,
                List.of(Ingredient.of(getMoldItem())),((Number)call(source,"getTime")).intValue()));
    }

    private List<AdvancedAlloyFurnaceRecipe> alloy(ResourceLocation id, Object source) throws ReflectiveOperationException {
        var inputFluids = new ArrayList<LongSizedFluidIngredient>();
        var outputs = new ArrayList<FluidStack>();
        FluidStack result = (FluidStack)call(source,"getOutput");
        if (result.isEmpty()) return List.of();
        outputs.add(result.copy());
        for (Object input : (List<?>)call(source,"getInputs")) {
            var choices = fluids((JsonElement)call(call(input,"fluid"),"serialize"));
            // A varying-amount compound cannot be collapsed into a cheaper fixed-size predicate.
            if (choices.size()!=1) return List.of();
            var fluid = choices.get(0);
            inputFluids.add(fluid);
            if ((Boolean)call(input,"catalyst")) {
                var stacks = fluid.ingredient().getStacks();
                // Catalytic fluid is returned, not consumed. Only an unambiguous native fluid
                // can be returned without changing which member of a tag was supplied.
                if (stacks.length!=1 || fluid.amount()>Integer.MAX_VALUE) return List.of();
                outputs.add(new FluidStack(stacks[0],(int)fluid.amount()));
            }
        }
        if (inputFluids.isEmpty()) return List.of();
        return List.of(recipe(id,0,List.of(),inputFluids,List.of(),outputs,List.of(Ingredient.of(getMoldItem())),
                AdapterUtils.DEFAULT_PROCESS_TIME));
    }

    private AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, int variant, List<CountedIngredient> items,
            List<LongSizedFluidIngredient> fluids, List<ItemStack> outputs, List<FluidStack> outputFluids,
            List<Ingredient> molds, int time) {
        if (time<0) throw new IllegalArgumentException("Negative native recipe time");
        return new AdvancedAlloyFurnaceRecipe(new ResourceLocation(id.getNamespace(),id.getPath()+"_"+machine+"_"+variant+"_converted"),
                items,fluids,List.of(),outputs,outputFluids,List.of(),AdapterUtils.DEFAULT_ENERGY,
                Math.max(1,time),Ingredient.EMPTY,0,molds,AlloyFurnaceMode.NORMAL);
    }

    /** Preserve Mantle's exact fluid IDs and live tags (native predicates ignore NBT). */
    static List<LongSizedFluidIngredient> fluids(JsonElement json) {
        var byAmount = new LinkedHashMap<Long,List<FluidIngredient>>();
        collectFluids(json,byAmount);
        var choices=byAmount.entrySet().stream().map(e -> new LongSizedFluidIngredient(FluidIngredient.any(e.getValue()),e.getKey())).toList();
        // Mantle compounds can overlap. Splitting one into alternative costs must not let a
        // later, cheaper branch bypass the native cost of the same fluid in this generation.
        for (int i=0;i<choices.size();i++) for (int j=i+1;j<choices.size();j++) {
            var left=choices.get(i).ingredient(); var right=choices.get(j).ingredient();
            for (var stack:left.getStacks()) if (right.test(stack))
                throw new IllegalArgumentException("Overlapping Mantle fluid alternatives with different costs");
        }
        return choices;
    }
    private static void collectFluids(JsonElement json, Map<Long,List<FluidIngredient>> out) {
        if (json.isJsonArray()) { for (var child:json.getAsJsonArray()) collectFluids(child,out); return; }
        var object=json.getAsJsonObject().deepCopy();
        long amount=object.get("amount").getAsLong();
        if (amount<=0 || (!object.has("fluid") && !object.has("tag"))) throw new IllegalArgumentException("Invalid Mantle fluid predicate");
        FluidIngredient ingredient=FluidIngredient.fromJson(object);
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Empty Mantle fluid predicate");
        out.computeIfAbsent(amount,k->new ArrayList<>()).add(ingredient);
    }
    static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    @Override public List<RecipeHolder<Recipe<?>>> findMatchingRecipes(Level level, Map<Ingredient,Long> items,
            Map<FluidStack,Long> fluids, ItemStack mold) {
        if (level==null || !matchesMold(mold)) return List.of();
        var matches = new ArrayList<RecipeHolder<Recipe<?>>>();
        for (var holder:RecipeManagerAccess.getRecipes(level.getRecipeManager())) {
            @SuppressWarnings("unchecked") var typed=(RecipeHolder<Recipe<?>>)(RecipeHolder<?>)holder;
            for (var converted:convertAll(typed,level)) {
                var required=new LinkedHashMap<Ingredient,Long>();
                for (var input:converted.inputs()) AdapterUtils.mergeIngredient(required,input.ingredient(),input.count());
                if (AdapterUtils.matchesRequired(items,required) && FluidIngredientAllocator.matchesLong(converted.inputFluids(),fluids,1L)) {
                    matches.add(typed); break;
                }
            }
        }
        return matches;
    }
}
