//------------------------------------------------------------------
// class Shell:
// The shell component of the threadOS operating system simulator
// This module extends the general functionality of a thread, and
// thusly behaves like any other threadOS process
//------------------------------------------------------------------
import java.util.*;
public class Shell extends Thread
{
  
 //------------------------------------------------------------------
 // class constructor
 // pre : none
 // post: none
 public Shell( )
 {
 }
 
 //------------------------------------------------------------------
 // Parses a string tokenizer around the sequences '&' and ';', all
 // other tokens are assumed to be part of the same sequence until 
 // one such character is reached. Throws illegal argument exception
 // if the parameter 'st' is null
 // pre : Parameter 'st' is not null
 // post: A string array whose elements consist of either a process
 //       name followed by its arguments, a "&" delimiter, or a ";"
 //       delimiter is returned
 private String[ ] parseBuffer( StringTokenizer st )
 {
   if( st == null ) 
     throw new IllegalArgumentException( );
   
   String[ ] retval = new String[ st.countTokens( ) ];
   int numatkns = 0;
   while( st.hasMoreTokens( ) )
   {
     String tkn = st.nextToken( );
     if( tkn.equals( "&" ) || tkn.equals( ";" ) )
     {
       numatkns++;
       retval[ numatkns ] = tkn;
       numatkns++;
     }
     else
     {
       if( retval[ numatkns ] == null )
       {
         retval[ numatkns ] = "";
         retval[ numatkns ] = retval[ numatkns ].concat( tkn );
       }
       else
         retval[ numatkns ] = retval[ numatkns ].concat( " " + tkn ); 
      }
  }
  return retval;
 }
 
 //------------------------------------------------------------------
 // Derived from its super class, this function executes the shell's
 // business logic, namely the elicitation and appropriate processing
 // of a command line entered by the user.
 // pre : none
 // post: Valid processes entered by user have been executed and
 //       terminated, and any changes in memory or disk stipulated by
 //       those processes have occurred
 public void run( )
 {
   int shellCount = 0;
   while( true )
   { 
     StringBuffer inBuffer = new StringBuffer( );
     shellCount++;
     SysLib.cout( "shell["+shellCount+"]% " );
     SysLib.cin( inBuffer );
     if( inBuffer.toString( ).equals( "exit" ) )
       break;

     String[ ] cmdline =
       parseBuffer( new StringTokenizer( inBuffer.toString( ) ) ) ;

     for( int i = 0; cmdline[ i ] != null; i++ )
     {
       if( cmdline[ i ].equals( "&" ) ) {/*continue with next cmd*/}
       else if( cmdline[ i ].equals( ";" ) )
         SysLib.join( );
       else
         SysLib.exec( SysLib.stringToArgs( cmdline[ i ] ) );
     }
   }
  SysLib.cout( "EOA!" );
 }
}
