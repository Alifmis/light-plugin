# LightPlugin

A Paper/Folia plugin that adds a survival-mode recipe for vanilla Light blocks — place a Glass block in the center of a crafting table with Torches around it.

## Recipe

```
[   T   ]          T = Torch
[   G   ]          G = Glass block
[       ]          (any arrangement, as long as glass is in the middle)
```

- **Center slot:** 1+ Glass block
- **Surrounding slots:** 0–8 Torches (any arrangement, remaining slots empty)
- **Result:** 1 Light block per craft

### Example patterns

**Light level 15 (8 torches):**
```
TTT
TGT
TTT
```

**Light level 1 (1 torch, any edge):**
```
T..
.G.
...
```

## Features

- Fully vanilla-accurate crafting behavior:
  - **Left/Right-click**: crafts 1 Light block to your cursor (or stacks with cursor item)
  - **Shift-click**: crafts as many as fit in your inventory; excess stays in the grid (vanilla behavior, no item loss)
- Stacked ingredients are fully supported — 64 glass + 64 torches works like any vanilla recipe
- **Crafter (redstone) support**: works with the 1.21 Crafter block
  - Place glass + torches in the crafter, trigger with redstone, and it ejects the correct Light block
  - The position check (glass in center, torches around) is enforced for both workbench and crafter
- Folia-supported

## Requirements

- Paper 1.21.11+ (or any Paper fork; Folia also supported)
- No dependencies — it's a single Java file

## Installation

1. Download the JAR from [releases](../../releases) or build from source
2. Drop `light-plugin-1.0-SNAPSHOT.jar` into your server's `plugins/` folder
3. Restart the server (or `/reload` — though we recommend a restart)

No configuration needed. The plugin has no commands or permissions.

## Building

```bash
git clone <repo-url>
cd light-plugin
./gradlew build
```

The JAR is at `build/libs/light-plugin-1.0-SNAPSHOT.jar`.

## How it works

Light blocks exist in vanilla Minecraft but are unobtainable in survival. This plugin uses three mechanisms to make them craftable:

### Workbench (manual crafting)

1. **`PrepareItemCraftEvent`** — validates the grid pattern (glass in center, torches in surrounding slots) and sets the result preview to a Light block with the appropriate light level.

2. **`InventoryClickEvent`** (LOWEST priority) — intercepts clicks on the result slot **before** CraftItemEvent fires. Cancels the default event and handles the craft manually. Left/right-click crafts 1 to cursor; shift-click crafts as many as fit in inventory, leaving excess in the grid.

### Crafter (redstone auto-crafting)

3. **`CrafterCraftEvent`** — fires when a Crafter is about to auto-craft. Validates the position (center glass, torches around) and overrides the result to the correct light level. Also registers 8 `ShapelessRecipe` recipes (1–8 torches × 1 glass) so the crafter's recipe matching can find our pattern.

### Light levels

The light level is stored as `BlockData` on the Light item, which the game reads when the block is placed. Each torch count maps to level `2×N−1`:

| Torches | Formula  | Light level |
|---------|----------|-------------|
| 1       | 2×1 − 1  | 1           |
| 2       | 2×2 − 1  | 3           |
| 3       | 2×3 − 1  | 5           |
| 4       | 2×4 − 1  | 7           |
| 5       | 2×5 − 1  | 9           |
| 6       | 2×6 − 1  | 11          |
| 7       | 2×7 − 1  | 13          |
| 8       | 2×8 − 1  | 15          |

## Project structure

```
light-plugin/
├── build.gradle              # Gradle build (Paper API 1.21.11)
├── src/main/
│   ├── java/org/alifmis/lightPlugin/
│   │   └── LightPlugin.java  # Single-file plugin (176 lines)
│   └── resources/
│       └── plugin.yml        # Plugin metadata (folia-supported: true)
└── README.md
```
