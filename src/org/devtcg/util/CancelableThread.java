/*
 * Copyright (C) 2009 Josh Guilfoyle <jasta@devtcg.org>
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

package org.devtcg.util;

public abstract class CancelableThread extends Thread
{
	private volatile boolean mCanceled = false;

	public void requestCancel()
	{
		if (mCanceled == true)
			return;

		mCanceled = true;

		interrupt();
		onRequestCancel();
	}

	protected void onRequestCancel() {}

	public void requestCancelAndWait()
	{
		requestCancel();
		joinUninterruptibly();
	}

	public boolean hasCanceled()
	{
		return mCanceled;
	}

	public void joinUninterruptibly()
	{
		while (true)
		{
			try {
				join();
				break;
			} catch (InterruptedException e) {}
		}
	}
}
