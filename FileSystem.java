/**
 * @file FileSystem.java
 * @author Midori Williams
 * @author Kevin Hsu
 * @author Bowman Simmons
 * @date Jun 9, 2019
 *
 * The File System contains all of logic for, and provides an interface for,
 * all disk operations. It is reliant on calls from other components of the
 * ThreadOS system.
 * */

public class FileSystem extends Thread {
    private final int  BLOCK_SIZE = 512;
    private Superblock superBlock;
    private Directory  directory;
    private FileTable  fileTable;

    /** ============================= Constructor ==============================
     * Single parameter constructor
     *
     * 1.) Creates the SuperBlock, Directory, FileTable
     * 2.) Reconstructs the directory
     *
     * @param diskSize - diskSize, the size of the disk, used to construct the
     *                   superblock
     * */
    public FileSystem(int diskSize) {
        // superblock, directory, filetable
        superBlock = new Superblock(diskSize);
        directory = new Directory( superBlock.totalInodes );
        fileTable = new FileTable( directory );

        // reconstruct directory
        FileTableEntry dirEnt = open("/","r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    //I don't think I need this
    void sync(){
        // actual functionality of synching directory to disk is performed
        // elsewhere
    }

    /** ================================ format ================================
     * Precondition:  Doesn't particularly matter, as it overwrites existing
     * Postcondition: The supberblock, directory, and filetable are all erased
     *                and replaced with new instances.
     *
     * @param files - the number of files and corresponding inodes to be
     *                allocated for by the superblock
     * */
    boolean format (int files) {
        superBlock.sbFormat(files);
        directory = new Directory(superBlock.totalInodes);
        fileTable = new FileTable(directory);
        return true;
    }

    /** ================================= open =================================
     * Precondition:  The given file needs opening
     * Postcondition: It has been opened in the appropriate mode
     *
     * @param filename - the name of the file to be opened
     * @param     mode - the mode (eg. read, write...) to open in
     * */
    public FileTableEntry open(String filename, String mode) {
        FileTableEntry ftEnt = fileTable.falloc(filename, mode);
        if (mode.equals("w")){
            if (deallocAllBlocks( ftEnt ) == false)
                return null;
        }
        return ftEnt;
    }


    /** ================================ close =================================
     * Precondition:  Have the file table entry of a file to be closed
     * Postcondition: The file has been closed
     *
     * @param ftEnt - The FileTableEntry of the file that is to be closed
     * */
    public boolean close (FileTableEntry ftEnt) {
        // decrement file usage by...
        ftEnt.count--;       // decrement file count
        ftEnt.inode.count--; // decrement inode count

        // if file not in use, remove from fileTable
        if (ftEnt.count <= 0) {
            return fileTable.ffree(ftEnt);
        }
        return true;
    }

    /** ================================ fsize =================================
     * Simply an accessor for a FileTableEntry's inode length as a way of
     * returning file size given a file table entry
     *
     * @param ftEnt - the fte for the file
     * */
    public int fsize(FileTableEntry ftEnt) {
        return ftEnt.inode.length;
    }

    /** ================================= read =================================
     * Precondition:  The file is there to be read and a buffer has been
     *                specified
     * Postcondition: The specified file has been written to the disk
     *
     * Further explanation: Reads up to buffer length bytes from file, starting
     * at possition of seek pointer. If bytes remaining between current seek
     * pointer and end of file are < buffer length, SysLib.read as many bytes as
     * possible and puts them in buffer. Increments seek pointer by number of
     * bytes read. Returns number of bytes read or -1 on error.
     *
     * @param    fte - The filetable entry to be read
     * @param buffer - A buffer for the data being read
     * @return       - The amount of data being read
     * */
    public int read(FileTableEntry fte, byte[] buffer) {
        //can't read if mode isn't read or read/write (w+)
        if (fte == null || (fte.mode.equals("r")
                || fte.mode.equals("w+")) == false)
            return -1;
        int bytesRead = 0;
        int bufferLen = buffer.length;
        int fileLen = fsize(fte);

        while (bytesRead < bufferLen && fte.seekPtr < fileLen) {
            byte[] tempCache = new byte[BLOCK_SIZE];
            short blockID = fte.inode.findTargetBlock(fte.seekPtr);
            SysLib.rawread(blockID, tempCache);

            int readPos = fte.seekPtr%BLOCK_SIZE;
            for (; readPos < tempCache.length && bytesRead < bufferLen
                    && fte.seekPtr < fileLen; readPos++) {
                buffer[bytesRead] = tempCache[readPos];
                bytesRead++;
                fte.seekPtr++;
            }
        }

        return bytesRead;
    }

    /** ================================ write =================================
     * Precondition:  There is a space in the FileSystem for the file that the
     *                user intends to write to it.
     * Postcondition: The specified file has been written to the disk
     *
     * Explanation: The contents of the buffer are written to the file specified
     * by the parameter file table entry, starting from the seek pointer. The
     * method iteratively adjusts the seek pointer and performs byte writes.
     *
     * @param    fte - The filetable entry to be written
     * @param buffer - A buffer for the data being written
     * @return       - The amount of data being written in bytes
     * */
    public int write(FileTableEntry fte, byte[] buffer) {
        int bufferLength = buffer.length;
        int prevFileLength = this.fsize(fte);
        int block = 512;
        int written = 0;

        if (fte == null || fte.mode == "r")
            return -1; // don't write

        synchronized (fte) {
            while (0 < bufferLength) {
                int loc = fte.inode.findTargetBlock(fte.seekPtr);

                // For null location
                if (loc == -1) {
                    short newFreeBlockLoc =
                            (short)(this.superBlock.getFreeBlock());
                    int result = fte.inode.registerTargetBlock(fte.seekPtr,
                            newFreeBlockLoc);

                    if (result == -3) {
                        short nxtFreeBlock =
                                (short)(this.superBlock.getFreeBlock());
                        if (!fte.inode.setIndexBlock(nxtFreeBlock))
                            return -1;
                        if (fte.inode.registerTargetBlock(fte.seekPtr,
                                newFreeBlockLoc) != 0)
                            return -1;
                    } else if (result != 0) {
                        return -1;
                    }
                    loc = newFreeBlockLoc;
                }

                byte[] temp = new byte[block];
                SysLib.rawread(loc, temp);
                int ptr = fte.seekPtr % block;
                int leftovers = block - ptr;

                // setup
                int increment = leftovers;
                int set = bufferLength - leftovers;
                if (leftovers > bufferLength) {
                    increment = bufferLength;
                    set = 0;
                }
                // logic
                System.arraycopy(buffer, written, temp, ptr, increment);
                SysLib.rawwrite(loc, temp);
                fte.seekPtr += increment;
                written += increment;
                bufferLength = set;
            }

            if (fte.seekPtr > prevFileLength)
                fte.inode.length = fte.seekPtr;
        }

        return written;
    }

    /** =========================== deallocAllBlocks ===========================
     * Postcondition: Absent errors, writes inodes back to the disk
     *
     * @param ftEnt - The file table entry to deallocate and write back
     * */
    private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        if (ftEnt == null)
            return false;

        short blockID = 0;
        // deallocate blocks from direct pointers
        for (int offset = 0; offset < ftEnt.inode.directSize; offset++) {
            blockID = ftEnt.inode.direct[offset];
            //nothing to deallocate
            if (blockID == -1)
                continue;
            //else add block to superblock free list and reset
            superBlock.returnBlock(blockID);
            ftEnt.inode.direct[offset] = -1;
        }
        byte[] indirectData = ftEnt.inode.unregisterIndexBlock();
        if (indirectData != null){
            //add blocks pointed to by the indirect block to the free list
            for (int offset = 0; offset < BLOCK_SIZE; offset += 2) {
                blockID = SysLib.bytes2short(indirectData, offset);
                if (blockID == -1)
                    break;
                superBlock.returnBlock(blockID);
            }
            //finally, add the indirect block itself back to the free list
            superBlock.returnBlock(ftEnt.inode.indirect);
        }
        //update disk with cleared inode
        ftEnt.inode.toDisk(ftEnt.iNumber);
        return true;
    }

    /** ================================ delete ================================
     * Precondition:  The specified file exists in the directory, and reaches
     * a point where no user is reading or writing
     * Postcondition: The specified file has been deleted in it's entirety
     * through deallocate
     *
     * @param filename - the name of the file to delete
     * @return         - true if successful, else failure
     * */
    public boolean delete(String filename) {
        FileTableEntry ftEnt = fileTable.falloc(filename, "w");
        if (deallocAllBlocks(ftEnt) && directory.ifree(ftEnt.iNumber)
                && close(ftEnt))
            return true;
        //else one or more of the 3 commands failed
        return false;
    }

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    /** ================================= seek =================================
     * Updates the seek pointer of a file table entry, returning -1 on failure 
     * and 0 or the pointer on success. Attempting to set the seek beyond the 
     * filesize puts it at the end of the file, and attempting to put seek 
     * pointer to a negative number sets it to 0.
     *
     * @param ftEnt  - the file table entry to have seek pointer updated
     * @param offset - the number of bytes to seek, negative to seek backwards
     * @param whence - mode of the seek, from start, current, or end location
     * @return       - new pointer location, -1 if failure
     * */
    public int seek(FileTableEntry ftEnt, int offset, int whence) {
        //System.out.println(ftEnt + "\n" + offset + "\n" + whence);
        int ptr = ftEnt.seekPtr;
        switch (whence) {
            case SEEK_SET:
                if (offset < 0) { ptr = 0; break; }
                if (offset > fsize(ftEnt)) { ptr = fsize(ftEnt); break; }
                ptr = offset;
                break;
            case SEEK_CUR:
                if (ptr + offset < 0) { ptr = 0; break; }
                if (ptr + offset > fsize(ftEnt)) { ptr = fsize(ftEnt); break; }
                ptr += offset;
                break;
            case SEEK_END:
                if (fsize(ftEnt) + offset < 0) { ptr = 0; break; }
                if (fsize(ftEnt) + offset > fsize(ftEnt)) { ptr = fsize(ftEnt);
                    break; }
                ptr = fsize(ftEnt) + offset;
                break;
            default:
                SysLib.cerr("Bad seek whence!");
                return -1;
        }
        ftEnt.seekPtr = ptr;
        return ptr;
    }
}
