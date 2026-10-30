# Useless Mod

A Minecraft mod featuring various utilities and enhancements.

## Forge 1.20.1

Version `20.2.4.11` targets Minecraft 1.20.1, Forge 47.3.10+, Java 17,
AE2 15.4.8+, Mekanism 10.4.15+ (compiled against 10.4.16.80),
Mekanism Extras 1.5.0+ and the Create 2 Mekanism: Sky Edition 2 integration profile.
Build with `./gradlew build` (Windows: `gradlew.bat build`); Python 3 is used by
release-bytecode and outline regression checks. Dependencies resolve from public Maven repositories.
The `portBridgeTest` suite also runs as part of `check` and is not shipped in the mod jar.

The EMI/JEMI bridge preserves custom AE keys and long quantities when filling native
processing patterns, including Applied Mekanistics chemicals. Native JEI types and
current quantities are retained for chemical tooltips and ghost dragging.

Compact C9/F9 host panels report their built-in level-nine hardware through the
native ECO artwork. Creative-mode breaking preserves compact C9/F9/L9 machine
data through the normal loot path. Compact F9 shares ECO's native coolant input
tank across all eight hosts while retaining the 128,000 mB input limit and existing
fluid NBT.

Forge, Minecraft and AE2 are required. Every compiled mod integration declares a
minimum and compatible version range in `src/main/resources/META-INF/mods.toml`;
these integrations remain optional, so an absent integration does not prevent startup.
Runtime minima and compile pins are separate: for example, Mekanism accepts
`10.4.15+` while this build compiles against `10.4.16.80`. Botany Pots is accepted
from `13.0.0`; Mekanism Extras starts at `1.5.0` and uses its plural
`registries`/`genregistries` API for the Naquadah reactor adapter. Ore Generator
samples accept Forge's `ores` and `raw_materials` item tags, plus the equivalent
`c:` datapack tags.

Optional integrations without a Forge implementation are excluded explicitly in
`gradle/forge1201-pack-profile.json`; this is not a universal backport of every
NeoForge integration. Extended Crafting compiles against official 6.0.10; the
pack's unofficial tier-5/11x11 extension is supported through its native recipe
contract and registry lookup without bundling or replacing the pack's jar.
Missing optional adapter classes are skipped quietly at debug level instead of
failing the interaction; actual invocation failures are reported once. The
Occultism spirit-egg handler is one such excluded adapter in this Forge build.

## Features

- Endless Beaf Tool with multiple modes and functionalities
- Enhanced Mekanism upgrades and integrations
- Thermal mod integrations and enhancements
- Various utilities and tools

## Wiki

- [KubeJS 万象合金炉配方指南](wiki/KUBEJS_ALLOY_FURNACE_GUIDE_CN.md)
- [KubeJS Advanced Alloy Furnace Recipe Guide](wiki/KUBEJS_ALLOY_FURNACE_GUIDE.md)
- [万象合金炉 BigInteger 发配 API 使用指南](wiki/ALLOY_FURNACE_BIGINT_API_ZH_CN.md)
- [Multiblock Alloy Furnace BigInteger Dispatch API Guide](wiki/ALLOY_FURNACE_BIGINT_API.md)

## License

The original Useless Mod code is licensed under the MIT License. Third-party
adaptations remain under their respective licenses described below.

### Third-party Code

This mod includes code from several open-source projects under their respective licenses:

- **Mekanism** - Copyright (c) 2017-2025 Aidan C. Brady
- **Mekanism Upgrades: Reborn** - Copyright (c) Y_Xiao233
- **PersonalSpace** - Portions of the configurable boundary, road, and
  center-marker generation model are adapted from
  [GT New Horizons/PersonalSpace](https://github.com/GTNewHorizons/PersonalSpace),
  licensed under the GNU LGPL v3.0. The Useless Mod interface, persistence,
  networking, and platform-specific extensions are original code. See the
  scoped notices in the affected generator/configuration files and
  `LICENSES/PersonalSpace-LGPL-3.0.txt`.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.
