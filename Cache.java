//-----------------------------------------------------------------------------------------
// Cache, maintains a cache of frequently used disk space accessed by a user program
// Supports the writing and reading of such byte data into and from its cached byte storage
//-----------------------------------------------------------------------------------------
import java.util.Vector;
class Cache
{
	//-------------------------------------------------------------------------------------
	// Data wrapper for the cached block. Stores a unique disk block number, bit 
	// determining whether it has been referenced, bit determining if it is being used,
	// and the byte data itself
	private class CacheBlock
	{
		private int diskblocknumber;
		private boolean referencebit;
		private boolean dirtybit;

		private byte[ ] blockdata;

		//---------------------------------------------------------------------------------
		// Allocates a new CacheBlock, initially set to unused/invalid, setting the size
		// of the byte array to parameter 'bsize'
		// pre : parameter 'bsize' is > 0
		// post: CacheBlock is setup, reference and dirty bits are 0/false
		public CacheBlock( int bsize )
		{
			bsize = ( bsize <= 0 ? 1 : bsize );
			this.diskblocknumber = -1;
			this.referencebit = false;
			this.dirtybit = false;
			this.blockdata = new byte[ bsize ];
		}

		public int getBlockId( )
		{
			return this.diskblocknumber;
		}

		public boolean isReferenced( )
		{
			return this.referencebit;
		}

		public boolean isDirty( )
		{
			return this.dirtybit;
		}

		public byte[ ] getBytes( )
		{
			this.referencebit = true;
			return this.blockdata;
		}

		public void setReferenced( boolean r )
		{
			this.referencebit = r;
		}

		public void invalidate( )
		{
			this.diskblocknumber = -1;
			this.dirtybit = false;
		}

		//-----------------------------------------------------------------------------------
		// Writes the passed bytes to this cacheblock, and resets its block number.
		// This is for writing/overwriting a cacheblock
		// pre : none
		// post: CacheBlock is written
		public void writeBytes( int blockId, byte[ ] buffer )
		{
			this.dirtybit = true;
			this.referencebit = true;
			this.diskblocknumber = blockId;
			try
			{
				System.arraycopy( buffer, 0, this.blockdata,
					this.blockdata.length, this.blockdata.length);
			}
			catch( ArrayIndexOutOfBoundsException aiobe ){ return; }
		}

	}

	private Vector<CacheBlock> cached;
	private int blockscapacity;
	private int blockbytesize;

	//-------------------------------------------------------------------------------------
	// Creates a new disk cache. Allocates 'cacheBlocks' number of blocks with 'blockSize'
	// sized byte array data. 
	// pre : parameters 'blockSize' and 'cacheBlocks' are both > 0
	// post: The cache is now initialized with 'cacheBlocks' number of unused blocks
	public Cache( int blockSize, int cacheBlocks )
	{
		blockSize = ( blockSize <= 0 ? 1 : blockSize );
		cacheBlocks = ( cacheBlocks <= 0 ? 1 : cacheBlocks );
		this.cached = new Vector<CacheBlock>( cacheBlocks );
		this.blockscapacity = cacheBlocks;
		this.blockbytesize = blockSize;
		for( int i = 0; i < this.blockscapacity; i++ )
			this.cached.add( new CacheBlock( this.blockbytesize ) );
	}

	//-------------------------------------------------------------------------------------
	// Read data from the cache. If the desired block is not found in the cache, read the
	// data from the corresponding block on disk to an unused cache block. If there are no
	// unused cache blocks, replace one with this operation using second chance
	// pre : parameter 'blockId' is > -1
	// post: Data has been read from the cache to the buffer, or an unused cacheblock now
	//       contains the data from its counterpart on the disk
	public boolean read( int blockId, byte[ ] buffer )
	{
		blockId = ( blockId < 0 ? -1 : blockId );
		CacheBlock readfrom = getBlock( blockId );

		if( readfrom == null )
		{
			boolean read = false;
			SysLib.rawread( blockId, buffer );
			for ( int i = 0; i < this.cached.size( ); i++ )
				if ( !this.cached.elementAt( i ).isDirty( ) )
				{
					this.cached.elementAt( i ).writeBytes( blockId, buffer );
					read = true;
				}

			if( !read )
				replaceWith( blockId, buffer );
		}
		else
			try
			{
				readfrom.setReferenced( true );
				System.arraycopy( readfrom.getBytes( ), 0, buffer,
										buffer.length, buffer.length);
			}
			catch( ArrayIndexOutOfBoundsException aiobe ){ return false; }
			
		return true;
	}

