package com.sorrowmist.useless.content.recipe.adapters.mi;

import aztech.modern_industrialization.machines.init.MIMachineRecipeTypes;

import java.util.List;

/**
 * Builds the alloy-furnace adapters for Modern Industrialization's machines.
 *
 * <p>For tiered machines the steam-age (bronze, or steel/steam where no bronze block exists)
 * machine block is used as the mold.</p>
 */
public final class MiRecipeCompatLoader {

    private MiRecipeCompatLoader() {
    }

    public static List<MiMachineRecipeAdapter> createAdapters() {
        return List.of(
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.ASSEMBLER, "assembler"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.CENTRIFUGE, "centrifuge"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.CHEMICAL_REACTOR, "chemical_reactor"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.COMPRESSOR, "bronze_compressor"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.CUTTING_MACHINE, "bronze_cutting_machine"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.DISTILLERY, "distillery"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.ELECTROLYZER, "electrolyzer"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.FURNACE, "bronze_furnace"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.MACERATOR, "bronze_macerator"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.MIXER, "bronze_mixer"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.PACKER, "steel_packer"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.POLARIZER, "polarizer"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.UNPACKER, "steel_unpacker"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.WIREMILL, "steel_wiremill"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.BLAST_FURNACE, "steam_blast_furnace"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.COKE_OVEN, "coke_oven"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.DISTILLATION_TOWER, "distillation_tower"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.FUSION_REACTOR, "fusion_reactor"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.HEAT_EXCHANGER, "heat_exchanger"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.IMPLOSION_COMPRESSOR, "implosion_compressor"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.OIL_DRILLING_RIG, "oil_drilling_rig"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.PRESSURIZER, "pressurizer"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.QUARRY, "steam_quarry"),
                new MiMachineRecipeAdapter(MIMachineRecipeTypes.VACUUM_FREEZER, "vacuum_freezer")
        );
    }
}
