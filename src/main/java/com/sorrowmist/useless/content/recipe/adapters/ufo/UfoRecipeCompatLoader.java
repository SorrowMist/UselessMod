package com.sorrowmist.useless.content.recipe.adapters.ufo;

import com.raishxn.ufo.recipe.UniversalMultiblockMachineKind;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;

import java.util.ArrayList;
import java.util.List;

/** Builds the alloy-furnace adapters for UFO Future's machines. */
public final class UfoRecipeCompatLoader {

    private UfoRecipeCompatLoader() {
    }

    public static List<IRecipeAdapter<?>> createAdapters() {
        List<IRecipeAdapter<?>> adapters = new ArrayList<>();
        adapters.add(new UfoDmaRecipeAdapter());
        adapters.add(new UfoStellarRecipeAdapter());
        adapters.add(new UfoUniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind.QMF, "quantum_matter_fabricator_controller"));
        adapters.add(new UfoUniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind.QUANTUM_SLICER, "quantum_slicer_controller"));
        adapters.add(new UfoUniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind.QUANTUM_PROCESSOR_ASSEMBLER, "quantum_processor_assembler_controller"));
        adapters.add(new UfoUniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind.QUANTUM_CRYOFORGE, "quantum_cryoforge_controller"));
        return adapters;
    }
}
