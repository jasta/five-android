/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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

import android.util.Log;

public class Stopwatch
{
	private volatile long mStart = -1;
	private volatile long mStop = -1;

	private static final ThreadLocal<Stopwatch> sInstance = new ThreadLocal<Stopwatch>()
	{
		@Override
		protected Stopwatch initialValue()
		{
			return new Stopwatch();
		}
	};

	public Stopwatch() {}

	public static Stopwatch getInstance()
	{
		Stopwatch watch = sInstance.get();

		if (watch.isRunning())
			return new Stopwatch();

		return watch;
	}

	public void start()
	{
		mStart = System.currentTimeMillis();
		mStop = -1;
	}

	public void stop()
	{
		if (mStart < 0)
			return;

		mStop = System.currentTimeMillis();
	}

	public long elapsed()
	{
		if (mStart < 0)
			return -1;

		return System.currentTimeMillis() - mStart;
	}

	public boolean isRunning()
	{
		return mStart >= 0 && mStop == -1;
	}

	public void stopAndDebugElapsed(String tag, String message)
	{
		stop();
		Log.d(tag, message + ": " + elapsed() + " ms elapsed");
	}
}
