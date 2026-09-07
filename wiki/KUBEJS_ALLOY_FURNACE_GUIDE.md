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

The three required arguments are the recipe data ID, the input list, and the output list. An omitted `count` defaults to 1.

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

For a fluid-only input or output, keep the other required list and set it to an empty array, such as `outputs: []`.

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
        process_time: 200,
        mode: 'press'
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

## Field Description

| Field | Type | Required | Default | Description |
|---|---|---:|---:|---|
| `id` | String | Yes | - | Recipe data ID, for example `kubejs:iron_alloy` |
| `ingredients` | Object[] | Yes | - | Input list; each entry has `ingredient` and an optional `count` |
| `input_fluids` | Object[] | No | `[]` | Fluid input list; each entry has a fluid `ingredient` and `amount` |
| `outputs` | ItemStack[] | Yes | - | Output list; strings and `Item.of(...)` are supported |
| `output_fluids` | FluidStack[] | No | `[]` | Fluid output list; `Fluid.of(...)` is supported |
| `mold` | Ingredient | No | Empty | Single mold requirement; item IDs and tags are supported |
| `molds` | Ingredient[] | No | Empty | Multiple independent mold requirements; mutually exclusive with `mold` |
| `tier` | Integer | No | Unspecified | Minimum coil tier, from `0` to `10` |
| `energy` | Long | No | `2000` | Energy consumed by one operation |
| `process_time` | Integer | No | `200` | Processing time in ticks |
| `catalyst` | Ingredient | No | Empty | Catalyst requirement |
| `catalyst_uses` | Integer | No | `0` | Number of uses allowed for the catalyst |
| `mode` | String | No | `normal` | Processing mode: `normal`, `insolator`, or `press` |

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
        catalyst: 'minecraft:diamond',
        catalyst_uses: 8,
        mode: 'normal'
    })
})
```

## Extended JSON

The Schema covers item inputs, fluid inputs, item outputs, fluid outputs, molds, tiers, and common processing options. For AE chemical stacks or other fields not exposed by the Schema, use `event.custom` and provide the serializer JSON directly. The raw JSON shape for fluid fields is:

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
        outputs: [],
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
2. Use positive input counts and provide at least one valid output.
3. When replacing a recipe, remove the old recipe before adding the replacement with the same `id`.
4. Use object syntax for multiple molds.
5. Only set `tier` when the recipe needs to override the server configuration; otherwise let `recipe_tier_rules` manage it.
6. Run `/reload` after changing scripts; reopening a recipe viewer alone is not enough.

## Troubleshooting

If a recipe does not appear or the machine cannot process it:

1. Check the game log for KubeJS recipe creation errors.
2. Confirm that `id` is a valid resource location in the form `namespace:path`.
3. Confirm that `id`, `ingredients`, and `outputs` are present.
4. Confirm that `mold` and `molds` are not both defined.
5. Check coil tier, molds, input counts, and processing mode.
6. For fluids or AE chemicals, verify the JSON field names and amount types passed to `event.custom`.
7. Run `/reload` after editing the script and check for script exceptions.
