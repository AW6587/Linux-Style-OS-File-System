/**
 * @file FileSystem.java
 * @author Midori Williams
 * @author Kevin Hsu
 * @author Bowman Simmons
 * @date Jun 9, 2019
 *
 * The Inode class serves as a descriptor and node holder of files. It contains
 * 12 pointers, 11 of which are direct and 1 of which is indirect, to the next 
 * block. It has usage flags and pointers to keep track of a file’s state and 
 * usage. The system can accomodate as many as 16 inodes.
 * */

/*
//    number of blocks = 1000
//    block size = 512 bytes each
//    inode size = 32 bytes each
//    inodes per block = 512/32 = 16 inodes per block
//    number of blocks to store inodes = 4 blocks
//    max number of inodes for 4 blocks = 16*4 = 64 inodes
//    number of indirect block pointers = 512 byte block / 2 byte pointers =  256 pointers
*/


public class Inode {
    private final static int iNodeSize = 32;       // inodes are 32 bytes
    public final static int directSize = 11;       // number of pointers for dir

    //inode status types
    public final static short UNUSED = 0;          // Default
    public final static short USED = 1;            // Used
    public final static short READ = 2;            // reading
    public final static short WRITE = 3;           // writing
    public final static short TO_BE_DELETED = -1;  // deleted/inaccessible

    public int   length;                           // file size (unit = bytes)
    public short count;                            // num entries pointing here
    public short status;                           // one of above status codes
    public short direct[] = new short[directSize]; // direct reference pointers
    public short indirect;                         //

    /** ========================= Default constructor ==========================
     * Straightforward default constructor
     * */
    Inode() {
        length = 0;
        count = 0;
        status = USED;
        for(int i = 0; i < directSize; i++)
            direct[i] = -1;
        indirect = -1;
    }

    /** ====================== Parameterized Constructor =======================
     * Takes in an inumber reads in the bytes, calculates the offset, allocates
     * space, and instantiates the new Inode
     *
     * @param iNumber - the other iNumber
     * */
    Inode(short iNumber) {
        int blockNumber = 1 + iNumber / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, data);
        int offset = (iNumber % 16) * 32;

        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        status = SysLib.bytes2short(data, offset);
        offset += 2;

        for(int i = 0; i < directSize; i++, offset += 2)
            direct[i] = SysLib.bytes2short( data, offset );
        indirect = SysLib.bytes2short(data, offset);
    }

    /** =============================== toDisk ================================
     * Saves the specified Inode to the disk
     *
     * @param iNumber - the idx of the Inode to write to the disk
     * */
    int toDisk(short iNumber) {
        int blkNumber = 1 + iNumber / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blkNumber, data);
        int offset = (iNumber % 16) * 32;

        SysLib.int2bytes(length, data, offset);
        offset += 4;
        SysLib.short2bytes(count, data, offset);
        offset += 2;
        SysLib.short2bytes(status, data, offset);
        offset += 2;

        for(int i = 0; i < directSize; i++, offset += 2)
            SysLib.short2bytes(direct[i], data, offset);
        SysLib.short2bytes(indirect, data, offset);
        SysLib.rawwrite(blkNumber, data);

        return 0;
    }

    /** ========================= getIndexBlockNumber ==========================
     * Essentially just an accessor for the indirect private member
     *
     * NOTE: THIS MAYBE WAS MEANT FOR THE LOGIC IN findTargetBlock()
     *
     * @return the current value of indirect
     * */
    short getIndexBlockNumber(){
        return indirect;
    }

    /** ============================ setIndexBlock =============================
     * register a free data block on disk for use as indirect index pointers
     * using SysLib.rawwrite() returns false if indexBlockNumber is
     * invalid/negative or if indirect is already used, else returns true
     *
     * @param indexBlockNumber - The number of the index block to set
     */
    public boolean setIndexBlock(short indexBlockNumber) {
        if (indexBlockNumber >= 0 && indirect == -1) {
            indirect = indexBlockNumber;

            // initialize indirect block's 512 pointers to -1
            byte[] initNum = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize / 2; i++) {
                SysLib.short2bytes((short) -1, initNum, i * 2);
            }
            SysLib.rawwrite(indexBlockNumber, initNum);
            return true;
        }
        return false;   // invalid block number
    }

    /** =========================== findTargetBlock ============================
     * searches direct and indirect index block pointers for data block with
     * given offset returns block if found, else -1 on failure
     *
     * made use of primarily by write
     *
     * @param offset - the offset
     * */
    public short findTargetBlock(int offset) {
        if (offset < 0)
            return -1;     // bad offset
        int targetBlock = offset/Disk.blockSize;
        if (targetBlock < directSize) {
            return direct[targetBlock];
        } else {
            if (indirect == -1) {
                return -1;     // no indirect block, cannot find block
            }
            // read indirect block
            byte[] indirectBlock = new byte[Disk.blockSize];
            SysLib.rawread(indirect, indirectBlock);
            targetBlock -= directSize;
            return SysLib.bytes2short(indirectBlock, targetBlock * 2);
        }
    }

    /** ========================= registerTargetBlock ==========================
     * Register disk block with direct or indirect pointers using
     * SysLib.rawwrite() returns 0 on success, -1, -2, or -3 on error
     *
     * @param offset - the offset
     * @param targetBlockNumber - the target to register
     * @return - success or failure
     * */
    public int registerTargetBlock(int offset, short targetBlockNumber) {
        int target_idx = offset / Disk.blockSize;

        if (directSize > target_idx) {
            if (direct[target_idx] >= 0)
                return -1;
            else if (target_idx > 0 && (direct[target_idx - 1] == -1))
                return -2;
            direct[target_idx] = targetBlockNumber;
            return 0;
        } else if (indirect < 0) {
            return -3;
        }

        byte[] writeto = new byte [Disk.blockSize];
        SysLib.rawread(indirect, writeto);

        if (SysLib.bytes2short(writeto, (2 * (target_idx - directSize))) > 0)
            return -1;

        SysLib.short2bytes(targetBlockNumber, writeto,
                (2 * (target_idx - directSize)));
        SysLib.rawwrite(indirect, writeto);
        return 0;
    }

    /** ========================= unregisterIndexBlock =========================
     * unregister/clear disk block being used for indirect index pointers
     * reads index block from disk using SysLib.rawread()
     * resets indirect = -1, returns byte array of indirect data
     * returns null if indirect == -1 (already no index block)
     *
     * @return - the bytes that were read from the disk
     */
    public byte[] unregisterIndexBlock() {
        if (indirect == -1)
            return null;
        byte[] indirectData = new byte[Disk.blockSize];
        SysLib.rawread(indirect, indirectData);
        indirect = -1;
        return indirectData;
    }

    @Override
    public String toString() {
        return "length: " + length + ", count: " + count + ", status" + status +
                ", direct: " + direct + ", indirect: " + indirect;
    }

    /** ============================ getDirectSize =============================
     * Essentially just an accessor for the directSize private member
     *
     * @return - directSize
     * */
    public int getDirectSize() {
        return directSize;
    }
}
