# Ego-System

<div align="center">

![Ego-System](https://img.shields.io/badge/Ego--System-Automation-E84C3D?style=for-the-badge&logo=minecraft&logoColor=white)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://jdk.java.net/21/)
[![Spigot](https://img.shields.io/badge/Spigot-1.21+-F7CF0C?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/)

**Automated Server Management & Plugin Updates** 🛠️
*Smooth, Silent, and Smart.*

[Features](#features) • [Installation](#installation) • [Commands](#commands) • [Configuration](#configuration)

</div>

---

## Features

### 🔄 Intelligent Auto-Update
Automatically detects, downloads, and updates plugins from multiple sources.
*   **Smart Source Detection**: Automatically identifies if a plugin is from **Modrinth**, **GitHub**, or a direct URL.
*   **Asynchronous Processing**: All checks and downloads happen in the background, ensuring **zero lag** for players.
*   **Integrity Safety**: Verifies downloads before applying.
*   **Schedule System**: Updates run on a customizable CRON schedule (default: 12:00 UTC+7).

### 📦 Local Backup
*   **World & Plugin Backup**: Secure your server data with scheduled local backups.
*   **Optimization**: Compresses backups to save space and auto-deletes old files.

### ⚡ Performance Focused
*   **OOP & SOLID**: Built with clean, maintainable architecture.
*   **Parallel Downloads**: Uses Java's `CompletableFuture` for concurrent non-blocking operations.
*   **Configurable**: Every aspect is tweakable in `config.yml`.

---

## Installation

1.  **Download**: Get the latest `Ego-System.jar` from releases.
2.  **Install**: Drop it into `plugins/`.
3.  **Restart**: Start the server.
4.  **Configure**:
    *   `config.yml`: Main settings.
    *   `list.yml`: Add your plugins and their update links here.

---

## Commands

Main Command: `/ssm` (Aliases: `/egosystem`, `/es`)

| Command | Description | Permission |
|---------|-------------|------------|
| `/ssm backup now` | Trigger an immediate local backup | `serverauto.backup` |
| `/ssm backup list` | List available backups | `serverauto.backup` |
| `/ssm update check` | Force check for plugin updates | `serverauto.update` |
| `/ssm update list` | Show pending updates | `serverauto.update` |
| `/ssm update download <plugin>` | Manually download a specific update | `serverauto.update` |
| `/ssm reload` | Reload configuration | `serverauto.admin` |

---

## Configuration

### list.yml
Map your plugins to their source URLs.
```yaml
EssentialsX: "https://modrinth.com/plugin/essentialsx"
EgoSMP: "https://github.com/ego-smp-labs/EgoSMP-Plugin"
```

### config.yml (Updates)
```yaml
updates:
  interval: 120 # Minutes
  schedule:
    cron: ['12:00']
    timezone: 'UTC+7'
performance:
  maxParallel: 4
```

---

## Development

Built with Gradle.
```bash
./gradlew build
```

**Repository**: [https://github.com/ego-smp-labs/Ego-System](https://github.com/ego-smp-labs/Ego-System)

Copyright © 2026 **NirussVn0** & Ego-SMP Labs.
