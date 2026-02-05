package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsEvents;
import kr.codename.focuscript.api.events.FsEventHandler;
import kr.codename.focuscript.api.events.FsPlayerBlockBreakEvent;
import kr.codename.focuscript.api.events.FsPlayerBlockPlaceEvent;
import kr.codename.focuscript.api.events.FsPlayerChatEvent;
import kr.codename.focuscript.api.events.FsPlayerCommandEvent;
import kr.codename.focuscript.api.events.FsPlayerDamageEvent;
import kr.codename.focuscript.api.events.FsPlayerDeathEvent;
import kr.codename.focuscript.api.events.FsPlayerJoinEvent;
import kr.codename.focuscript.api.events.FsPlayerQuitEvent;
import kr.codename.focuscript.api.events.FsSubscription;
import kr.codename.focuscript.core.bridge.PaperEventBridge;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PaperFsEvents implements FsEvents {

    private final String moduleId;
    private final PaperEventBridge bridge;
    private final CopyOnWriteArrayList<FsSubscription> subscriptions = new CopyOnWriteArrayList<>();

    public PaperFsEvents(String moduleId, PaperEventBridge bridge) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public FsSubscription onJoin(FsEventHandler<FsPlayerJoinEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerJoin(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onQuit(FsEventHandler<FsPlayerQuitEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerQuit(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onChat(FsEventHandler<FsPlayerChatEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerChat(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onCommand(FsEventHandler<FsPlayerCommandEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerCommand(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onBlockBreak(FsEventHandler<FsPlayerBlockBreakEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerBlockBreak(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onBlockPlace(FsEventHandler<FsPlayerBlockPlaceEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerBlockPlace(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onDeath(FsEventHandler<FsPlayerDeathEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerDeath(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    @Override
    public FsSubscription onDamage(FsEventHandler<FsPlayerDamageEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        FsSubscription sub = bridge.registerDamage(moduleId, handler);
        subscriptions.add(sub);
        return () -> {
            sub.unsubscribe();
            subscriptions.remove(sub);
        };
    }

    public void unsubscribeAll() {
        for (FsSubscription sub : List.copyOf(subscriptions)) {
            try {
                sub.unsubscribe();
            } catch (Throwable ignored) {}
        }
        subscriptions.clear();
    }
}
