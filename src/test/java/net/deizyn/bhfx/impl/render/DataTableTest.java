package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.api.client.FxData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataTableTest {
    @Test
    void newTableUploadsEverythingOnce() {
        DataTable t = new DataTable(10);
        assertEquals(0, t.uploadFrom());
        assertEquals(10, t.uploadTo());
        t.uploaded();
        assertEquals(-1, t.uploadFrom());
    }

    @Test
    void writesMergeIntoOneRange() {
        DataTable t = new DataTable(100);
        t.uploaded();
        t.set(40, 1, 2, 3, 4);
        t.set(12, 0, 0, 0, 0);
        t.fill(60, 63, 5, 5, 5, 5);
        assertEquals(12, t.uploadFrom());
        assertEquals(63, t.uploadTo());
        t.uploaded();
        assertEquals(-1, t.uploadFrom());
        t.put(7, new float[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertEquals(7, t.uploadFrom());
        assertEquals(9, t.uploadTo());
    }

    @Test
    void lostGpuSendsEverythingAgain() {
        DataTable t = new DataTable(8);
        t.uploaded();
        t.set(3, 1, 1, 1, 1);
        t.lostGpu();
        assertEquals(0, t.uploadFrom());
        assertEquals(8, t.uploadTo());
    }

    @Test
    void valuesRoundTrip() {
        DataTable t = new DataTable(4);
        t.set(2, 1.5f, -2f, 3f, 0.25f);
        assertEquals(1.5f, t.get(2, 0));
        assertEquals(-2f, t.get(2, 1));
        assertEquals(3f, t.get(2, 2));
        assertEquals(0.25f, t.get(2, 3));
        t.put(0, new float[]{9, 8, 7, 6});
        assertEquals(6f, t.get(0, 3));
        assertEquals(0f, t.get(3, 0));
    }

    @Test
    void sizeIsClamped() {
        assertEquals(1, new DataTable(0).size());
        assertEquals(FxData.MAX_SIZE, new DataTable(FxData.MAX_SIZE + 5).size());
    }

    @Test
    void outOfRangeThrows() {
        DataTable t = new DataTable(4);
        assertThrows(IndexOutOfBoundsException.class, () -> t.set(4, 0, 0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.set(-1, 0, 0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.put(3, new float[8]));
        assertThrows(IndexOutOfBoundsException.class, () -> t.fill(2, 5, 0, 0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.get(0, 4));
        assertThrows(IllegalArgumentException.class, () -> t.put(0, new float[3]));
    }

    @Test
    void emptyWritesChangeNothing() {
        DataTable t = new DataTable(4);
        t.uploaded();
        t.put(0, new float[0]);
        t.fill(2, 2, 1, 1, 1, 1);
        assertEquals(-1, t.uploadFrom());
    }
}
