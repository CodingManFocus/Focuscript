package kr.codename.focuscript.api.events;

import kr.codename.focuscript.api.FsPlayer;

import java.util.Objects;

public final class FsPlayerDamageEvent {
    private final FsPlayer player;
    private final double damage;
    private final String cause;
    private final String damagerType;
    private final FsPlayer damagerPlayer;

    public FsPlayerDamageEvent(
            FsPlayer player,
            double damage,
            String cause,
            String damagerType,
            FsPlayer damagerPlayer
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.damage = damage;
        this.cause = Objects.requireNonNull(cause, "cause");
        this.damagerType = Objects.requireNonNull(damagerType, "damagerType");
        this.damagerPlayer = damagerPlayer;
    }

    public FsPlayer getPlayer() {
        return player;
    }

    public double getDamage() {
        return damage;
    }

    public String getCause() {
        return cause;
    }

    public String getDamagerType() {
        return damagerType;
    }

    public FsPlayer getDamagerPlayer() {
        return damagerPlayer;
    }
}
