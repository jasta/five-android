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

import java.io.IOException;

import org.devtcg.five.music.util.streaming.StreamMediaPlayer.RandomAccessStream;

import android.util.Log;

/**
 * TailStream which blocks during open until the specified download reaches
 * a stage where the content length is known.
 */
public class DownloadTailStream extends TailStream
{
	public static final String TAG = "DownloadTailStream";

	private final DownloadManager.Download mDownload;

	public DownloadTailStream(DownloadManager.Download dl)
	{
		super(dl.getDestination().getAbsolutePath());
		mDownload = dl;
	}

	@Override
	public void open() throws IOException
	{
		super.open();

		Log.i(TAG, "Waiting for connection...");			
		mDownload.waitForResponse();
		Log.i(TAG, "Got it, length=" + mDownload.getContentLength());
		setLength(mDownload.getContentLength());
	}

	public RandomAccessStream newInstance()
	{
		return new DownloadTailStream(mDownload);
	}
}

