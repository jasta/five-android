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

package org.devtcg.five.music.util.streaming;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.devtcg.five.music.util.streaming.StreamMediaPlayer.RandomAccessStream;

import android.util.Log;

/**
 * Simple access stream to "tail" a changing file on disk.
 */
public class TailStream extends RandomAccessStream
{
	public static final String TAG = "TailStream";

	protected String mPath;
	protected long mLength = -1;
	private FileChannel mChannel;

	private long mRemaining = 0;
	
	protected TailStream(String path)
	{
		mPath = path;
	}

	public TailStream(String path, long length)
	{
		if (length <= 0)
			throw new IllegalArgumentException("Length must be positive");

		mPath = path;
		setLength(length);
	}

	public RandomAccessStream newInstance()
	{
		return new TailStream(mPath, mLength);
	}

	protected void setLength(long length)
	{
		mLength = length;
		mRemaining = length;
	}

	@Override
	public void abort()
	{
		/* XXX: We need to call abort as well, but there is no way for
		 * us to get the Thread handle that we're running under. */
		try {
			mChannel.close();
		} catch (IOException e) {
			Log.e(TAG, "Error during abort", e);
		}
	}

	@Override
	public void open() throws IOException
	{
		mChannel = (new RandomAccessFile(mPath, "r")).getChannel();
	}

	@Override
	public void close() throws IOException
	{
		mChannel.close();
		mChannel = null;
	}

	@Override
	public void seek(long pos) throws IOException
	{
		mChannel.position(pos);
		mRemaining = mLength - pos;
	}

	@Override
	public long size()
	  throws IllegalStateException
	{
		if (mLength < 0)
			throw new IllegalStateException("Length not set after open()");

		return mLength;
	}

	@Override
	public int read() throws IOException
	{
		throw new RuntimeException("Don't invoke this method.");
	}

	private void waitForData() throws IOException
	{
		long pos = mLength - mRemaining;

//		Log.d(TAG, "Waiting for more data from " + mPath);
		long now = System.currentTimeMillis();

		Thread self = Thread.currentThread();

		while (pos >= mChannel.size() && self.isInterrupted() == false)
		{
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new EOFException("Aborted stream");
			}
		}

		long elapsed = System.currentTimeMillis() - now;
//		Log.d(TAG, "Waited " + elapsed + " milliseconds.");

		if (self.isInterrupted() == true)
			throw new EOFException("Aborted stream");
	}

	@Override
	public int read(byte[] b, int offs, int len)
	  throws IOException
	{
		if (mRemaining == 0)
			return -1;

		if (len > mRemaining)
			len = (int)mRemaining;

		ByteBuffer buf = ByteBuffer.wrap(b, offs, len);

		int n = mChannel.read(buf);
		assert mRemaining >= n;

		if (n >= 0)
		{
			mRemaining -= n;
			return n;
		}
		else
		{
			waitForData();

			/* Next read will find data... */
			return 0;
		}
	}
}

