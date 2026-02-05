package kr.codename.focuscript.core;

import kr.codename.focuscript.logging.FocuscriptLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class ExampleWorkspaceSeeder {
    private final Path scriptsRoot;
    private final Path marker;
    private final FocuscriptLogger log;

    public ExampleWorkspaceSeeder(Path scriptsRoot, Path marker, FocuscriptLogger log) {
        this.scriptsRoot = scriptsRoot;
        this.marker = marker;
        this.log = log;
    }

    public void seed() {
        try {
            if (java.nio.file.Files.exists(marker)) return;

            if (!java.nio.file.Files.isDirectory(scriptsRoot)) {
                java.nio.file.Files.writeString(marker, "skipped: no scripts dir", StandardCharsets.UTF_8);
                return;
            }
            try (var stream = java.nio.file.Files.list(scriptsRoot)) {
                if (stream.findAny().isPresent()) {
                    java.nio.file.Files.writeString(marker, "skipped: existing scripts", StandardCharsets.UTF_8);
                    return; // already has scripts
                }
            }

            Path hello = scriptsRoot.resolve("hello");
            if (java.nio.file.Files.exists(hello)) {
                java.nio.file.Files.writeString(marker, "skipped: hello exists", StandardCharsets.UTF_8);
                return;
            }

            java.nio.file.Files.createDirectories(hello.resolve("src"));

            String yml = String.join("\n",
                    "id: hello",
                    "name: Hello Module",
                    "version: 1.0.0",
                    "api: 1",
                    "entry: src/main.fs",
                    "load: enable",
                    "depends: []",
                    "permissions:",
                    "  - focuscript.example.hello",
                    "  - focuscript.example.teleport",
                    "  - focuscript.example.block",
                    "commands:",
                    "  - hello",
                    "  - teleport",
                    "  - block",
                    "settings:",
                    "  welcome: \"Welcome to the server!\"",
                    "  broadcastIntervalMinutes: 3",
                    "options:",
                    "  debug: true",
                    ""
            );
            java.nio.file.Files.writeString(hello.resolve("script.yml"), yml, StandardCharsets.UTF_8);

            String fs = String.join("\n",
                    "module {",
                    "  val welcome = config.getString(\"settings.welcome\", \"Welcome!\")",
                    "  val interval = config.getInt(\"settings.broadcastIntervalMinutes\", 3)",
                    "",
                    "  log.info(\"hello module enabled\")",
                    "",
                    "  events.onJoin { e ->",
                    "    val joins = storage.getInt(\"stats.joins\", 0) + 1",
                    "    storage.setInt(\"stats.joins\", joins)",
                    "    e.player.sendText(text(welcome).color(\"green\").bold())",
                    "    e.player.sendActionBar(text(\"접속 횟수: $joins\").color(\"yellow\"))",
                    "    val again = server.getPlayer(e.player.uniqueId)",
                    "    if (again != null) {",
                    "      log.debug(\"player lookup ok: ${again.name}\")",
                    "    }",
                    "    val inv = e.player.inventory",
                    "    val held = e.player.itemInMainHand",
                    "    log.debug(\"main hand: ${held.type} x${held.amount}\")",
                    "    val gift = FsItemStack.of(\"DIAMOND\", 1)",
                    "    val meta = FsSimpleItemMeta()",
                    "    meta.displayName = text(\"Focuscript Token\").color(\"aqua\").bold()",
                    "    meta.lore = listOf(",
                    "      text(\"owner: ${e.player.name}\").color(\"gray\"),",
                    "      text(\"/block <type>\").color(\"yellow\")",
                    "    )",
                    "    meta.customModelData = 1",
                    "    gift.itemMeta = meta",
                    "    val leftovers = inv.addItem(gift)",
                    "    if (leftovers.isNotEmpty()) {",
                    "      e.player.sendText(text(\"inventory is full\").color(\"red\"))",
                    "    }",
                    "    val world = server.getWorld(e.player.location.worldName)",
                    "    val block = world?.getBlockAt(e.player.location)",
                    "    if (block != null) {",
                    "      log.debug(\"standing on: ${block.type}\")",
                    "    }",
                    "  }",
                    "",
                    "  events.onQuit { e ->",
                    "    log.info(\"quit: ${e.player.name}\")",
                    "  }",
                    "",
                    "  events.onChat { e ->",
                    "    log.debug(\"chat: ${e.player.name}: ${e.message}\")",
                    "  }",
                    "",
                    "  events.onCommand { e ->",
                    "    log.debug(\"command: ${e.commandLine}\")",
                    "  }",
                    "",
                    "  events.onBlockBreak { e ->",
                    "    log.debug(\"break: ${e.blockType} @ ${e.location.worldName}\")",
                    "  }",
                    "",
                    "  events.onBlockPlace { e ->",
                    "    log.debug(\"place: ${e.blockType} @ ${e.location.worldName}\")",
                    "  }",
                    "",
                    "  events.onDeath { e ->",
                    "    log.info(\"death: ${e.player.name} ${e.message}\")",
                    "  }",
                    "",
                    "  events.onDamage { e ->",
                    "    val damager = e.damagerPlayer?.name ?: e.damagerType",
                    "    log.debug(\"damage: ${e.player.name} <- $damager (${e.cause} ${e.damage})\")",
                    "  }",
                    "",
                    "  commands.register(\"hello\", \"focuscript.example.hello\") { ctx ->",
                    "    val sender = ctx.sender",
                    "    val argsText = if (ctx.args.isEmpty()) \"\" else ctx.args.joinToString(\", \")",
                    "    sender.sendText(text(\"hello: $argsText\").color(\"aqua\"))",
                    "    val first = ctx.args.firstOrNull()",
                    "    if (first == \"announce\") {",
                    "      server.dispatchCommand(\"say [Focuscript] hello\")",
                    "    } else if (first != null && first.isNotBlank()) {",
                    "      val target = server.getPlayer(first)",
                    "      if (target != null) {",
                    "        target.sendText(text(\"hello from ${sender.name}\").color(\"green\"))",
                    "      }",
                    "    }",
                    "    if (sender.isPlayer) {",
                    "      val p = sender.player ?: return@register",
                    "      sender.sendText(text(\"HP ${p.health}/${p.maxHealth}, food ${p.foodLevel}\").color(\"gray\"))",
                    "    }",
                    "  }",
                    "",
                    "  commands.register(\"teleport\", \"focuscript.example.teleport\") { ctx ->",
                    "    val sender = ctx.sender",
                    "    val player = sender.player ?: run {",
                    "      sender.sendText(text(\"players only\").color(\"red\"))",
                    "      return@register",
                    "    }",
                    "    if (!player.hasPermission(\"focuscript.example.teleport\")) {",
                    "      sender.sendText(text(\"no permission\").color(\"red\"))",
                    "      return@register",
                    "    }",
                    "    val loc = player.location",
                    "    player.teleport(location(loc.worldName, loc.x, loc.y + 1.0, loc.z, loc.yaw, loc.pitch))",
                    "    player.sendText(text(\"teleported up\").color(\"green\"))",
                    "  }",
                    "",
                    "  commands.register(\"block\", \"focuscript.example.block\") { ctx ->",
                    "    val sender = ctx.sender",
                    "    val player = sender.player ?: run {",
                    "      sender.sendText(text(\"players only\").color(\"red\"))",
                    "      return@register",
                    "    }",
                    "    val type = ctx.args.firstOrNull()",
                    "    if (type == null || type.isBlank()) {",
                    "      sender.sendText(text(\"usage: /block <material>\").color(\"yellow\"))",
                    "      return@register",
                    "    }",
                    "    val world = server.getWorld(player.location.worldName)",
                    "    if (world == null) {",
                    "      sender.sendText(text(\"world not found\").color(\"red\"))",
                    "      return@register",
                    "    }",
                    "    val block = world.getBlockAt(player.location)",
                    "    val before = block.type",
                    "    try {",
                    "      block.setType(type)",
                    "    } catch (e: Exception) {",
                    "      sender.sendText(text(\"invalid material: $type\").color(\"red\"))",
                    "      return@register",
                    "    }",
                    "    sender.sendText(text(\"block: $before -> ${block.type}\").color(\"green\"))",
                    "  }",
                    "",
                    "  scheduler.every(interval.minutes) {",
                    "    server.broadcast(text(\"online: ${server.onlinePlayerCount}\").color(\"gold\"))",
                    "  }",
                    "",
                    "  scheduler.after(5.seconds) {",
                    "    val names = server.onlinePlayers.joinToString(\", \") { it.name }",
                    "    log.info(\"online players: $names\")",
                    "    val worlds = server.worlds.joinToString(\", \") { it.name }",
                    "    log.info(\"worlds: $worlds\")",
                    "  }",
                    "",
                    "  onDisable {",
                    "    storage.save()",
                    "  }",
                    "}",
                    ""
            );
            java.nio.file.Files.writeString(hello.resolve("src").resolve("main.fs"), fs, StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(marker, "created", StandardCharsets.UTF_8);

            log.info("Created example workspace: scripts/hello");
        } catch (Exception e) {
            log.warn("Failed to create example workspace: " + e.getMessage());
            try {
                if (!java.nio.file.Files.exists(marker)) {
                    java.nio.file.Files.writeString(marker, "error: " + e.getClass().getSimpleName(), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
        }
    }
}
