/**
 * @file FileTable.java
 * @author Midori Williams
 * @author Kevin Hsu
 * @author Bowman Simmons
 * @date Jun 9, 2019
 *
 * The File Table class holds a collection of file table entries, or
 * descriptions of files, in its vectors, and the file system utilizes it to
 * keep track of everything. It does this through the use of the vecotor (table)
 * */
import java.util.Vector;

public class FileTable {

    private Vector table;         // the file table
    private Directory dir;        // the root


    /** ============================== FileTable ===============================
     * Basict constructor that instatiates the two private fields
     *
     * @param directory - the directory from the file system
     * */
    public FileTable(Directory directory) {
        table = new Vector();     // instantiate a file (structure) table
        dir = directory;          // receive a reference to the Directory
    }                             // from the file system

    /** ================================ falloc ================================
     * Allocates new file table entry for the filename
     * Allocate/retrieve and register the corresponding inode using dir
     * increment this inode's count
     * immediately write back this inode to the disk
     * Returns reference to the file table entry
     *
     * @param filename - the specified file's file table entry
     * @return         - the reference to the file table entry
     * */
    public synchronized FileTableEntry falloc(String filename, String mode) {
        short iNumber = -1;
        Inode inode = null;

        while (true) {
            iNumber = (filename.equals("/") ? 0 : dir.getinum(filename));

            if (iNumber >= 0) { // else if iNumber represents an existing file
                inode = new Inode (iNumber);      // retrieve inode from disk

                if (mode.equals("r")) {           // if requesting read
                    // no need to wait, inode can be shared
                    if (inode.status == inode.READ) {
                        break;
                        // wait for other writer to release status
                    } else if (inode.status == inode.WRITE) {
                        try {
                            wait();
                        } catch(InterruptedException e) {
                            return null;
                        }
                        return null;
                    } else if (inode.status == inode.TO_BE_DELETED) { // no view
                        iNumber = -1;
                        return null;
                    } else {  // ( inode.status == inode.USED
                        // || inode.status == inode.UNUSED)
                        // inode free, set status to read
                        inode.status = inode.READ;
                        break;
                    }
                } else {  // requesting mode equals( "w" "w+" or "a" )
                    if (inode.status == inode.USED
                            || inode.status == inode.UNUSED) { // file exists,
                        inode.status = inode.WRITE; // but not active on another
                        break;                    // process, set status to write
                    } else { // status is READ or WRITE, wait for file to be free
                        try {
                            wait();
                        } catch(InterruptedException e) {
                            return null;
                        }
                        return null;
                    }
                }
            } else {                    // no file, create new inode
                if (mode.equals("r")) { // if "r", can't read from a absent file
                    return null;        // so don't do anything
                }
                iNumber = dir.ialloc(filename); // for others allocate iNumber
                inode = new Inode ();           // and create new Inode for file
            }
        }

        inode.count++;
        inode.toDisk(iNumber);             // save inode to disk
        FileTableEntry fte = new FileTableEntry(inode, iNumber, mode);
        table.addElement(fte);             // create table entry and register it
        return fte;
    }

    /** ================================ ffree =================================
     * receive a file table entry reference
     * save the corresponding inode to the disk
     * free this file table entry
     * return true if this file table entry found in my table
     *
     * @param fte - specified file table entry reference
     * @return    - boolean representing success or failure
     * */
    public synchronized boolean ffree(FileTableEntry fte) {
        if (table.remove(fte)) {    // fte found, removed successfully
            fte.inode.status = fte.inode.UNUSED;
            fte.inode.toDisk( fte.iNumber );
            notifyAll();
            return true;
        }
        return false;  // fte not found
    }

    /** ================================ fempty ================================
     * A boolean indicator for whether the file table is empty
     *
     * @return - true if empty, false if not
     * */
    public synchronized boolean fempty() {
        return table.isEmpty();
    }

}
