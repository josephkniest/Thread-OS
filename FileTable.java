// FileTable, maintains a list of all open files in a table 
// in kernel memory
// Written by Brad B Baker and Joseph Kniest
import java.util.Vector;
public class FileTable
{
	private Vector<FileTableEntry> table;// file table entries
	private Directory dir;		// Root directory

	// Constructs the filetable module and allocates memory for
	// its member fields
	public FileTable( Directory dir )
	{
		this.dir = dir;
		this.table = new Vector<FileTableEntry>( );
	}

	// Create a new entry into the table and potentially allocate a new
	// inode if it is not already on kernel memory. Inode.flag = 0, unused,
	// 1 being read, 2 being written. Threads that request a write operation
	// to a file must wait for another thread to finish with that file
	// before it is allowed access thereto
	// pre : Parameter 'filename' is not null, parameter 'mode' is not null,
	//       and is a valid file mode
	// post: A new file table entry is both allocated for the calling thread
	//	 and into the list of file table entries
	public synchronized FileTableEntry falloc( String filename, String mode )
	{
		Inode inode = null;
		short iNumber = -1;
		while( true )
		{
			iNumber = ( filename.equals( "/" ) ?
					 0 : dir.namei( filename ) );	 
			if( iNumber >= 0 )
			{
				// Loads the existing inode from disk
				// into memory, for opening existing file
				inode = new Inode( iNumber );
				if( mode.equals( "r" ) )
				{
					// file is available for reading?
					if( inode.flag == 0 || inode.flag == 1 )
					{
						inode.flag = 1;
						break;
					}
					else if( inode.flag == 2 )//being written
						try{ wait( ); }
						catch( InterruptedException ie )
						{ return null; }

					inode.flag = 1;
				}
				else if( mode.equals( "w" ) 
					|| mode.equals( "w+" ) 
					|| mode.equals( "a" ) )
				{
					// file is available for writing?
					if( inode.flag == 0 )
					{
						inode.flag = 2;
						break;
					}
					if( inode.flag == 1 || inode.flag == 2 )
						try{ wait( ); }
						catch( InterruptedException ie )
						{ return null; }

					inode.flag = 2;
				}
			}
			else
			{
				// file does not exist to read!
				if( mode.equals( "r" ) )
					return null;

				inode = new Inode( );
				iNumber = dir.ialloc( filename );

				// No more room for additional files/inodes?
				if( iNumber == -1 )
					return null;
			}
		}
		inode.count++;
		inode.toDisk( iNumber );
		FileTableEntry fEnt = new FileTableEntry( inode, iNumber, mode );
		this.table.add( fEnt );
		return fEnt;
	}

	// Forces the thread to notify, meaning that it is done with
	// this entry. Also checks if the inode's thread-use count
	// is zero, in which case it is removed from memory and
	// written to disk
	// pre : Paramete 'fEnt' is not null
	// post: 
	public synchronized boolean ffree( FileTableEntry fEnt )
	{
		if( fEnt == null )
			return false;

		notifyAll( );

		// Need to also write this file's data to the disk
		// using the inode's direct pointers
		fEnt.count--;
		if( fEnt.count == 0 )
		{
			fEnt.inode.count--;
			if( fEnt.inode.count == 0 )
			{
				fEnt.inode.flag = 0;
				fEnt.inode.toDisk( fEnt.iNumber );
				this.table.remove( fEnt );

			}
		}

		return true;
	}

	// Checks if this file table is empty
	public synchronized boolean fempty( )
	{
		return table.isEmpty( );
	}
}
