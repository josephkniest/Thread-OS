// The main data wrapper for the file system
// Source written by Brad B Baker and Joseph Kniest
public class FileSystem
{
	private SuperBlock superBlock;
	private Directory dir;
	private FileTable fileTable;

	// Constructs a new filesystem, initializing a superblock
	// directory and filetable onto main memory
	public FileSystem( int diskBlocks )
	{
		this.superBlock = new SuperBlock( diskBlocks );
		this.dir = new Directory( this.superBlock.inodeBlocks( ) * 16 );
		fileTable = new FileTable( dir );
		FileTableEntry dirfEnt = open( "/", "r" );
		int dirsize = fsize( dirfEnt );

		if( dirsize > 0 )
		{
			byte[ ] dirdata = new byte[ dirsize ];
			read( dirfEnt, dirdata );
			dir.bytes2directory( dirdata );
		}
		close( dirfEnt );
	}

	// Instructs the filetable to allocate a new file descriptor
	// for the calling thread, and returns it
	// pre : Parameter 'filename' is not null, parameter 'mode' is not null,
	//       and is a valid file mode
	// post: The value of the calling thread's next file descriptor is returned
	public FileTableEntry open( String filename, String mode )
	{

		FileTableEntry fEnt = fileTable.falloc( filename, mode );
		if( mode.equals( "w" ) )
			if( !deallocateAllBlocks( fEnt ) )
				return null;

		return fEnt;
	}

	// Calling thread reads as much data as it can from the file
	// specified by the table entry into the buffer
	// pre : Parameters 'fEnt' and 'buffer' are not null
	// post: Returns the number of bytes read from the file into
	// the buffer, or -1 if there was any error in the operation 
	public int read( FileTableEntry fEnt, byte[ ] buffer )
	{
		if( fEnt == null )
			return -1;

		int curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
		if( curblk == -1 )
			return -1;

		int remainingbufferspace = buffer.length;
		int readbytes = 0;
		byte[ ] inodeBlock = new byte[ 512 ];

		int currPos = fEnt.seekPtr % 512;

		 // number of bytes to be copied over this time
		int limit = ( 512 - currPos < remainingbufferspace
		? 512 - currPos : remainingbufferspace );

		SysLib.rawread( curblk, inodeBlock );

		//Eof?
		limit = ( limit >= fEnt.inode.length
		? fEnt.inode.length : limit );

		// Read the last bytes in this block
		for( int i = 0; i < limit; i++ )
			buffer[ i ] = inodeBlock[ currPos ];

		remainingbufferspace -= limit;
		readbytes += limit;
		fEnt.seekPtr += limit;

		// Eof was hit, return read bytes
		if( limit == fEnt.inode.length )
			return readbytes;

		// Writing full blocks
		while( remainingbufferspace > 512
			&& !( ( fEnt.seekPtr + 512 ) >= fEnt.inode.length ) )
		{
			curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
			if( curblk == -1 )
				return readbytes;

			SysLib.rawread( curblk, inodeBlock );
			for( int i = 0; i < 512; i++ )
				buffer[ i + readbytes ] = inodeBlock[ i ];

			remainingbufferspace -= 512;
			readbytes += 512;
			fEnt.seekPtr += 512;
		}

		// Get last block
		curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
			if( curblk == -1 )
				return readbytes;

		// Do last bytes in next block
		SysLib.rawread( curblk, inodeBlock );

		//Eof?
		limit = ( limit >= fEnt.inode.length
		? fEnt.inode.length : limit );

		for( int i = 0; i < limit; i++ )
			buffer[ i + readbytes ] = inodeBlock[ i ];

		readbytes += limit;
		fEnt.seekPtr += limit;

		return readbytes;
	}

	// Calling thread writes as much data as it can from the file
	// specified by the table entry into the buffer. Additional
	// blocks are allocated to the inode as needed
	// pre : Parameters 'fEnt' and 'buffer' are not null
	// post: Returns the number of bytes written to the file from
	// the buffer, or -1 if there was any error in the operation
	public int write( FileTableEntry fEnt, byte[ ] buffer )
	{

		if( fEnt == null ) 
			return -1;

		int remainingbytes = buffer.length;
		int writtenbytes = 0;
		byte[ ] inodeBlock = new byte[ 512 ];
		// Find the starting block
		int curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
		// Allocate a new block for the inode and reset curblock
		if( curblk == -1 )
		{
			fEnt.inode.addBlock( ( short )this.superBlock.getFreeBlock( ) );
			curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
		}

		// copy buffer to the end of the block, or to the end of buffer	
		int currPos = fEnt.seekPtr % 512; 
		// number of bytes to be copied over this time
		int limit = ( 512 - currPos < remainingbytes 
		? 512 - currPos : remainingbytes );

		SysLib.rawread( curblk, inodeBlock );
		for( int i = 0; i < limit; i++ )
			inodeBlock[ currPos + i ] = buffer[ i ];

		// update seek ptr
		fEnt.seekPtr += limit;

		// update number of remainingbytes
		remainingbytes -= limit;

		// update number of written bytes
		writtenbytes += limit;

		// write the updated block to disk
		SysLib.rawwrite( curblk, inodeBlock );

		// write the middle blocks
		while( remainingbytes > 512 )
		{

			curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
			// Allocate a new block for the inode and reset curblock
			if( curblk == -1 )
			{
				fEnt.inode.addBlock( ( short )this.superBlock.getFreeBlock( ) );
				curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );

				// no more blocks for the inode?
				if( curblk == -1 )
					return -1;
			}

			// write next whole 512 bytes from buffer to block
			for( int i = 0; i < 512; i++ )
				inodeBlock[ i ] = buffer[ i + ( buffer.length - remainingbytes ) ];

			// update seek ptr	
			fEnt.seekPtr += 512;

			// update number of remainingbytes
			remainingbytes -= 512;

			// update number of written bytes
			writtenbytes += 512;

			// write the updated block to disk
			SysLib.rawwrite( curblk, inodeBlock );

		}

		curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
		// Allocate a new block for the inode and reset curblock
		if( curblk == -1 )
		{
			fEnt.inode.addBlock( ( short )this.superBlock.getFreeBlock( ) );
			curblk = fEnt.inode.findTargetBlock( fEnt.seekPtr );
		}

		// wipe the temporary byte[] data store
		inodeBlock = new byte[ 512 ];

		// write remaining bytes to next block
		for( int i = 0; i < remainingbytes; i++ )
			inodeBlock[ i ] = buffer[ i + ( buffer.length - remainingbytes ) ];

		// update seek ptr
		fEnt.seekPtr += remainingbytes;	

		writtenbytes += remainingbytes;	

		// write the updated block to disk
		SysLib.rawwrite( curblk, inodeBlock );

		fEnt.inode.length = ( fEnt.seekPtr 
		> fEnt.inode.length ? fEnt.seekPtr : fEnt.inode.length );

		return writtenbytes;
	}

	// Return the file size of the calling thread's file,
	// or -1 if there was an error
	public int fsize( FileTableEntry fEnt )
	{
		if( fEnt == null )
			return -1;

		int len = 0;
		try{ len = fEnt.inode.length; }
		catch( NullPointerException npe )
		{ return -1; }

		return len;
	}

	// Modifies the calling thread's open file's seek pointer
	// (modifies the File Table Entry referenced)
	// pre : Parameter 'fEnt' is not null, and 'whence' is
	//       valid, i.e. = 1, 2 or 0
	// post: The referenced file table entry's seek pointer
	//       has been adjusted and 0 is returned, or if there
	//       was an error, -1 was returned  
	public int seek( FileTableEntry fEnt, int offset, int whence )
	{
		int fSize = fsize( fEnt );
		if( whence == 0 )
			fEnt.seekPtr = offset;
		else if( whence == 1 )
			fEnt.seekPtr += offset;
		else if( whence == 2 )
			fEnt.seekPtr = fSize + offset;
		else
			return -1;

		if( fEnt.seekPtr < 0 )
			fEnt.seekPtr = 0;

		if( fEnt.seekPtr > fSize )
			fEnt.seekPtr = fSize;

		return 0;
	}

	// Closes the file associated with the calling thread by
	// removing the corresponding file table entry
	// pre : Parameter 'fEnt' is not null
	// post: The file table entry for this thread is removed 
	public int close( FileTableEntry fEnt )
	{
		if( fEnt == null )
			return -1;

		return ( this.fileTable.ffree( fEnt ) ? 0 : -1 );
	}

	// Enables the calling thread to delete the file specified
	// by 'filename.' Will wait until all other threads are done
	// operating on the file to actually delete it
	// pre : Parameter 'filename' is not null
	// post: The file's contents on memory are reset, inode's
	//       block pointers are reset
	public int delete( String filename )
	{
		short iNum = this.dir.namei( filename );
		if( iNum == -1 )
			return -1;

		return ( this.dir.ifree( iNum ) ? 0 :-1 );
	}

	// Removes all blocks associated with this file table entry
	// from memory and writes them to disk
	private boolean deallocateAllBlocks( FileTableEntry fEnt )
	{
		if( fEnt == null )
			return false;

		fEnt.seekPtr = 0;
		for( int i = 0; i < fEnt.inode.direct.length; i++ )
			this.superBlock.returnBlock( fEnt.inode.direct[ i ] );
			
		fEnt.inode.reset( );
		
		return true;
	}

	// Instructs the file system's superblock to format the disk
	public int format( int files )
	{
		if( this.fileTable.fempty( ) )
			return this.superBlock.format( files );
		else 
			return -1;		
	}

	// Synchronizes the superblock
	public int syncSuperBlock( )
	{
		return this.superBlock.sync( );
	}

}
