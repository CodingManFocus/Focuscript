package kr.codename.focuscript.api;

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

public interface FsEvents {
    FsSubscription onJoin(FsEventHandler<FsPlayerJoinEvent> handler);
    FsSubscription onQuit(FsEventHandler<FsPlayerQuitEvent> handler);

    FsSubscription onChat(FsEventHandler<FsPlayerChatEvent> handler);
    FsSubscription onCommand(FsEventHandler<FsPlayerCommandEvent> handler);
    FsSubscription onBlockBreak(FsEventHandler<FsPlayerBlockBreakEvent> handler);
    FsSubscription onBlockPlace(FsEventHandler<FsPlayerBlockPlaceEvent> handler);
    FsSubscription onDeath(FsEventHandler<FsPlayerDeathEvent> handler);
    FsSubscription onDamage(FsEventHandler<FsPlayerDamageEvent> handler);
}