	//-------------------------------------------------------------------------------------
	// Write data to the cache. If the desired block is not found in the cache, write the
	// data to an unused cache block. If there are no unused cache blocks, replace one with 
	// this operation using second chance algorithm
	// pre : parameter 'blockId' is > -1
	// post: Data has been written to the cache from the buffer, or to an unused cacheblock
	public boolean write( int blockId, byte[ ] buffer )
	{
		blockId = ( blockId < 0 ? -1 : blockId );
		CacheBlock writeto = getBlock( blockId );

		if( writeto != null && !writeto.isDirty( ) )
			writeto.writeBytes( blockId, buffer );
		else
			try
			{
				boolean wrote = false;
				for( int i = 0; i < this.cached.size( ); i++ )
					if ( !this.cached.elementAt( i ).isDirty( ) )
					{
						this.cached.elementAt( i ).writeBytes( blockId, buffer );
						wrote = true;
					}

				if( !wrote )
					replaceWith( blockId, buffer );

			}
			catch( NullPointerException npe ){ return false; }
			catch( ArrayIndexOutOfBoundsException aiobe ) { return false; }	

		return true;
	}

	//-------------------------------------------------------------------------------------
	// Write back all used/dirty blocks to the disk. Maintain clean/unused blocks
	// pre : none
	// post: All used/dirty blocks are written back to the disk
	public void sync( )
	{
		for( int i = 0; i < this.cached.size( ); i++ )
			if ( this.cached.elementAt( i ).isDirty( ) )
				SysLib.rawwrite( this.cached.elementAt( i ).getBlockId( ),
					this.cached.elementAt( i ).getBytes( ) );
	}

	//-------------------------------------------------------------------------------------
	// Write back all used/dirty blocks to the disk. Invalidates all cached blocks
	// pre : none
	// post: All used/dirty blocks are written back to the disk
	public void flush( )
	{
		for( int i = 0; i < this.cached.size( ); i++ )
		{
			if( this.cached.elementAt( i ).isDirty( ) )
				SysLib.rawwrite( this.cached.elementAt( i ).getBlockId( ),
					this.cached.elementAt( i ).getBytes( ) );

			this.cached.elementAt( i ).invalidate( );

		}
	}

	//-------------------------------------------------------------------------------------
	// The business logic of replacing a block when all blocks are dirty. Relies on the
	// second chance algorithm to determine which block will be overwritten
	// pre : parameter 'blockId' > -1
	// post: A victim chosen by second chance is returned 
	private void replaceWith( int blockId, byte[ ] buffer )
	{
		CacheBlock victim = null;
		while( ( victim = secondChance( ) ) == null );
		if( victim.isDirty( ) )
			SysLib.rawwrite( victim.getBlockId( ), victim.getBytes( ) );

		victim.writeBytes( blockId, buffer );
	}

	//-------------------------------------------------------------------------------------
	// Returns null unless a victim is found by the second chance algorithm somewhere in
	// a pass through the cached blocks
	// pre : none
	// post: A victim of second chance from a pass through the blocks algorithm is returned
	private CacheBlock secondChance( )
	{
		CacheBlock check = null;
		try
		{
		for( int i = 0; i < this.cached.size( ); i++ )
		{
			check = this.cached.elementAt( i );
			if( check.isReferenced( ) )
			{
				check.setReferenced( false );
				this.cached.remove( check );
				this.cached.add( check );
				if( i + 1 == this.cached.size( ) ) return null;
			}
			else break;
		}
		}
		catch( NullPointerException npe ) { return null; }
		catch( ArrayIndexOutOfBoundsException aiobe ) { return null; }	

		return check;
	}

	//-------------------------------------------------------------------------------------
	// Returns the block in the cache whose id is passed. If no such block exists, null
	// pre : blockId is > -1
	// post: The block in the cache whose id is passed is returned if found, otherwise null
	private CacheBlock getBlock( int blockId )
	{
		for( int i = 0; i < this.cached.size( ); i++ )
			if( this.cached.elementAt( i ).getBlockId( ) == blockId )
				return this.cached.elementAt( i );

		return null;
	}

}
