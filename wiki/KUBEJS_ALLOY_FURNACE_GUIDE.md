# KubeJS Advanced Alloy Furnace Recipe Guide

<p>
    <a href="KUBEJS_ALLOY_FURNACE_GUIDE.md">English</a> |
    <a href="KUBEJS_ALLOY_FURNACE_GUIDE_CN.md">简体中文</a>
</p>

## Overview

The Advanced Alloy Furnace supports adding, replacing, and removing recipes through KubeJS during the server recipe-loading phase. Put scripts in `kubejs/server/`; changes take effect after `/reload`.

This integration registers the recipe type `useless_mod:advanced_alloy_furnace` and exposes it as:

```javascript
event.recipes.useless_mod.advanced_alloy_furnace(...)
```

Install a KubeJS build for NeoForge 1.21.1. Without KubeJS, Useless Mod remains usable and the optional integration is inactive.

## Table of Contents

- [Basic Usage](#basic-usage)
- [Input/Output Combinations](#inputoutput-combinations)
- [AEKey Inputs and Outputs](#aekey-inputs-and-outputs)
- [Field Description](#field-description)
- [Recipe Tiers](#recipe-tiers)
- [Replacing and Removing Recipes](#replacing-and-removing-recipes)
- [Complete Example](#complete-example)
- [Extended JSON](#extended-json)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Basic Usage

### Minimal Recipe

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace(
        'kubejs:iron_alloy',
        [
            { ingredient: 'minecraft:iron_ingot', count: 4 },
            { ingredient: 'minecraft:gold_ingot', count: 2 }
        ],
        [
            Item.of('kubejs:iron_alloy', 1)
        ]
    )
})
```

The positional form takes the recipe data ID, item input list, and item output list. An omitted `count` defaults to 1. For fluid-only outputs, use object syntax and omit `outputs`.

### Items and Fluids Together

Items and fluids use separate fields: item inputs use `ingredients`, fluid inputs use `input_fluids`, item outputs use `outputs`, and fluid outputs use `output_fluids`. One recipe may contain item and fluid inputs and outputs together:

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: 'kubejs:water_alloy',
        ingredients: [
            { ingredient: 'minecraft:iron_ingot', count: 2 }
        ],
        input_fluids: [
            { ingredient: 'minecraft:water', amount: 1000 }
        ],
        outputs: [
            Item.of('kubejs:water_alloy', 1)
        ],
        output_fluids: [
            Fluid.of('minecraft:lava', 250)
        ]
    })
})
```

For a fluid-only recipe, use object syntax and omit the unused item list. The `ingredients` field itself remains required by the schema; if there are no item inputs, set `ingredients: []`.

### Mold and Tier

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace(
        'kubejs:gear_alloy',
        [
            { ingredient: '#c:ingots/iron', count: 4 },
            { ingredient: '#c:ingots/copper', count: 4 }
        ],
        [
            Item.of('kubejs:gear_alloy', 1)
        ],
        'useless_mod:metal_mold_gear',
        3
    )
})
```

The fourth positional argument is a single `mold`; the fifth is `tier`. This recipe requires a coil tier of at least 3.

### Object Syntax

Object syntax is recommended when setting a tier without a mold, multiple molds, or several optional fields:

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: 'kubejs:press_alloy',
        ingredients: [
            { ingredient: 'minecraft:diamond', count: 8 },
            { ingredient: 'minecraft:netherite_ingot', count: 1 }
        ],
        outputs: [
            Item.of('kubejs:press_alloy', 1)
        ],
        tier: 5,
        energy: 500000,
        process_time: 200
    })
})
```

### Multiple Molds

Use `molds` to declare multiple independent mold requirements:

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: 'kubejs:multi_mold_alloy',
        ingredients: [
            { ingredient: 'minecraft:iron_block', count: 2 }
        ],
        outputs: [
            Item.of('kubejs:multi_mold_alloy', 1)
        ],
        molds: [
            'useless_mod:metal_mold_gear',
            'useless_mod:metal_mold_plate'
        ],
        tier: 4
    })
})
```

Do not define both `mold` and `molds`. Multiple mold requirements are handled by the Advanced Alloy Furnace mold hub; a regular single-mold slot does not support them.

## Input/Output Combinations

The recipe supports these combinations of item/fluid input and output shapes:

| Test ID suffix | Inputs | Outputs |
|---|---|---|
| `item_to_item` | Item | Item |
| `item_to_fluid` | Item | Fluid |
| `item_fluid_to_item` | Item + Fluid | Item |
| `item_fluid_to_fluid` | Item + Fluid | Fluid |
| `item_to_item_fluid` | Item | Item + Fluid |
| `item_fluid_to_item_fluid` | Item + Fluid | Item + Fluid |
| `fluid_to_item` | Fluid | Item |
| `fluid_to_fluid` | Fluid | Fluid |
| `fluid_to_item_fluid` | Fluid | Item + Fluid |

For example, an item and fluid input with both output types is:

```javascript
event.recipes.useless_mod.advanced_alloy_furnace({
    id: 'kubejs:alloy_furnace_item_fluid_to_item_fluid_test',
    ingredients: [
        { ingredient: { item: 'minecraft:redstone' }, count: 1 }
    ],
    input_fluids: [
        { ingredient: { fluid: 'minecraft:water' }, amount: 1000 }
    ],
    outputs: [
        Item.of('minecraft:gold_nugget', 1)
    ],
    output_fluids: [
        Fluid.of('minecraft:lava', 250)
    ],
    tier: 0
})
```

When a test has no item input, keep the required `ingredients` field and set it to an empty array:

```javascript
event.recipes.useless_mod.advanced_alloy_furnace({
    id: 'kubejs:alloy_furnace_fluid_to_item_test',
    ingredients: [],
    input_fluids: [
        { ingredient: { fluid: 'minecraft:water' }, amount: 1000 }
    ],
    outputs: [Item.of('minecraft:coal', 1)],
    tier: 0
})
```

Mold requirements can contain one, two, or three independent entries:

```javascript
// One mold
molds: [{ item: 'minecraft:stick' }]

