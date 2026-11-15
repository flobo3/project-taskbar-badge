# Project Taskbar Badge

A lightweight JetBrains IDE plugin (PhpStorm, IntelliJ IDEA, WebStorm, etc.) for Windows that displays each open project's icon as a taskbar overlay badge on its window button.

## Features

- **Custom project icons:** Automatically picks up `.idea/icon_dark.png`, `.idea/icon.png`, or `.idea/icon.svg`.
- **Project-colored letter fallback:** When a project has no custom icon, optionally renders its generated letter badge in the project's header color (matching the IDE window header).
- **Settings UI:** Configure under **Settings | Tools | Project Taskbar Badge**:
  - Enable/disable the plugin
  - Toggle the project letter fallback
  - Adjust badge size (16–32 px, default `24`)
  - Changes apply immediately to all open project windows without restarting the IDE.

## Installation

Download the `.zip` from [Releases](https://github.com/flobo3/project-taskbar-badge/releases) (or JetBrains Marketplace) and install via **Settings | Plugins | ⚙️ | Install Plugin from Disk…**.

> **Note:** On Windows, disable taskbar button grouping (*Taskbar settings → Combine taskbar buttons → Never* or *When taskbar is full*) so each open project window has its own taskbar button.

## Requirements

- Windows (uses `java.awt.Taskbar` / `ITaskbarList3::SetOverlayIcon`)
- JetBrains IDE build `251+` (2025.1 or newer)

## Build from source

Requires Python 3 and a local JetBrains IDE installation (uses its bundled `jbr` and `lib/*`):

```bash
python build.py /path/to/PhpStorm
```

The installable archive is written to `dist/project-taskbar-badge-1.1.0.zip`.

## License

MIT — see [LICENSE](LICENSE).
