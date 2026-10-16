package net.offkung.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Shader passes requested this frame. {@code shader} "ns:name" loads assets/ns/shaders/bhfx/name.fsh. */
public interface PassList {
    void add(ResourceLocation shader, Consumer<Uniforms> setup);

    void add(ResourceLocation shader, PassOptions options, Consumer<Uniforms> setup);
}