// Two molds
molds: [
    { item: 'minecraft:stick' },
    { item: 'minecraft:stone' }
]

// Three molds
molds: [
    { item: 'minecraft:stick' },
    { item: 'minecraft:stone' },
    { item: 'minecraft:cobblestone' }
]
```

Each mold entry is an independent requirement. Do not combine `mold` and `molds` in one recipe.

## AEKey Inputs and Outputs

The schema exposes AE2 generic stacks through `key_inputs` and `key_outputs`. Each entry is a `GenericStack`: `#t` selects the registered AEKey type and `#` is the amount. This supports custom key types without a hard-coded dependency on their mod.

For a Data Energistics data flow recipe, install and load Data Energistics so that `data_energistics:data_flow` is registered before the recipe is parsed:

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: 'kubejs:data_flow_alloy',
        ingredients: [],
        key_inputs: [
            { '#t': 'data_energistics:data_flow', '#': 1200 }
        ],
        key_outputs: [
            { '#t': 'data_energistics:data_flow', '#': 200 }
        ],
        tier: 0,
        energy: 1000,
        process_time: 20
    })
})
```

`data_energistics:data_flow` is an AEKey, not an item and not a normal fluid. Its amount therefore belongs in `key_inputs` or `key_outputs`. The recipe still needs `ingredients: []` when it has no item input, but ordinary `outputs` and `output_fluids` can remain omitted when `key_outputs` supplies the output.

AE2 item and fluid keys use the same format:

```javascript
key_inputs: [
    { '#t': 'ae2:i', id: 'minecraft:iron_ingot', '#': 1 },
    { '#t': 'ae2:f', id: 'minecraft:water', '#': 1000 }
],
key_outputs: [
    { '#t': 'ae2:i', id: 'minecraft:gold_ingot', '#': 1 },
    { '#t': 'ae2:f', id: 'minecraft:lava', '#': 250 }
]
```

Use `input_fluids` and `output_fluids` for the alloy furnace's ordinary machine fluid ports. Use `ae2:f` inside `key_inputs` and `key_outputs` when the fluid is meant to be transferred through the AE network as an AEKey.

## Field Description

| Field | Type | Required | Default | Description |
|---|---|---:|---:|---|
| `id` | String | Yes | - | Recipe data ID, for example `kubejs:iron_alloy` |
| `ingredients` | Object[] | Yes | - | Input list; each entry has `ingredient` and an optional `count` |
| `input_fluids` | Object[] | No | `[]` | Fluid input list; each entry has a fluid `ingredient` and `amount` |
| `key_inputs` | GenericStack[] | No | `[]` | AEKey input list; each entry uses the AE2 generic stack format |
| `outputs` | ItemStack[] | No | `[]` | Item output list; strings and `Item.of(...)` are supported |
| `output_fluids` | FluidStack[] | No | `[]` | Fluid output list; `Fluid.of(...)` is supported |
| `key_outputs` | GenericStack[] | No | `[]` | AEKey output list; each entry uses the AE2 generic stack format |
| `mold` | Ingredient | No | Empty | Single mold requirement; item IDs and tags are supported |
| `molds` | Ingredient[] | No | Empty | Multiple independent mold requirements; mutually exclusive with `mold` |
| `tier` | Integer | No | Unspecified | Minimum coil tier, from `0` to `10` |
| `energy` | Long | No | `2000` | Energy consumed by one operation |
| `process_time` | Integer | No | `200` | Processing time in ticks |
| `catalyst` | Ingredient | No | Empty | Catalyst requirement; the current alloy-furnace runtime does not consume it |

### Item Input Format

```javascript
ingredients: [
    { ingredient: 'minecraft:iron_ingot', count: 4 },
    { ingredient: '#c:ingots/copper', count: 2 },
    { ingredient: 'minecraft:diamond' }
]
```

`ingredient` uses the standard KubeJS Ingredient syntax. It may be an item ID, a tag, or another Ingredient expression supported by KubeJS. Each input is checked independently.

### Fluid Input Format

```javascript
input_fluids: [
    { ingredient: 'minecraft:water', amount: 1000 },
    { ingredient: '#c:lava', amount: 250 }
]
```

`amount` is measured in mB and supports large integers. `ingredient` may be a fluid ID, a fluid tag, or another fluid Ingredient expression supported by KubeJS.

### Output Format

```javascript
outputs: [
    Item.of('minecraft:diamond', 2),
    Item.of('kubejs:alloy', 1, { display: { Name: 'Custom alloy' } })
]
```

An item string can also be used:

```javascript
outputs: [ 'minecraft:iron_ingot' ]
```

### Fluid Output Format

```javascript
output_fluids: [
    Fluid.of('minecraft:water', 1000),
    Fluid.of('minecraft:lava', 250)
]
```

Item outputs are optional, so a recipe may produce only fluids:

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: 'kubejs:fluid_only_alloy',
        ingredients: [
            { ingredient: 'minecraft:iron_ingot', count: 1 }
        ],
        output_fluids: [
            Fluid.of('minecraft:lava', 1000)
        ]
    })
})
```

