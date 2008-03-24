/*
 * $Id$
 *
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

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
			try { join(); break; }
			catch (InterruptedException e) { }
		}
	}

	public boolean isStopped()
	{
		return mStopped;
	}
}
