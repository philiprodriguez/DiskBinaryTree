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

/**
 * A class which implements a non-balanced binary tree which is 100% represented on disk and therefore should not consume any significant amounnt of RAM no matter how many elements are in the on-disk set.
 * 
 * This class does not allow removal. All remove operations will throw an unchecked exception.
 * 
 * The file that data gets stored into is structured as follows:
 * size in number of entries (8 bytes)
 * next empty position (8 bytes)
 * ...list of node data...
 * 
 * Each entry in the list of node data is of the form:
 * left pointer (8 bytes)
 * right pointer (8 bytes)
 * payload size (4 bytes)
 * payload (variable size)
 * 
 * Philip Rodriguez, February 2020
 * 
 * @author philipjamesrodriguez@gmail.com
 * @param <T>
 */
public class DiskBinaryTreeSet<T extends Serializable & Comparable<T>> implements Set<T>, Closeable {
    private final RandomAccessFile randomAccessFile;

    public DiskBinaryTreeSet(File file) throws IOException {
        randomAccessFile = new RandomAccessFile(file, "rw");

        // Perform file initialization tasks
        if (file.length() <= 0) {
            setSize(0);
            setNextEmptyPosition(16);
        }
    }

    private long getRootAddress() {
        return 16;
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

    private int getPayloadSize(long startAddress) throws IOException {
        long curPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(startAddress+16);
        int ps = randomAccessFile.readInt();
        randomAccessFile.seek(curPos);
        return ps;
    }

    private T getPayloadObject(long startAddress) throws IOException, ClassNotFoundException {
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
        randomAccessFile.seek(startAddress+16);
        randomAccessFile.writeInt(objectBytes.length);
        randomAccessFile.seek(startAddress+24);
        randomAccessFile.write(objectBytes);
        long nextPos = randomAccessFile.getFilePointer();
        randomAccessFile.seek(curPos);
        return nextPos;
    }

    private static class AddressDescriptor {
        public final long address;

        enum Description {
            EXACT_ADDRESS, PARENT_ADDRESS_LEFT, PARENT_ADDRESS_RIGHT, EMPTY_ROOT_ADDRESS;
        };

        public final Description description;

        public AddressDescriptor(long address, Description description) {
            this.address = address;
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
        if (getSize() == 0) {
            return new AddressDescriptor(getRootAddress(), AddressDescriptor.Description.EMPTY_ROOT_ADDRESS);
        } 
        
        long curNodeAddress = getRootAddress();
        while (true) {
            T curNodeValue = getPayloadObject(curNodeAddress);
            int ctr = value.compareTo(curNodeValue);
            if (ctr < 0) {
                // Go left if not empty
                if (getLeftPointer(curNodeAddress) != -1) {
                    curNodeAddress = getLeftPointer(curNodeAddress);
                } else {
                    return new AddressDescriptor(curNodeAddress, AddressDescriptor.Description.PARENT_ADDRESS_LEFT);
                }
            } else if (ctr > 0) {
                // Go right if not empty
                if (getRightPointer(curNodeAddress) != -1) {
                    curNodeAddress = getRightPointer(curNodeAddress);
                } else {
                    return new AddressDescriptor(curNodeAddress, AddressDescriptor.Description.PARENT_ADDRESS_RIGHT);
                }
            } else {
                return new AddressDescriptor(curNodeAddress, AddressDescriptor.Description.EXACT_ADDRESS);
            }
        }
    }

    public boolean add(T value) {
        try {
            AddressDescriptor addressDescriptor = findAddressForValue(value);
            if (addressDescriptor.description == AddressDescriptor.Description.EMPTY_ROOT_ADDRESS) {
                // Just plop in the root
                setLeftPointer(addressDescriptor.address, -1L);
                setRightPointer(addressDescriptor.address, -1L);
                setNextEmptyPosition(setPayloadObject(addressDescriptor.address, value));
                setSize(1);
                return true;
            } else if (addressDescriptor.description == AddressDescriptor.Description.PARENT_ADDRESS_LEFT) {
                // Plop it in to the left
                long newNodeAddress = getNextEmptyPosition();
                setLeftPointer(newNodeAddress, -1L);
                setRightPointer(newNodeAddress, -1L);
                setNextEmptyPosition(setPayloadObject(newNodeAddress, value));

                setLeftPointer(addressDescriptor.address, newNodeAddress);

                setSize(getSize()+1);
                return true;
            } else if (addressDescriptor.description == AddressDescriptor.Description.PARENT_ADDRESS_RIGHT) {
                // Plop it in to the right
                long newNodeAddress = getNextEmptyPosition();
                setLeftPointer(newNodeAddress, -1L);
                setRightPointer(newNodeAddress, -1L);
                setNextEmptyPosition(setPayloadObject(newNodeAddress, value));

                setRightPointer(addressDescriptor.address, newNodeAddress);

                setSize(getSize()+1);
                return true;
            } else {
                // EXACT_ADDRESS, the item already exists in the set!
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

        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());
        // System.out.println(diskBinaryTreeSet.add(BigInteger.valueOf(1586598062L)));
        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());
        // System.out.println(diskBinaryTreeSet.add(BigInteger.valueOf(-1246424590L)));
        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());
        // System.out.println(diskBinaryTreeSet.add(BigInteger.valueOf(87)));
        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());
        // System.out.println(diskBinaryTreeSet.contains(BigInteger.valueOf(1586598062L)));
        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());
        // System.out.println(diskBinaryTreeSet.contains(BigInteger.valueOf(-1246424590L)));
        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());
        // System.out.println(diskBinaryTreeSet.contains(BigInteger.valueOf(87)));
        // System.out.println(diskBinaryTreeSet.getSize());
        // System.out.println(diskBinaryTreeSet.getNextEmptyPosition());

        DiskBinaryTreeSet<BigInteger> diskBinaryTreeSet = new DiskBinaryTreeSet<>(new File("diskBinaryTreeSetTest.dbts"));

        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            int rand = r.nextInt();
            System.out.println("Inserting " + rand);
            treeSet.add(BigInteger.valueOf(rand));
            diskBinaryTreeSet.add(BigInteger.valueOf(rand));

            if (treeSet.size() != diskBinaryTreeSet.size()) {
                System.out.println("Size does not match: " + treeSet.size() + " vs " + diskBinaryTreeSet.size());
            }
        }

        for (BigInteger bi : treeSet) {
            if (diskBinaryTreeSet.contains(bi)) {
                // ok
            } else {
                System.out.println("diskBinaryTreeSet missing " + bi);
            }
        }
    }
}