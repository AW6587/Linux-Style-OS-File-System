/**
 * @file FileTableEntry.java
 * @author Midori Williams
 * @author Kevin Hsu
 * @author Bowman Simmons
 * @date Jun 9, 2019
 *
 * The File Table Entry class serves as descriptors and references for each file
 * in the file system, and the FileTable class is composed of these entries.
 * */
public class FileTableEntry {
    public final Inode  inode;    // the entry's inode
    public final short  iNumber;  // the inode number or index
    public int          seekPtr;  // the seek pointer for the file
    public final String mode;     // possibilities are read ("r"), write ("w")
                                      // also "w+" and and "a"
    public int          count;    // user threads utilizing this FTE

    /** ============================== Constructor =============================
     * A basic constructor for initializing each private member based on the
     * parameters
     *
     * @param i       - Inode reference to assign to inode
     * @param inumber - short value to assign to iNumber
     * @param m       - string value to assign to mode
     * */
    public FileTableEntry (Inode i, short inumber, String m) {
        inode =   i;        // the inode assignment
        iNumber = inumber;  // the inumber assignment
        seekPtr = 0;        // the seek pointer assignment
        count =   1;        // the count assignment
        mode =    m;        // the mode assignment
        if (mode.compareTo("a") == 0)
            seekPtr = inode.length; // conditional seek reassignment
    }

    /** =============================== toString ===============================
     * @return - a string representation of this FTE entry
     * */
    @Override
    public String toString() {
        return "seekPtr: " + seekPtr + ", inode: " + inode.toString() + ", " +
                "iNumber: " + iNumber + ", count: " + count + ", mode: " + mode;
    }
}
