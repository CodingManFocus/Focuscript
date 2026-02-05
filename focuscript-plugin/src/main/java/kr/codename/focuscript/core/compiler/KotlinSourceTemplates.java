package kr.codename.focuscript.core.compiler;

import java.nio.file.Path;

/**
 * Generates Kotlin sources used for compilation.
 *
 * IMPORTANT: These generated files are compiled into the module.jar.
 */
public final class KotlinSourceTemplates {
    private KotlinSourceTemplates() {}

    public static String prelude(String modulePackage) {
        return """
                @file:Suppress("unused", "MemberVisibilityCanBePrivate")
                @file:JvmName("FocuscriptPrelude")
                package %s

                import kr.codename.focuscript.api.*
                import java.time.Duration

                typealias FsItemStack = kr.codename.focuscript.api.FsItemStack
                typealias FsItemMeta = kr.codename.focuscript.api.FsItemMeta
                typealias FsSimpleItemMeta = kr.codename.focuscript.api.FsSimpleItemMeta
                typealias FsInventory = kr.codename.focuscript.api.FsInventory
                typealias FsWorld = kr.codename.focuscript.api.FsWorld
                typealias FsBlock = kr.codename.focuscript.api.FsBlock

                 /**
                  * DSL receiver for `module { ... }`
                  *
                  * Provides:
                  * - server / events / scheduler / log / config / commands / storage
                  * - onDisable { }
                  */
                 class FsModuleScope internal constructor(
                     private val ctx: FsContext,
                     private val disableHandlers: MutableList<() -> Unit>
                 ) {
                     val server: FsServer get() = ctx.server
                     val events: FsEvents get() = ctx.events
                     val scheduler: FsScheduler get() = ctx.scheduler
                     val log: FsLogger get() = ctx.log
                     val config: FsConfig get() = ctx.config
                     val commands: FsCommands get() = ctx.commands
                     val storage: FsStorage get() = ctx.storage
 
                     fun onDisable(handler: () -> Unit) {
                         disableHandlers += handler
                     }
                 }


                private class ScriptFsModule(
                    private val block: FsModuleScope.() -> Unit
                ) : FsModule {
                    private val disableHandlers = mutableListOf<() -> Unit>()

                    override fun onEnable(context: FsContext) {
                        val scope = FsModuleScope(context, disableHandlers)
                        scope.block()
                    }

                    override fun onDisable() {
                        for (i in disableHandlers.size - 1 downTo 0) {
                            try {
                                disableHandlers[i].invoke()
                            } catch (_: Throwable) {
                                // swallow
                            }
                        }
                    }
                }

                fun module(block: FsModuleScope.() -> Unit): FsModule = ScriptFsModule(block)

                fun text(message: String): FsText = FsText.of(message)

                fun location(
                    worldName: String,
                    x: Double,
                    y: Double,
                    z: Double,
                    yaw: Float = 0f,
                    pitch: Float = 0f
                ): FsLocation = FsLocation.of(worldName, x, y, z, yaw, pitch)

                val Int.ticks: Duration get() = Duration.ofMillis(this.toLong() * 50L)
                val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
                val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
                val Int.hours: Duration get() = Duration.ofHours(this.toLong())
                """.formatted(modulePackage);
    }

    public static String entry(String modulePackage, String entryFsContent) {
        return """
                @file:Suppress("unused")
                @file:JvmName("FocuscriptEntry")
                package %s

                import kr.codename.focuscript.api.FsModule

                // Generated entry wrapper. Original entry .fs content is used as the initializer.
                val focuscriptModule: FsModule = %s
                """.formatted(modulePackage, entryFsContent.trim());
    }

    public static String source(String modulePackage, String content, Path originalPath) {
        return """
                @file:Suppress("unused")
                package %s

                // Source: %s
                %s
                """.formatted(
                modulePackage,
                originalPath.toString().replace("\\\\", "/"),
                content.trim()
        );
    }
}
