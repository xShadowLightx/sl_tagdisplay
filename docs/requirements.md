# Requirements and initial design

## Confirmed requirements

- Platform: Minecraft Java, Paper.
- Primary target: Paper 26.3 and later, with a separately built Paper 1.21.11-compatible artifact for testing.
- Use Java 25 for the 26.3+ build and Java 21 for the 1.21.11 build.
- Two independent features: headtags and gangtags.
- Each player chooses which tag is active from the tags they are allowed to use.
- LuckPerms permission nodes control access; players cannot create their own tags.
- Administrators can create, edit, and remove tags through configuration and/or an in-game admin command, with live reload and no server restart.
- Store each player's headtag and gangtag selections independently.
- Headtags display above the player in-world. Expose the active headtag as an appended PlaceholderAPI placeholder for chat formatting. Do not rewrite the server's tab list or existing LuckPerms prefix.
- Gangtags display below the player in-world. Do not insert them into chat or tab text unless a separate placeholder is explicitly configured by the server owner.
- A failure in one tag feature must not prevent the other feature from starting or operating.

## Initial design direction

- Use the standard Bukkit-compatible `plugin.yml` loader for broad Paper ecosystem compatibility; avoid Paper's experimental plugin manifest unless a specific need emerges.
- Keep the core plugin independent of optional integrations. Detect LuckPerms and PlaceholderAPI at startup and isolate their adapters so either integration can be absent without disabling the display modules.
- Define separate services for tag definitions, player selections, headtag display, gangtag display, menus, admin commands, and optional placeholders.
- Treat configuration reload as a validated replacement: parse and validate the new definitions first, then swap them in and refresh online players only when validation succeeds.
- Store player selections by UUID, separately from tag definitions. Recheck permissions when a player opens the menu, selects a tag, and joins, so lost permissions cannot leave an unauthorized tag active.
- Prefer Adventure components for rendered text and escape or reject unsupported formatting inputs in configuration.

## Decisions still needed

- Whether both server targets need full feature parity, or whether features that only exist in the 26.3 API may be omitted from the 1.21.11 testing artifact.
- Whether the server should support Paper only or also Spigot. Some desired display behavior may require Paper-specific APIs.
- Whether headtags and gangtags allow one active tag each or multiple stacked lines.
- Exact commands, permission nodes, menu layout, tag format syntax, and persistence format.
- Display implementation and visibility behavior for the chosen server versions; verify this with a small in-game prototype before expanding the feature set.

## Implementation phases

1. Pin the supported server and Java versions, then add the Gradle project and minimal plugin entry point.
2. Add validated tag configuration and UUID-based player selection storage.
3. Implement and verify headtag display independently.
4. Implement and verify gangtag display independently.
5. Add the selection menu, admin management/reload commands, LuckPerms gating, and PlaceholderAPI expansion.
6. Verify compatibility and document installation, permissions, configuration, and known limitations.
