package kr.codename.focuscript.api;

/**
 * Runtime context provided to modules.
 * This is the only surface the module should need to build features.
 */
public interface FsContext {
    FsServer getServer();
    FsEvents getEvents();
    FsScheduler getScheduler();
    FsLogger getLog();
    FsConfig getConfig();
    FsCommands getCommands();
    FsStorage getStorage();
}
