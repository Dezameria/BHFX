package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxFrameTest {
    private static final double EPS = 1e-9;

    private static void near(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < 1e-6, () -> "expected " + expected + " got " + actual);
    }

    private static void orthonormal(FxFrame f) {
        assertEquals(1.0, f.x().length(), 1e-9);
        assertEquals(1.0, f.y().length(), 1e-9);
        assertEquals(1.0, f.z().length(), 1e-9);
        assertEquals(0.0, f.x().dot(f.y()), 1e-9);
        assertEquals(0.0, f.y().dot(f.z()), 1e-9);
        assertEquals(0.0, f.z().dot(f.x()), 1e-9);
    }

    @Test
    void turnsGoTheDocumentedWay() {
        FxFrame f = FxFrame.at(Vec3.ZERO);
        double q = Math.PI / 2;
        near(new Vec3(0, 0, 1), f.turnedX(q).y());   // y towards z
        near(new Vec3(0, -1, 0), f.turnedX(q).z());
        near(new Vec3(1, 0, 0), f.turnedY(q).z());   // z towards x
        near(new Vec3(0, 1, 0), f.turnedZ(q).x());   // x towards y
    }

    @Test
    void chainsStayOrthonormal() {
        FxFrame f = FxFrame.looking(new Vec3(3, 4, 5), new Vec3(1, 2, 3), new Vec3(0, 1, 0));
        for (int i = 0; i < 200; i++) {
            f = f.turnedX(0.37).turnedY(-0.21).turnedZ(0.13).moved(0.1, 0, 0.2);
        }
        orthonormal(f);
        orthonormal(f.turned(new Vec3(0, 1, 0), 1.1));
    }

    @Test
    void lookingMatchesBodyPoseHandedness() {
        // facing +z with y up, a body part's right is -x
        FxFrame f = FxFrame.looking(Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 1, 0));
        near(new Vec3(-1, 0, 0), f.x());
        near(new Vec3(0, 1, 0), f.y());
        near(new Vec3(0, 0, 1), f.z());
        // straight up with up also straight up still gives a frame
        orthonormal(FxFrame.looking(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 1, 0)));
    }

    @Test
    void localAndWorldRoundTrip() {
        FxFrame f = FxFrame.looking(new Vec3(10, 64, -3), new Vec3(0.3, -0.2, 1), new Vec3(0, 1, 0)).turnedZ(0.4);
        Vec3 p = new Vec3(12.5, 66, -1);
        Vec3 l = f.toLocal(p);
        near(p, f.point(l.x, l.y, l.z));
        near(f.point(1, 2, 3), f.moved(1, 2, 3).origin());
        near(new Vec3(7, 60, -3), f.relativeTo(new Vec3(3, 4, 0)).origin());
    }

    @Test
    void twoBoneJointKeepsBoneLengths() {
        Vec3 root = new Vec3(0, 10, 0);
        Vec3 target = new Vec3(1.2, 9.1, 0.4);
        Vec3 pole = new Vec3(0, 10, 5);
        Vec3 joint = FxFrame.twoBoneJoint(root, target, 1.0, 0.9, pole);
        assertEquals(1.0, joint.distanceTo(root), 1e-6);
        assertEquals(0.9, joint.distanceTo(target), 1e-6);
        // bends towards the pole
        Vec3 dir = target.subtract(root).normalize();
        Vec3 mid = root.add(dir.scale(joint.subtract(root).dot(dir)));
        assertTrue(joint.subtract(mid).dot(pole.subtract(root)) > 0);
    }

    @Test
    void twoBoneJointOutOfReachLiesOnTheLine() {
        Vec3 joint = FxFrame.twoBoneJoint(Vec3.ZERO, new Vec3(10, 0, 0), 1.0, 1.0, new Vec3(0, 1, 0));
        assertEquals(1.0, joint.x, 1e-3);
        assertEquals(0.0, joint.y, 1e-2);
        assertEquals(0.0, joint.z, EPS);
    }
}
