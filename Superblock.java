import java.util.Date;

public class Superblock {
    private final int DEFAULT_INODES = 64;
    private final int TOTAL_BLOCKS_OFFSET = 0;
    private final int TOTAL_INODES_OFFSET = 4;
    private final int FREE_LIST_OFFSET = 8;
    public int totalBlocks; //the number of disk blocks
    public int totalInodes; // the number of inodes(16 per block)
    public int freeListHead;    // the block number of the free list's head

    // Constructor
    public Superblock(int diskSize) {
        //read superblock from disk
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, TOTAL_BLOCKS_OFFSET);
        totalInodes = SysLib.bytes2int(superBlock, TOTAL_INODES_OFFSET);
        freeListHead = SysLib.bytes2int(superBlock, FREE_LIST_OFFSET);
        
        //at startup, has it been formatted yet?
        if (totalBlocks == diskSize && totalInodes > 0 && freeListHead >= 2) {
            //disk contents are valid
            return;
        } else {
            //need to format disk
            totalBlocks = diskSize; // this will be 1000 for a new file system
            sbFormat(DEFAULT_INODES);
        }
    }
    
    // Format disk
    // freeListHead: linked list created starting at the first block number
    // following the inodes (16 per block) + block zero for superblock.
    // Total blocks defaults to 1000, per the test file and project descriptions
    void sbFormat (int inodes) {
        byte[] defaultSuperBlock = new byte[Disk.blockSize];
        totalBlocks = 1000;
        totalInodes = inodes;
        freeListHead = (totalInodes / 16) + 2; //16 per block + 2 for offset
        
        //create new inodes
        for (short iNumber = 0; iNumber < totalInodes; iNumber++) {
            Inode inode = new Inode();
            inode.toDisk(iNumber);
        }
        
        //set up the linked list for freeList
        //each freeBlock stores the number of the nextBlock
        byte[] freeBlock = null;
        int freeBlkNum = freeListHead;
        int nextBlkNum = freeListHead +1;
        for (; nextBlkNum < totalBlocks; freeBlkNum++, nextBlkNum++) {
            freeBlock = new byte[Disk.blockSize];
            SysLib.int2bytes(nextBlkNum, freeBlock, 0);
            
            //write freeBlock info to disk
            SysLib.rawwrite(freeBlkNum, freeBlock);
        }
        
        //last block stores -1 to denote end of list
        SysLib.int2bytes(-1, freeBlock, 0);
        //write last freeBlock info to disk
        SysLib.rawwrite(freeBlkNum, freeBlock);
        
        //update superBlock on disk
        sync();
    }

    // Write back totalBlocks, inodeBlocks, and freeList to disk
    void sync () {
        //store superblock contents in a byte array
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, TOTAL_BLOCKS_OFFSET);
        SysLib.int2bytes(totalInodes, superBlock, TOTAL_INODES_OFFSET);
        SysLib.int2bytes(freeListHead, superBlock, FREE_LIST_OFFSET);
        
        //write superBlock to the disk's first block
        SysLib.rawwrite(0, superBlock);
    }

    // Dequeue the top block from the free list
    int getFreeBlock () {
        if (freeListHead > -1 && freeListHead < totalBlocks) {
            int blockNumber = freeListHead;
            byte[] tempBlock = new byte[Disk.blockSize];
            
            //read the number of the next free block and reassign it to
            // freeListHead
            SysLib.rawread(freeListHead, tempBlock);
            freeListHead = SysLib.bytes2int(tempBlock, 0);
            
            //clear tempBlock's contents (so it doesn't have the next block's
            // number in it)
            SysLib.int2bytes(0, tempBlock, 0);
            
            //write the cleared block back to disk before returning its block
            // number
            SysLib.rawwrite(blockNumber, tempBlock);
    
            //update superBlock on disk
            sync();
            return blockNumber;
        }

        // there are no more free blocks (freeListHead == -1) or there was an
        // error
        return -1;
    }

    // Enqueue a given block to the front of the free list
    boolean returnBlock(int blockNumber) {
        if (blockNumber > 0 && blockNumber < totalBlocks) {
            byte[] newFreeBlock = new byte[Disk.blockSize];
            
            //point to previous freeListHead
            SysLib.int2bytes(freeListHead, newFreeBlock, 0);
            
            //freeListHead now points to the newly added block.
            freeListHead = blockNumber;
            
            //write newFreeBlock info to disk
            SysLib.rawwrite(blockNumber, newFreeBlock);
    
            //update superBlock on disk
            sync();
            return true;
        }
        //error, invalid blockNumber
        return false;
    }
}
