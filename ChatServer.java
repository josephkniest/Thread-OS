//----------------------------------------------------------------
// Acts as the intermediary for chat messaging relay
// Maintains a list of all clients participating in the car
// session by their socket references.
//----------------------------------------------------------------
// Specification
//
// -- When accepting new client connections, only allows
//    a client 500 msec to connect before moving on
//----------------------------------------------------------------
// Assumptions
//
// -- There will be 255 or less clients attempting to connect
//----------------------------------------------------------------
import java.net.*;
import java.io.*;
public class ChatServer
{
	private ServerSocket serverSocket;
	private Socket[ ] clientSockets;
	private int numClients;
	private static final int maxClients = 255;

	private boolean newInput;

	//-------------------------------------------------------
	// Sets up the server's socket assigning it the port
	// passed from the terminal
	//-------------------------------------------------------
	public ChatServer( int port )
	{
		numClients = 0;
		newInput = false;
		try
		{
			serverSocket = new ServerSocket( port );
			clientSockets = new Socket[ maxClients ];
			while( true )
			{
				if( acceptClient( ) )
				{
				DataInputStream in = new DataInputStream( 
				clientSockets[ numClients - 1 ].getInputStream( ) );

				outToClients( in.readUTF( ) + " has joined the chat room." );
				}

				if( numClients > 0 )
					serviceClients( );
			}
		}
		catch( UnknownHostException uhe ){ }
		catch( IOException ioe ){ }
	}

	//-------------------------------------------------------
	// Subthread responsible for accepting a new client
	// connection. If no client connects to the server
	// within 500 msec, no connection is made and this
	// returns false, otherwise returns true after
	// storing the new client reference
	//-------------------------------------------------------
	public boolean acceptClient( )
	{
		try
		{
		serverSocket.setSoTimeout( 500 );
		clientSockets[numClients] = serverSocket.accept();
		if( clientSockets[ numClients ] == null )
			return false;
		}
		catch( SocketException se )
		{ return false; }
		catch( SocketTimeoutException ste )
		{ return false; }
		catch( IOException ioe )
		{ return false; }

		numClients++;
		return true;
	}

	//------------------------------------------------------
	// Sends the passed string to all clients logged in
	//------------------------------------------------------
	void outToClients( String str )
	{
		System.out.println( str );
	}

	//-------------------------------------------------------
	// Checks for input from any client, and writes new
	// any new input to all clients' output streams
	//-------------------------------------------------------
	void serviceClients( )
	{
		try
		{
		String input = null;
		for( int i = 0; i < numClients; i++ )
			if( ( input = new DataInputStream(
			 clientSockets[ i ].getInputStream( ) )
			.readUTF( ) ) != null  )
			{
				for( int j = 0; j < numClients; j++ )
				{
				DataOutputStream o = new DataOutputStream(
				clientSockets[ j ].getOutputStream( ) );

				o.writeUTF( input ); 
				}

			}

		outToClients( "" );
		}
		catch( UnknownHostException uhe ){ }
		catch( IOException ioe ){ }
	}

	//-------------------------------------------------------
	// Is called from jvm and initializes the chat server to
	// use the passed port
	// Usage: java ChatServer <portnumber>
	//-------------------------------------------------------
	public static void main( String[ ] args )
	{
		new ChatServer( Integer.parseInt( args[ 0 ] ) );
	}
}
