  val welcome = config.getString("settings.welcome", "Welcome!")

  log.info("hello events enabled")

  events.onJoin { e ->
    val joins = storage.getInt("stats.joins", 0) + 1
    storage.setInt("stats.joins", joins)
    e.player.sendText(text(welcome).color("green").bold())
    e.player.sendActionBar(text("접속 횟수: $joins").color("yellow"))
    val again = server.getPlayer(e.player.uniqueId)
    if (again != null) {
      log.debug("player lookup ok: ${again.name}")
    }
  }

  events.onQuit { e ->
    log.info("quit: ${e.player.name}")
  }

  events.onChat { e ->
    log.debug("chat: ${e.player.name}: ${e.message}")
  }

  events.onCommand { e ->
    log.debug("command: ${e.commandLine}")
  }

  events.onBlockBreak { e ->
    log.debug("break: ${e.blockType} @ ${e.location.worldName}")
  }

  events.onBlockPlace { e ->
    log.debug("place: ${e.blockType} @ ${e.location.worldName}")
  }

  events.onDeath { e ->
    log.info("death: ${e.player.name} ${e.message}")
  }

  events.onDamage { e ->
    val damager = e.damagerPlayer?.name ?: e.damagerType
    log.debug("damage: ${e.player.name} <- $damager (${e.cause} ${e.damage})")
  }
