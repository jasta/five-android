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

package org.devtcg.five.util.streaming;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import android.util.Log;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

/**
 * Extended MediaPlayer to introduce arbitrary input support.  Uses a
 * local HTTP server to provide the effect of streaming.
 * 
 * Keep in mind that the MediaPlayer imposes overhead with this hack as it
 * stores locally a read-ahead cache of the stream on internal storage.
 * 
 * Ensure that you call {@link release()} to shutdown the server socket.
 */
public class StreamMediaPlayer extends MediaPlayer
{
	public static final String TAG = "StreamMediaPlayer";

	protected StreamingHttpServer mServer;
	
	/* Flag to help us work around Android issue 959. */
	protected boolean mUsed;

	/* See our setDataSource implementation.  We need to store these
	 * here in case we have to reset the MediaPlayer to work around
	 * some lame Android bug. */
	protected OnBufferingUpdateListener mBufferingUpdateListener;
	protected OnCompletionListener mCompletionListener;
	protected OnErrorListener mErrorListener;
	protected OnPreparedListener mPreparedListener;
	protected OnSeekCompleteListener mSeekCompleteListener;

	public StreamMediaPlayer()
	{
		super();
		mUsed = false;
	}
	
	@Override
	public void setDataSource(Context context, Uri uri) 
	  throws IOException, IllegalArgumentException, SecurityException,
	  IllegalStateException
	{
		Log.d(TAG, "setDataSource(context,uri)");
		mUsed = true;
		
		try {
			super.setDataSource(context, uri);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Illegal state exception, TODO: try again!");
			throw e;
		}
	}

	@Override
	public void setDataSource(FileDescriptor fd, long offset, long length)
	  throws IOException, IllegalArgumentException, IllegalStateException
	{
		Log.d(TAG, "setDataSource(fd,offset,length)");
		mUsed = true;
		
		try {
			super.setDataSource(fd, offset, length);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Illegal state exception, TODO: try again!");
			throw e;
		}
	}

	@Override
	public void setDataSource(FileDescriptor fd)
	  throws IOException, IllegalArgumentException, IllegalStateException
	{
		Log.d(TAG, "setDataSource(fd)");
		mUsed = true;
		
		try {
			super.setDataSource(fd);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Illegal state exception, TODO: try again!");
			throw e;
		}
	}

	@Override
	public void setDataSource(String path)
	  throws IOException, IllegalArgumentException, IllegalStateException
	{
		Log.d(TAG, "setDataSource(path=" + path + ")");
		mUsed = true;

		try {
			super.setDataSource(path);
		} catch (IllegalStateException e) {
			/* See Android issue 957.  The MediaPlayer can sometimes report
			 * illegal state and then recalling this method will fix it. */
			super.reset();
			super.setOnBufferingUpdateListener(mBufferingUpdateListener);
			super.setOnCompletionListener(mCompletionListener);
			super.setOnErrorListener(mErrorListener);
			super.setOnPreparedListener(mPreparedListener);
			super.setOnSeekCompleteListener(mSeekCompleteListener);

			Log.d(TAG, "setDataSource(path=" + path + ") *AGAIN*");
			super.setDataSource(path);
		}			
	}

	public void setDataSource(RandomAccessStream in)
	  throws IllegalStateException, IllegalArgumentException, IOException
	{
		Log.d(TAG, "setDataSource(RandomAccessStream)");

		if (mServer != null)
			mServer.reset(in);
		else
		{
			mServer = new StreamingHttpServer(in);
			mServer.start();
		}

		setDataSource(mServer.makeUri());
	}
	
	private void resetInternalListeners()
	{
		mBufferingUpdateListener = null;
		mCompletionListener = null;
		mErrorListener = null;
		mPreparedListener = null;
		mSeekCompleteListener = null;		
	}

	public void reset()
	{
		Log.d(TAG, "reset");

		resetInternalListeners();

		if (mUsed == true)
			super.reset();
		else
			Log.i(TAG, "Ignored unnecessary request to reset()");

		if (mServer != null)
			mServer.reset(null);
	}

	@Override
	public void release()
	{
		Log.d(TAG, "release");

		if (mServer != null)
		{
			mServer.shutdown();
			mServer = null;
		}

		resetInternalListeners();

		if (mUsed == true)
			super.release();
		else
			Log.i(TAG, "Ignored unnecessary request to release()");
	}

	@Override
	public void setOnBufferingUpdateListener(OnBufferingUpdateListener l)
	{
		mBufferingUpdateListener = l;
		super.setOnBufferingUpdateListener(l);
	}

	@Override
	public void setOnCompletionListener(OnCompletionListener l)
	{
		mCompletionListener = l;
		super.setOnCompletionListener(l);
	}

	@Override
	public void setOnErrorListener(OnErrorListener l)
	{
		mErrorListener = l;
		super.setOnErrorListener(l);
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener l)
	{
		mPreparedListener = l;
		super.setOnPreparedListener(l);
	}

	@Override
	public void setOnSeekCompleteListener(OnSeekCompleteListener l)
	{
		mSeekCompleteListener = l;
		super.setOnSeekCompleteListener(l);
	}

	@Override
	public void pause() throws IllegalStateException
	{
		Log.d(TAG, "pause");
		super.pause();
	}

	@Override
	public void prepareAsync() throws IllegalStateException
	{
		Log.d(TAG, "prepareAsync");
		super.prepareAsync();
	}

	@Override
	public void seekTo(int msec) throws IllegalStateException
	{
		Log.d(TAG, "seekTo(" + msec + ")");
		super.seekTo(msec);
	}

