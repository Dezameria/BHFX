package net.offkung.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.offkung.bhfx.impl.client.ClientFxManager;

/**
 * Fired once on each mod's event bus during client setup. Register every effect id your mod starts:
 * <pre>{@code
 * @SubscribeEvent static void onRegisterFx(RegisterFxEvent e) { e.register(MyMod.id("meteor"), MeteorFx::new); }
 * }</pre>
 * Client only — subscribe from a class that is loaded only on the client (and only when bhfx is present).
 */
public final class RegisterFxEvent extends Event implements IModBusEvent {
    public RegisterFxEvent() {
    }

    public void register(ResourceLocation id, FxFactory factory) {
        ClientFxManager.register(id, factory);
    }
}
