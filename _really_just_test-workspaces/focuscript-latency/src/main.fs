  val intervalSeconds = config.getInt("settings.logIntervalSeconds", 5)

  var totalEvents = 0L
  var totalNanos = 0L

  var joinCount = 0L
  var quitCount = 0L
  var chatCount = 0L
  var commandCount = 0L
  var blockBreakCount = 0L
  var blockPlaceCount = 0L
  var deathCount = 0L
  var damageCount = 0L

  var lastJoin = ""
  var lastQuit = ""
  var lastChat = ""
  var lastCommand = ""
  var lastBlockBreak = ""
  var lastBlockPlace = ""
  var lastDeath = ""
  var lastDamage = ""

  fun record(startNs: Long) {
    totalEvents += 1
    totalNanos += (System.nanoTime() - startNs)
  }

  events.onJoin { e ->
    val start = System.nanoTime()
    joinCount += 1
    lastJoin = e.player.name
    record(start)
  }

  events.onQuit { e ->
    val start = System.nanoTime()
    quitCount += 1
    lastQuit = e.player.name
    record(start)
  }

  events.onChat { e ->
    val start = System.nanoTime()
    chatCount += 1
    lastChat = "${e.player.name}: ${e.message}"
    record(start)
  }

  events.onCommand { e ->
    val start = System.nanoTime()
    commandCount += 1
    lastCommand = e.commandLine
    record(start)
  }

  events.onBlockBreak { e ->
    val start = System.nanoTime()
    blockBreakCount += 1
    lastBlockBreak = e.blockType
    record(start)
  }

  events.onBlockPlace { e ->
    val start = System.nanoTime()
    blockPlaceCount += 1
    lastBlockPlace = e.blockType
    record(start)
  }

  events.onDeath { e ->
    val start = System.nanoTime()
    deathCount += 1
    lastDeath = e.message
    record(start)
  }

  events.onDamage { e ->
    val start = System.nanoTime()
    damageCount += 1
    val damager = e.damagerPlayer?.name ?: e.damagerType
    lastDamage = "${e.player.name}:${e.cause}:$damager"
    record(start)
  }

  scheduler.every(intervalSeconds.seconds) {
    val events = totalEvents
    val nanos = totalNanos
    val avgNs = if (events > 0L) nanos / events else 0L

    log.info("[LatencyProbe] events=$events avgNs=$avgNs join=$joinCount quit=$quitCount chat=$chatCount cmd=$commandCount break=$blockBreakCount place=$blockPlaceCount death=$deathCount damage=$damageCount")
    log.info("[LatencyProbe] last join=$lastJoin quit=$lastQuit chat=$lastChat cmd=$lastCommand break=$lastBlockBreak place=$lastBlockPlace death=$lastDeath damage=$lastDamage")

    totalEvents = 0
    totalNanos = 0
    joinCount = 0
    quitCount = 0
    chatCount = 0
    commandCount = 0
    blockBreakCount = 0
    blockPlaceCount = 0
    deathCount = 0
    damageCount = 0
  }
