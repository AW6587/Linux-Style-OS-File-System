# File System Program Report
Alex Williams, Kevin Hsu, & Bowman Simmons
6/3/19
## Specification
Our file system utilizes the standard ThreadOS files with the following substitutions from our submission:
- Kernel.java (this is the version provided in program 4, with editions for program 5)
- FileSystem.java
- FileTable.java
- FileTableEntry.java
- Inode.java
- Superblock.java
- Directory.java

This program simulates a unix-like file system. Upon running Test5.java from the ThreadOS
command prompt, a call to SysLib.format( ) receives the number of files to initialize and passes that on to the Kernel. The Kernel then calls to FileSystem.java to create new instances of the Superblock, Directory, and File Table. After the format is completed, the file system is ready to manage requests to open, read, write, read/write, append, and delete files located on the Disk.

## Assumptions
The first of our assumptions was that adhering to the provided assignment documentation and
passing the provided Test5 meant we had provided sufficiently functional and robust File
System functionality. As a result of that base assumption, we also assumed that only minimal
validation of read and write requests was necessary, and that the user did not require any
means of performing file system interactions beyond the running of tests (as the assignment did not require implementation of any such interface). A third assumptions resultant from the first two was that file accesses were always acceptable requests, and that we did not need to
validate their source or provide any security measure to prevent unauthorized accesses.

## Internal Design
### Kernel
The Kernel facilitates user requests to SysLib, the public interface between user space and Kernel mode. For this project, we implemented functionality for the Kernel to accept requests to access/modify files located on the Disk. The Kernel interfaces with the actual File System to carry out those requests. We will outline only data members and methods relevant to this project and utilized or implemented by us.

#### Relevant Data Member
- **private static FileSystem fs:** an instance of our File System

#### Functions/Interrupt Cases
- **Case READ:** provides functionality to read keyboard input from user or read an existing file from the Disk, if it’s already opened. The 2nd part is completed by interfacing with fs.read( )
- **Case WRITE:** provides functionality to write to console, or interfase with the File System to write over an existing file (new or otherwise), write to a predefined location in a file, or append to the end of file.
- **Case OPEN:** interfaces with File System to open a requested file and grab the file descriptor from the Thread Control Block.
- **Case CLOSE:** interfaces with File System to close a requested file.
- **Case SIZE:** interfaces with File System to service a request for the size of an existing file.
- **Case SEEK:** interfaces with File System to change the location of the seek pointer in a requested file, returning a pointer to the files desired location.
- **Case FORMAT:** interfaces with File System to reformat the entities involved in the file system management by passing the number of files desired.
- **Case DELETE:** interfaces with File System to delete a requested file, true if successful and false if deletion failed.

### File System
The FileSystem class is reliant on the FileTable class (listed below). The File System contains all of logic for, and provides an interface for, all disk operations. It is reliant on calls from other components of the. ThreadOS system. It’s private members and functions are as follows.

#### Data Members:
- **private Superblock superblock:** a private instance of the superblock class for the file system.
- **private Directory directory:** a private instance of the directory class for the file system solution.
- **private FileTable fileTable:** a private instance of the FileTable class for the file system.

#### Functions:
- (constructor) **FileSystem(int diskSize):** Instantiates the three private members above and, if necessary, reconstructs the directory.
- **sync( ):** Not implemented, see sections Current Functionality and Limitations and
Possible Extended Functionality
- **public boolean(int files):** reformats the superblock and erases and reinstantiates the directory and file table, and then returns true to indicate success and completion.
- **public FileTableEntry open(String filename, String mode):** Opends a file in the
appropriate mode (eg. read, write, etc.) and returns the file’s file table entry.
- **public boolean close(FileTableEntry ftEnt):** Closes a file assuming that it is not
currently in use and returns a boolean to indicate success.
- **public int fsize(FileTableEntry ftEnt):** returns the length of the parameter entry’s inode’s length.
- **public int read(FileTableEntry fte, byte[] buffer):** Reads up to buffer length bytes from file, starting at possition of seek pointer. If bytes remaining between current seek pointer and end of file are < buffer length, SysLib.read as many bytes as possible and puts them in buffer. Increments seek pointer by number of bytes read. Returns number of bytes read or -1 on error.
- **public int write(FileTableEntry fte, byte[] buffer):** The contents of the buffer are written to the file specified by the parameter file table entry, starting from the seek pointer. The method iteratively adjusts the seek pointer and performs byte writes.
- **public boolean deallocAllBlocks(FileTableEntry ftEnt):** Writes all the inodes back to the disk.
- **public boolean delete(String filename):** Deletes a specified file by file name.
- **public int seek(FileTableEntry ftEnt, int offset, int whence):** Updates the seek pointer of a file table entry, returning -1 on failure and 0 or the pointer on success. Attempting to set the seek beyond the filesize puts it at the end of the file, and attempting to put seek pointer to a negative number sets it to 0.

