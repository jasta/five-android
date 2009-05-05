package org.devtcg.util;

public class ThreadUtilities
{
	/**
	 * Join a thread, retrying if interrupted.
	 */
	public static void joinUninterruptibly(Thread thread)
	{
		while (true)
		{
			try {
				thread.join();
				break;
			} catch (InterruptedException e) {}
		}
	}
}
