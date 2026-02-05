package kr.codename.focuscript.core;

import kr.codename.focuscript.api.FsModule;
import kr.codename.focuscript.core.workspace.ScriptManifest;
import kr.codename.focuscript.runtime.PaperFsContext;

import java.nio.file.Path;

public record LoadedModule(
        ScriptManifest manifest,
        Path workspaceDir,
        Path moduleJar,
        ClassLoader classLoader,
        FsModule module,
        PaperFsContext context
) {
}
