// Represents the superblock module in memory, is responsible
// for maintaining the disk, returning free blocks
class SuperBlock
{
	public final int defaultInodeBlocks = 64;
	private int totalBlocks;
	private int inodeBlocks; //num files
	private int freeList;

	public int lastFree;

	public SuperBlock ( int diskSize ) {
		// read the superblock from disk
		byte[] superBlock = new byte[ Disk.blockSize] ;
		SysLib.rawread( 0, superBlock );

		this.totalBlocks = SysLib.bytes2int( superBlock, 0 );
		this.inodeBlocks = SysLib.bytes2int( superBlock, 4 );
		this.freeList = SysLib.bytes2int( superBlock, 8 );
		this.lastFree = SysLib.bytes2int( superBlock, 12 );

		//disk haS been formatted?
		if ( !( this.totalBlocks == diskSize &&
			 this.inodeBlocks > 0 && this.freeList >= 2
			&& this.lastFree <= this.totalBlocks) )
		 {
			//need to format disk
			this.totalBlocks = diskSize;
			this.lastFree = diskSize - 1;
			format( defaultInodeBlocks );
		}
	}

	public int format (int numInodes ) {
		//creates inodeBlocks full of inodes and writes them to disk.

		this.inodeBlocks = numInodes / 16;
		this.freeList = this.inodeBlocks + 1;
		try {
			byte[] inodeBlock = new byte[512]; //one inodeBlock
			byte[] inode = new byte[32];	   //one default inode
			SysLib.int2bytes( 0, inode, 0 );
            		SysLib.short2bytes( (short)0, inode, 4 );
        		SysLib.short2bytes( (short)0, inode, 6 );
			for( int i = 0; i < 11; i++ ) //magic 11: Inode.direct[].size
        			SysLib.short2bytes( (short)-1, inode, (8 + (2 * i) ) );

        		SysLib.short2bytes( (short)-1, inode, 30 );

			for (int i = 0; i < 16; i++) //fill inodeBlock with default inodes
				System.arraycopy( inode, 0, inodeBlock, i * 32, 32 );
			//writes inodeBlocks to propper disk blocks
			for (int i = 1; i <= inodeBlocks; i++)
				SysLib.rawwrite( i, inodeBlock );

		//initializes all free blocks and sets up freeList
		//first free block to next to last disk block
		byte[] block = new byte[ 512 ];
		for (int i = 0; i < 512; i++)
			block[i] = 0;

		for (int i = inodeBlocks; i < totalBlocks -2; i++) {
			short next = ( short )( i + 1 );
			SysLib.short2bytes( next, block, 0 ); //write next block ptr
			SysLib.rawwrite( i, block ); //write block to disk
		}
		//last disk block
		SysLib.short2bytes( (short)-1, block, 0 ); //write next block ptr
		SysLib.rawwrite( totalBlocks -1, block );
		return sync( );
		}
		catch( ArrayIndexOutOfBoundsException aiobe ) { return -1; }
	}
	// Write the disk's metadata to the 0 block on disk
	public int sync () {
		//write back totalBlocks, inodeBlocks, and freeList to disk
		try {
			byte[] block = new byte[512];
			SysLib.int2bytes( this.totalBlocks, block, 0 );
			SysLib.int2bytes( this.inodeBlocks, block, 4 );
			SysLib.int2bytes( this.freeList, block, 8 );
			SysLib.int2bytes( this.lastFree, block, 12 );
			SysLib.rawwrite( 0, block );
		    }
		catch ( ArrayIndexOutOfBoundsException aiobe) { return -1; }
		return 0;
	}

	// Advances the freelist one block and returns the current free block
	public int getFreeBlock( )
	{
		//Dequeue the top block from the free list
		int freed = freeList;
		if ( freed == -1 )
			return -1;

		byte[] nextfreeblock = new byte[512];
		SysLib.rawread( freeList, nextfreeblock );
		Short next = new Short( SysLib.bytes2short( nextfreeblock, 0 ) );
		// advance freeList
		freeList = next.intValue( );
		return freed;
	}

	//Frees the block passed by the index and modifies the freeList
	public int returnBlock( short blockNumber ) {
		//Enqueue a given block to the end of the free list
		if( !( blockNumber > inodeBlocks && blockNumber < totalBlocks ) )
			return -1;
		byte[] last = new byte[512];
		SysLib.rawread( lastFree, last ); //read next free Block

		SysLib.short2bytes( (short)blockNumber, last, 0 ); //replace last 
		SysLib.rawwrite( lastFree, last );
		lastFree = blockNumber;
		return 0;
	}
	
	public int inodeBlocks( )
	{
		return this.inodeBlocks;
	}
}
