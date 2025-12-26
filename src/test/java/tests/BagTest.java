package tests;

import codes.Bag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class BagTest {

    @Test
    void newBag_isEmpty() {
        Bag<Integer> b = new Bag<>();
        assertTrue(b.isEmpty());
        assertEquals(0, b.size());

        Iterator<Integer> it = b.iterator();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void add_incrementsSize_andIterationReturnsAllItems() {
        Bag<Integer> b = new Bag<>();
        b.add(1);
        b.add(2);
        b.add(3);

        assertFalse(b.isEmpty());
        assertEquals(3, b.size());

        ArrayList<Integer> out = new ArrayList<>();
        for (int x : b) out.add(x);

        // Order depends on your Bag implementation (tail pointer means insertion order).
        // Here we only assert it contains all items.
        assertEquals(3, out.size());
        assertTrue(out.contains(1));
        assertTrue(out.contains(2));
        assertTrue(out.contains(3));
    }
}