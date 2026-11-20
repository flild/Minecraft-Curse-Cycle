package com.cursecycle.challenge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FloorIsLavaPlacementsTest {
    @Test
    void blockLimitRejectsNewMarksWithoutEvictingExistingSafety() {
        var marks = new FloorIsLavaPlacements.Marks(2, 10);
        var first = new BlockPos(0, 0, 0);
        var second = first.east();
        var third = second.east();
        assertTrue(marks.add(World.OVERWORLD, first));
        assertTrue(marks.add(World.OVERWORLD, second));
        for (int i = 0; i < 100; i++) {
            assertFalse(marks.add(World.OVERWORLD, third));
            assertTrue(marks.add(World.OVERWORLD, first));
        }
        assertEquals(2, marks.size());
        assertEquals(1, marks.sectionCount());
        assertTrue(marks.contains(World.OVERWORLD, first));
        assertTrue(marks.contains(World.OVERWORLD, second));
        assertFalse(marks.contains(World.OVERWORLD, third));
        marks.remove(World.OVERWORLD, first);
        assertTrue(marks.add(World.OVERWORLD, third));
        assertEquals(2, marks.size());
    }

    @Test
    void sparsePlacementsAreBoundedAndEmptySectionsAreReleased() {
        var marks = new FloorIsLavaPlacements.Marks(100, 2);
        var origin = BlockPos.ORIGIN;
        assertTrue(marks.add(World.OVERWORLD, origin));
        assertTrue(marks.add(World.NETHER, origin));
        for (int i = 1; i <= 100; i++)
            assertFalse(marks.add(World.OVERWORLD, new BlockPos(i * 16, 0, 0)));
        assertEquals(2, marks.size());
        assertEquals(2, marks.sectionCount());
        assertFalse(marks.contains(World.END, origin));
        marks.remove(World.NETHER, origin);
        marks.remove(World.NETHER, origin);
        assertEquals(1, marks.sectionCount());
        assertTrue(marks.contains(World.OVERWORLD, origin));
        assertTrue(marks.add(World.END, origin));
        // A full section table still accepts additions inside an existing section.
        assertTrue(marks.add(World.END, origin.up()));
        marks.remove(World.OVERWORLD, origin);
        marks.remove(World.END, origin);
        marks.remove(World.END, origin.up());
        assertEquals(0, marks.size());
        assertEquals(0, marks.sectionCount());
    }

    @Test
    void everyBitIsIndependentAcrossNegativeCoordinatesAndSectionBoundaries() {
        var marks = new FloorIsLavaPlacements.Marks(8192, 2);
        for (int y = -16; y < 16; y++) for (int z = -16; z < 0; z++) for (int x = -16; x < 0; x++)
            assertTrue(marks.add(World.OVERWORLD, new BlockPos(x, y, z)));
        assertEquals(8192, marks.size());
        assertEquals(2, marks.sectionCount());
        for (int y = -16; y < 16; y++) for (int z = -16; z < 0; z++) for (int x = -16; x < 0; x++) {
            var pos = new BlockPos(x, y, z);
            assertTrue(marks.contains(World.OVERWORLD, pos));
            assertFalse(marks.contains(World.NETHER, pos));
            marks.remove(World.OVERWORLD, pos);
            assertFalse(marks.contains(World.OVERWORLD, pos));
        }
        assertEquals(0, marks.size());
        assertEquals(0, marks.sectionCount());
    }
}