### Inode
The Inode class serves as a descriptor and node holder of files. It contains 12 pointers, 11 of which are direct and 1 of which is indirect, to the next block. It has usage flags and pointers to keep track of a file’s state and usage. The system can accommodate as many as 16 inodes (not very many, see limitations for more).

#### Data Members:
- **public int length:** the file size in bytes
- **public short count:** the number of entries with pointers to this Inode
- **public short status:** a status code indicating whether the file is being read, written, used, not, or marked for delete. These codes are stored in the class as public final static shorts.
- **public short direct[ ]:** has a length of 11, and contains a list of the direct pointers.
- **public short indirect:** the indirect pointer

#### Functions:
- (default constructor) **public Inode():** The default constructor which initializes each value to defaults (mostly 0’s).
- **public Inode(short iNumber):** Takes in an inumber reads in the bytes, calculates the offset, allocates space, and instantiates the new Inode.
- **public int toDisk(short iNumber):** Saves the specified Inode to the disk.
- **public boolean setIndexBlock(short indexBlockNumber):** Register a free data block on disk for use as indirect index pointers using SysLib.rawwrite(). Returns false if indexBlockNumber is invalid/negative or if indirect is already used, else returns true.
- **public short findTargetBlock(int offset):** Searches direct and indirect index block pointers for data block with given offset returns block if found, else -1 on failure.
- **public int registerTargetBlock(int offset, short targetBlockNumber):** Register disk block with direct or indirect pointers using SysLib.rawwrite(). Returns 0 on success, -1, -2, or -3 on error for designation of particular types of errors.

### Superblock
The Superblock class manages the allocation of blocks and formatting the disk.

#### Data Members:
- **public int totalBlocks:** the number of blocks for the file system.
- **public int totalInodes:** the number of inodes per block
- **public int freeListHead:** the block number that indicates the beginning of the free list

#### Functions:
- (constructor) **public Superblock(int diskSize):** The default constructor which reads the
superblock information from disk into memory
- **void sbFormat(int inodes):** formats and resets data for each block of the disk
- **void sync():** writes back the current superblock information back to disk
- **int getFreeBlock():** Dequeue the top block from the free list
- **boolean returnBlock(int blockNumber):** Enqueue a given block to the front of the free list

### Directory
The directory is to manage active files. Two arrays are utilized to this end, fsize and fnames, keeping track of file sizes and names respectively.

#### Data Members:
- **private int fsize[]:** Each element stores a different file size.
- **private char fnames[][]:** each element stores a different file name as a char array.

#### Functions:
- (constructor) **Directory(int totalInodes):** this.fsize and this.fnames are each initialized to a length of the paramater value, total nodes. Each file size is initialized to 0 (which we believe java does anyways). Each subarray of fnames is initialized to a length of
maxChars. Lastly, the root is set up at index 0.
- **public int bytes2directory(byte data[]):** Takes a byte array of data and puts it into the directory. (NOTE: UNUSED, and not required for our purposes)
- **public byte[] directory2bytes():** Retrieves bytes of data from the directory.
- **public int ialloc(String filename):** allocates an Inode for a file. Returns either the index on success, or -1 on failure.
- **public ifree(short iNumber):** Deletes a file’s Inode and frees the space for another.
- **public short getinum(String filename):** Returns the iNumber for an existing file based
on its string ile name.