## Recipe Tiers

`tier` is the minimum Advanced Alloy Furnace coil tier required by the recipe, from `0` to `10`.

- `tier: 3` requires a coil tier of at least 3.
- `tier: 0` explicitly makes the recipe unrestricted.
- If `tier` is omitted, the server `recipe_tier_rules` configuration is used.
- `recipe_tier_rules` entries use `recipe ID pattern,tier`, for example `kubejs:*,3`.
- An explicit recipe `tier` takes priority over `recipe_tier_rules`.

Example configuration:

```toml
recipe_tier_rules = [
    "useless_mod:advanced_alloy/gear/*,4",
    "kubejs:*,2"
]
```

The configuration only applies to recipes without an explicit `tier`. Reload the configuration or restart the game according to the configuration screen message.

## Replacing and Removing Recipes

### Remove One Recipe

```javascript
ServerEvents.recipes(event => {
    event.remove({ id: 'useless_mod:advanced_alloy/gear/useless_gear_tier_1' })
})
```

### Remove by Type

```javascript
ServerEvents.recipes(event => {
    event.remove({ type: 'useless_mod:advanced_alloy_furnace' })
})
```

Removing by type removes every Advanced Alloy Furnace recipe, so use it carefully.

### Replace One Recipe

```javascript
ServerEvents.recipes(event => {
    const id = 'useless_mod:advanced_alloy/gear/useless_gear_tier_1'

    event.remove({ id: id })
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: id,
        ingredients: [
            { ingredient: 'minecraft:iron_ingot', count: 8 }
        ],
        outputs: [
            Item.of('useless_mod:useless_gear_tier_1', 1)
        ],
        mold: 'useless_mod:metal_mold_gear',
        tier: 2
    })
})
```

