# Changelog

## 1.0.0 - 2026-11-25
First public release.

- **Magic Chests:** craftable chest (owner, trust list, glow and particles, explosion-proof, only joins Magic Chests of the same owner), `/magicchests trust|untrust|trusted|list|info|give|reload`, optional per-player limit with `wintermark.magicchests.limit.<n>`.
- **Crafting table:** the vanilla recipe book fills the grid from Magic Chests within range (shift = up to a stack, clicking again adds a set). Sneak + right-click opens the Storage Crafting menu (categories, search, craft 1 / craft a stack, crafting remainders returned).
- **Furnace, smoker, blast furnace:** "Smelt from storage" menu; the furnace keeps loading the item and fuel from your chests (configurable fuels incl. item tags), finished items go back into a Magic Chest, done / out of items / no fuel messages, jobs survive restarts.
- **Stonecutter:** load a stack of a material from storage; leftovers go back into a Magic Chest.
- **Smithing table:** netherite upgrades with the template, item and ingot loaded from your inventory or chests (enchanted gear keeps its enchantments).
- **/storage:** all Magic Chest contents in one menu: take a stack / 1 / as many as fit, search, sort, shift-click to store, store your whole inventory.
- Renamed, enchanted and plugin items in chests are never used as crafting ingredients (except the gear you upgrade at the smithing table, which keeps its enchantments and name); items always leave the chest before they go anywhere else.
- SQLite storage, no dependencies, everything configurable with MiniMessage.
