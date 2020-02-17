# DiskBinaryTree
This repository contains DiskBinaryTree* implementations. These are binary-tree-based data structures which do not store themselves on the heap or in memory. Instead, these structures store themselves directly in a file on disk. This allows for drop-in replacement of Java's similar data structures but with "no limits" on how large they can grow since they are represented on disk (yes, they are technically limited in size by how large your disk is).

## DiskBinaryTreeSet
Intended to be similar to Java's TreeSet class, but instead stored on disk. Implements the Set interface. Does not support removal because I did not want to deal with data fragmentation issues. 

It is implemented using generics, but requires that the generic type implement Serializable since this needs to be persistable to disk. Luckily, the vast majority of simple data types and structures built into Java are Serializable. This is primarily intended for tasks in which you need to insert a ton of items into a set and later query if an item is in the set. 

The implementation is a self-balancing binary tree (AVL style) so that add and contains operations are guaranteed to always be O(log(n)). Note that since the data is being persisted to disk, there's a fairly large constant factor on that runtime, but do play around with it and you will see overall logarithmic runtime. Also consider editing the implementation's PRINT_DEBUG_INFORMATION constant to true, which will tell you after every insertion the height of the tree :).

If you seriously seriously need removal support and don't care about wasting disk space, a naive modification/approach would be to simply not clean up deleted nodes in the backing file, but just unlink them from all other nodes. Or perhaps, similarly, you could just mark the deleted nodes as "deleted", though this is an even worse approach because then deleted nodes impact runtime. I did not like either of these and so have not yet implemented either.
