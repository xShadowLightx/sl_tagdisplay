# SL Tag Display <>!

SL Tag Display is a Minecraft Java plugin for Paper servers. It is designed to let each player choose a headtag and a gangtag from tags they have permission to use, then show each tag in its own place around the player's name.

The project is in early development. This README describes the intended behavior; display rendering, menus, saved player choices, and integrations are not implemented yet. We’ll keep this page up to date as those features are built.

## What the plugin will do

### Headtags

- Show the player's selected headtag above their name in the world.
- Provide an appended PlaceholderAPI placeholder for server owners to use in chat formats.
- Leave the server's existing chat format and tab list alone. In particular, the plugin will not replace or reorder LuckPerms prefixes or TAB formatting. The headtag appears in chat only when a server owner adds its placeholder to their chat format.

### Gangtags

- Show the player's selected gangtag below their name in the world.
- Keep the gangtag out of chat and tab list formatting by default.
- Any later gangtag placeholder will be opt-in: it will have no effect unless a server owner explicitly adds it to a format.

### Player choices and permissions

- Players choose their own active headtag and gangtag from in-game menus.
- Permission nodes determine which tags each player may select. LuckPerms can manage those permissions, including through the server's existing group and Discord-role setup.
- Players cannot create or edit tag definitions.
- A player's headtag and gangtag choices are saved separately.

### Administration and reliability

- Administrators can create, edit, and remove tag definitions in configuration or with in-game admin commands.
- Administrators can reload tag configuration without restarting the server.
- The headtag and gangtag systems are separate modules. An issue in one should not disable the other.

## Compatibility targets

The project will produce two builds from one codebase:

| Build | Server target | Java |
| --- | --- | --- |
| Main | Paper 26.3 and later 26.x releases | 25 |
| Compatibility | Paper 1.21.11 | 21 |

The 1.21.11 build is for testing the plugin on that server version. The builds use matching Paper API dependencies and `api-version` metadata. Shared code will be kept compatible with the 1.21.11 API; version-specific behavior can be isolated in adapters.

## Optional integrations

- **LuckPerms:** provides the permission groups and nodes used to grant access to tags. The plugin will use normal server permissions so tag access can also be managed by another compatible permissions plugin.
- **PlaceholderAPI:** provides the headtag placeholder for chat-format plugins. PlaceholderAPI is optional; headtag and gangtag display should work without it.
- **TAB:** the plugin will not modify TAB's player list formatting.

## Build

The project uses Gradle and Paper's standard Bukkit-compatible `plugin.yml` format.

```text
gradle build -PpaperTarget=26.3
gradle build -PpaperTarget=1.21.11
```

The output JAR filename identifies its target. The Paper version can be changed with `paperTarget`; use a supported 26.x release for the main build, or `1.21.11` for the compatibility build.

## Current implementation status

- Project scaffold and build targets: added, not yet built or verified.
- Tag configuration examples and plugin metadata: added.
- Headtag and gangtag rendering: planned.
- Player selection menus and saved choices: planned.
- Permission checks, admin management, and live reload: planned.
- PlaceholderAPI expansion: planned.

See [`docs/requirements.md`](docs/requirements.md) for the fuller requirements and design notes.
