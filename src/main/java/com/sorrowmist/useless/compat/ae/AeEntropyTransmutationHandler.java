package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IMEChest;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Server-authoritative, disk-local transformation. Never extracts from the ME network. */
public final class AeEntropyTransmutationHandler {
    private AeEntropyTransmutationHandler() {}

    /** Also used for read-only queries: no remote inventory probing or chunk loading. */
    public static boolean canInteract(ServerLevel level, BlockPos pos, Player player) {
        if (player.level() != level || !player.isAlive() || player.isSpectator()
                || !player.mayBuild() || !player.isShiftKeyDown()
                || !holdsManipulator(player) || !level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) return false;
        double reach = Math.min(64.0, player.getBlockReach());
        if (!Double.isFinite(reach) || reach <= 0 || !player.canReach(pos, 0)) return false;
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    public static boolean holdsManipulator(Player player) {
        return player.getMainHandItem().getItem() instanceof appeng.items.tools.powered.EntropyManipulatorItem
                || player.getOffhandItem().getItem() instanceof appeng.items.tools.powered.EntropyManipulatorItem;
    }

    public static boolean tryTransmute(ServerLevel level, BlockPos pos, Player player) {
        if (!canInteract(level, pos, player) || !(level.getBlockEntity(pos) instanceof IMEChest chest)) return false;
        MEStorage storage = chest.isPowered() ? chest.getCellInventory(0) : null;
        if (storage == null) return false;
        IActionSource source = IActionSource.ofPlayer(player, chest);
        Match match = findMatch(level, storage, source);
        if (match == null) return false;

        // Both preview and execution use the same allocation. No input can spend the same key twice.
        for (Extraction extraction : match.plan()) {
            if (storage.extract(extraction.key(), extraction.amount(), Actionable.SIMULATE, source)
                    != extraction.amount()) return false;
        }
        List<Extraction> removed = new ArrayList<>();
        for (Extraction extraction : match.plan()) {
            long extracted = storage.extract(extraction.key(), extraction.amount(), Actionable.MODULATE, source);
            if (extracted > 0) removed.add(new Extraction(extraction.key(), extracted));
            if (extracted != extraction.amount()) {
                refund(level, pos, storage, source, removed);
                return false;
            }
        }
        if (!level.setBlock(pos, match.output(), Block.UPDATE_ALL)) {
            refund(level, pos, storage, source, removed);
            return false;
        }
        // AE2's normal onRemove drops the disk (including its remaining contents) and chest input.
        return true;
    }

    public static @Nullable BlockState previewOutput(ServerLevel level, BlockPos pos, Player player) {
        if (!canInteract(level, pos, player) || !(level.getBlockEntity(pos) instanceof IMEChest chest)) return null;
        MEStorage storage = chest.isPowered() ? chest.getCellInventory(0) : null;
        if (storage == null) return null;
        Match match = findMatch(level, storage, IActionSource.ofPlayer(player, chest));
        return match == null ? null : match.output();
    }

    private static @Nullable Match findMatch(ServerLevel level, MEStorage storage, IActionSource source) {
        // RecipeManager iteration is not stable across reloads. Preview and click must choose the same winner.
        List<EntropyTransmutationRecipe> recipes = new ArrayList<>(
                level.getRecipeManager().getAllRecipesFor(EntropyTransmutationRecipe.TYPE));
        recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        var available = storage.getAvailableStacks();
        for (EntropyTransmutationRecipe recipe : recipes) {
            List<Extraction> plan = planExtraction(storage, available, recipe.inputs(), source);
            if (plan != null) {
                BlockState output = ((BlockItem) recipe.output().getItem()).getBlock().defaultBlockState();
                return new Match(output, plan);
            }
        }
        return null;
    }

    /**
     * Integral max-flow assigns each stored item to at most one demand. Unlike per-input totals or
     * greedy consumption, this handles duplicate ingredients AND overlapping tags/NBT predicates.
     * Non-consumed demands reserve their items but are not included in the extraction plan.
     */
    static @Nullable List<Extraction> planExtraction(MEStorage storage,
            Iterable<Object2LongMap.Entry<AEKey>> available, List<CountedIngredient> inputs, IActionSource source) {
        List<AEItemKey> keys = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) continue;
            if (inputs.stream().noneMatch(input -> input.ingredient().test(key.getReadOnlyStack()))) continue;
            long amount = Math.min(entry.getLongValue(),
                    storage.extract(key, entry.getLongValue(), Actionable.SIMULATE, source));
            if (amount > 0) {
                keys.add(key);
                amounts.add(amount);
            }
        }
        int sourceNode = keys.size() + inputs.size();
        int sink = sourceNode + 1;
        List<List<Edge>> graph = new ArrayList<>(sink + 1);
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        List<Edge> demands = new ArrayList<>();
        for (int k = 0; k < keys.size(); k++) {
            connect(graph, sourceNode, k, amounts.get(k));
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i).ingredient().test(keys.get(k).getReadOnlyStack())) {
                    connect(graph, k, keys.size() + i, amounts.get(k));
                }
            }
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).count() <= 0) return null;
            demands.add(connect(graph, keys.size() + i, sink, inputs.get(i).count()));
        }
        // No sum of long demands is formed, so even multiple Long.MAX_VALUE inputs cannot wrap.
        while (augment(graph, sourceNode, sink)) { /* until no augmenting path */ }
        if (demands.stream().anyMatch(edge -> edge.capacity != 0)) return null;

        List<Extraction> plan = new ArrayList<>();
        for (int k = 0; k < keys.size(); k++) {
            long consumed = 0;
            for (Edge edge : graph.get(k)) {
                int input = edge.to - keys.size();
                if (input >= 0 && input < inputs.size() && inputs.get(input).consumed()) {
                    consumed += graph.get(edge.to).get(edge.reverse).capacity;
                }
            }
            if (consumed > 0) plan.add(new Extraction(keys.get(k), consumed));
        }
        return List.copyOf(plan);
    }

    private static Edge connect(List<List<Edge>> graph, int from, int to, long amount) {
        Edge forward = new Edge(to, graph.get(to).size(), amount);
        Edge reverse = new Edge(from, graph.get(from).size(), 0);
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }

    private static boolean augment(List<List<Edge>> graph, int source, int sink) {
        int[] previous = new int[graph.size()];
        int[] previousEdge = new int[graph.size()];
        Arrays.fill(previous, -1);
        previous[source] = source;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty() && previous[sink] < 0) {
            int node = queue.removeFirst();
            for (int i = 0; i < graph.get(node).size(); i++) {
                Edge edge = graph.get(node).get(i);
                if (edge.capacity <= 0 || previous[edge.to] >= 0) continue;
                previous[edge.to] = node;
                previousEdge[edge.to] = i;
                queue.addLast(edge.to);
            }
        }
        if (previous[sink] < 0) return false;
        long amount = Long.MAX_VALUE;
        for (int node = sink; node != source; node = previous[node]) {
            amount = Math.min(amount, graph.get(previous[node]).get(previousEdge[node]).capacity);
        }
        for (int node = sink; node != source; node = previous[node]) {
            Edge edge = graph.get(previous[node]).get(previousEdge[node]);
            edge.capacity -= amount;
            graph.get(node).get(edge.reverse).capacity += amount;
        }
        return true;
    }

    private static void refund(ServerLevel level, BlockPos pos, MEStorage storage, IActionSource source,
                               List<Extraction> removed) {
        for (Extraction extraction : removed) {
            long restored = storage.insert(extraction.key(), extraction.amount(), Actionable.MODULATE, source);
            long remainder = extraction.amount() - restored;
            // A third-party cell can refuse reinsertion. Do not silently void the already removed items.
            while (remainder > 0) {
                int count = (int) Math.min(remainder, extraction.key().getMaxStackSize());
                Block.popResource(level, pos, extraction.key().toStack(count));
                remainder -= count;
            }
        }
    }

    record Extraction(AEItemKey key, long amount) {}
    private record Match(BlockState output, List<Extraction> plan) {}
    private static final class Edge {
        final int to;
        final int reverse;
        long capacity;
        Edge(int to, int reverse, long capacity) {
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
        }
    }
}
