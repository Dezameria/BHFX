package net.offkung.bhfx.api.client;

@FunctionalInterface
public interface FxFactory {
    Fx create(FxInit init);
}
