package net.deizyn.bhfx.impl.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.deizyn.bhfx.api.client.FxPose;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Blockbench / GeckoLib {@code .animation.json} (Bedrock format, {@code animations} → clip → {@code bones} →
 * {@code rotation} / {@code position} / {@code scale}) into clips that sample into an {@link FxPose} without
 * allocating. Keyframes: a constant ({@code [x, y, z]}, one number or Molang string for all three), keyframe maps by
 * time in seconds, values as arrays, {@code {"vector": ...}} or {@code {"pre": ..., "post": ...}} (a jump at that time),
 * numbers or {@link Molang} strings. Between keyframes, like Blockbench: {@code step} holds the earlier value;
 * {@code catmullrom} on either end makes a smooth curve through the neighbours; {@code bezier} on either end uses the
 * handles ({@code left}, {@code left_time}, {@code right}, {@code right_time}; ±0.1 s flat handles on plain keyframes);
 * else the later keyframe's GeckoLib {@code easing} ({@code linear}, {@code step}, {@code easeIn/Out/InOut} +
 * {@code Sine Quad Cubic Quart Quint Expo Circ Back Elastic Bounce}; {@code easingArgs[0]} for step count, back
 * overshoot). {@code loop}: {@code true} wraps, {@code false} / {@code "hold_on_last_frame"} hold the last frame.
 * Pure Java (no GL, no Minecraft), so it runs off-thread and in tests.
 */
public final class GeoAnimationLoader {
    private GeoAnimationLoader() {
    }

    /** The file's clips by name (and by the part after the last dot, when that is unique), plus load warnings. */
    public record Library(Map<String, Clip> clips, List<String> warnings) {
    }

