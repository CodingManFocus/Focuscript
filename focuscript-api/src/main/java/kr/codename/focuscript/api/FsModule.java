package kr.codename.focuscript.api;

/**
 * Module entrypoint (compiled from .fs).
 *
 * Implementations are created/loaded by Focuscript runtime.
 */
public interface FsModule {

    /**
     * Called when the module is enabled.
     */
    void onEnable(FsContext context) throws Exception;

    /**
     * Called when the module is disabled.
     * Default: no-op.
     */
    default void onDisable() throws Exception {
        // no-op
    }
}
