# KubeJS 万象合金炉配方指南

<p>
    <a href="KUBEJS_ALLOY_FURNACE_GUIDE.md">English</a> |
    <a href="KUBEJS_ALLOY_FURNACE_GUIDE_CN.md">简体中文</a>
</p>

## 概述

万象合金炉支持通过 KubeJS 在服务器配方加载阶段添加、替换和删除配方。脚本文件放在 `kubejs/server/` 下，修改后执行 `/reload` 生效。

本联动注册的配方类型为 `useless_mod:advanced_alloy_furnace`，对应脚本入口为：

```javascript
event.recipes.useless_mod.advanced_alloy_furnace(...)
```

需要安装适用于 NeoForge 1.21.1 的 KubeJS。没有安装 KubeJS 时，Useless Mod 仍然可以正常运行。

## 目录

- [基础用法](#基础用法)
- [字段说明](#字段说明)
- [配方等级](#配方等级)
- [替换和删除配方](#替换和删除配方)
- [完整示例](#完整示例)
- [扩展 JSON](#扩展-json)
- [最佳实践](#最佳实践)
- [排错](#排错)

## 基础用法

### 最简单的配方

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

三个必填参数依次为：配方数据 ID、输入物列表、输出物列表。`count` 省略时，输入数量默认为 1。

### 同时使用物品和流体

物品和流体使用不同字段声明：物品输入使用 `ingredients`，流体输入使用 `input_fluids`；物品输出使用 `outputs`，流体输出使用 `output_fluids`。同一个配方可以同时包含物品和流体输入、输出：

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

如果配方只有流体输入或输出，仍需保留另一个必填列表，并将其设为空数组，例如 `outputs: []`。

### 添加模具和等级

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

位置调用的第四个参数是单个 `mold`，第五个参数是 `tier`。上面的配方要求线圈等级至少为 3。

### 对象写法

对象写法适合同时指定等级、模具和其他可选字段，也可以在没有模具时直接指定 `tier`：

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

### 多个模具

使用 `molds` 可以声明多个独立的模具要求：

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

`mold` 和 `molds` 不能同时填写。多个模具由万象炉的模具仓/模具中心处理；普通单模具槽机器不支持多个模具要求。

## 字段说明

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `id` | String | 是 | - | 配方数据 ID，例如 `kubejs:iron_alloy` |
| `ingredients` | Object[] | 是 | - | 输入物列表，每项包含 `ingredient` 和可选的 `count` |
| `input_fluids` | Object[] | 否 | `[]` | 流体输入列表，每项包含流体 `ingredient` 和 `amount` |
| `outputs` | ItemStack[] | 是 | - | 输出物列表，可使用字符串或 `Item.of(...)` |
| `output_fluids` | FluidStack[] | 否 | `[]` | 流体输出列表，可使用 `Fluid.of(...)` |
| `mold` | Ingredient | 否 | 空 | 单个模具要求，可使用物品 ID 或标签 |
| `molds` | Ingredient[] | 否 | 空 | 多个独立模具要求，不能与 `mold` 同时使用 |
| `tier` | Integer | 否 | 未指定 | 线圈最低等级，范围为 `0-10` |
| `energy` | Long | 否 | `2000` | 一次处理消耗的能量 |
| `process_time` | Integer | 否 | `200` | 处理时间，单位为 tick |
| `catalyst` | Ingredient | 否 | 空 | 催化剂要求 |
| `catalyst_uses` | Integer | 否 | `0` | 催化剂可使用次数 |
| `mode` | String | 否 | `normal` | 工作模式：`normal`、`insolator` 或 `press` |

### 物品输入格式

```javascript
ingredients: [
    { ingredient: 'minecraft:iron_ingot', count: 4 },
    { ingredient: '#c:ingots/copper', count: 2 },
    { ingredient: 'minecraft:diamond' }
]
```

`ingredient` 使用 KubeJS 标准 Ingredient 写法，可以是物品 ID、标签或其他受 KubeJS 支持的 Ingredient 表达式。每种输入物会分别检查数量。

### 流体输入格式

```javascript
input_fluids: [
    { ingredient: 'minecraft:water', amount: 1000 },
    { ingredient: '#c:lava', amount: 250 }
]
```

`amount` 的单位是 mB，支持较大的整数。`ingredient` 可以使用流体 ID、流体标签或 KubeJS 支持的流体 Ingredient 表达式。

### 输出物格式

```javascript
outputs: [
    Item.of('minecraft:diamond', 2),
    Item.of('kubejs:alloy', 1, { display: { Name: 'Custom alloy' } })
]
```

也可以直接使用物品字符串：

```javascript
outputs: [ 'minecraft:iron_ingot' ]
```

### 流体输出格式

```javascript
output_fluids: [
    Fluid.of('minecraft:water', 1000),
    Fluid.of('minecraft:lava', 250)
]
```

## 配方等级

`tier` 表示配方要求的万象炉线圈等级，范围为 `0-10`。

- 填写 `tier: 3` 时，配方最低需要 3 级线圈。
- 填写 `tier: 0` 时，配方明确表示不限制等级。
- 不填写 `tier` 时，使用服务器配置中的 `recipe_tier_rules`。
- `recipe_tier_rules` 的格式为 `配方 ID 通配符,等级`，例如 `kubejs:*,3`。
- 配方中显式填写的 `tier` 优先于 `recipe_tier_rules`。

示例配置：

```toml
recipe_tier_rules = [
    "useless_mod:advanced_alloy/gear/*,4",
    "kubejs:*,2"
]
```

配置只对未显式指定 `tier` 的配方生效。修改配置后需要重新加载配置或重启游戏，具体取决于配置界面提示。

## 替换和删除配方

### 删除指定配方

```javascript
ServerEvents.recipes(event => {
    event.remove({ id: 'useless_mod:advanced_alloy/gear/useless_gear_tier_1' })
})
```

### 按类型删除

```javascript
ServerEvents.recipes(event => {
    event.remove({ type: 'useless_mod:advanced_alloy_furnace' })
})
```

按类型删除会移除所有万象合金炉配方，使用时需要谨慎。

### 替换指定配方

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

## 完整示例

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

## 扩展 JSON

当前 Schema 已覆盖物品输入、流体输入、物品输出、流体输出、模具、等级和常用处理参数。需要 AE 化学物或其他尚未暴露的字段时，可以用 `event.custom` 直接提交配方 JSON。流体字段的原始 JSON 形状如下：

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

`event.custom` 不会经过本 Schema 的参数转换，字段名和 JSON 类型必须符合万象合金炉配方序列化器的定义。

## 最佳实践

1. 使用自己的模组 ID 或 `kubejs:` 作为配方 ID 前缀，避免覆盖其他配方。
2. 输入物数量使用正数，并确认输出列表至少包含一个有效物品。
3. 替换配方时先删除旧配方，再使用相同的 `id` 添加新配方。
4. 多模具配方使用对象写法，避免依赖位置参数。
5. 只有需要覆盖配置等级时才填写 `tier`；不填写可以继续由服务器配置统一管理。
6. 添加或修改脚本后执行 `/reload`，不要只重新打开配方查看器。

## 排错

如果配方没有出现或机器无法处理：

1. 检查游戏日志中是否有 KubeJS 配方创建错误。
2. 确认 `id` 是合法的资源位置格式：`namespace:path`。
3. 确认 `ingredients`、`outputs` 和 `id` 都已填写。
4. 确认没有同时填写 `mold` 和 `molds`。
5. 检查线圈等级、模具、输入数量和处理模式是否满足配方要求。
6. 使用流体或 AE 化学物时，检查 `event.custom` 中的 JSON 字段名称和数量类型。
7. 修改脚本后执行 `/reload`，并确认日志中没有脚本异常。
