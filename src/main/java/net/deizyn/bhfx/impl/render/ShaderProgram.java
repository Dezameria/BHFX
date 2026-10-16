package net.deizyn.bhfx.impl.render;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

/** A linked fullscreen program with a uniform-location cache. */
final class ShaderProgram {
    private final int id;
    private final Map<String, Integer> locations = new HashMap<>();

    ShaderProgram(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }

    /** Cached location; -1 when the shader does not use the uniform. */
    int location(String name) {
        Integer cached = locations.get(name);
        if (cached == null) {
            cached = GL20.glGetUniformLocation(id, name);
            locations.put(name, cached);
        }
        return cached;
    }

    void delete() {
        GL20.glDeleteProgram(id);
    }
}
