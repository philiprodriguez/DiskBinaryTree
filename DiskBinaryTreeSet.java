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
import java.util.Set;
import java.util.TreeSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import java.util.Stack;

/**
 * A class which implements a balanced binary tree which is 100% represented on disk and therefore should not consume any significant amounnt of RAM no matter how many elements are in the on-disk set.
 * 
 * This class does not allow removal. All remove operations will throw an unchecked exception.
 * 
 * The file that data gets stored into is structured as follows:
 * size in number of entries (8 bytes)
 * next empty position (8 bytes)
 * root node address (8 bytes)
 * ...list of node data...
 * 
 * Each entry in the list of node data is of the form:
 * left pointer (8 bytes)
 * right pointer (8 bytes)
 * height of subtree rooted here (4 bytes)
 * payload size (4 bytes)
 * payload (variable size)
 * 
 * Philip Rodriguez, February 2020
 * 
 * @author philipjamesrodriguez@gmail.com
 * @param <T>
 */
public class DiskBinaryTreeSet<T extends Serializable & Comparable<T>> implements Set<T>, Closeable {
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

    private long getRootAddress() throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(16);
        long ra = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return ra;
    }

    private void setRootAddress(long rootAddress) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(16);
        randomAccessFile.writeLong(rootAddress);
        randomAccessFile.seek(curPos);
    }

    private void setSize(long size) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(0);
        randomAccessFile.writeLong(size);
        randomAccessFile.seek(curPos);
    }

    private long getSize() throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(0);
        long size = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return size;
    }

    private void setNextEmptyPosition(long pos) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(8);
        randomAccessFile.writeLong(pos);
        randomAccessFile.seek(curPos);
    }

    private long getNextEmptyPosition() throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(8);
        long pos = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return pos;
    }

    private long getLeftPointer(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;
            
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress);
        long lp = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return lp;
    }

    private void setLeftPointer(long startAddress, long leftPointer) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress);
        randomAccessFile.writeLong(leftPointer);
        randomAccessFile.seek(curPos);
    }

    private long getRightPointer(long startAddress) throws IOException {
        if (startAddress < 0)
        return -1;
        
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+8);
        long rp = randomAccessFile.readLong();
        randomAccessFile.seek(curPos);
        return rp;
    }

    private void setRightPointer(long startAddress, long rightPointer) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+8);
        randomAccessFile.writeLong(rightPointer);
        randomAccessFile.seek(curPos);
    }

    private int getHeightOfSubtree(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;
        
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+16);
        int hos = randomAccessFile.readInt();
        randomAccessFile.seek(curPos);
        return hos;
    }

    private void setHeightOfSubtree(long startAddress, int heightOfSubtree) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+16);
        randomAccessFile.writeInt(heightOfSubtree);
        randomAccessFile.seek(curPos);
    }

    private int getPayloadSize(long startAddress) throws IOException {
        if (startAddress < 0)
            return -1;
        
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+20);
        int ps = randomAccessFile.readInt();
        randomAccessFile.seek(curPos);
        return ps;
    }

    private T getPayloadObject(long startAddress) throws IOException, ClassNotFoundException {
        if (startAddress < 0)
            return null;
        
        long curPos = randomAccessFile.getFilePointer();
        int payloadSize = getPayloadSize(startAddress);
        byte[] payloadBytes = new byte[payloadSize];
        randomAccessFile.seek(startAddress+24);
        randomAccessFile.read(payloadBytes);
        randomAccessFile.seek(curPos);
        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(payloadBytes));
        return (T) objectInputStream.readObject();
    }

    private long setPayloadObject(long startAddress, T payloadObject) throws IOException {
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
        };

        public final Description description;

        public AddressDescriptor(Stack<Long> addressStack, Description description) {
            this.addressStack = addressStack;
            this.description = description;
        }
    }

    /**
     * Searches the tree as necessary and returns one of the following:
     * 
     * EXACT_ADDRESS - The item already exists in the tree and the address is the address of the existing item.
     * PARENT_ADDRESS_LEFT - The item does not exist in the tree and the address is that of the parent were the item to have been in the tree. The item belongs to the left of the parent.
     * PARENT_ADDRESS_RIGHT - The item does not exist in the tree and the address is that of the parent were the item to have been in the tree. The item belongs to the right of the parent.
     * EMPTY_ROOT_ADDRESS - The item did not exist in the tree and the address is that of the root node, which is not used or set yet.
     */
    private AddressDescriptor findAddressForValue(T value) throws IOException, ClassNotFoundException {
        Stack<Long> addressStack = new Stack<Long>();
        addressStack.push(getRootAddress());
        
        if (getSize() == 0) {
            return new AddressDescriptor(addressStack, AddressDescriptor.Description.EMPTY_ROOT_ADDRESS);
        }

        while (true) {
            long curNodeAddress = addressStack.peek();
            T curNodeValue = getPayloadObject(curNodeAddress);
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
    private void rotateRight(long axisAddress, long parentAddress) throws IOException {
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
    private void rotateLeft(long axisAddress, long parentAddress) throws IOException {
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
     * Traverse back up the tree accodring to the stack in {@code addressDescriptor} and update all relevant heights and perform any rebalancing actions needed.
     * 
     * @param addressStack
     */
    private void updateHeightsAndBalance(Stack<Long> addressStack) throws IOException {
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

    public boolean add(T value) {
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

    public boolean contains(Object o) {
        try {
            T value = (T) o;
            AddressDescriptor addressDescriptor = findAddressForValue(value);
            return addressDescriptor.description == AddressDescriptor.Description.EXACT_ADDRESS;
        } catch (IOException | ClassNotFoundException | ClassCastException exc) {
            return false;
        }
    }

    public int size() {
        long actual = actualSize();
        return actual > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) actual;
    }

    public long actualSize() {
        try {
            return getSize();
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to read size, cannot recover.");
        }
    }

    public boolean isEmpty() {
        try {
            return getSize() == 0;
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to read size and therefore failed to check emptiness, cannot recover.");
        }
    }

    public boolean addAll(Collection<? extends T> collection) {
        boolean anySucceed = false;
        for (T value : collection) {
            anySucceed |= add(value);
        }
        return anySucceed;
    }

    public boolean remove(Object o) {
        throw new IllegalStateException("Not implemented! Cannot remove from DiskBinaryTreeSet!");
    }

    public Iterator<T> iterator() {
        throw new IllegalStateException("Not yet implemented!");
    }

    public boolean removeAll(Collection<?> collection) {
        throw new IllegalStateException("Not implemented! Cannot remove from DiskBinaryTreeSet!");
    }

    public boolean retainAll(Collection<?> collection) {
        throw new IllegalStateException("Not implemented! Cannot remove from DiskBinaryTreeSet!");
    }

    public boolean containsAll(Collection<?> collection) {
        throw new IllegalStateException("Not yet implemented!");
    }

    public void clear() {
        throw new IllegalStateException("Not yet implemented!");
    }

    public Object[] toArray() {
        throw new IllegalStateException("Not yet implemented!");
    }

    public <A> A[] toArray(A[] a) {
        throw new IllegalStateException("Not yet implemented!");
    }

    public void close() throws IOException {
        randomAccessFile.close();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Testing DiskBinaryTreeSet against TreeSet!");
        TreeSet<BigInteger> treeSet = new TreeSet<BigInteger>();

        new File("diskBinaryTreeSetTest.dbts").delete();
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            int rand = r.nextInt();
            System.out.println("Inserting " + rand);
            treeSet.add(BigInteger.valueOf(rand));
            diskBinaryTreeSet.add(BigInteger.valueOf(rand));

            if (treeSet.size() != diskBinaryTreeSet.size()) {
                throw new IllegalStateException("Size does not match: " + treeSet.size() + " vs " + diskBinaryTreeSet.size());
            }

            for (BigInteger bi : treeSet) {
                if (!diskBinaryTreeSet.contains(bi)) {
                    throw new IllegalStateException("diskBinaryTreeSet missing " + bi);
                }
            }
        }

        new File("diskBinaryTreeSetTestInOrder.dbts").delete();
        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSetInOrder = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTestInOrder.dbts"));
        for (int i = 0; i < 100; i++) {
            System.out.println("Inserting " + (i+1));
            diskBinaryTreeSetInOrder.add(BigInteger.valueOf(i+1));

            for (int j = 0; j <= i; j++) {
                if (!diskBinaryTreeSetInOrder.contains(BigInteger.valueOf(j+1))) {
                    throw new IllegalStateException("diskBinaryTreeSetInOrder missing " + (j+1));
                }
            }
        }

        System.out.println("PASS! :)");
    }
}