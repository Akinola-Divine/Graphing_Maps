package codes;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A generic bag (multiset) implementation using a singly linked list.
 *
 * <p>A bag is a collection that supports adding items but not removing them.
 * It supports iteration over the items in no particular order.</p>
 *
 * <p>Design inspired by Robert Sedgewick and Kevin Wayne's {@code codes.Bag} class
 * from <i>Algorithms, 4th Edition</i> (Addison-Wesley, 2011).</p>
 *
 * @param <T> the type of elements in this bag
 * @see <a href="https://algs4.cs.princeton.edu/13stacks/">Stacks and Queues - Algorithms, 4th Edition</a>
 */
public class Bag<T> implements Iterable<T> {

    private Node<T> head;    // beginning of bag
    private Node<T> tail;   // end of bag
    private int size;      // number of elements in bag

    /**
     * A helper linked list node class.
     */
    private static class Node<T> {
        private T data;
        private Node<T> next;
    }

    /**
     * Initializes an empty bag.
     */
    public Bag() {
        head = null;
        tail = null;
        size = 0;
    }

    /**
     * Adds an item to the bag.
     *
     * @param data the item to add
     */
    public void add(T data) {
        Node<T> node = new Node<>();
        node.data = data;

        if (head == null) {
            head = node;
            tail = head;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /**
     * Returns true if this bag is empty.
     *
     * @return {@code true} if this bag is empty; {@code false} otherwise
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of items in this bag.
     *
     * @return the number of items in this bag
     */
    public int size() {
        return size;
    }

    /**
     * Returns an iterator that iterates over the items in this bag.
     *
     * @return an iterator that iterates over the items in this bag
     */
    public Iterator<T> iterator() {
        return new LinkedIterator(head);
    }

    /**
     * An iterator over a linked list.
     */
    private class LinkedIterator implements Iterator<T> {
        private Node<T> current;

        public LinkedIterator(Node<T> first) {
            current = first;
        }

        public boolean hasNext() {
            return current != null;
        }

        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            T item = current.data;
            current = current.next;
            return item;
        }
    }
}