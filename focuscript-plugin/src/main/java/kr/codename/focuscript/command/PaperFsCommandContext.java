package kr.codename.focuscript.command;

import kr.codename.focuscript.api.FsCommandContext;
import kr.codename.focuscript.api.FsCommandSender;

import java.util.List;
import java.util.Objects;

public final class PaperFsCommandContext implements FsCommandContext {
    private final FsCommandSender sender;
    private final String name;
    private final String label;
    private final List<String> args;

    public PaperFsCommandContext(FsCommandSender sender, String name, String label, List<String> args) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.name = Objects.requireNonNull(name, "name");
        this.label = Objects.requireNonNull(label, "label");
        this.args = List.copyOf(args);
    }

    @Override
    public FsCommandSender getSender() {
        return sender;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public List<String> getArgs() {
        return args;
    }
}