    public static Library load(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("animations") || !root.get("animations").isJsonObject()) {
            throw new IllegalArgumentException("not an .animation.json (no animations)");
        }
        List<String> warnings = new ArrayList<>();
        Map<String, Clip> clips = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> a : root.getAsJsonObject("animations").entrySet()) {
            if (a.getValue().isJsonObject()) {
                clips.put(a.getKey(), clip(a.getKey(), a.getValue().getAsJsonObject(), warnings));
            }
        }
        Map<String, Clip> all = new LinkedHashMap<>(clips);
        Map<String, Integer> shortCount = new LinkedHashMap<>();
        for (String name : clips.keySet()) {
            shortCount.merge(shortName(name), 1, Integer::sum);
        }
        for (Map.Entry<String, Clip> c : clips.entrySet()) {
            String s = shortName(c.getKey());
            if (shortCount.get(s) == 1) {
                all.putIfAbsent(s, c.getValue());
            }
        }
        return new Library(Collections.unmodifiableMap(all), List.copyOf(warnings));
    }

    private static String shortName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    // ---------------------------------------------------------------- clips

    public enum Loop { ONCE, LOOP, HOLD }

    /** One animation. {@code length} in seconds. */
    public static final class Clip {
        private final String name;
        private final float length;
        private final Loop loop;
        private final Track[] tracks;
        private final float[] value = new float[3];

        Clip(String name, float length, Loop loop, Track[] tracks) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            this.tracks = tracks;
        }

        public String name() {
            return name;
        }

        public float length() {
            return length;
        }

        public Loop loop() {
            return loop;
        }

        public boolean loops() {
            return loop == Loop.LOOP;
        }

        /** The clip's own time for {@code seconds} since it started: wrapped when it loops, else held at the end. */
        public float time(float seconds) {
            if (!(seconds > 0f)) {
                return 0f;
            }
            if (loop == Loop.LOOP) {
                return length > 0f ? seconds % length : 0f;
            }
            return Math.min(seconds, length);
        }

        /**
         * Put this clip at {@code seconds} into {@code pose}: the channels it animates, blended over what the pose has
         * by {@code weight} (1 = the clip's values; missing channels count as as-modelled).
         */
        public void apply(float seconds, FxPose pose, float weight) {
            float t = time(seconds);
            float w = Math.max(0f, Math.min(1f, weight));
            if (w <= 0f) {
                return;
            }
            for (Track track : tracks) {
                if (track.rotation != null) {
                    track.rotation.sample(t, value);
                    blend(pose, track.bone, 0, w, 0f);
                    pose.rotation(track.bone, value[0], value[1], value[2]);
                }
                if (track.position != null) {
                    track.position.sample(t, value);
                    blend(pose, track.bone, 3, w, 0f);
                    pose.position(track.bone, value[0], value[1], value[2]);
                }
                if (track.scale != null) {
                    track.scale.sample(t, value);
                    blend(pose, track.bone, 6, w, 1f);
                    pose.scale(track.bone, value[0], value[1], value[2]);
                }
            }
        }

        private void blend(FxPose pose, String bone, int first, float w, float rest) {
            if (w >= 1f) {
                return;
            }
            float[] had = pose.channels(bone);
            for (int k = 0; k < 3; k++) {
                float from = had != null ? had[first + k] : rest;
                value[k] = from + (value[k] - from) * w;
            }
        }
    }

    record Track(String bone, @Nullable Channel rotation, @Nullable Channel position, @Nullable Channel scale) {
    }

    private static final byte LINEAR = 0;
    private static final byte CATMULLROM = 1;
    private static final byte STEP = 2;
    private static final byte BEZIER = 3;

    /** One animated vector: keyframes sorted by time. */
    static final class Channel {
        final float[] times;
        final float[] pre;
        final float[] post;
        @Nullable
        final Molang.Expr[] preExpr;
        @Nullable
        final Molang.Expr[] postExpr;
        final byte[] interp;
        final byte[] easing;
        final float[] easingArg;
        /** Per keyframe: left xyz, left_time xyz, right xyz, right_time xyz. */
        final float[] handles;

        Channel(List<Key> keys) {
            int n = keys.size();
            times = new float[n];
            pre = new float[n * 3];
            post = new float[n * 3];
            interp = new byte[n];
            easing = new byte[n];
            easingArg = new float[n];
            handles = new float[n * 12];
            boolean anyExpr = false;
            for (Key k : keys) {
                for (int a = 0; a < 3; a++) {
                    anyExpr |= k.preExpr[a] != null || k.postExpr[a] != null;
                }
            }
            preExpr = anyExpr ? new Molang.Expr[n * 3] : null;
            postExpr = anyExpr ? new Molang.Expr[n * 3] : null;
            for (int i = 0; i < n; i++) {
                Key k = keys.get(i);
                times[i] = k.time;
                System.arraycopy(k.pre, 0, pre, i * 3, 3);
                System.arraycopy(k.post, 0, post, i * 3, 3);
                if (anyExpr) {
                    System.arraycopy(k.preExpr, 0, preExpr, i * 3, 3);
                    System.arraycopy(k.postExpr, 0, postExpr, i * 3, 3);
                }
                interp[i] = k.interp;
                easing[i] = k.easing;
                easingArg[i] = k.easingArg;
                System.arraycopy(k.handles, 0, handles, i * 12, 12);
            }
        }

        private float pre(int i, int a, float t) {
            Molang.Expr e = preExpr != null ? preExpr[i * 3 + a] : null;
            return e != null ? (float) e.eval(t) : pre[i * 3 + a];
        }

        private float post(int i, int a, float t) {
            Molang.Expr e = postExpr != null ? postExpr[i * 3 + a] : null;
            return e != null ? (float) e.eval(t) : post[i * 3 + a];
        }

        void sample(float t, float[] out) {
            int n = times.length;
            if (t < times[0]) {
                for (int a = 0; a < 3; a++) {
                    out[a] = pre(0, a, t);
                }
                return;
            }
            if (n == 1 || t >= times[n - 1]) {
                for (int a = 0; a < 3; a++) {
                    out[a] = post(n - 1, a, t);
                }
                return;
            }
            int lo = 0;
            int hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= t) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            int i = lo;
            int j = lo + 1;
            float t0 = times[i];
            float t1 = times[j];
            float f = t1 > t0 ? (t - t0) / (t1 - t0) : 1f;
            if (interp[i] == STEP) {
                for (int a = 0; a < 3; a++) {
                    out[a] = post(i, a, t);
                }
                return;
            }
            if (interp[i] == CATMULLROM || interp[j] == CATMULLROM) {
                for (int a = 0; a < 3; a++) {
                    float p1 = post(i, a, t);
                    float p2 = pre(j, a, t);
                    float p0 = i > 0 ? post(i - 1, a, t) : p1;
                    float p3 = j + 1 < n ? pre(j + 1, a, t) : p2;
                    out[a] = catmullRom(f, p0, p1, p2, p3);
                }
                return;
            }
            if (interp[i] == BEZIER || interp[j] == BEZIER) {
                for (int a = 0; a < 3; a++) {
                    float v0 = post(i, a, t);
                    float v1 = pre(j, a, t);
                    float c0t = t0 + handles[i * 12 + 9 + a];
                    float c0v = v0 + handles[i * 12 + 6 + a];
                    float c1t = t1 + handles[j * 12 + 3 + a];
                    float c1v = v1 + handles[j * 12 + a];
                    out[a] = bezier(t, t0, v0, c0t, c0v, c1t, c1v, t1, v1);
                }
                return;
            }
            float e = (float) ease(easing[j], f, easingArg[j]);
            for (int a = 0; a < 3; a++) {
                float v0 = post(i, a, t);
                out[a] = v0 + (pre(j, a, t) - v0) * e;
            }
        }
    }

    static float catmullRom(float f, float p0, float p1, float p2, float p3) {
        float f2 = f * f;
        float f3 = f2 * f;
        return 0.5f * ((2f * p1) + (-p0 + p2) * f + (2f * p0 - 5f * p1 + 4f * p2 - p3) * f2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * f3);
    }

    /** A cubic Bézier through (t0, v0) .. (t1, v1) with control points c0, c1, read at time {@code t}. */
    static float bezier(float t, float t0, float v0, float c0t, float c0v, float c1t, float c1v, float t1, float v1) {
        // keep the control times inside the segment, so time only goes forward along the curve
        c0t = Math.max(t0, Math.min(t1, c0t));
        c1t = Math.max(t0, Math.min(t1, c1t));
        float lo = 0f;
        float hi = 1f;
        float u = 0.5f;
        for (int k = 0; k < 24; k++) {
            u = (lo + hi) * 0.5f;
            if (cubic(u, t0, c0t, c1t, t1) < t) {
                lo = u;
            } else {
                hi = u;
            }
        }
        return cubic(u, v0, c0v, c1v, v1);
    }

    private static float cubic(float u, float a, float b, float c, float d) {
        float v = 1f - u;
        return v * v * v * a + 3f * v * v * u * b + 3f * v * u * u * c + u * u * u * d;
    }

    // ---------------------------------------------------------------- easing

    private static final String[] FAMILIES = {"sine", "quad", "cubic", "quart", "quint", "expo", "circ", "back",
            "elastic", "bounce"};

    /** 0 linear, 1 step, then 2 + family * 3 + (0 in, 1 out, 2 in-out); -1 when unknown. */
    static int easing(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        if (s.equals("linear")) {
            return 0;
        }
        if (s.equals("step")) {
            return 1;
        }
        int mode;
        String rest;
        if (s.startsWith("easeinout")) {
            mode = 2;
            rest = s.substring(9);
        } else if (s.startsWith("easein")) {
            mode = 0;
            rest = s.substring(6);
        } else if (s.startsWith("easeout")) {
            mode = 1;
            rest = s.substring(7);
        } else {
            return -1;
        }
        for (int f = 0; f < FAMILIES.length; f++) {
            if (FAMILIES[f].equals(rest)) {
                return 2 + f * 3 + mode;
            }
        }
        return -1;
    }

    static double ease(int type, double f, double arg) {
        if (type <= 0) {
            return f;
        }
        if (type == 1) {
            double steps = arg > 0 ? Math.max(1, Math.round(arg)) : 2;
            return Math.floor(f * steps) / steps;
        }
        int family = (type - 2) / 3;
        int mode = (type - 2) % 3;
        return switch (mode) {
            case 0 -> easeIn(family, f, arg);
            case 1 -> 1.0 - easeIn(family, 1.0 - f, arg);
            default -> f < 0.5 ? easeIn(family, f * 2.0, arg) * 0.5 : 1.0 - easeIn(family, (1.0 - f) * 2.0, arg) * 0.5;
        };
    }

    private static double easeIn(int family, double f, double arg) {
        return switch (family) {
            case 0 -> 1.0 - Math.cos(f * Math.PI / 2.0);
            case 1 -> f * f;
            case 2 -> f * f * f;
            case 3 -> f * f * f * f;
            case 4 -> f * f * f * f * f;
            case 5 -> f <= 0.0 ? 0.0 : Math.pow(2.0, 10.0 * f - 10.0);
            case 6 -> 1.0 - Math.sqrt(Math.max(0.0, 1.0 - f * f));
            case 7 -> {
                double c1 = arg != 0.0 ? arg : 1.70158;
                yield (c1 + 1.0) * f * f * f - c1 * f * f;
            }
            case 8 -> f <= 0.0 ? 0.0 : f >= 1.0 ? 1.0
                    : -Math.pow(2.0, 10.0 * f - 10.0) * Math.sin((f * 10.0 - 10.75) * (2.0 * Math.PI / 3.0));
            default -> 1.0 - bounceOut(1.0 - f);
        };
    }

    private static double bounceOut(double f) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (f < 1.0 / d1) {
            return n1 * f * f;
        } else if (f < 2.0 / d1) {
            f -= 1.5 / d1;
            return n1 * f * f + 0.75;
        } else if (f < 2.5 / d1) {
            f -= 2.25 / d1;
            return n1 * f * f + 0.9375;
        }
        f -= 2.625 / d1;
        return n1 * f * f + 0.984375;
    }

    // ---------------------------------------------------------------- parsing

    private static final class Key {
        float time;
        final float[] pre = new float[3];
        final float[] post = new float[3];
        final Molang.Expr[] preExpr = new Molang.Expr[3];
        final Molang.Expr[] postExpr = new Molang.Expr[3];
        byte interp = LINEAR;
        byte easing = 0;
        float easingArg;
        final float[] handles = {0, 0, 0, -0.1f, -0.1f, -0.1f, 0, 0, 0, 0.1f, 0.1f, 0.1f};
    }

    private static Clip clip(String name, JsonObject a, List<String> warnings) {
        Loop loop = Loop.ONCE;
        if (a.has("loop")) {
            JsonElement l = a.get("loop");
            if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) {
                loop = l.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
            } else if (l.isJsonPrimitive() && "hold_on_last_frame".equals(l.getAsString())) {
                loop = Loop.HOLD;
            }
        }
        List<Track> tracks = new ArrayList<>();
        float last = 0f;
        if (a.has("bones") && a.get("bones").isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : a.getAsJsonObject("bones").entrySet()) {
                if (!b.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject bone = b.getValue().getAsJsonObject();
                String where = name + "/" + b.getKey();
                Channel rot = channel(bone.get("rotation"), 0f, where + "/rotation", warnings);
                Channel pos = channel(bone.get("position"), 0f, where + "/position", warnings);
                Channel scl = channel(bone.get("scale"), 1f, where + "/scale", warnings);
                for (Channel c : new Channel[]{rot, pos, scl}) {
                    if (c != null) {
                        last = Math.max(last, c.times[c.times.length - 1]);
                    }
                }
                if (rot != null || pos != null || scl != null) {
                    tracks.add(new Track(b.getKey(), rot, pos, scl));
                }
            }
        }
        float length = a.has("animation_length") ? a.get("animation_length").getAsFloat() : last;
        if (!(length >= 0f) || Float.isInfinite(length)) {
            length = last;
        }
        return new Clip(name, length, loop, tracks.toArray(new Track[0]));
    }

    @Nullable
    private static Channel channel(@Nullable JsonElement el, float rest, String where, List<String> warnings) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        List<Key> keys = new ArrayList<>();
        if (el.isJsonObject() && !isKeyObject(el.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> k : el.getAsJsonObject().entrySet()) {
                Double time = Molang.number(k.getKey());
                if (time == null || !Double.isFinite(time)) {
                    warnings.add(where + ": keyframe time '" + k.getKey() + "' is not a number");
                    continue;
                }
                Key key = key(k.getValue(), rest, where + "@" + k.getKey(), warnings);
                key.time = time.floatValue();
                keys.add(key);
            }
        } else {
            keys.add(key(el, rest, where, warnings));
        }
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort((x, y) -> Float.compare(x.time, y.time));
        return new Channel(keys);
    }

    private static boolean isKeyObject(JsonObject o) {
        return o.has("vector") || o.has("pre") || o.has("post");
    }

    private static Key key(JsonElement el, float rest, String where, List<String> warnings) {
        Key k = new Key();
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("pre") || o.has("post")) {
                JsonElement pre = o.has("pre") ? o.get("pre") : o.get("post");
                JsonElement post = o.has("post") ? o.get("post") : o.get("pre");
                vector(pre, rest, k.pre, k.preExpr, where, warnings);
                vector(post, rest, k.post, k.postExpr, where, warnings);
            } else {
                vector(o.get("vector"), rest, k.pre, k.preExpr, where, warnings);
                System.arraycopy(k.pre, 0, k.post, 0, 3);
                System.arraycopy(k.preExpr, 0, k.postExpr, 0, 3);
            }
            if (o.has("lerp_mode")) {
                String mode = o.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT);
                k.interp = switch (mode) {
                    case "catmullrom" -> CATMULLROM;
                    case "step" -> STEP;
                    case "bezier" -> BEZIER;
                    default -> LINEAR;
                };
            }
            if (o.has("easing")) {
                String e = o.get("easing").getAsString();
                if (e.equalsIgnoreCase("bezier")) {
                    k.interp = BEZIER;
                } else if (e.equalsIgnoreCase("catmullrom")) {
                    k.interp = CATMULLROM;
                } else {
                    int type = easing(e);
                    if (type < 0) {
                        warnings.add(where + ": easing '" + e + "' unknown, linear used");
                    } else {
                        k.easing = (byte) type;
                    }
                }
            }
            if (o.has("easingArgs") && o.get("easingArgs").isJsonArray() && !o.getAsJsonArray("easingArgs").isEmpty()) {
                k.easingArg = o.getAsJsonArray("easingArgs").get(0).getAsFloat();
            }
            handle(o, "left", k.handles, 0);
            handle(o, "left_time", k.handles, 3);
            handle(o, "right", k.handles, 6);
            handle(o, "right_time", k.handles, 9);
        } else {
            vector(el, rest, k.pre, k.preExpr, where, warnings);
            System.arraycopy(k.pre, 0, k.post, 0, 3);
            System.arraycopy(k.preExpr, 0, k.postExpr, 0, 3);
        }
        return k;
    }

    private static void handle(JsonObject o, String key, float[] handles, int first) {
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                handles[first + i] = a.get(i).getAsFloat();
            }
        }
    }

    private static void vector(@Nullable JsonElement el, float rest, float[] out, Molang.Expr[] exprs, String where,
                               List<String> warnings) {
        out[0] = rest;
        out[1] = rest;
        out[2] = rest;
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("vector")) {
            el = el.getAsJsonObject().get("vector");
        }
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (int i = 0; i < 3; i++) {
                if (i < a.size()) {
                    scalar(a.get(i), rest, out, exprs, i, where, warnings);
                }
            }
        } else if (el.isJsonPrimitive()) {
            for (int i = 0; i < 3; i++) {
                scalar(el, rest, out, exprs, i, where, warnings);
            }
        }
    }

    private static void scalar(JsonElement el, float rest, float[] out, Molang.Expr[] exprs, int i, String where,
                               List<String> warnings) {
        if (!el.isJsonPrimitive()) {
            return;
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) {
            out[i] = p.getAsFloat();
            return;
        }
        String text = p.getAsString();
        Double n = Molang.number(text);
        if (n != null) {
            out[i] = n.floatValue();
            return;
        }
        if (text.isBlank()) {
            return;
        }
        try {
            exprs[i] = Molang.compile(text);
        } catch (IllegalArgumentException ex) {
            out[i] = rest;
            warnings.add(where + ": '" + text + "' not supported (" + ex.getMessage() + "), " + rest + " used");
        }
    }
}