	@Override
	public void start() throws IllegalStateException
	{
		Log.d(TAG, "start");
		super.start();
	}

	@Override
	public void stop() throws IllegalStateException
	{
		Log.d(TAG, "stop");		

		if (mUsed == true)
			super.stop();
		else
			Log.i(TAG, "Ignored unnecessary request to release()");			
	}

	/**
	 * Connection-based stream which provides seek and open to facilitate
	 * arbitrary media streams.
	 */
	public static abstract class RandomAccessStream extends InputStream
	{
		/**
		 * Each access stream must have a unique identifier so that the
		 * hack here can differentiate various streams as there is only one
		 * static server that will be launched.
		 * 
		 * @deprecated
		 *
		 * @return
		 *   Unique identifier for the resource described by this stream.
		 */
		public String getId() { return null; }
		
		/**
		 * Construct a new instance of the stream, opened and positioned
		 * at the 0th seek position.
		 */
		public abstract RandomAccessStream newInstance();
		
		/**
		 * Set stream position.
		 */
		public abstract void seek(long pos)
		  throws IOException;

		/**
		 * Open the stream.  Called exactly once prior to invocation of any
		 * other method besides {@link getId}.
		 */
		public abstract void open()
		  throws IOException;

		/**
		 * Abort the connection and close the stream.
		 */
		public abstract void abort();

		/**
		 * Answers the total number of bytes in the stream if deterministic;
		 * otherwise, -1.
		 */
		public abstract long size();
	}

	/**
	 * Hack to feed the MediaPlayer with an HTTP stream to simulate an
	 * arbitrary InputStream.  
	 */
	private static class StreamingHttpServer extends LocalHttpServer
	{
		protected RandomAccessStream mStream;
		
		public StreamingHttpServer()
		  throws IOException
		{
			super(0);
			setRequestHandler(mHttpHandler);
		}

		public StreamingHttpServer(RandomAccessStream stream)
		  throws IOException
		{
			this();
			mStream = stream;
		}

		public void reset(RandomAccessStream stream)
		{
			super.reset();
			mStream = stream;
		}

		@Override
		public void shutdown()
		{
			super.shutdown();
			mStream = null;
		}

		public String makeUri()
		{
			return "http://127.0.0.1:" + getPort() + "/";
		}

		private final HttpRequestHandler mHttpHandler = new HttpRequestHandler()
		{
			private void interpretRangeThenSeek(HttpRequest req,
			  RandomAccessStream stream)
			  throws IOException
			{
				Header hdr = req.getLastHeader("Range");

				if (hdr == null)
					return;

				String rangeStr = hdr.getValue();
				Pattern pattern = Pattern.compile("bytes=(\\d+)-(\\d+)");
				Matcher matcher = pattern.matcher(rangeStr);

				if (matcher.matches() == false)
				{
					Log.w(TAG, "Failed to parse range header: " + rangeStr);
					return;
				}

				long low;
				long high;

				try {
					low = Long.parseLong(matcher.group(1));
					high = Long.parseLong(matcher.group(2));
				} catch (NumberFormatException e) {
					Log.w(TAG, "Failed to parse range header: " + rangeStr);
					return;
				}

				/* We assume that high is actually just the end of the
				 * stream as it was originally defined, so we aren't going
				 * to honor it explicitly. */
				assert stream.size() == high;

				Log.i(TAG, "Serving range " + low + "-" + high);
				stream.seek(low);
			}

			public void handle(HttpRequest request, HttpResponse response,
			  HttpContext context)
			  throws HttpException, IOException
			{
				RequestLine reqLine = request.getRequestLine();
				Log.v(TAG, "reqLine=" + reqLine);

				String method = reqLine.getMethod().toUpperCase(Locale.ENGLISH);
				if (method.equals("GET") == false)
				{
					throw new MethodNotSupportedException(method +
					  " method not supported");
				}

				RandomAccessStream stream = mStream.newInstance();

				/* Side-effect: will open the stream, so we can seek if
				 * necessary. */
				RandomAccessStreamEntity ent =
				  new RandomAccessStreamEntity(stream);

				interpretRangeThenSeek(request, stream);

				response.setEntity(ent);
				response.setStatusCode(HttpStatus.SC_OK);
			}
		};

		public class RandomAccessStreamEntity
		  extends AbstractHttpEntity
		{
			private final static int BUFFER_SIZE = 2048;

			private final RandomAccessStream mStream;
			private final long mLength;
			private boolean mConsumed = false;

			public RandomAccessStreamEntity(RandomAccessStream stream)
			  throws IOException
			{
				super();

				mStream = stream;

				try {
					stream.open();
				} catch (IOException e) {
					Log.e(TAG, "Stream open failure", e);
					throw e;
				}

				mLength = stream.size();
			}

			public long getContentLength()
			{
				return mLength;
			}

			public void writeTo(OutputStream outstream)
			  throws IOException
			{
				Log.i(TAG, "writeTo...");

				try {
					byte[] b = new byte[BUFFER_SIZE];
					int n;

					while ((n = mStream.read(b)) >= 0)
						outstream.write(b, 0, n);

					mConsumed = true;
				} finally {
					Log.i(TAG, "writeTo finished...");
					mStream.close();
				}
			}

			public void consumeContent()
			  throws IOException
			{
				throw new RuntimeException("Is this used?");
			}

			public InputStream getContent()
			  throws IOException, IllegalStateException
			{
				return mStream;
			}

			public boolean isRepeatable()
			{
				return false;
			}

			public boolean isStreaming()
			{
				return !mConsumed;
			}
		}
	}
}
