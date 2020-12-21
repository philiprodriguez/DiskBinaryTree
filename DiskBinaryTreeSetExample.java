package pjrodriguez;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

public class DiskBinaryTreeSetExample {
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