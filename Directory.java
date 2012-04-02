// Represents the unix root directory on memory. Stores the file names of
// files and is used to map these names to their respective inumbers
// Written by Joseph Kniest and Brad Baker
public class Directory
{
	private static int maxChars = 30; // max characters of each file name

	// Directory entries
	private int fsizes[ ];        // each element stores a different file size. (bytes)
	private char fnames[ ][ ];    // each element stores a different file name.

	// Not written to disk
	private int maxInumber;

	public Directory( int maxInumber )
	{
		this.maxInumber = maxInumber;
		this.fsizes = new int[ maxInumber ];
		for( int i = 0; i < maxInumber; i++ )
			fsizes[ i ] = 0;

		this.fnames = new char[ maxInumber ][ maxChars ];
		String root = "/";
		this.fsizes[ 0 ] = root.length( );
		root.getChars( 0, fsizes[ 0 ], fnames[ 0 ], 0 );
        }

	// Convert and instantiate this directory from the passed byte array
	public int bytes2directory( byte data[ ] )
	{
		try
		{
			int charsstart = 4 * this.maxInumber;
			for( int i = 0; i < this.maxInumber; i++ )
			this.fsizes[ i ] = SysLib.bytes2int( data, i * 4 );

			for( int i = 1; i < maxInumber; i++ )
			for( int j = 0; j < maxChars; j++ )
			this.fnames[ i ][ j ] =
			( char )SysLib.bytes2short( data, charsstart + ( i * 2 ) );
		}
		catch( ArrayIndexOutOfBoundsException aiobe ) { return -1; }

		return 0;
	}

	// Writes 30 characters to represent each filename no matter what,
	// to maintain logical disk addressing consistency
	public byte[ ] directory2bytes( )
	{
		byte[ ] directory = new byte[ ( 4 * this.maxInumber )
					 + ( 2 * this.maxInumber * maxChars ) ];

		int charsstart = 4 * this.maxInumber;
		for( int i = 0; i < maxInumber; i++ )
			SysLib.int2bytes( this.fsizes[ i ], directory, i * 4 );

		for( int i = 0; i < maxInumber; i++ )
		for( int j = 0; j < maxChars; j++ )
		SysLib.short2bytes( ( short )this.fnames[ i ][ j ],
			 directory, charsstart + ( i * 2 ) );

		return directory;
	}

	// Allocate a new file using the next available iNumber
	// and return that available iNumber
	public short ialloc( String filename )
	{
		for( int i = 0; i < maxInumber; i++ )
			if( fnames[ i ][ 0 ] == 0 )
			{
				fnames[ i ] = filename.toCharArray( );
				fsizes[ i ] = filename.length( );
				
				return ( short )i;
			}
		 
		return -1;
	}

	//Delete the inode (file) at the given iNumber
	public boolean ifree( short iNumber )
	{
		try
		{
			for (int i = 0; i < fsizes[ iNumber ]; i++)
				fnames[ iNumber ][ i ] = 0;

			fsizes[ iNumber ] = 0;
		}
		catch( ArrayIndexOutOfBoundsException aiobe )
		{ return false; }

		return true;
	}

	//return index of directory entry of given filename
	public short namei( String filename )
	{
         	for( int i = 0; i < maxInumber; i++ )
		{
			if( filename.equals( new String( fnames[ i ] ).trim( ) ) )	
				return ( short )i;
		}
		return -1;
	}

	public void p( )
	{
		System.out.println( "Start of directory" );
		for( int i = 0; i < fnames.length; i++ )
		{
			if( fnames[ i ][ 0 ] != 0 )
				System.out.println( fnames[ i ] );
		}
		System.out.println( "End of directory" );
	}

}

