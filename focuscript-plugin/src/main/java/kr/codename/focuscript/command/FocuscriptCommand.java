package kr.codename.focuscript.command;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.core.ModuleManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FocuscriptCommand implements CommandExecutor, TabCompleter {

    private final FocuscriptPlugin plugin;
    private final ModuleManager moduleManager;
    private final ModuleCommandRegistry commandRegistry;

    public FocuscriptCommand(FocuscriptPlugin plugin, ModuleManager moduleManager, ModuleCommandRegistry commandRegistry) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Focuscript commands:");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " cmd <moduleId> <command> [args...]");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " webide [port] [bindHost]");
            sender.sendMessage(ChatColor.DARK_GRAY + "  - stop: /" + label + " webide stop");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                sender.sendMessage(ChatColor.YELLOW + "Reloading Focuscript modules...");
                int disabled = moduleManager.disableAll();
                int queued = moduleManager.loadAll();
                sender.sendMessage(ChatColor.GREEN + "Done. disabled=" + disabled + ", queued=" + queued);
                return true;
            }
            case "webide" -> {
                // Disabled by default. Starts only when explicitly requested.
                // Usage:
                //   /fs webide
                //   /fs webide stop
                //   /fs webide 17777 0.0.0.0
                if (args.length >= 2) {
                    String a1 = args[1];
                    if ("stop".equalsIgnoreCase(a1)) {
                        plugin.getWebIdeManager().stop();
                        sender.sendMessage(ChatColor.GREEN + "Web IDE stopped.");
                        return true;
                    }
                    if ("status".equalsIgnoreCase(a1)) {
                        var ide = plugin.getWebIdeManager();
                        if (!ide.isRunning()) {
                            sender.sendMessage(ChatColor.YELLOW + "Web IDE is not running.");
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "Web IDE is running on " + ide.getBindHost() + ":" + ide.getPort());
                        }
                        return true;
                    }
                }

                int port = 17777;
                String bind = "0.0.0.0";

                // Optional args: [port] [bindHost]
                if (args.length >= 2) {
                    String a1 = args[1];
                    try {
                        port = Integer.parseInt(a1);
                    } catch (NumberFormatException ignored) {
                        bind = a1;
                    }
                }
                if (args.length >= 3) {
                    String a2 = args[2];
                    try {
                        port = Integer.parseInt(a2);
                    } catch (NumberFormatException ignored) {
                        bind = a2;
                    }
                }

                plugin.getWebIdeManager().openAsync(sender, bind, port);
                return true;
            }
            case "cmd", "command" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " cmd <moduleId> <command> [args...]");
                    return true;
                }
                String moduleId = args[1];
                String commandName = args[2];
                List<String> cmdArgs = args.length > 3
                        ? Arrays.asList(args).subList(3, args.length)
                        : List.of();
                boolean handled = commandRegistry.dispatch(
                        moduleId,
                        commandName,
                        new PaperFsCommandSender(sender),
                        label,
                        cmdArgs
                );
                if (!handled) {
                    sender.sendMessage(ChatColor.RED + "Unknown module/command: " + moduleId + "/" + commandName);
                }
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + sub);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            if ("reload".startsWith(p)) out.add("reload");
            if ("cmd".startsWith(p)) out.add("cmd");
            if ("webide".startsWith(p)) out.add("webide");
        } else if (args.length == 2 && "cmd".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase();
            for (var lm : moduleManager.getLoadedModules()) {
                String id = lm.manifest().id();
                if (id.toLowerCase().startsWith(p)) {
                    out.add(id);
                }
            }
        } else if (args.length == 2 && "webide".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase();
            if ("status".startsWith(p)) out.add("status");
            if ("stop".startsWith(p)) out.add("stop");
            if ("17777".startsWith(p)) out.add("17777");
            if ("0.0.0.0".startsWith(p)) out.add("0.0.0.0");
            if ("127.0.0.1".startsWith(p)) out.add("127.0.0.1");
        } else if (args.length == 3 && "cmd".equalsIgnoreCase(args[0])) {
            String moduleId = args[1];
            String p = args[2].toLowerCase();
            for (String cmd : commandRegistry.getCommandNames(moduleId)) {
                if (cmd.startsWith(p)) {
                    out.add(cmd);
                }
            }
        }
        return out;
    }
}
