# 许可证说明

本仓库的代码与美术资源采用两套不同的授权：

| 范围 | 路径 | 授权 |
| --- | --- | --- |
| 代码与行为性数据 | `src/main/java/`、`build.gradle`、`settings.gradle`、`gradle.properties`、`src/main/resources/data/` | MIT，见根目录 `LICENSE` |
| 美术资源与内置资源包 | `src/main/resources/assets/`、`src/main/resources/xia/` | All Rights Reserved，见 `Assets-All-Rights-Reserved.txt` |
| 第三方代码 | 见各文件内注释 | 各自原许可证，见下表 |

## 第三方许可证文件

| 文件 | 涉及内容 |
| --- | --- |
| `PersonalSpace-LGPL-3.0.txt` | 可配置边界、道路、中心标记生成模型，改编自 GT New Horizons/PersonalSpace（LGPL-3.0） |

## 新增文件时的判定

- 新增 Java 类、构建脚本、配方/标签/战利品表 → 归 MIT 范围，无需额外操作。
- 新增贴图、模型、音效、图标 → 自动落入 ARR 范围，无需额外操作。
- 引入外部代码或资源 → 在此表和根目录 `README.md` 的 License 段落登记来源与许可证，许可证全文放入本目录。