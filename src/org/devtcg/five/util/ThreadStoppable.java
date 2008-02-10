package org.devtcg.five.util;

public class ThreadStoppable extends Thread
{
	protected boolean mStopped = false;
	
	public ThreadStoppable()
	{
		super();
	}
	
	public ThreadStoppable(Runnable target)
	{
		super(target);
	}
	
	/**
	 * Interrupts the running thread and schedules it to be stopped.  It is up
	 * to the subclass to implement the stop behaviour accordingly.  Failure to
	 * do so will result in a dead lock.
	 *
	 * Do not call this method from within the subclassed thread.
	 */
	public void interruptAndStop()
	{
		mStopped = true;

		interrupt();

		while (true)
		{
			try { join(); }
			catch (InterruptedException e) { }
		}
	}

	public boolean isStopped()
	{
		return mStopped;
	}
}
