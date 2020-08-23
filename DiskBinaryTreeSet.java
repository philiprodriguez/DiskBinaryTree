import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

/**
 * A class which implements a balanced binary tree which is 100% represented on disk and therefore should not consume any significant amounnt of RAM no matter how many elements are in the on-disk set.
 * <p/>
 * This class does not allow removal. All remove operations will throw an unchecked exception.
 * <p/>
 * The file that data gets stored into is structured as follows:
 * size in number of entries (8 bytes)
 * next empty position (8 bytes)
 * root node address (8 bytes)
 * ...list of node data...
 * <p/>
 * Each entry in the list of node data is of the form:
 * left pointer (8 bytes)
 * right pointer (8 bytes)
 * height of subtree rooted here (4 bytes)
 * payload size (4 bytes)
 * payload (variable size)
 * <p/>
 * Philip Rodriguez, February 2020
 * <p/>
 *
 * @author philipjamesrodriguez@gmail.com
 */
public class DiskBinaryTreeSet<E extends Serializable & Comparable<E>> implements Set<E>, Closeable {
    private static final boolean PRINT_DEBUG_INFORMATION = false;

    private final RandomAccessFile randomAccessFile;

    public DiskBinaryTreeSet(File file) throws IOException {
        randomAccessFile = new RandomAccessFile(file, "rw");

        // Perform file initialization tasks
        if (file.length() <= 0) {
            setSize(0);
            setNextEmptyPosition(16);
            setRootAddress(24);
        }
    }

