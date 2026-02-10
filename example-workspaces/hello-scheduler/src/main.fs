  val interval = config.getInt("settings.broadcastIntervalMinutes", 3)
  val messages = config.getStringList("settings.announcements")

  scheduler.every(interval.minutes) {
    val count = server.onlinePlayerCount
    server.broadcast(text("online: $count").color("gold"))

    if (messages.isNotEmpty()) {
      val idx = storage.getInt("announce.index", 0)
      val message = messages[idx % messages.size]
      storage.setInt("announce.index", idx + 1)
      server.broadcast(text(message).color("yellow"))
    }
  }

  scheduler.after(5.seconds) {
    val names = server.onlinePlayers.joinToString(", ") { it.name }
    log.info("online players: $names")
  }

  onDisable {
    storage.save()
  }
