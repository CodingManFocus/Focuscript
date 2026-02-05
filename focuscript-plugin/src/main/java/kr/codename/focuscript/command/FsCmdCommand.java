package kr.codename.focuscript.command;

import kr.codename.focuscript.core.ModuleManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class FsCmdCommand implements CommandExecutor, TabCompleter {
    private final ModuleManager moduleManager;
    private final ModuleCommandRegistry commandRegistry;

    public FsCmdCommand(ModuleManager moduleManager, ModuleCommandRegistry commandRegistry) {
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <workspace> <command> [args...]");
            return true;
        }

        String moduleId = args[0];
        String commandName = args[1];
        List<String> cmdArgs = args.length > 2
                ? Arrays.asList(args).subList(2, args.length)
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (var lm : moduleManager.getLoadedModules()) {
                String id = lm.manifest().id();
                if (id.toLowerCase().startsWith(p)) {
                    out.add(id);
                }
            }
        } else if (args.length == 2) {
            String moduleId = args[0];
            String p = args[1].toLowerCase();
            for (String cmdName : commandRegistry.getCommandNames(moduleId)) {
                if (cmdName.startsWith(p)) {
                    out.add(cmdName);
                }
            }
        }
        return out;
    }
}
