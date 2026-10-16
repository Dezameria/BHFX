package net.offkung.bhfx.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.offkung.bhfx.BHFxMod;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads fragment passes from any namespace: "ns:name" → assets/ns/shaders/bhfx/name.fsh, with
 * {@code #include "ns:file.glsl"} → assets/ns/shaders/bhfx/include/file.glsl (each file included once).
 * Compiled lazily, cached until {@link #clear()} (resource reload or /bhfx reload).
 */
final class ShaderManager {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");

    private static final String VERTEX = """
            #version 150
            out vec2 texCoord;
            void main() {
                // one triangle covering the screen: (-1,-1), (3,-1), (-1,3)
                vec2 p = vec2(float((gl_VertexID & 1) << 2) - 1.0, float((gl_VertexID & 2) << 1) - 1.0);
                texCoord = p * 0.5 + 0.5;
                gl_Position = vec4(p, 0.0, 1.0);
            }
            """;

    /** Prepended to every pass. Source string 0. */
    private static final String HEADER = """
            #version 150
            uniform sampler2D DiffuseSampler;
            uniform sampler2D SceneSampler;
            uniform sampler2D DepthSampler;
            uniform mat4 InvProj;
            uniform mat4 InvView;
            uniform mat4 ViewProj;
            uniform vec2 OutSize;
            uniform vec2 ScreenSize;
            uniform float GameTime;
            in vec2 texCoord;
            out vec4 fragColor;
            """;

    private final Map<ResourceLocation, ShaderProgram> programs = new HashMap<>();
    private final Set<ResourceLocation> broken = new HashSet<>();

    ShaderProgram get(ResourceLocation id) {
        ShaderProgram program = programs.get(id);
        if (program != null || broken.contains(id)) {
            return program;
        }
        List<ResourceLocation> files = new ArrayList<>();
        try {
            StringBuilder source = new StringBuilder(HEADER);
            files.add(id);
            append(source, toPass(id), files, new HashSet<>(), 1);
            program = link(id, source.toString(), files);
            programs.put(id, program);
            BHFxMod.LOGGER.debug("bhfx compiled {}", id);
        } catch (IOException | IllegalStateException e) {
            broken.add(id);
            BHFxMod.LOGGER.error("bhfx shader {} is broken and will be skipped until reload: {}", id, e.getMessage());
        }
        return program;
    }

    boolean isBroken(ResourceLocation id) {
        return broken.contains(id);
    }

    void clear() {
        programs.values().forEach(ShaderProgram::delete);
        programs.clear();
        broken.clear();
    }

    int compiledCount() {
        return programs.size();
    }

    private static ResourceLocation toPass(ResourceLocation id) {
        return new ResourceLocation(id.getNamespace(), "shaders/bhfx/" + id.getPath() + ".fsh");
    }

    private static ResourceLocation toInclude(String spec, String defaultNamespace) {
        ResourceLocation rl = spec.contains(":") ? new ResourceLocation(spec) : new ResourceLocation(defaultNamespace, spec);
        return new ResourceLocation(rl.getNamespace(), "shaders/bhfx/include/" + rl.getPath());
    }

    private static void append(StringBuilder out, ResourceLocation file, List<ResourceLocation> files,
                               Set<ResourceLocation> included, int fileIndex) throws IOException {
        String text = read(file);
        out.append("#line 1 ").append(fileIndex).append('\n');
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = INCLUDE.matcher(lines[i]);
            if (!m.matches()) {
                out.append(lines[i]).append('\n');
                continue;
            }
            ResourceLocation inc = toInclude(m.group(1), file.getNamespace());
            if (included.add(inc)) {
                files.add(inc);
                append(out, inc, files, included, files.size());
            }
            out.append("#line ").append(i + 2).append(' ').append(fileIndex).append('\n');
        }
    }

    private static String read(ResourceLocation file) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            throw new IOException("missing " + file);
        }
        try (InputStream in = resource.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ShaderProgram link(ResourceLocation id, String fragmentSource, List<ResourceLocation> files) {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX, id, files);
        int fs;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource, id, files);
        } catch (IllegalStateException e) {
            GL20.glDeleteShader(vs);
            throw e;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL30.glBindFragDataLocation(program, 0, "fragColor");
        GL20.glLinkProgram(program);
        boolean ok = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0;
        String log = ok ? "" : GL20.glGetProgramInfoLog(program, 16384);
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (!ok) {
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("link failed: " + log);
        }
        return new ShaderProgram(program);
    }

    private static int compile(int type, String source, ResourceLocation id, List<ResourceLocation> files) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 16384);
            GL20.glDeleteShader(shader);
            StringBuilder legend = new StringBuilder();
            for (int i = 0; i < files.size(); i++) {
                legend.append("\n  source ").append(i + 1).append(" = ").append(files.get(i));
            }
            throw new IllegalStateException((type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment")
                    + " compile failed for " + id + ":\n" + log + "\n  source 0 = bhfx header" + legend);
        }
        return shader;
    }
}
