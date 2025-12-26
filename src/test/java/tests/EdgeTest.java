package tests;

import codes.Edge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeTest {

    @Test
    void constructor_negativeEndpoint_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Edge(-1, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Edge(0, -1, 1.0));
    }

    @Test
    void constructor_nanWeight_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Edge(0, 1, Double.NaN));
    }

    @Test
    void id_initiallyMinusOne() {
        Edge e = new Edge(0, 1, 1.0);
        assertEquals(-1, e.edgeID());
    }

    @Test
    void setId_writeOnce() {
        Edge e = new Edge(0, 1, 1.0);
        assertEquals(-1, e.edgeID());
        e.setiD(0);
        assertEquals(0, e.edgeID());

        // Your setiD should be write-once and throw if called again.
        assertThrows(IllegalStateException.class, () -> e.setiD(1));
    }

    @Test
    void endpoints_stable() {
        Edge e = new Edge(3, 7, 2.5);
        assertEquals(3, e.firstEnd());
        assertEquals(7, e.otherEnd());
    }
}