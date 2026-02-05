module {
  log.info("hello commands enabled")

  commands.register("hello", "focuscript.example.hello") { ctx ->
    val sender = ctx.sender
    val argsText = if (ctx.args.isEmpty()) "" else ctx.args.joinToString(", ")
    sender.sendText(text("hello: $argsText").color("aqua"))
    val first = ctx.args.firstOrNull()
    if (first == "announce") {
      server.dispatchCommand("say [Focuscript] hello")
    } else if (first != null && first.isNotBlank()) {
      val target = server.getPlayer(first)
      if (target != null) {
        target.sendText(text("hello from ${sender.name}").color("green"))
      }
    }
    if (sender.isPlayer) {
      val p = sender.player ?: return@register
      sender.sendText(text("HP ${p.health}/${p.maxHealth}, food ${p.foodLevel}").color("gray"))
    }
  }

  commands.register("teleport", "focuscript.example.teleport") { ctx ->
    val sender = ctx.sender
    val player = sender.player ?: run {
      sender.sendText(text("players only").color("red"))
      return@register
    }
    if (!player.hasPermission("focuscript.example.teleport")) {
      sender.sendText(text("no permission").color("red"))
      return@register
    }
    val loc = player.location
    player.teleport(location(loc.worldName, loc.x, loc.y + 1.0, loc.z, loc.yaw, loc.pitch))
    player.sendText(text("teleported up").color("green"))
  }
}
