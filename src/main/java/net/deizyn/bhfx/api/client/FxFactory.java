package net.deizyn.bhfx.api.client;

@FunctionalInterface
public interface FxFactory {
    Fx create(FxInit init);
}
