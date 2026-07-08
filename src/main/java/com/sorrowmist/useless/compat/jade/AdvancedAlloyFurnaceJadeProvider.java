package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum AdvancedAlloyFurnaceJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = UselessMod.id("advanced_alloy_furnace_tasks");
    private static final String TAG_TASKS = "UselessModAeTasks";
    private static final String TAG_PRODUCT = "Product";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_TOTAL_OUTPUT = "TotalOutput";
    private static final String TAG_STATUS_KEY = "StatusKey";
    private static final String TAG_STATUS_DETAIL = "StatusDetail";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        ListTag tasks = accessor.getServerData().getList(TAG_TASKS, Tag.TAG_COMPOUND);
        if (tasks.isEmpty()) {
            return;
        }

        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_tasks", tasks.size())
                .withStyle(ChatFormatting.DARK_PURPLE));
        for (int i = 0; i < tasks.size(); i++) {
            CompoundTag task = tasks.getCompound(i);
            String productName = task.getString(TAG_PRODUCT);
            int progress = task.getInt(TAG_PROGRESS);
            int maxProgress = task.getInt(TAG_MAX_PROGRESS);
            int totalOutput = task.getInt(TAG_TOTAL_OUTPUT);
            String statusKey = task.getString(TAG_STATUS_KEY);
            String statusDetail = task.getString(TAG_STATUS_DETAIL);
            Component product = Component.literal(productName);
            Component status = Component.translatable(statusKey);

            if (maxProgress > 1) {
                tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_progress_ticks",
                        product, totalOutput, progress, maxProgress, status));
            } else if (!statusDetail.isBlank()) {
                tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_waiting_detail",
                        product, totalOutput, status, createDetailComponent(statusDetail)).withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_waiting",
                        product, totalOutput, status).withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private Component createDetailComponent(String statusDetail) {
        if (statusDetail.startsWith("gui.useless_mod.")) {
            return Component.translatable(statusDetail);
        }
        return Component.literal(statusDetail);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof AdvancedAlloyFurnaceBlockEntity furnace)) {
            return;
        }

        ListTag tasks = new ListTag();
        for (AdvancedAlloyFurnaceAeManager.AETaskProgress progress : furnace.getAETaskProgressList()) {
            CompoundTag task = new CompoundTag();
            task.putString(TAG_PRODUCT, progress.getProductName());
            task.putInt(TAG_PROGRESS, progress.getProgress());
            task.putInt(TAG_MAX_PROGRESS, progress.getMaxProgress());
            task.putInt(TAG_TOTAL_OUTPUT, progress.getTotalOutputCount());
            task.putString(TAG_STATUS_KEY, progress.getStatusKey());
            task.putString(TAG_STATUS_DETAIL, progress.getStatusDetail());
            tasks.add(task);
        }
        data.put(TAG_TASKS, tasks);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
