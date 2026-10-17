package net.deizyn.bhfx.api.client;

@FunctionalInterface
/** Creates one effect instance per start, usually a constructor reference such as {@code MeteorFx::new}. */
public interface FxFactory {
    Fx create(FxInit init);
}
