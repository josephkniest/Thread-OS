// Represents an inode on memory, maintaining pointers to all the blocks
// that hold the file's data. Written by Joseph Kniest and Brad Baker
public class Inode
{
	private final static int iNodeSize = 32;          // fix to 32 bytes
	private final static int directSize = 11;         // # direct pointers
	public int length;                                // file size in bytes
	public short count;                               // # file-table entries pointing to this
	public short flag;                                // 0 = unused, 1 = used, ...
	public short direct[ ] = new short[ directSize ]; // direct pointers
	public short indirect;                            // a indirect pointer

	Inode( )
	{
        	length = 0;
        	count = 0;
        	flag = 1;
        	for( int i = 0; i < directSize; i++ )
        		direct[ i ] = -1;

        	indirect = -1;
	}

	// Read in the inode from the disk that is specified by the inumber
	Inode( short iNumber )
	{
		int readblock = ( iNumber / 16 ) + 1;
		byte[ ] inode = new byte[ 32 ];
		byte[ ] block = new byte[ 512 ];
		SysLib.rawread( readblock, block );
		int startaddress = ( iNumber % 16 ) * 32;
		System.arraycopy( inode, 0, block, startaddress, 32 );

            	this.length = SysLib.bytes2int( inode, 0 );
		this.count = SysLib.bytes2short( inode, 4 );
		this.flag = SysLib.bytes2short( inode, 6 );
		for( int i = 0; i < directSize; i++ )
			this.direct[ i ] = SysLib.bytes2short( inode, 8 + ( i * 2 ) );

		this.indirect = SysLib.bytes2short( inode, 8 + ( 2 * directSize ) );
	}

	// Write this inode's data back to disk
	// Implement option 2: to maintain inode consistency!
	int toDisk( short iNumber )
	{
		int writeblock = ( iNumber / 16 ) + 1;
		byte[ ] inode = new byte[ 32 ];
		byte[ ] block = new byte[ 512 ];
		int writebackoffset = ( iNumber % 16 ) * 32;
		SysLib.rawread( writeblock, block );

        	SysLib.int2bytes( this.length, inode, 0 );
            	SysLib.short2bytes( this.count, inode, 4 );
        	SysLib.short2bytes( this.flag, inode, 6 );
		for( int i = 0; i < directSize; i++ )
			SysLib.short2bytes( this.direct[ i ], inode, 8 + ( i * 2 ) );

        	SysLib.short2bytes( this.indirect, inode, 30 );

		System.arraycopy( inode, 0, block, writebackoffset, 32 );
		SysLib.rawwrite( writeblock, block );
		return 0;
	}

	// Functionality to use Inodes

	//adds a freeBlock to this inode
	//uses direct blocks first, then indirect if(file size > 12 blocks)
	//HOW TO KEEP TRACK OF FILE SIZE?
	boolean addBlock (short blockNumber) {
		//try direct
		for (int i = 0; i < directSize; i++) {
			if (direct[i] == (short)-1) {
				direct[i] = blockNumber;
				return true;
			}
		}
		
		return false; //inode full
	}

	// gets block number from an offset
	short findTargetBlock ( int offset ) {
		short blk = ( short ) (offset / 512);
		if ( blk >= directSize )
			return -1;
			
		return direct[ blk ];
	}

	// reset this inode, setting all its values back to default
	public void reset( )
	{
		this.length = 0;
		this.count = ( short )0;
		this.flag = ( short )0;
		for( int i = 0; i < directSize; i++ )
			this.direct[ i ] = ( short )0;
	}
}