### File Table
The File Table’s primary role is file access management and data integrity. A thread’s request to open a file will be granted or put on hold ( wait() and notifyAll() ), depending on current user modes. It also creates new files for write/append requests on files that do not exist. Access to inode members and functionality is granted through the File Table.

#### Private Members:
- **Private Vector table:** a data structure for recording all instances of open
FileTableEntries.
- **Private Directory dir:** the root directory.

#### Public Methods:
- **Public synchronized FileTableEntry fallock( String filename, String mode):** allocates new FileTableEntry, registers directory inode, and increments count of using threads before updating the disk with new inode status. This method is also responsible for managing who has permission to open a file depending on request mode and current file usage by other threads, by calling Java’s wait() if necessary.
- **Public synchronized boolean ffree(FileTableEntry fte):** removes requested
FileTableEntry from vector, updates inode with Disk, and notifies all waiting threads that the file is available.
- **Public synchronized boolean fempty( ):** verifies if File Table is empty or not

## Performance Estimation
### Current Functionality and Limitations
Performance assessment, given the provided tests, was largely not quantitative. Observations like, that the final deliverable passed Test5, and that it behaved like the provided .class file, were the primary source of validation that we had correctly implemented our file system. Further, the observation that the disk did appropriately get changed on closing ThreadOS, and that during debugging appropriate changes could be observed in inconveniently large byte arrays, demonstrated that we provided the requisite behavior.

It was our observation that Test5 does not request service from SysLib.sync(), which in tern calls Kernel.sync(). We believe the intent of this method is for the file system to write the disk information to an actual file on the computer. In Linux, the man page describes this command as a way to force all changed blocks back to Disk and update the Superblock. Due to time limitations, and sync not being a required functionality, we chose not to implement it in the ThreadOS file system. So long as a file is properly closed by calling SysLib.close (as Test5 requests for all previously opened files), our system performs the correct functionality of saving changes to the simulated Disk and Superblock.

The most serious limitation differentiating our file system from a real one however is the lack of access tiers and permissions for reading and writing to ensure that proper authorization. Our file system methods can change the entire disk at will, and that functionality is provided through a public interface.

### Possible Extended Functionality
As previously mentioned, our file system does not currently offer the ability to perform a full synchronization of all open files with the Disk or Superblock. That means the Disk is volatile. Adding SysLib.sync() would allow all open files to be forced to Disk and/or saved to a local file.

Another possible area to add functionality would be to include some sort of disk cache. As our disk for ThreadOS is not an actual physical disk, this would have to be simulated using system memory. For ThreadOS, this is easy and a non-concern, as the disk is particularly small relative to memory. We could even use the enhanced second chance algorithm discussed in class and implemented in Program 4. Some of the limitations not mentioned above, which stem from inherent aspects of ThreadOS like how small it is, suggest further potential areas of expansion. For instance, the specified and implemented file system solution is not very good for large files (which aren’t really a factor in ThreadOS), because indirect pointers would need to be used after a very low threshold. That said, increasing Inode capacity isn’t necessarily a good idea given the application of ThreadOS.

## Test Results
After iterative development using Test5 to direct progress, using ThreadOS minimal in IntelliJ throughout the course of our efforts, we eventually managed to get all tests passing from the provided Test5.java. Upon reaching that point, we transferred our files to a clean, full version of ThreadOS, compiled them, and ran Test5 again, getting the same results (all tests passing). Consult Figure 1 for a full readout of these results.

#### Figure 1: Output From Test5 Utilizing the full ThreadOS on Linux
![image](https://user-images.githubusercontent.com/36549707/143809401-6432c081-61c3-496c-a54a-f73f20f6a381.png)

#### Figure 2: Output From Test5 After Commented Out Log Print-outs
![image](https://user-images.githubusercontent.com/36549707/143809155-b38f5674-9276-4bff-b199-9dc8cbdf2710.png)

