package kr.codename.focuscript.api;

import java.util.List;

public interface FsCommandContext {
    FsCommandSender getSender();

    String getName();

    String getLabel();

    List<String> getArgs();
}