## Complete Example

```javascript
ServerEvents.recipes(event => {
    const { useless_mod } = event.recipes

    useless_mod.advanced_alloy_furnace({
        id: 'kubejs:complete_alloy',
        ingredients: [
            { ingredient: 'minecraft:iron_block', count: 2 },
            { ingredient: '#c:ingots/gold', count: 8 }
        ],
        outputs: [
            Item.of('kubejs:complete_alloy', 1)
        ],
        mold: 'useless_mod:metal_mold_gear',
        tier: 3,
        energy: 100000,
        process_time: 100,
        catalyst: 'minecraft:diamond'
    })
})
```

## Extended JSON

The Schema covers item inputs, fluid inputs, AEKey inputs, item outputs, AEKey outputs, fluid outputs, molds, tiers, and common processing options. For other fields not exposed by the Schema, use `event.custom` and provide the serializer JSON directly. The raw JSON shape for fluid fields is:

```javascript
ServerEvents.recipes(event => {
    event.custom({
        type: 'useless_mod:advanced_alloy_furnace',
        id: 'kubejs:fluid_alloy',
        ingredients: [
            {
                ingredient: { item: 'minecraft:iron_ingot' },
                count: 4
            }
        ],
        input_fluids: [
            {
                ingredient: { fluid: 'minecraft:water' },
                amount: 1000
            }
        ],
        output_fluids: [
            { id: 'minecraft:water', amount: 1000 }
        ],
        tier: 2
    })
})
```

`event.custom` bypasses this Schema's argument conversion. Field names and JSON types must match the Advanced Alloy Furnace recipe serializer.

## Best Practices

1. Prefix recipe IDs with your mod ID or `kubejs:` to avoid collisions.
2. Use positive input counts and provide at least one valid item, fluid, or AEKey output.
3. When replacing a recipe, remove the old recipe before adding the replacement with the same `id`.
4. Use object syntax for multiple molds.
5. Only set `tier` when the recipe needs to override the server configuration; otherwise let `recipe_tier_rules` manage it.
6. Run `/reload` after changing scripts; reopening a recipe viewer alone is not enough.

## Troubleshooting

If a recipe does not appear or the machine cannot process it:

1. Check the game log for KubeJS recipe creation errors.
2. Confirm that `id` is a valid resource location in the form `namespace:path`.
3. Confirm that `id` and `ingredients` are present, and that at least one valid entry exists in `outputs`, `output_fluids`, or `key_outputs`.
4. Confirm that `mold` and `molds` are not both defined.
5. Check coil tier, molds, input counts, and catalyst requirements.
6. For ordinary fluids, use `input_fluids`/`output_fluids`; for AEKey fluids or other AE resources, use `key_inputs`/`key_outputs` and verify the `#t` type is registered.
7. Run `/reload` after editing the script and check for script exceptions.
