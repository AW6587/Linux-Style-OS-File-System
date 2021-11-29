/**
 * @file Directory.java
 * @author Midori Williams
 * @author Kevin Hsu
 * @author Bowman Simmons
 * @date Jun 9, 2019
 *
 * The directory is to manage active files.
 *
 * Two arrays are utilized to this end, fsize and fnames, keeping track of file
 * sizes and names respectively.
 *
 * NOTE: directory2bytes is unused, and as you will later see, so is sync().
 *       Those functionalities are implemented elsewhere.
 * */

// imports
import java.lang.String;
import java.util.Arrays;

public class Directory
{
    private static int maxChars = 30; // max chars of each filename

    // Directory entries
    private int fsize[];        // each element stores a different file size.
    private char fnames[][];    // each element stores a different file name.
                                // as a char arr

    /** ============================= Constructor ==============================
     * Precondition:  Unitialized Directory object.
     * Postcondition: this.fsize and this.fnames are each initialized to a
     *                a length of the paramater value, total nodes.
     *                each file size is initialized to 0. (which we believe
     *                java does anyways). Each subarray of fnames is initialized
     *                to a length of maxChars. Lastly, the root is set up at
     *                index 0.
     *
     * @param totalInodes - Integer representing the maximum number of nodes
     * */
    public Directory(int totalInodes) {
        // totalInodes = max files/inodes
        fsize = new int[totalInodes];
        for (int i = 0; i < totalInodes; i++)
            fsize[i] = 0;
        fnames = new char[totalInodes][maxChars];
        String root = "/";
        fsize[0] = root.length();
        root.getChars(0, fsize[0],
                fnames[0], 0);
    }

    /** =========================== bytes2directory ============================
     * Precondition:  (and assumption) the paramater byte array data has
     *                received information from the disk to be put in the dir
     * Postcondition: Put the data[] in the the directory
     * */
    public int bytes2directory(byte data[]) {
        int offset = 0;
        for (int i = 0; i < fsize.length; i++, offset += 4){
            fsize[i] = SysLib.bytes2int( data, offset );
        }

        for (int i = 0; i < fnames.length; i++, offset += maxChars * 2){
            String fname = new String (data, offset, maxChars * 2);
            fname.getChars(0, fsize[i], fnames[i], 0);
        }
        return 0;
    }

    /** =========================== directory2bytes ============================
     * Precondition:  Information is in the directory
     * Postcondition: A returned byte array, ordered by file size,
     *
     * @return - the data
     * */
    public byte[] directory2bytes() {
        int offset = 0;

        // directory's disk block
        byte[] data = new byte[(4 * fsize.length) +
                (fnames.length * maxChars * 2)];

        for (int i = 0; i < fsize.length; i++, offset += 4) {
            // only when there's an inode entry should we copy
            if (fsize[i] > 0)
                SysLib.int2bytes(fsize[i], data, offset);
        }
        for (int i = 0; i < fnames.length; i++, offset += maxChars * 2) {
            String fname = new String ( fnames[i] );
            byte[] temp = fname.getBytes();
            System.arraycopy(temp, 0, data, offset, temp.length);
        }
        return data;
    }

    /** ================================ ialloc ================================
     * Precondition:  An inode has not been allocated for the designated file
     * Postcondition: One has...
     *
     * @param  - The name of the file without allocated space
     * @return - The inumber where the filename ends up being stored or -1 if
     *           there's no room
     * */
    public short ialloc(String filename) {
        
        // if the file is not there already
        if (getinum( filename ) == -1) {
            int fs = filename.length();
            for (int i = 0; i < fsize.length; i++) {
                if (fsize[i] == 0) {    // if there's an empty slot for it
                    fsize[i] = fs;
                    fnames[i] = filename.toCharArray();

                    return (short) i;
                }
            }
        }

        // if it's already there then, well, don't
        return (short) -1;
    }

    /** ================================ ifree =================================
     * Precondition:  an allocated space at the inumber
     * Postcondition: file deleted and space freed
     *
     * @param  - the inumber to determine what to free (inode num / index num)
     * @return - true for success and false for failure
     * */
    public boolean ifree(short iNumber) {
        if (iNumber < fsize.length && fsize[iNumber] > 0) {
            fsize[iNumber] = 0;
            Arrays.fill(fnames[iNumber], '\0');
            return true;
        }
        // inumber not present
        return false;
    }

    /** =============================== getinum ================================
     * Precondition:  Have the name of an existing file
     * Postcondition: Return the inumber for that existing file
     *
     * @param  - the name of the file to retreive the inum for
     * @return - the inumber of the file
     * */
    public short getinum(String filename) {
        // returns the inumber (index number) corresponding to this filename
        for (int i = 0; i < fnames.length; i++){
            if (filename.equals(new String(fnames[i],0, fsize[i])))
                return (short) i;
        }

        //filename not found
        return (short) -1;
    }
}

