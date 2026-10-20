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
- [输入输出组合](#输入输出组合)
- [AEKey 输入和输出](#aekey-输入和输出)
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

位置调用依次接收配方数据 ID、物品输入列表和物品输出列表。`count` 省略时，输入数量默认为 1。只有流体输出时，请使用对象写法并省略 `outputs`。

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

如果配方只有流体输入或输出，请使用对象写法并省略未使用的物品列表。`ingredients` 本身仍是 Schema 的必填字段；没有物品输入时写成 `ingredients: []` 即可。

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

### 五芒星模具（必须带组件）

`mold` / `molds` 是标准 Ingredient：**只写物品 ID 或标签时不包含任何 NBT/组件**，会退化为“任意仪式蓝图都满足”。要绑定特定的神秘学五芒星，必须带上 `useless_mod:ritual_blueprint_pentacle` 组件：

```javascript
ServerEvents.recipes(event => {
    event.recipes.useless_mod.advanced_alloy_furnace({
        id: 'kubejs:foliot_miner',
        ingredients: [
            { ingredient: 'occultism:book_of_binding_bound_foliot', count: 1 },
            { ingredient: 'occultism:iesnium_pickaxe', count: 1 },
            { ingredient: 'occultism:magic_lamp_empty', count: 1 }
        ],
        outputs: [Item.of('occultism:miner_foliot_unspecialized', 1)],
        mold: Item.of('useless_mod:ritual_blueprint', {
            'useless_mod:ritual_blueprint_pentacle': ['occultism:craft_foliot']
        }),
        tier: 0
    })
})
```

等价的 SNBT 字符串写法：

```javascript
mold: 'useless_mod:ritual_blueprint{useless_mod:ritual_blueprint_pentacle:["occultism:craft_foliot"]}'
```

`molds` 的每一项目同样需要带组件，可以与普通模具混用：

```javascript
molds: [
    Item.of('useless_mod:ritual_blueprint', {
        'useless_mod:ritual_blueprint_pentacle': ['occultism:craft_foliot']
    }),
    'useless_mod:metal_mold_gear'
]
```

> 匹配时只要玩家蓝图上的五芒星**包含**配方要求的五芒星即算满足，所以一张刻印了多个五芒星的蓝图可以同时满足多条配方。

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
        process_time: 200
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

## 输入输出组合

配方支持以下物品/流体输入和输出组合：

| 测试 ID 后缀 | 输入 | 输出 |
|---|---|---|
| `item_to_item` | 物品 | 物品 |
| `item_to_fluid` | 物品 | 流体 |
| `item_fluid_to_item` | 物品 + 流体 | 物品 |
| `item_fluid_to_fluid` | 物品 + 流体 | 流体 |
| `item_to_item_fluid` | 物品 | 物品 + 流体 |
| `item_fluid_to_item_fluid` | 物品 + 流体 | 物品 + 流体 |
| `fluid_to_item` | 流体 | 物品 |
| `fluid_to_fluid` | 流体 | 流体 |
| `fluid_to_item_fluid` | 流体 | 物品 + 流体 |

例如，同时使用物品和流体输入，并同时输出物品和流体：

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

如果测试配方没有物品输入，`ingredients` 仍然是必填字段，应明确写成空数组：

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

模具要求可以分别包含 1、2、3 个独立条目：

```javascript
// 1 个模具
molds: [{ item: 'minecraft:stick' }]

// 2 个模具
molds: [
    { item: 'minecraft:stick' },
    { item: 'minecraft:stone' }
]

// 3 个模具
molds: [
    { item: 'minecraft:stick' },
    { item: 'minecraft:stone' },
    { item: 'minecraft:cobblestone' }
]
```

每个模具条目都是独立要求。一个配方中不要同时使用 `mold` 和 `molds`。

## AEKey 输入和输出

Schema 通过 `key_inputs` 和 `key_outputs` 暴露 AE2 通用堆栈。每一项都是一个 `GenericStack`：`#t` 选择已注册的 AEKey 类型，`#` 表示数量。因此不需要在本模组中硬编码其他模组的 key 类。

以 Data Energistics 数据流为例，需要安装并加载 Data Energistics，确保配方解析前已经注册 `data_energistics:data_flow`：

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

`data_energistics:data_flow` 是 AEKey，不是物品，也不是普通流体，所以数量必须写在 `key_inputs` 或 `key_outputs` 中。配方没有物品输入时仍需写 `ingredients: []`；如果 `key_outputs` 已经提供输出，普通的 `outputs` 和 `output_fluids` 可以省略。

AE2 物品和流体 key 使用相同格式：

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

普通机器流体口使用 `input_fluids` 和 `output_fluids`；当流体需要作为 AE 网络中的 AEKey 传输时，才在 `key_inputs` 和 `key_outputs` 中使用 `ae2:f`。

## 字段说明

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `id` | String | 是 | - | 配方数据 ID，例如 `kubejs:iron_alloy` |
| `ingredients` | Object[] | 是 | - | 输入物列表，每项包含 `ingredient` 和可选的 `count` |
| `input_fluids` | Object[] | 否 | `[]` | 流体输入列表，每项包含流体 `ingredient` 和 `amount` |
| `key_inputs` | GenericStack[] | 否 | `[]` | AEKey 输入列表，每项使用 AE2 通用堆栈格式 |
| `outputs` | ItemStack[] | 否 | `[]` | 输出物列表，可使用字符串或 `Item.of(...)` |
| `output_fluids` | FluidStack[] | 否 | `[]` | 流体输出列表，可使用 `Fluid.of(...)` |
| `key_outputs` | GenericStack[] | 否 | `[]` | AEKey 输出列表，每项使用 AE2 通用堆栈格式 |
| `mold` | Ingredient | 否 | 空 | 单个模具要求：物品 ID、标签或带组件的 `Item.of(...)`；绑定五芒星必须带 `useless_mod:ritual_blueprint_pentacle` |
| `molds` | Ingredient[] | 否 | 空 | 多个独立模具要求，不能与 `mold` 同时使用；每项同样支持组件 |
| `tier` | Integer | 否 | 未指定 | 线圈最低等级，范围为 `0-10` |
| `energy` | Long | 否 | `2000` | 一次处理消耗的能量 |
| `process_time` | Integer | 否 | `200` | 处理时间，单位为 tick |
| `catalyst` | Ingredient | 否 | 空 | 催化剂要求；当前万象合金炉运行逻辑不会消耗催化剂 |

### 物品输入格式

```javascript
ingredients: [
    { ingredient: 'minecraft:iron_ingot', count: 4 },
    { ingredient: '#c:ingots/copper', count: 2 },
    { ingredient: 'minecraft:diamond' }
]
```

`ingredient` 使用 KubeJS 标准 Ingredient 写法，可以是物品 ID、标签或其他受 KubeJS 支持的 Ingredient 表达式。每种输入物会分别检查数量。

需要带 NBT/组件的输入时，必须使用 `Item.of(...)` 或 SNBT 字符串形式：

```javascript
ingredients: [
    // 带组件：只要求这些组件存在且值相同，其它组件可以额外存在
    { ingredient: Item.of('occultism:book_of_binding_bound_foliot', {
        'occultism:spirit_name': 'Some Name'
    }), count: 1 },
    // 等价的 SNBT 字符串写法
    { ingredient: 'occultism:book_of_binding_bound_foliot{occultism:spirit_name:"Some Name"}', count: 1 },
    // 不带组件：任意该物品都满足
    { ingredient: 'minecraft:iron_ingot', count: 4 }
]
```

> 只写物品 ID（例如 `'occultism:book_of_binding_bound_foliot'`）**不会**保留 NBT，任何该物品都会匹配；模具同理。需要精确匹配时请带上组件。

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

物品输出不是必填项，因此配方也可以只产出流体：

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

> ⚠️ 上面示例里的 `kubejs:*,2` 会让**所有** `kubejs:` 开头的配方都要求至少 2 级线圈。低阶线圈的机器上，这些配方在 JEI 里看得见但机器不会启动——排查“配方能看见却不工作”时请先检查 `recipe_tier_rules`。

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
        catalyst: 'minecraft:diamond'
    })
})
```

## 扩展 JSON

当前 Schema 已覆盖物品输入、流体输入、AEKey 输入、物品输出、AEKey 输出、流体输出、模具、等级和常用处理参数。需要其他尚未暴露的字段时，可以用 `event.custom` 直接提交配方 JSON。流体字段的原始 JSON 形状如下：

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

`event.custom` 不会经过本 Schema 的参数转换，字段名和 JSON 类型必须符合万象合金炉配方序列化器的定义。

## 最佳实践

1. 使用自己的模组 ID 或 `kubejs:` 作为配方 ID 前缀，避免覆盖其他配方。
2. 输入物数量使用正数，并确认 `outputs`、`output_fluids` 或 `key_outputs` 中至少有一个有效输出。
3. 替换配方时先删除旧配方，再使用相同的 `id` 添加新配方。
4. 多模具配方使用对象写法，避免依赖位置参数。
5. 只有需要覆盖配置等级时才填写 `tier`；不填写可以继续由服务器配置统一管理。
6. 添加或修改脚本后执行 `/reload`，不要只重新打开配方查看器。

## 排错

如果配方没有出现或机器无法处理：

1. 检查游戏日志中是否有 KubeJS 配方创建错误。
2. 确认 `id` 是合法的资源位置格式：`namespace:path`。
3. 确认 `ingredients` 和 `id` 已填写，并确认 `outputs`、`output_fluids` 或 `key_outputs` 中至少有一个有效输出。
4. 确认没有同时填写 `mold` 和 `molds`。
5. 检查线圈等级、模具、输入数量和催化剂要求是否满足配方条件。
6. 普通机器流体使用 `input_fluids`/`output_fluids`；AEKey 流体或其他 AE 资源使用 `key_inputs`/`key_outputs`，并检查 `#t` 对应的 key 类型已注册。
7. 修改脚本后执行 `/reload`，并确认日志中没有脚本异常。
8. `mold`/`molds` 只写了物品 ID 而没有组件时会退化为“任意蓝图”，可能与其它五芒星配方互相冲突；绑定五芒星请按[五芒星模具](#五芒星模具必须带组件)带上组件。
9. 用 `occultism_kubejs` 重加神秘学仪式时注意：`event.recipes.occultism.ritual(...)` 会把 `ritual_type` 默认写成 `occultism:craft`（原版矿工仪式是 `occultism:craft_miner_spirit`），并且配方 ID 会变成你自己的命名空间（例如 `cdp2:recipes/occultism/ritual/...`）。本模组按“输出物品是否为矿工之灵 / `ritual_type`”语义识别矿工仪式，**不依赖配方 ID**，因此重加后矿工之灵仍可正常转化。
