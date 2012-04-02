//------------------------------------------------------------------
// class Scheduler, a threadOS thread responsible for maintaining
// operational synchronization of all executing user threads. This
// module implements the functionality using a multi-level feed-
// back queue setup
//------------------------------------------------------------------
import java.util.*;
public class Scheduler extends Thread
{
  private Vector[ ] queues;
  private int timeSlice;
  private static final int DEFAULT_TIME_SLICE = 1000;
  
  private boolean[ ] tids;
  private static final int DEFAULT_MAX_THREADS = 10000;

  //----------------------------------------------------------------
  // No parameter constructor, sets the standard time quantum
  // and max threads to their defaults
  public Scheduler( )
  {
    this( DEFAULT_TIME_SLICE );
  }
  
  //----------------------------------------------------------------
  // Allows client to specify the time in miliseconds the length
  // of the standard quantum is, sets max threads to default
  public Scheduler( int quantum )
  {
    this( quantum, DEFAULT_MAX_THREADS );
  }
  
  //----------------------------------------------------------------
  // Allows client to specify the time in miliseconds the length
  // of the standard quantum and the max possible threads this
  // scheduler can handle at a given state
  public Scheduler( int quantum, int maxthreads )
  {
    this.queues = new Vector[ 3 ];
    this.queues[ 0 ] = new Vector( );
    this.queues[ 1 ] = new Vector( );
    this.queues[ 2 ] = new Vector( ); 
    this.timeSlice = quantum;
    this.tids = new boolean[ maxthreads ];
    for( int i = 0; i < maxthreads; i++ )
      this.tids[ i ] = false;
  }
  
  //----------------------------------------------------------------
 // Called from the kernel, this function adds a thread to the
 // zero execution queue as prescribed by the multi-level feedback
 // queue scheduling algorithm
 // pre : Parameter 't' is not null
 // post: A new thread control block set to the passed thread
 //       has been added to the zero queue
  public TCB addThread( Thread t )
  {
    int tid = getNewTid( );
    TCB curthread = getMyTcb( );
    int pid = ( curthread != null ? curthread.getTid( ) : -1 );
    TCB newthrd = new TCB( t, tid, pid );
    queues[ 0 ].add( newthrd );
    return newthrd;
  }
  
  //----------------------------------------------------------------
  // Simply sets the current thread's control block to terminated,
  // returns true unless there are no threads to be terminated
  // pre : none
  // post: The current thread is set to terminated, or if there are
  //       no threads, false is returned
  public boolean deleteThread( )
  {
    TCB tcb = getMyTcb( );
    if( tcb == null )
      return false;
    
    tids[ tcb.getTid( ) ] = true;
    return tcb.setTerminated( );
  }
  
  //----------------------------------------------------------------
  // Return the TCB of the currently executing thread. Returns null
  // if there is no executing thread. 
  // pre : none
  // post: A handle to the current thread is returned
  public TCB getMyTcb( )
  {
    Thread thrd = Thread.currentThread( );
    synchronized( queues )
    {
      for( int i = 0; i < 3; i++ )
      {
        for( int j = 0; j < queues[ i ].size( ); j++ )
        {
          TCB check = ( TCB )queues[ i ].elementAt( j );
          Thread t = check.getThread( ); 
          if( t != null && t == thrd )
            return check;
        } 
      }
    }
    return null;
  }

  //---------------------------------------------------------------
  // Returns the index of the queue whose thread should run.
  // Current thread is defined by
  // the MLFQ algorithm to be the next thread in the zero queue, 
  // and the next thread in the one queue of the zero queue is
  // empty, and so on for the two queue 
  private int nextPriority( )
  {
    if( !this.queues[ 0 ].isEmpty( ) )
      return 0;
    else if( !this.queues[ 1 ].isEmpty( ) )
      return 1;
    else if( !this.queues[ 2 ].isEmpty( ) )
      return 2;
    else
      return -1;
  }

  //----------------------------------------------------------------
  // This scheduler thread sleeps for a given time quantum
  // pre : Parameter 'quantum' is > 0
  // post: Thread has slept for 'quantum' time
  public void sleepThread( int quantum )
  {
    try { Thread.sleep( quantum ); }
    catch( InterruptedException ie ) { }
  }
  
  //----------------------------------------------------------------
  // Sets the lowest available thread id value to unavailable, then
  // returns it, if there are no available tids, the scheduler is
  // maxed and -1 is returned
  // pre : none
  // post: The index of an available thread id is returned, or if
  //       none, -1 is returned
  private int getNewTid( )
  {
    for( int i = 0; i < tids.length; i++ )
      if( !this.tids[ i ] )
      {
        this.tids[ i ] = true;
        return i;
      }
    return -1;
  }
  
  //----------------------------------------------------------------
  // Returns the max number of threads this scheduler can support
  // pre : none
  // post: Max number of supported threads is returned
  public int getMaxThreads( )
  {
    return tids.length;
  }
  
  //----------------------------------------------------------------
  // Resets the thread's priority by moving it to the back of the
  // next queue up. If the current queue is 2 however the thread is
  // moved to the back of the queue
  // pre : Parameter 'q_index' is between 0 and 2, 'thread' != null 
  private void reprioritizeThread( TCB thread, int q_index )
  {
      if( q_index == 2 )
      {
        queues[ 2 ].remove( thread );
        queues[ 2 ].add( thread );
      }
      else
      {
        queues[ q_index ].remove( thread );
        queues[ q_index + 1 ].add( thread );
      }
  }
  
  //----------------------------------------------------------------
  // Business logic of the scheduler. This attempts to execute the
  // current thread, and if that thread is not finished executing
  // when its quantum is over it is moved to the back of the next
  // queue. If that queue is two however, it is continuously pushed
  // to the back of that queue until it is done executing
  public void run( )
  {
    TCB curthread = null;
    int q_index = -1;
    int phase[ ] = new int[ 3 ];
    phase[ 0 ] = 0;
    phase[ 1 ] = 0;
    phase[ 2 ] = 0;

    while( true )
    {
      q_index = nextPriority( );
      curthread = ( q_index > -1 ? 
		( TCB )queues[ q_index ].firstElement( ) : null );   
      try
      {
      // Handle appropriate thread execution
      if( curthread == null || curthread.getThread( ) == null )
        continue;
      else if( curthread != null &&
              curthread.getTerminated( ) && q_index > -1 )
      {
        phase[ q_index ] = 0;
        queues[ q_index ].remove( curthread );
      }
      else
      {
        if( !curthread.getThread( ).isAlive( ) )
          curthread.getThread( ).start( );
        else
          curthread.getThread( ).resume( );
        
        // Allow execution of current thread for half a quantum
        sleepThread( timeSlice / 2 );
	if( q_index > -1 )
          phase[ q_index ]++;

      
        // If that thread is still not finished executing then
        // reprioritize it and temporarily suspend it
        if( curthread != null && curthread.getThread( ) != null
             && curthread.getThread( ).isAlive( ) && q_index > - 1 )
        {
          synchronized( queues )
          {
          curthread.getThread( ).suspend( );
	  if( q_index == 0 || phase[ q_index ] == ( 2 * q_index ) )
	  {
	    phase[ q_index ] = 0;
            reprioritizeThread( curthread, q_index );
	  }
          }
        }
       
      }
      }
      catch( NullPointerException npe ) {  }
    }
  }
}
