package net.deizyn.bhfx.impl.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.impl.BHFxConfig;
import net.deizyn.bhfx.impl.block.MirrorTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws what the mirror blocks reflect: players only, with what they hold and wear (Figura avatars too, see
 * {@link FiguraMirror}). The block's own texture is the backdrop.
 *
 * <p>Two steps per frame:
 * <ol>
 *   <li>{@link #drawReflections} at {@code AFTER_SOLID_BLOCKS}, before the world's entity pass: into a screen-sized
 *       target of our own, for each mirror surface (farthest first) open its screen area where the world does not
 *       hide it (depth 1, colour cleared; {@code mirror_area} tests against a depth pass of the mirror faces),
 *       draw the players mirrored across it (front face flipped, because a reflection reverses winding), and close
 *       the area again (depth 0). Players only ever land inside their own mirror's area, and a nearer mirror
 *       overwrites a farther one where they overlap on screen.</li>
 *   <li>{@link #composite} at {@code AFTER_BLOCK_ENTITIES}: draw the mirror faces into the main target with
 *       {@code mirror_composite}, which copies the pixel under each fragment. The main depth test keeps the
 *       reflection behind whatever stands in front of the mirror; the world's depth buffer is never written.</li>
 * </ol>
 * Reflecting before the entity pass also means Figura's part matrices are last written by the real render, so
 * physics scripts never see mirrored positions.
 *
 * <p>A player is reflected only if the way from the mirror to them is clear of blocks (see {@link #seen}), so nobody
 * shows up through a wall. It is checked per player, not per pixel: someone half behind a wall shows whole.
 *
 * <p>With an Iris/Oculus shader pack the pack's programs replace the vanilla ones (and bind their own buffers) for
 * the whole level render, so step 1 moves to {@link #drawReflectionsBeforeLevel}, which runs from the camera event
 * just before the level render starts, and step 2 ({@link #compositeForPack}) draws the mirror faces as an ordinary
 * entity-cutout surface textured with the reflection, so the pack shades it like any entity.
 */
final class MirrorRenderer {
    /** Pulls the mirror quad toward the camera so it wins the depth test against the block's own face. */
    private static final double LIFT = 0.002;

    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1 << 18));

    /** Set by {@code RegisterShadersEvent}; null until the first resource load finishes. */
    @Nullable
    static ShaderInstance composite;
    /** Opens and closes surface areas only where no nearer mirror face hides them; null until loaded, like composite. */
    @Nullable
    static ShaderInstance area;
    /** Near plane of vanilla's projection ({@code GameRenderer} keeps it private). */
    private static final float NEAR = 0.05f;
    @Nullable
    private static TextureTarget reflection;
    /** Depth of this frame's reflecting faces, which {@link #area} tests against. */
    @Nullable
    private static TextureTarget faces;
    /** Surfaces drawn into {@link #reflection} this frame, waiting for {@link #composite}. */
    private static final List<Plane> PENDING = new ArrayList<>();

    private static final ResourceLocation REFLECTION_TEXTURE = BHFxMod.id("mirror_reflection");
    private static boolean textureRegistered;
    /** View and projection the reflection was drawn with; the pack composite maps mirror vertices to it. */
    private static final Matrix4f DRAWN_POSE = new Matrix4f();
    private static final Matrix4f DRAWN_PROJECTION = new Matrix4f();
    /** Pack composite: mirror rectangles are split into cells this size (blocks) so screen-space UVs stay close. */
    private static final double PACK_CELL = 0.5;
    private static final Vector4f[] CELL = {new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()};
    private static final Vector4f[] CELL_SCREEN = {new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()};

    /** Line-of-sight answers, per surface and player, reused for the rest of the game tick. */
    private static final Map<Long, Map<Integer, Boolean>> SEEN = new HashMap<>();
    private static long seenTick = Long.MIN_VALUE;
    /** True while {@link #drawPlayers} renders players into the reflection target. */
    private static boolean drawing;

    private MirrorRenderer() {
    }

    /** Whether the entity renders running now are drawing a mirror reflection, not the real world. */
    static boolean drawing() {
        return drawing;
    }

    /** Frees the reflection targets once no mirror is loaded. */
    static void tick() {
        if (reflection != null && MirrorTracker.isEmpty()) {
            reflection.destroyBuffers();
            reflection = null;
        }
        if (faces != null && MirrorTracker.isEmpty()) {
            faces.destroyBuffers();
            faces = null;
        }
    }

    /**
     * Shader-pack path for step 1: the camera is set up for this frame but the pack has not started the level
     * render, so vanilla shaders still draw into our target. Rebuilds the view rotation the way
     * {@code GameRenderer.renderLevel} does from the final angles (runs at the lowest priority, after other mods).
     */
    static void drawReflectionsBeforeLevel(ViewportEvent.ComputeCameraAngles event) {
        if (!ShaderpackDetector.packActive()) {
            return;
        }
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(Axis.ZP.rotationDegrees(event.getRoll()));
        poseStack.mulPose(Axis.XP.rotationDegrees(event.getPitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(event.getYaw() + 180.0f));
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = new Frustum(poseStack.last().pose(), RenderSystem.getProjectionMatrix());
        frustum.prepare(cam.x, cam.y, cam.z);
        drawReflections(poseStack, event.getCamera(), frustum, (float) event.getPartialTick());
    }

    /** Vanilla path for step 1, at {@code AFTER_SOLID_BLOCKS}. */
    static void drawReflectionsInLevel(PoseStack poseStack, Camera camera, Frustum frustum, float partialTick) {
        if (!ShaderpackDetector.packActive()) {
            drawReflections(poseStack, camera, frustum, partialTick);
        }
    }

    private static void drawReflections(PoseStack poseStack, Camera camera, Frustum frustum, float partialTick) {
        PENDING.clear();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        int maxPlanes = BHFxConfig.MIRROR_MAX_PLANES.get();
        if (level == null || composite == null || maxPlanes == 0 || MirrorTracker.isEmpty()) {
            return;
        }
        if (level.getGameTime() != seenTick) {
            seenTick = level.getGameTime();
            SEEN.clear();
        }
        Vec3 cam = camera.getPosition();
        List<Plane> planes = collect(cam, frustum, maxPlanes);
        int budget = BHFxConfig.MIRROR_MAX_REFLECTIONS.get(); // player draws, nearest surfaces first
        for (Plane plane : planes) {
            if (budget <= 0) {
                break;
            }
            plane.players = reflected(level, plane, cam, partialTick);
            if (plane.players.size() > budget) {
                plane.players = plane.players.subList(0, budget);
            }
            if (!plane.players.isEmpty()) {
                budget -= plane.players.size();
                PENDING.add(plane);
            }
        }
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.sort(Comparator.comparingDouble((Plane p) -> p.nearest).reversed());

        RenderTarget main = mc.getMainRenderTarget();
        TextureTarget target = target(main);
        Matrix4f pose = poseStack.last().pose();
        DRAWN_POSE.set(pose);
        DRAWN_PROJECTION.set(RenderSystem.getProjectionMatrix());
        int sceneDepth = -1;
        try {
            if (area != null) {
                sceneDepth = drawFaceDepth(main, pose, cam);
                area.safeGetUniform("DepthRange").set(NEAR, mc.gameRenderer.getDepthFar());
            }
            target.bindWrite(true);
            RenderSystem.clearColor(0f, 0f, 0f, 0f);
            RenderSystem.clearDepth(0.0); // everything starts closed
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.clearDepth(1.0);
            // vanilla sets the entity lights only after the cutout layers
            if (level.effects().constantAmbientLight()) {
                Lighting.setupNetherLevel(pose);
            } else {
                Lighting.setupLevel(pose);
            }
            for (Plane plane : PENDING) {
                setArea(plane, pose, cam, 1.0, true, sceneDepth);
                drawPlayers(mc, plane, poseStack, cam, partialTick);
                setArea(plane, pose, cam, 0.0, false, sceneDepth);
            }
        } finally {
            main.bindWrite(true);
            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glDepthRange(0.0, 1.0);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
        }
    }

    /** The mirror faces, depth-tested against the world, each pixel taken from the reflection target. */
    static void composite(PoseStack poseStack, Camera camera) {
        if (PENDING.isEmpty() || reflection == null || composite == null) {
            PENDING.clear();
            return;
        }
        if (ShaderpackDetector.packActive()) {
            compositeForPack(poseStack, camera);
            PENDING.clear();
            return;
        }
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> composite);
        RenderSystem.setShaderTexture(0, reflection.getColorTextureId());
        drawQuads(PENDING, poseStack.last().pose(), camera.getPosition());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        PENDING.clear();
    }

    /**
     * Shader-pack path for step 2: the faces go through {@code entityCutoutNoCull} with the reflection as texture, so
     * they land in the pack's buffers and get its lighting. The UVs are each vertex's screen position under the
     * matrices the reflection was drawn with; faces are split into strips because UVs are interpolated
     * perspective-correctly, not in screen space. Full-bright light, since the reflection already carries its own.
     * Empty reflection pixels are cut out, so the block texture shows there.
     */
    private static void compositeForPack(PoseStack poseStack, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (!textureRegistered) {
            mc.getTextureManager().register(REFLECTION_TEXTURE, new ReflectionTexture());
            textureRegistered = true;
        }
        Vec3 cam = camera.getPosition();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        Matrix4f toScreen = new Matrix4f(DRAWN_PROJECTION).mul(DRAWN_POSE);
        VertexConsumer out = BUFFERS.getBuffer(RenderType.entityCutoutNoCull(REFLECTION_TEXTURE));
        for (Plane plane : PENDING) {
            Direction.Axis axis = plane.facing.getAxis();
            double depth = plane.at + plane.facing.getAxisDirection().getStep() * LIFT;
            Vec3 n = Vec3.atLowerCornerOf(plane.facing.getNormal());
            for (MirrorTracker.Cluster cluster : plane.clusters) {
                int[] rects = cluster.rects();
                for (int r = 0; r < rects.length; r += 4) {
                    int cellsU = (int) Math.ceil((rects[r + 2] - rects[r]) / PACK_CELL);
                    int cellsV = (int) Math.ceil((rects[r + 3] - rects[r + 1]) / PACK_CELL);
                    double du = (rects[r + 2] - rects[r]) / (double) cellsU;
                    double dv = (rects[r + 3] - rects[r + 1]) / (double) cellsV;
                    for (int i = 0; i < cellsU; i++) {
                        for (int j = 0; j < cellsV; j++) {
                            double a = rects[r] + i * du;
                            double b = rects[r + 1] + j * dv;
                            corner(CELL[0], axis, depth, a, b, cam);
                            corner(CELL[1], axis, depth, a + du, b, cam);
                            corner(CELL[2], axis, depth, a + du, b + dv, cam);
                            corner(CELL[3], axis, depth, a, b + dv, cam);
                            boolean behind = false;
                            for (int k = 0; k < 4; k++) {
                                toScreen.transform(CELL[k], CELL_SCREEN[k]);
                                behind |= CELL_SCREEN[k].w < 0.05f;
                            }
                            if (behind) {
                                continue; // a cell crossing the camera plane has no sensible screen UV
                            }
                            for (int k = 0; k < 4; k++) {
                                Vector4f c = CELL[k];
                                Vector4f sc = CELL_SCREEN[k];
                                out.vertex(pose, c.x, c.y, c.z)
                                        .color(1f, 1f, 1f, 1f)
                                        .uv(sc.x / sc.w * 0.5f + 0.5f, sc.y / sc.w * 0.5f + 0.5f)
                                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                                        .uv2(LightTexture.FULL_BRIGHT)
                                        .normal(normalMatrix, (float) n.x, (float) n.y, (float) n.z)
                                        .endVertex();
                            }
                        }
                    }
                }
            }
        }
        BUFFERS.endBatch();
    }

    private static void corner(Vector4f out, Direction.Axis axis, double depth, double u, double v, Vec3 cam) {
        double x = axis == Direction.Axis.X ? depth : u;
        double y = axis == Direction.Axis.Y ? depth : axis == Direction.Axis.X ? u : v;
        double z = axis == Direction.Axis.Z ? depth : v;
        out.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
    }

    /** Hands the reflection target's colour texture to the texture manager under {@link #REFLECTION_TEXTURE}. */
    private static final class ReflectionTexture extends AbstractTexture {
        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public int getId() {
            return reflection != null ? reflection.getColorTextureId() : MissingTextureAtlasSprite.getTexture().getId();
        }

        @Override
        public void releaseId() {
            // owned by the TextureTarget
        }
    }

    /**
     * Depth of every surface in {@link #PENDING}, for {@link #setArea}. Built from the mirror faces rather than taken
     * from the main target, because with a shader pack the reflections are drawn before the level render, when the
     * main depth is still last frame's (or the pack's own). Returns the depth texture.
     */
    private static int drawFaceDepth(RenderTarget main, Matrix4f pose, Vec3 cam) {
        if (faces == null) {
            faces = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (faces.width != main.width || faces.height != main.height) {
            faces.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        faces.bindWrite(true);
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        drawQuads(PENDING, pose, cam);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableCull();
        return faces.getDepthTextureId();
    }

    private static TextureTarget target(RenderTarget main) {
        if (reflection == null) {
            reflection = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (reflection.width != main.width || reflection.height != main.height) {
            reflection.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        return reflection;
    }

    /**
     * Writes {@code depth} over the surface's screen area in the reflection target: 1 opens it for the players (and
     * clears colour another surface may have left there), 0 closes it again. With {@code sceneDepth} (from
     * {@link #drawFaceDepth}, or -1) only the pixels where no nearer mirror face hides the surface are touched;
     * without it a surface hidden behind another one, such as a mirror floor going on behind a mirror wall, would wipe
     * that one's reflection.
     */
    private static void setArea(Plane plane, Matrix4f pose, Vec3 cam, double depth, boolean clearColour,
            int sceneDepth) {
        RenderSystem.colorMask(clearColour, clearColour, clearColour, clearColour);
        RenderSystem.setShaderColor(0f, 0f, 0f, 0f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();
        if (sceneDepth >= 0 && area != null) {
            area.safeGetUniform("AreaDepth").set((float) depth);
            RenderSystem.setShaderTexture(0, sceneDepth);
            RenderSystem.setShader(() -> area);
        } else {
            GL11.glDepthRange(depth, depth);
            RenderSystem.setShader(GameRenderer::getPositionShader);
        }
        drawQuads(List.of(plane), pose, cam);
        GL11.glDepthRange(0.0, 1.0);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableCull();
    }

    /**
     * The nearest mirror surfaces in range, facing the camera and in view. Only the chunk sections around the camera
     * are looked at, and whole clusters are culled, never single blocks.
     */
    private static List<Plane> collect(Vec3 cam, Frustum frustum, int maxPlanes) {
        double range = BHFxConfig.MIRROR_DISTANCE.get();
        Map<Long, Plane> byPlane = new HashMap<>();
        MirrorTracker.forEachNear(cam, range, cluster -> {
            Direction facing = cluster.key.facing();
            if ((axis(cam, facing.getAxis()) - cluster.faceAt) * facing.getAxisDirection().getStep() <= 0) {
                return; // looking at its back
            }
            AABB box = cluster.bounds();
            double dist = distanceSqr(box, cam);
            if (dist > range * range || !frustum.isVisible(box)) {
                return;
            }
            Plane plane = byPlane.computeIfAbsent(cluster.key.planeId(), id -> new Plane(id, facing, cluster.faceAt));
            plane.add(cluster, dist);
        });
        List<Plane> planes = new ArrayList<>(byPlane.values());
        planes.sort(Comparator.comparingDouble(p -> p.nearest));
        return planes.size() > maxPlanes ? planes.subList(0, maxPlanes) : planes;
    }

    private static List<Player> reflected(ClientLevel level, Plane plane, Vec3 cam, float partialTick) {
        Direction.Axis axis = plane.facing.getAxis();
        int sign = plane.facing.getAxisDirection().getStep();
        double range = BHFxConfig.MIRROR_PLAYER_RANGE.get();
        List<Player> players = new ArrayList<>();
        for (Player player : level.players()) {
            // the box centre, not the feet: someone standing on a floor mirror has their feet exactly on the plane
            if (player.isSpectator() || (axis(player.getBoundingBox().getCenter(), axis) - plane.at) * sign <= 0
                    || distanceSqr(plane.bounds, player.position()) > range * range) {
                continue;
            }
            Map<Integer, Boolean> seen = SEEN.computeIfAbsent(plane.id, id -> new HashMap<>());
            if (seen.computeIfAbsent(player.getId(), id -> seen(level, plane, player, cam, partialTick))) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Whether the camera can see any of {@code player} in this surface. For points spread over their box (eyes, top,
     * middle, feet, the four sides), follow the camera's ray to the reflected point; where it meets a mirror of this
     * surface, the player counts as seen if the way from there to the real point is clear of blocks. When no point
     * lands on a mirror (only an arm or a Figura part may reach it), try from the mirror point nearest to the middle
     * one. Visual shapes, so glass does not hide anyone.
     */
    private static boolean seen(ClientLevel level, Plane plane, Player player, Vec3 cam, float partialTick) {
        Direction.Axis axis = plane.facing.getAxis();
        Vec3 normal = Vec3.atLowerCornerOf(plane.facing.getNormal());
        Vec3 feet = player.getPosition(partialTick);
        double h = player.getBbHeight();
        double w = player.getBbWidth() * 0.5;
        Vec3 middle = feet.add(0, h * 0.5, 0);
        Vec3[] samples = {player.getEyePosition(partialTick), feet.add(0, h - 0.05, 0), middle, feet.add(0, 0.1, 0),
                middle.add(w, 0, 0), middle.add(-w, 0, 0), middle.add(0, 0, w), middle.add(0, 0, -w)};
        Vec3 middleHit = null;
        for (Vec3 sample : samples) {
            Vec3 hit = mirrorPoint(plane, axis, cam, sample);
            if (hit == null) {
                continue;
            }
            if (sample == middle) {
                middleHit = hit;
            }
            if (onPlane(plane, hit, normal) && clear(level, hit.add(normal.scale(0.05)), sample, player)) {
                return true;
            }
        }
        if (middleHit == null || plane.bounds == null) {
            return false;
        }
        Vec3 nearest = new Vec3(Mth.clamp(middleHit.x, plane.bounds.minX + 0.01, plane.bounds.maxX - 0.01),
                Mth.clamp(middleHit.y, plane.bounds.minY + 0.01, plane.bounds.maxY - 0.01),
                Mth.clamp(middleHit.z, plane.bounds.minZ + 0.01, plane.bounds.maxZ - 0.01));
        nearest = with(nearest, axis, plane.at);
        // only an arm's length off the mirror; farther, nothing of the player can show in it
        if (nearest.distanceToSqr(middleHit) > 1.0 || !onPlane(plane, nearest, normal)) {
            return false;
        }
        Vec3 from = nearest.add(normal.scale(0.05));
        return clear(level, from, middle, player) || clear(level, from, samples[0], player);
    }

    /** Where the camera's ray to the reflection of {@code point} crosses the surface, or null if it doesn't. */
    @Nullable
    private static Vec3 mirrorPoint(Plane plane, Direction.Axis axis, Vec3 cam, Vec3 point) {
        double image = 2 * plane.at - axis(point, axis);
        double denom = image - axis(cam, axis);
        if (Math.abs(denom) < 1e-6) {
            return null;
        }
        double t = (plane.at - axis(cam, axis)) / denom;
        if (t <= 0 || t >= 1) {
            return null;
        }
        Vec3 imagePoint = with(point, axis, image);
        return cam.add(imagePoint.subtract(cam).scale(t));
    }

    private static boolean onPlane(Plane plane, Vec3 facePoint, Vec3 normal) {
        return MirrorTracker.hasFace(BlockPos.containing(facePoint.subtract(normal.scale(0.5))), plane.facing);
    }

    private static boolean clear(ClientLevel level, Vec3 from, Vec3 to, Player player) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    private static void drawPlayers(Minecraft mc, Plane plane, PoseStack poseStack, Vec3 cam, float partialTick) {
        Direction.Axis axis = plane.facing.getAxis();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        double rel = plane.at - axis(cam, axis);
        poseStack.pushPose();
        // reflect across the plane in camera-relative space; set both matrices by hand because PoseStack.scale
        // mishandles the normal matrix for a negative determinant
        poseStack.translate(axis == Direction.Axis.X ? rel : 0, axis == Direction.Axis.Y ? rel : 0,
                axis == Direction.Axis.Z ? rel : 0);
        float sx = axis == Direction.Axis.X ? -1f : 1f;
        float sy = axis == Direction.Axis.Y ? -1f : 1f;
        float sz = axis == Direction.Axis.Z ? -1f : 1f;
        poseStack.last().pose().scale(sx, sy, sz);
        poseStack.last().normal().scale(sx, sy, sz);
        poseStack.translate(axis == Direction.Axis.X ? -rel : 0, axis == Direction.Axis.Y ? -rel : 0,
                axis == Direction.Axis.Z ? -rel : 0);

        Runnable restoreFigura = FiguraMirror.begin(plane.players);
        GL11.glFrontFace(GL11.GL_CW);
        dispatcher.setRenderShadow(false);
        drawing = true;
        try {
            for (Player player : plane.players) {
                double x = Mth.lerp(partialTick, player.xOld, player.getX()) - cam.x;
                double y = Mth.lerp(partialTick, player.yOld, player.getY()) - cam.y;
                double z = Mth.lerp(partialTick, player.zOld, player.getZ()) - cam.z;
                float yRot = Mth.lerp(partialTick, player.yRotO, player.getYRot());
                dispatcher.render(player, x, y, z, yRot, partialTick, poseStack, BUFFERS,
                        dispatcher.getPackedLightCoords(player, partialTick));
            }
            BUFFERS.endBatch();
        } finally {
            drawing = false;
            restoreFigura.run();
            dispatcher.setRenderShadow(true);
            GL11.glFrontFace(GL11.GL_CCW);
            poseStack.popPose();
        }
    }

    /** Quads over the reflecting face of every mirror in the surfaces, drawn with the current shader. */
    private static void drawQuads(List<Plane> planes, Matrix4f pose, Vec3 cam) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (Plane plane : planes) {
            Direction.Axis axis = plane.facing.getAxis();
            double depth = plane.at + plane.facing.getAxisDirection().getStep() * LIFT;
            for (MirrorTracker.Cluster cluster : plane.clusters) {
                int[] rects = cluster.rects();
                for (int r = 0; r < rects.length; r += 4) {
                    // winding does not matter, culling is off
                    vertex(builder, pose, axis, depth, rects[r], rects[r + 1], cam);
                    vertex(builder, pose, axis, depth, rects[r + 2], rects[r + 1], cam);
                    vertex(builder, pose, axis, depth, rects[r + 2], rects[r + 3], cam);
                    vertex(builder, pose, axis, depth, rects[r], rects[r + 3], cam);
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, Direction.Axis axis, double depth, double u,
            double v, Vec3 cam) {
        double x = axis == Direction.Axis.X ? depth : u;
        double y = axis == Direction.Axis.Y ? depth : axis == Direction.Axis.X ? u : v;
        double z = axis == Direction.Axis.Z ? depth : v;
        builder.vertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).endVertex();
    }

    private static double axis(Vec3 v, Direction.Axis axis) {
        return axis.choose(v.x, v.y, v.z);
    }

    private static Vec3 with(Vec3 v, Direction.Axis axis, double value) {
        return new Vec3(axis == Direction.Axis.X ? value : v.x, axis == Direction.Axis.Y ? value : v.y,
                axis == Direction.Axis.Z ? value : v.z);
    }

    private static double distanceSqr(AABB box, Vec3 p) {
        double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** One flat mirror surface: the visible clusters that share a facing and a face plane. */
    private static final class Plane {
        final long id;
        final Direction facing;
        final double at;
        final List<MirrorTracker.Cluster> clusters = new ArrayList<>();
        AABB bounds;
        double nearest = Double.MAX_VALUE;
        List<Player> players = List.of();

        Plane(long id, Direction facing, double at) {
            this.id = id;
            this.facing = facing;
            this.at = at;
        }

        void add(MirrorTracker.Cluster cluster, double dist) {
            clusters.add(cluster);
            bounds = bounds == null ? cluster.bounds() : bounds.minmax(cluster.bounds());
            nearest = Math.min(nearest, dist);
        }
    }
}