    private synchronized long getRootAddress() throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(16);
        long ra = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return ra;
    }

    private synchronized void setRootAddress(long rootAddress) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(16);
        randomAccessFile.writeLong(rootAddress);
        randomAccessFile.seek(curPos);
    }

    private synchronized void setSize(long size) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(0);
        randomAccessFile.writeLong(size);
        randomAccessFile.seek(curPos);
    }

    private synchronized long getSize() throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(0);
        long size = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return size;
    }

    private synchronized void setNextEmptyPosition(long pos) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(8);
        randomAccessFile.writeLong(pos);
        randomAccessFile.seek(curPos);
    }

    private synchronized long getNextEmptyPosition() throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(8);
        long pos = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return pos;
    }

    private synchronized long getLeftPointer(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;

        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress);
        long lp = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return lp;
    }

    private synchronized void setLeftPointer(long startAddress, long leftPointer) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress);
        randomAccessFile.writeLong(leftPointer);
        randomAccessFile.seek(curPos);
    }

    private synchronized long getRightPointer(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;

        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+8);
        long rp = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return rp;
    }

    private synchronized void setRightPointer(long startAddress, long rightPointer) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+8);
        randomAccessFile.writeLong(rightPointer);
        randomAccessFile.seek(curPos);
    }

    private synchronized int getHeightOfSubtree(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;

        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+16);
        int hos = randomAccessFile.readInt();
        randomAccessFile.seek(curPos);
        return hos;
    }

    private synchronized void setHeightOfSubtree(long startAddress, int heightOfSubtree) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+16);
        randomAccessFile.writeInt(heightOfSubtree);
        randomAccessFile.seek(curPos);
    }

    private synchronized int getPayloadSize(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;

        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+20);
        int ps = randomAccessFile.readInt();
        randomAccessFile.seek(curPos);
        return ps;
    }

    private synchronized E getPayloadObject(long startAddress) throws IOException, ClassNotFoundException {
        if (startAddress < 0)
            throw new IndexOutOfBoundsException("start address cannot be negative");

        long curPos = randomAccessFile.getFilePointer();
        int payloadSize = getPayloadSize(startAddress);
        byte[] payloadBytes = new byte[payloadSize];
        randomAccessFile.seek(startAddress+24);
        randomAccessFile.read(payloadBytes);
        randomAccessFile.seek(curPos);
        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(payloadBytes));
        return (E) objectInputStream.readObject();
    }

    private synchronized long setPayloadObject(long startAddress, E payloadObject) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(payloadObject);
        objectOutputStream.flush();
        byte[] objectBytes = byteArrayOutputStream.toByteArray();
        objectOutputStream.close();
        randomAccessFile.seek(startAddress+20);
        randomAccessFile.writeInt(objectBytes.length);
        randomAccessFile.seek(startAddress+24);
        randomAccessFile.write(objectBytes);
        long nextPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(curPos);
        return nextPos;
    }

    private static class AddressDescriptor {
        public final Stack<Long> addressStack;

        enum Description {
            EXACT_ADDRESS, PARENT_ADDRESS_LEFT, PARENT_ADDRESS_RIGHT, EMPTY_ROOT_ADDRESS;
        }

        public final Description description;

        public AddressDescriptor(Stack<Long> addressStack, Description description) {
            this.addressStack = addressStack;
            this.description = description;
        }
    }

    /**
     * Searches the tree as necessary and returns one of the following:
     * <p>
     * EXACT_ADDRESS - The item already exists in the tree and the address is the address of the existing item.
     * PARENT_ADDRESS_LEFT - The item does not exist in the tree and the address is that of the parent were the item to have been in the tree. The item belongs to the left of the parent.
     * PARENT_ADDRESS_RIGHT - The item does not exist in the tree and the address is that of the parent were the item to have been in the tree. The item belongs to the right of the parent.
     * EMPTY_ROOT_ADDRESS - The item did not exist in the tree and the address is that of the root node, which is not used or set yet.
     */
    private synchronized AddressDescriptor findAddressForValue(E value) throws IOException, ClassNotFoundException {
        Stack<Long> addressStack = new Stack<>();
        addressStack.push(getRootAddress());

        if (getSize() == 0) {
            return new AddressDescriptor(addressStack, AddressDescriptor.Description.EMPTY_ROOT_ADDRESS);
        }

        while (true) {
            long curNodeAddress = addressStack.peek();
            E curNodeValue = getPayloadObject(curNodeAddress);
            int ctr = value.compareTo(curNodeValue);
            if (ctr < 0) {
                // Go left if not empty
                if (getLeftPointer(curNodeAddress) != -1) {
                    addressStack.push(getLeftPointer(curNodeAddress));
                } else {
                    return new AddressDescriptor(addressStack, AddressDescriptor.Description.PARENT_ADDRESS_LEFT);
                }
            } else if (ctr > 0) {
                // Go right if not empty
                if (getRightPointer(curNodeAddress) != -1) {
                    addressStack.push(getRightPointer(curNodeAddress));
                } else {
                    return new AddressDescriptor(addressStack, AddressDescriptor.Description.PARENT_ADDRESS_RIGHT);
                }
            } else {
                return new AddressDescriptor(addressStack, AddressDescriptor.Description.EXACT_ADDRESS);
            }
        }
    }

    // To perform rotateRight, it is only necessary that axisAddress has a valid left pointer.
    private synchronized void rotateRight(long axisAddress, long parentAddress) throws IOException {
        if (parentAddress == -1) {
            // axisAddress is the root of the tree, and so there is no actual parent, but we'll change the root of the tree.
            setRootAddress(getLeftPointer(axisAddress));
            setLeftPointer(axisAddress, getRightPointer(getRootAddress()));
            setRightPointer(getRootAddress(), axisAddress);

            // Update heights
            setHeightOfSubtree(axisAddress, Math.max(getHeightOfSubtree(getLeftPointer(axisAddress)), getHeightOfSubtree(getRightPointer(axisAddress)))+1);
            setHeightOfSubtree(getRootAddress(), Math.max(getHeightOfSubtree(getLeftPointer(getRootAddress())), getHeightOfSubtree(getRightPointer(getRootAddress())))+1);
        } else {
            // parentAddress is an actual parent node's address. We first need to know which leg of parentAddress to be changing.

            if (getLeftPointer(parentAddress) == axisAddress) {
                // Left leg is "root" to be changing
                setLeftPointer(parentAddress, getLeftPointer(axisAddress));
                setLeftPointer(axisAddress, getRightPointer(getLeftPointer(parentAddress)));
                setRightPointer(getLeftPointer(parentAddress), axisAddress);

                // Update heights
                setHeightOfSubtree(axisAddress, Math.max(getHeightOfSubtree(getLeftPointer(axisAddress)), getHeightOfSubtree(getRightPointer(axisAddress)))+1);
                setHeightOfSubtree(getLeftPointer(parentAddress), Math.max(getHeightOfSubtree(getLeftPointer(getLeftPointer(parentAddress))), getHeightOfSubtree(getRightPointer(getLeftPointer(parentAddress))))+1);
            } else {
                // Right leg is "root" to be changing
                setRightPointer(parentAddress, getLeftPointer(axisAddress));
                setLeftPointer(axisAddress, getRightPointer(getRightPointer(parentAddress)));
                setRightPointer(getRightPointer(parentAddress), axisAddress);

                // Update heights
                setHeightOfSubtree(axisAddress, Math.max(getHeightOfSubtree(getLeftPointer(axisAddress)), getHeightOfSubtree(getRightPointer(axisAddress)))+1);
                setHeightOfSubtree(getRightPointer(parentAddress), Math.max(getHeightOfSubtree(getLeftPointer(getRightPointer(parentAddress))), getHeightOfSubtree(getRightPointer(getRightPointer(parentAddress))))+1);
            }
        }
    }

    // To perform rotateLeft, it is only necessary that axisAddress has a valid right pointer.
    private synchronized void rotateLeft(long axisAddress, long parentAddress) throws IOException {
        if (parentAddress == -1) {
            // axisAddress is the root of the tree, and so there is no actual parent, but we'll change the root of the tree.
            setRootAddress(getRightPointer(axisAddress));
            setRightPointer(axisAddress, getLeftPointer(getRootAddress()));
            setLeftPointer(getRootAddress(), axisAddress);

            // Update heights
            setHeightOfSubtree(axisAddress, Math.max(getHeightOfSubtree(getLeftPointer(axisAddress)), getHeightOfSubtree(getRightPointer(axisAddress)))+1);
            setHeightOfSubtree(getRootAddress(), Math.max(getHeightOfSubtree(getLeftPointer(getRootAddress())), getHeightOfSubtree(getRightPointer(getRootAddress())))+1);
        } else {
            // parentAddress is an actual parent node's address. We first need to know which leg of parentAddress to be changing.

            if (getLeftPointer(parentAddress) == axisAddress) {
                // Left leg is "root" to be changing
                setLeftPointer(parentAddress, getRightPointer(axisAddress));
                setRightPointer(axisAddress, getLeftPointer(getLeftPointer(parentAddress)));
                setLeftPointer(getLeftPointer(parentAddress), axisAddress);

                // Update heights
                setHeightOfSubtree(axisAddress, Math.max(getHeightOfSubtree(getLeftPointer(axisAddress)), getHeightOfSubtree(getRightPointer(axisAddress)))+1);
                setHeightOfSubtree(getLeftPointer(parentAddress), Math.max(getHeightOfSubtree(getLeftPointer(getLeftPointer(parentAddress))), getHeightOfSubtree(getRightPointer(getLeftPointer(parentAddress))))+1);
            } else {
                // Right leg is "root" to be changing
                setRightPointer(parentAddress, getRightPointer(axisAddress));
                setRightPointer(axisAddress, getLeftPointer(getRightPointer(parentAddress)));
                setLeftPointer(getRightPointer(parentAddress), axisAddress);

                // Update heights
                setHeightOfSubtree(axisAddress, Math.max(getHeightOfSubtree(getLeftPointer(axisAddress)), getHeightOfSubtree(getRightPointer(axisAddress)))+1);
                setHeightOfSubtree(getRightPointer(parentAddress), Math.max(getHeightOfSubtree(getLeftPointer(getRightPointer(parentAddress))), getHeightOfSubtree(getRightPointer(getRightPointer(parentAddress))))+1);
            }
        }
    }

    /**
     * Traverse back up the tree according to the stack (usu. from {@code addressDescriptor}), update all relevant heights, and perform any rebalancing actions needed.
     */
    private synchronized void updateHeightsAndBalance(Stack<Long> addressStack) throws IOException {
        while (!addressStack.isEmpty()) {
            long curNodeAddress = addressStack.pop();
            long curNodeAddressParent = addressStack.size() > 0 ? addressStack.peek() : -1;
            long leftNodeAddress = getLeftPointer(curNodeAddress);
            long rightNodeAddress = getRightPointer(curNodeAddress);
            int heightOfLeftSubtree = getHeightOfSubtree(leftNodeAddress);
            int heightOfRightSubtree = getHeightOfSubtree(rightNodeAddress);

            // Do a comparison to see if we must rebalance
            if (Math.abs(heightOfLeftSubtree-heightOfRightSubtree) > 1) {
                if (heightOfLeftSubtree > heightOfRightSubtree) {
                    if (getHeightOfSubtree(getLeftPointer(leftNodeAddress)) > getHeightOfSubtree(getRightPointer(leftNodeAddress))) {
                        // Doubly left heavy -> just right rotate about curNodeAddress
                        rotateRight(curNodeAddress, curNodeAddressParent);
                    } else {
                        // Left heavy then right heavy -> left rotate about leftNodeAddress then right rotate about curNodeAddress
                        rotateLeft(leftNodeAddress, curNodeAddress);
                        rotateRight(curNodeAddress, curNodeAddressParent);
                    }
                } else {
                    // right > left
                    if (getHeightOfSubtree(getRightPointer(rightNodeAddress)) > getHeightOfSubtree(getLeftPointer(rightNodeAddress))) {
                        // Doubly right heavy -> just left rotate about curNodeAddress
                        rotateLeft(curNodeAddress, curNodeAddressParent);
                    } else {
                        // Right heavy then left heavy -> right rotate about rightNodeAddress then left rotate about curNodeAddress
                        rotateRight(rightNodeAddress, curNodeAddress);
                        rotateLeft(curNodeAddress, curNodeAddressParent);
                    }
                }
            } else {
                setHeightOfSubtree(curNodeAddress, Math.max(getHeightOfSubtree(getLeftPointer(curNodeAddress)), getHeightOfSubtree(getRightPointer(curNodeAddress)))+1);
            }
        }
    }

    @Override
    public synchronized boolean add(E value) {
        try {
            AddressDescriptor addressDescriptor = findAddressForValue(value);
            if (addressDescriptor.description == AddressDescriptor.Description.EMPTY_ROOT_ADDRESS) {
                // Just plop in the root
                setLeftPointer(addressDescriptor.addressStack.peek(), -1L);
                setRightPointer(addressDescriptor.addressStack.peek(), -1L);
                setNextEmptyPosition(setPayloadObject(addressDescriptor.addressStack.peek(), value));
                updateHeightsAndBalance(addressDescriptor.addressStack);
                setSize(1);
                if (PRINT_DEBUG_INFORMATION)
                    System.out.println("Added item " + value + "; tree has height " + getHeightOfSubtree(getRootAddress()) + " and tree has total size " + getSize());
                return true;
            } else if (addressDescriptor.description == AddressDescriptor.Description.PARENT_ADDRESS_LEFT) {
                // Plop it in to the left
                long newNodeAddress = getNextEmptyPosition();
                setLeftPointer(newNodeAddress, -1L);
                setRightPointer(newNodeAddress, -1L);
                setNextEmptyPosition(setPayloadObject(newNodeAddress, value));

                setLeftPointer(addressDescriptor.addressStack.peek(), newNodeAddress);

                addressDescriptor.addressStack.push(newNodeAddress);
                updateHeightsAndBalance(addressDescriptor.addressStack);

                setSize(getSize()+1);
                if (PRINT_DEBUG_INFORMATION)
                    System.out.println("Added item " + value + "; tree has height " + getHeightOfSubtree(getRootAddress()) + " and tree has total size " + getSize());
                return true;
            } else if (addressDescriptor.description == AddressDescriptor.Description.PARENT_ADDRESS_RIGHT) {
                // Plop it in to the right
                long newNodeAddress = getNextEmptyPosition();
                setLeftPointer(newNodeAddress, -1L);
                setRightPointer(newNodeAddress, -1L);
                setNextEmptyPosition(setPayloadObject(newNodeAddress, value));

                setRightPointer(addressDescriptor.addressStack.peek(), newNodeAddress);

                addressDescriptor.addressStack.push(newNodeAddress);
                updateHeightsAndBalance(addressDescriptor.addressStack);

                setSize(getSize()+1);
                if (PRINT_DEBUG_INFORMATION)
                    System.out.println("Added item " + value + "; tree has height " + getHeightOfSubtree(getRootAddress()) + " and tree has total size " + getSize());
                return true;
            } else {
                // EXACT_ADDRESS, the item already exists in the set!
                if (PRINT_DEBUG_INFORMATION)
                    System.out.println("Added item " + value + "; tree has height " + getHeightOfSubtree(getRootAddress()) + " and tree has total size " + getSize());
                return false;
            }
        } catch (IOException | ClassNotFoundException exc) {
            return false;
        }
    }

    @Override
    public synchronized boolean contains(Object o) {
        try {
            E value = (E) o;
            AddressDescriptor addressDescriptor = findAddressForValue(value);
            return addressDescriptor.description == AddressDescriptor.Description.EXACT_ADDRESS;
        } catch (ClassNotFoundException | ClassCastException exc) {
            return false;
        } catch (IOException exc) {
            throw new IllegalStateException(exc);
        }
    }

    @Override
    public synchronized int size() {
        long actual = actualSize();
        return (int) Math.min(actual, Integer.MAX_VALUE);
    }

    public synchronized long actualSize() {
        try {
            return getSize();
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to read size, cannot recover.");
        }
    }

    @Override
    public synchronized boolean isEmpty() {
        try {
            return getSize() == 0;
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to read size and therefore failed to check emptiness, cannot recover.");
        }
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> collection) {
        boolean anySucceed = false;
        for (E value : collection) {
            anySucceed |= add(value);
        }
        return anySucceed;
    }

    private synchronized E higherRec(long currentAddress, E value) throws IOException, ClassNotFoundException {
        if (currentAddress == -1) {
            // No subtree here.
            return null;
        }
        E currentValue = getPayloadObject(currentAddress);
        if (currentValue.compareTo(value) <= 0) {
            // We are smaller than or equal to value, so it could not be us, but it could be someone 
            // in our right subtree. If our right subtree's got no answer, then we also have no answer.
            return higherRec(getRightPointer(currentAddress), value); 
        }
        else {
            // Current value is too big, it could be us, but it could also be in our left subtree.
            E valLeft = higherRec(getLeftPointer(currentAddress), value);
            if (valLeft == null) {
                // We're the answer.
                return currentValue;
            } else {
                // Better answer was found in our left subtree.
                return valLeft;
            }
        }
    }

    public synchronized E higher(E value) {
        try {
            if (isEmpty()) {
                return null;
            }

            return higherRec(getRootAddress(), value);
        } catch (ClassNotFoundException exc) {
            throw new IllegalStateException("Failed to parse node payload, cannot recover.");
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to access tree on disk, cannot recover.");
        }
    }

    private synchronized E ceilingRec(long currentAddress, E value) throws IOException, ClassNotFoundException {
        if (currentAddress == -1) {
            // No subtree here.
            return null;
        }
        E currentValue = getPayloadObject(currentAddress);
        if (currentValue.compareTo(value) < 0) {
            // We are smaller than value, so it could not be us, but it could be someone 
            // in our right subtree. If our right subtree's got no answer, then we also have no answer.
            return ceilingRec(getRightPointer(currentAddress), value); 
        } else if (currentValue.compareTo(value) > 0) {
            // Current value is too big, it could be us, but it could also be in our left subtree.
            E valLeft = ceilingRec(getLeftPointer(currentAddress), value);
            if (valLeft == null) {
                // We're the answer.
                return currentValue;
            } else {
                // Better answer was found in our left subtree.
                return valLeft;
            }
        } else {
            // Exact match. We are the answer.
            return currentValue;
        }
    }

    public synchronized E ceiling(E value) {
        try {
            if (isEmpty()) {
                return null;
            }

            return ceilingRec(getRootAddress(), value);
        } catch (ClassNotFoundException exc) {
            throw new IllegalStateException("Failed to parse node payload, cannot recover.");
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to access tree on disk, cannot recover.");
        }
    }

    private synchronized E floorRec(long currentAddress, E value) throws IOException, ClassNotFoundException {
        if (currentAddress == -1) {
            // No subtree here.
            return null;
        }
        E currentValue = getPayloadObject(currentAddress);
        if (currentValue.compareTo(value) > 0) {
            // We are greater than value, so it could not be us, but it could be someone
            // in our left subtree. If our left subtree's got no answer, then we also have no answer.
            return floorRec(getLeftPointer(currentAddress), value);
        } else if (currentValue.compareTo(value) < 0) {
            // Current value is too small, it could be us, but it could also be in our right subtree.
            E valRight = floorRec(getRightPointer(currentAddress), value);
            if (valRight == null) {
                // We're the answer.
                return currentValue;
            } else {
                // Better answer was found in our left subtree.
                return valRight;
            }
        } else {
            // Exact match. We are the answer.
            return currentValue;
        }
    }

    public synchronized E floor(E value) {
        try {
            if (isEmpty()) {
                return null;
            }

            return floorRec(getRootAddress(), value);
        } catch (ClassNotFoundException exc) {
            throw new IllegalStateException("Failed to parse node payload, cannot recover.");
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to access tree on disk, cannot recover.");
        }
    }

    public synchronized E first() {
        try {
            if (isEmpty()) {
                throw new NoSuchElementException();
            }
            long currentAddress = getRootAddress();
            while(getLeftPointer(currentAddress) != -1) {
                currentAddress = getLeftPointer(currentAddress);
            }
            return getPayloadObject(currentAddress);
        } catch (ClassNotFoundException exc) {
            throw new IllegalStateException("Failed to parse node payload, cannot recover.");
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to access tree on disk, cannot recover.");
        }
    }

    public synchronized E last() {
        try {
            if (isEmpty()) {
                throw new NoSuchElementException();
            }
            long currentAddress = getRootAddress();
            while(getRightPointer(currentAddress) != -1) {
                currentAddress = getRightPointer(currentAddress);
            }
            return getPayloadObject(currentAddress);
        } catch (ClassNotFoundException exc) {
            throw new IllegalStateException("Failed to parse node payload, cannot recover.");
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to access tree on disk, cannot recover.");
        }
    }

    @Override
    public synchronized boolean remove(Object o) {
        throw new UnsupportedOperationException("Not implemented! Cannot remove from DiskBinaryTreeSet!");
    }

    @Override
    public synchronized Iterator<E> iterator() {
        return new Iterator<E>() {
            private E current;

            @Override
            public synchronized boolean hasNext() {
                if (current == null && actualSize() > 0)
                    return true;
                return higher(current) != null;
            }

            @Override
            public synchronized E next() {
                try {
                    if (actualSize() <= 0)
                        throw new NoSuchElementException();
                    if (current == null) {
                        current = first();
                        return first();
                    }

                    current = higher(current);
                    if (current == null)
                        throw new NoSuchElementException();
                    
                    // Return a deep copy of current, not current itself, to avoid manipulation of the returned object breaking the iterator
                    return getPayloadObject(findAddressForValue(current).addressStack.peek());
                } catch (ClassNotFoundException exc) {
                    throw new IllegalStateException("Failed to parse node payload, cannot recover.");
                } catch (IOException exc) {
                    throw new IllegalStateException("Failed to access tree on disk, cannot recover.");
                }
            }
        };
    }

    @Override
    public synchronized boolean removeAll(Collection<?> collection) {
        throw new UnsupportedOperationException("Not implemented! Cannot remove from DiskBinaryTreeSet!");
    }

    @Override
    public synchronized boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException("Not implemented! Cannot remove from DiskBinaryTreeSet!");
    }

    @Override
    public synchronized boolean containsAll(Collection<?> collection) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    // Can't remove from DiskBinaryTreeSet, but can delete the backing file to clear it
    @Override
    public synchronized void clear() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public synchronized Object[] toArray() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public synchronized <A> A[] toArray(A[] a) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public synchronized void close() throws IOException {
        randomAccessFile.close();
    }

    /*
        BELOW THIS LINE IS STATIC, TEST-ONLY CODE
    */

    private static void testContainsAndSize_RandomInsertions() throws Exception {
        System.out.println("Running testContainsAndSize_RandomInsertions");
        Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        TreeSet<BigInteger> treeSet = new TreeSet<>();
        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            int rand = r.nextInt();
            System.out.println("Inserting " + rand);
            treeSet.add(BigInteger.valueOf(rand));
            diskBinaryTreeSet.add(BigInteger.valueOf(rand));

            if (treeSet.size() != diskBinaryTreeSet.size()) {
                throw new IllegalStateException("Size does not match.\nExpected: " + treeSet.size() + "\nActual:   " + diskBinaryTreeSet.size());
            }

            for (BigInteger bi : treeSet) {
                if (!diskBinaryTreeSet.contains(bi)) {
                    throw new IllegalStateException("diskBinaryTreeSet missing " + bi);
                }
            }
        }
        System.out.println("PASS! :)");
    }

    private static void testContainsAndSize_InOrderInsertions() throws Exception {
        System.out.println("Running testContainsAndSize_InOrderInsertions");
        Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        for (int i = 0; i < 100; i++) {
            System.out.println("Inserting " + (i+1));
            diskBinaryTreeSet.add(BigInteger.valueOf(i+1));

            if (i+1 != diskBinaryTreeSet.size()) {
                throw new IllegalStateException("Size does not match.\nExpected: " + (i+1) + "\nActual:   " + diskBinaryTreeSet.size());
            }

            for (int j = 0; j <= i; j++) {
                if (!diskBinaryTreeSet.contains(BigInteger.valueOf(j+1))) {
                    throw new IllegalStateException("diskBinaryTreeSet missing " + (j+1));
                }
            }
        }
        System.out.println("PASS! :)");
    }

    private static void testHigher_RandomInsertions() throws Exception {
        System.out.println("Running testHigher_RandomInsertions");
        Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        TreeSet<BigInteger> treeSet = new TreeSet<>();
        Random r = new Random();
        for (int n = 0; n < 5; n++) {
            for (int i = 0; i < 250; i++) {
                int rand = 50+r.nextInt(250);
                System.out.println("Inserting " + rand);
                treeSet.add(BigInteger.valueOf(rand));
                diskBinaryTreeSet.add(BigInteger.valueOf(rand));
            }

            for (int i = 0; i < 1000; i++) {
                int rand = r.nextInt(350);
                System.out.print("Querying higher of " + rand + "...");
                BigInteger tsv = treeSet.higher(BigInteger.valueOf(rand));
                BigInteger dbtsv = diskBinaryTreeSet.higher(BigInteger.valueOf(rand));
                System.out.println("TreeSet reported " + tsv + ", and DiskBinaryTreeSet reported " + dbtsv);
                if (tsv == null && dbtsv == null) {
                    continue;
                } else if (tsv != null && tsv.compareTo(dbtsv) == 0) {
                    continue;
                } else {
                    throw new IllegalStateException("Querying higher of " + rand + " failed! TreeSet reported " + tsv + ", but DiskBinaryTreeSet reported " + dbtsv);
                }
            }
        }

        System.out.println("PASS! :)");
    }

    private static void testCeiling_RandomInsertions() throws Exception {
        System.out.println("Running testCeiling_RandomInsertions");
        Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        TreeSet<BigInteger> treeSet = new TreeSet<>();
        Random r = new Random();
        for (int n = 0; n < 5; n++) {
            for (int i = 0; i < 250; i++) {
                int rand = 50+r.nextInt(250);
                System.out.println("Inserting " + rand);
                treeSet.add(BigInteger.valueOf(rand));
                diskBinaryTreeSet.add(BigInteger.valueOf(rand));
            }

            for (int i = 0; i < 1000; i++) {
                int rand = r.nextInt(350);
                System.out.print("Querying ceiling of " + rand + "...");
                BigInteger tsv = treeSet.ceiling(BigInteger.valueOf(rand));
                BigInteger dbtsv = diskBinaryTreeSet.ceiling(BigInteger.valueOf(rand));
                System.out.println("TreeSet reported " + tsv + ", and DiskBinaryTreeSet reported " + dbtsv);
                if (tsv == null && dbtsv == null) {
                    continue;
                } else if (tsv != null && tsv.compareTo(dbtsv) == 0) {
                    continue;
                } else {
                    throw new IllegalStateException("Querying ceiling of " + rand + " failed! TreeSet reported " + tsv + ", but DiskBinaryTreeSet reported " + dbtsv);
                }
            }
        }

        System.out.println("PASS! :)");
    }

    private static void testFirst_RandomInsertions() throws Exception {
        System.out.println("Running testFirst_RandomInsertions");
        for (int n = 0; n < 5; n++) {
            Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
            DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

            TreeSet<BigInteger> treeSet = new TreeSet<>();
            Random r = new Random();
            
            for (int i = 0; i < 250; i++) {
                int rand = r.nextInt();
                System.out.println("Inserting " + rand);
                treeSet.add(BigInteger.valueOf(rand));
                diskBinaryTreeSet.add(BigInteger.valueOf(rand));
            }

            System.out.print("Testing first...");
            BigInteger tsv = treeSet.first();
            BigInteger dbtsv = diskBinaryTreeSet.first();
            System.out.println("TreeSet says " + tsv + " and DiskBinaryTreeSet says " + dbtsv);
            if (!tsv.equals(dbtsv)) {
                throw new IllegalStateException("First failed! TreeSet says " + tsv + " but DiskBinaryTreeSet says " + dbtsv);
            }
        }

        System.out.println("PASS! :)");
    }

        private static void testLast_RandomInsertions() throws Exception {
        System.out.println("Running testLast_RandomInsertions");
        for (int n = 0; n < 5; n++) {
            Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
            DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

            TreeSet<BigInteger> treeSet = new TreeSet<>();
            Random r = new Random();
            
            for (int i = 0; i < 250; i++) {
                int rand = r.nextInt();
                System.out.println("Inserting " + rand);
                treeSet.add(BigInteger.valueOf(rand));
                diskBinaryTreeSet.add(BigInteger.valueOf(rand));
            }

            System.out.print("Testing last...");
            BigInteger tsv = treeSet.last();
            BigInteger dbtsv = diskBinaryTreeSet.last();
            System.out.println("TreeSet says " + tsv + " and DiskBinaryTreeSet says " + dbtsv);
            if (!tsv.equals(dbtsv)) {
                throw new IllegalStateException("Last failed! TreeSet says " + tsv + " but DiskBinaryTreeSet says " + dbtsv);
            }
        }

        System.out.println("PASS! :)");
    }

    private static void testIterator_RandomInsertions() throws Exception {
        System.out.println("Running testIterator_RandomInsertions");
        Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        TreeSet<BigInteger> treeSet = new TreeSet<>();
        Random r = new Random();
        for (int n = 0; n < 5; n++) {
            for (int i = 0; i < 250; i++) {
                int rand = r.nextInt();
                System.out.println("Inserting " + rand);
                treeSet.add(BigInteger.valueOf(rand));
                diskBinaryTreeSet.add(BigInteger.valueOf(rand));
            }

            System.out.print("Iterating...");
            Iterator<BigInteger> tsi = treeSet.iterator();
            Iterator<BigInteger> dbtsi = diskBinaryTreeSet.iterator();
            
            while (tsi.hasNext()) {
                if (!dbtsi.hasNext()) {
                    throw new IllegalStateException("DiskBinaryTreeSet hasNext() was false when it should have been true when iterating!");
                }
                BigInteger tsv = tsi.next();
                BigInteger dbtsv = dbtsi.next();
                System.out.println("TreeSet thinks next is " + tsv + " and DiskBinaryTreeSet thinks next is " + dbtsv);
                if (!tsv.equals(dbtsv)) {
                    throw new IllegalStateException("Iterator next() failed! TreeSet reported " + tsv + ", but DiskBinaryTreeSet reported " + dbtsv);
                }
            }
            if (dbtsi.hasNext()) {
                throw new IllegalStateException("DiskBinaryTreeSet hasNext() was true when it should have been false when iterating!");
            }
        }

        System.out.println("PASS! :)");
    }

    public static void main(String[] args) throws Exception {
        testContainsAndSize_RandomInsertions();
        testContainsAndSize_InOrderInsertions();
        testHigher_RandomInsertions();
        testCeiling_RandomInsertions();
        testFirst_RandomInsertions();
        testIterator_RandomInsertions();
        testLast_RandomInsertions();

        Files.deleteIfExists(Paths.get("diskBinaryTreeSetTest.dbts"));
        System.out.println("PASS ALL! :)");
    }
}
