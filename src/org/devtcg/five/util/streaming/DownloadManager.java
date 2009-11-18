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

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.devtcg.util.CancelableThread;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Process;
import android.util.Log;

/**
 * Abstraction to generically manage multiple simultaneous HTTP downloads.
 * Downloads using this class must have an HTTP Content-Length specified by the
 * server.
 */
public abstract class DownloadManager
{
	public static final String TAG = "DownloadManager";

	private final Map<String, Download> mDownloads =
	  Collections.synchronizedMap(new HashMap<String, Download>());

	protected final ConnectivityManager mConnMan;

	private FailfastHttpClient mClient =
	  FailfastHttpClient.newInstance(null);

	private volatile boolean mDuringShutdown = false;

	/**
	 * Number of times we will retry after unhandled errors.  Note that we
	 * consider the case of a failed local network handled (by a
	 * ConnectivityReceiver in PlaylistService), and so will not count
	 * as a retry attempt.
	 */
	private static final int NUM_RETRIES = 2;

	/**
	 * Number of seconds to wait after each retry.
	 */
	private static final int RETRY_DISTANCE[] = { 15, 30, 60 };

	/** Uninitialized state; caller should never see this. */
	public static final int STATE_UNKNOWN = 0;

	/** Connecting to remote peer. */
	public static final int STATE_CONNECTING = 7;

	/** Connected to peer, transfer will or has begun. */
	public static final int STATE_CONNECTED = 8;

	/** Download is paused due to local network failure; to be resumed
	 * when connectivity returns. */
	public static final int STATE_PAUSED_LOCAL_FAILURE = 1;

	/** Download has permanently failed due to an unexpected failure
	 * negotiating with server. */
	public static final int STATE_HTTP_ERROR = 2;

	/** Download is paused due to apparent remote network failure; to be
	 * retried up to {@link NUM_RETRIES} times. */
	public static final int STATE_PAUSED_REMOTE_FAILURE = 3;

	/** Download has permanently failed due to an unexpected failure
	 * writing output to disk. */
	public static final int STATE_FILE_ERROR = 4;

	/** Intentionally aborted. */
	public static final int STATE_ABORTED = 5;

	/** Download has permanently failed after too many unsuccessful retries. */
	public static final int STATE_TOO_MANY_RETRIES = 6;

	public DownloadManager(Context ctx)
	{
		mConnMan = (ConnectivityManager)ctx.getSystemService
		  (Context.CONNECTIVITY_SERVICE);
	}

	/**
	 * This method is provided to work around an apparent bug in HttpClient
	 * where aborted connections stay in the connection pool. Therefore,
	 * aborting downloads up to the connection pool limit will cause the
	 * HttpClient to refuse to execute new methods.
	 */
	/* package */ void refreshHttpClient()
	{
		if (mDuringShutdown == false && mClient != null)
		{
			mClient.close();
			mClient = FailfastHttpClient.newInstance(null);
		}
	}

	public void shutdown()
	{
		mDuringShutdown = true;
		stopAllDownloads();
	}

	public Download lookupDownload(String url)
	{
		return mDownloads.get(url);
	}

	public Download startDownload(String url, String path, long resumeFrom)
	  throws IOException
	{
		Download d = newDownload(url, path, resumeFrom);
		mDownloads.put(url, d);
		d.start();
		return d;
	}

	public Download startDownload(String url, String path)
	  throws IOException
	{
		Download d = newDownload(url, path);
		mDownloads.put(url, d);
		d.start();
		return d;
	}

	public void stopDownload(String url)
	{
		stopDownload(mDownloads.get(url));
	}

	public void stopDownload(Download d)
	{
		if (d != null)
		{
			/* It's important that we synchronously join this thread as an
			 * abort causes the HttpClient instance we use to shutdown
			 * and recreate.  It's important that we don't then schedule
			 * some new download on the soon-to-be-closed instance. */
			d.requestCancelAndWait();
		}
	}

	public void stopAllDownloads()
	{
		Download[] downloadsCopy;

		synchronized(mDownloads) {
			downloadsCopy =
			  mDownloads.values().toArray(new Download[mDownloads.size()]);
		}

		for (Download d: downloadsCopy)
			stopDownload(d);
	}

	public void resumeDownloads()
	{
		synchronized(mDownloads) {
			for (Download d: mDownloads.values())
			{
				if (d.isPaused() == true)
					d.retry();
			}
		}
	}

	protected Download newDownload(String url, String path, long resumeFrom)
	  throws IOException
	{
		return new Download(this, url, path, resumeFrom);
	}

	protected Download newDownload(String url, String path)
	  throws IOException
	{
		return new Download(this, url, path);
	}

	protected void removeDownload(String url)
	{
		mDownloads.remove(url);
	}

	public List<Download> getDownloadsCopy()
	{
		synchronized(mDownloads) {
			return new ArrayList<Download>(mDownloads.values());
		}
	}

	public boolean isNetworkAvailable()
	{
		NetworkInfo info = mConnMan.getActiveNetworkInfo();
		if (info == null)
			return false;

		return info.isConnected();
	}

	public abstract void onProgressUpdate(String url, int percent);
	public abstract void onStateChange(String url, int state, String message);

	/**
	 * Handle fatal download failure.  The default response is to delete
	 * the destination file.
	 */
	public void onError(String url, int state, String err)
	{
		Download dl = mDownloads.get(url);
		dl.getDestination().delete();
	}

	/**
	 * Triggered after a download was aborted.  Default response is
	 * to delete the destination file.
	 */
	public void onAborted(String url)
	{
		Download dl = mDownloads.get(url);
		dl.getDestination().delete();
	}

	public abstract void onFinished(String url);

	public static class Download extends CancelableThread
	{
		private static final int BUFFER_SIZE = 2048;

		private static final AtomicInteger mCount = new AtomicInteger(1);

		private final DownloadManager mManager;
		private final String mUrl;
		private final File mDest;

		private final FileOutputStream mOut;
		private HttpGet mMethod;

		private final Object mPauseLock = new Object();
		private volatile int mState = STATE_UNKNOWN;
		private String mStateMsg;

		/* Tracks download attempts so that we can eventually fail. */
		private int mAttempts = 0;

		private long mResumeFrom;

		private final Object mResponseLock = new Object();
		private volatile boolean mPostResponse;
		private long mBytes = 0;
		private long mLength = -1;

		private int mLastProgress = 0;

		private Download(DownloadManager mgr, String url, String path,
		  long resumeFrom)
		  throws IOException
		{
			super("Download #" + mCount.getAndIncrement() + ": " + url);

			mManager = mgr;
			mUrl = url;
			mDest = new File(path);

			mResumeFrom = resumeFrom;
			mBytes = resumeFrom;
			mOut = new FileOutputStream(path, (resumeFrom > 0));
		}

		private Download(DownloadManager mgr, String url, String path)
		  throws IOException
		{
			this(mgr, url, path, 0);
		}

		public String getUrl()
		{
			return mUrl;
		}

		public File getDestination()
		{
			return mDest;
		}

		public int getDownloadState()
		{
			return mState;
		}

		public String getStateMessage()
		{
			return mStateMsg;
		}

		@Override
		protected void onRequestCancel()
		{
			synchronized(this) {
				mState = STATE_ABORTED;
				mManager.onStateChange(mUrl, STATE_ABORTED, null);

				if (mMethod != null)
					mMethod.abort();

				/*
				 * HttpClient4 that ships with Android apparently has issues
				 * releasing connections properly when their method is
				 * prematurely aborted. To work around this issue, we recreate
				 * the HttpClient object on abort only.
				 */
				mManager.refreshHttpClient();

				/* We've changed the state away from paused so this should
				 * work just fine to break out of that loop. */
				synchronized(mPauseLock) {
					mPauseLock.notify();
				}
			}
		}

		public synchronized boolean isPaused()
		{
			if (mState == STATE_PAUSED_REMOTE_FAILURE)
				return true;

			if (mState == STATE_PAUSED_LOCAL_FAILURE)
				return true;

			return false;
		}

		public void retry()
		{
			Log.i(DownloadManager.TAG, "Forcing retry: " + mUrl);

			assert isPaused() == true;

			/* Break out of a timed wait... */
			interrupt();

			/* Break out of an indefinite wait... */
			synchronized(mPauseLock) {
				try {
					setState(STATE_UNKNOWN);
					mPauseLock.notify();
				} catch (AbortedException e) {}
			}
		}

		public long getContentLength()
		{
			return mLength;
		}

		/**
		 * Efficiently wait on the download thread to get a
		 * response from the remote peer.  Intended to be called from
		 * an external thread in order to access the response
		 * content length.
		 *
		 * Returns immediately if the response is already available.
		 */
		public void waitForResponse()
		{
			synchronized(mResponseLock) {
				while (isAlive() == true && mPostResponse == false)
				{
					try {
						mResponseLock.wait();
					} catch (InterruptedException e) {}
				}
			}
		}

		public int getProgress()
		{
			return mLastProgress;
		}

		public synchronized void setState(int state)
		  throws AbortedException
		{
			setState(state, null);
		}

		public synchronized void setState(int state, String message)
		  throws AbortedException
		{
			if (mState == STATE_ABORTED)
				throw new AbortedException();

			mState = state;
			mStateMsg = message;
			mManager.onStateChange(mUrl, state, message);
		}

		private void tryDownload()
		  throws Exception
		{
			HttpGet method = new HttpGet(mUrl);

			if (mResumeFrom > 0)
				method.addHeader("Range", "bytes=" + mResumeFrom + "-");

			setState(STATE_CONNECTING);

			synchronized(this) {
				mMethod = method;
			}

			InputStream in = null;

			/* Differentiates failure to save to disk versus failure
			 * to retrieve from the network. */
			boolean networkIO = true;

			try {
				HttpEntity ent = null;

				try {
					/* Synchronization is necessary as we need to
					 * reset the mClient instance to work around a
					 * connection release bug in HttpClient 4.x. */
					HttpClient client;
					synchronized(mManager) {
						client = mManager.mClient;
					}

					HttpResponse resp = client.execute(mMethod);

					setState(STATE_CONNECTED);

					StatusLine status = resp.getStatusLine();
					int statusCode = status.getStatusCode();

					if (mResumeFrom == 0)
					{
						if (statusCode != HttpStatus.SC_OK)
							throw new IOException("HTTP GET failed: " + status);
					}
					else
					{
						if (statusCode != HttpStatus.SC_PARTIAL_CONTENT)
							throw new IOException("HTTP GET failed: " + status);
					}

					if ((ent = resp.getEntity()) == null)
						throw new IOException("No entity?");

					if (mResumeFrom == 0)
						mLength = ent.getContentLength();
					else
					{
						String rangeHdr =
							resp.getLastHeader("Content-Range").getValue();

						Matcher matcher =
						  Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)")
							.matcher(rangeHdr);

						if (matcher.matches() == false)
							throw new IOException("Can't parse Content-Range");

						long firstBytePos = Long.parseLong(matcher.group(1));
						long lastBytePos = Long.parseLong(matcher.group(2));
						long length = Long.parseLong(matcher.group(3));

						if (lastBytePos + 1 != length)
							throw new IOException("Range request inconsistently answered");

						if (firstBytePos != mResumeFrom)
							throw new IOException("Range request inconsistently answered");

						mLength = length;
					}
				} finally {
					synchronized(mResponseLock) {
						mPostResponse = true;
						mResponseLock.notify();
					}
				}

				if (hasCanceled())
					return;

				in = ent.getContent();

				byte[] b = new byte[BUFFER_SIZE];
				int n;

				while ((n = in.read(b)) >= 0)
				{
					if (hasCanceled())
						break;

					try {
						mOut.write(b, 0, n);
					} catch (IOException e) {
						throw new LocalIOException(e);
					}

					mBytes += n;

					int progress = (int)
					  (((float)mBytes / (float)mLength) * 100f);

					if (progress > mLastProgress)
					{
						mManager.onProgressUpdate(mUrl, progress);
						mLastProgress = progress;
					}
				}

				if (mBytes < mLength)
					throw new HttpException("Server didn't send as much as it said it would.");
			} catch (HttpException e) {
				setState(STATE_HTTP_ERROR, e.toString());
				throw e;
			} catch (LocalIOException e) {
				setState(STATE_FILE_ERROR, e.toString());
				throw e;
			} catch (IOException e) {
				if (mManager.isNetworkAvailable() == false)
					setState(STATE_PAUSED_LOCAL_FAILURE, e.toString());
				else
					setState(STATE_PAUSED_REMOTE_FAILURE, e.toString());
				throw e;
			} finally {
				synchronized(this) {
					mMethod = null;
				}

				if (in != null)
					try { in.close(); } catch (IOException e) {}
			}
		}

		public void run()
		{
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

DOWNLOAD_RETRY_LOOP:
			for (;;)
			{
				boolean pauseIndefinitely = false;

				try {
					tryDownload();
					mManager.onFinished(mUrl);
					break;
				} catch (Exception e) {
					Log.d(DownloadManager.TAG,
						"Download of " + mUrl + " failed: " + e.toString());

					switch (mState)
					{
					case STATE_PAUSED_REMOTE_FAILURE:
					case STATE_HTTP_ERROR:
						/* Don't count as a retry failure unless no data was
						 * downloaded during this attempt. */
						if (mBytes > mResumeFrom)
							mAttempts = 0;
						else
						{
							if (mAttempts++ >= NUM_RETRIES)
							{
								mManager.onError(mUrl, STATE_TOO_MANY_RETRIES, e.toString());
								break DOWNLOAD_RETRY_LOOP;
							}
						}

						break;
					case STATE_PAUSED_LOCAL_FAILURE:
						mAttempts = 0;
						pauseIndefinitely = true;
						break;
					case STATE_FILE_ERROR:
						mManager.onError(mUrl, STATE_FILE_ERROR, e.toString());
						break DOWNLOAD_RETRY_LOOP;
					case STATE_ABORTED:
						mManager.onAborted(mUrl);
						break DOWNLOAD_RETRY_LOOP;
					default:
						throw new IllegalStateException("Unknown state " + mState);
					}

					mResumeFrom = mBytes;
				}

				if (pauseIndefinitely == true)
				{
					/* Wait until we are manually restarted or stopped.  Will
					 * never expire. */
					Log.i(DownloadManager.TAG,
						"Waiting indefinitely to retry failed download: " + mUrl);

					synchronized(mPauseLock) {
						try {
							while (isPaused() == true)
								mPauseLock.wait();
						} catch (InterruptedException e) {}
					}
				}
				else
				{
					/* Wait longer after each failed attempt. */
					int wait = RETRY_DISTANCE[mAttempts];
					Log.i(DownloadManager.TAG, "Waiting " + wait +
					  " seconds to retry failed download: " + mUrl);
					try {
						Thread.sleep(wait * 1000);
					} catch (InterruptedException e) {}
				}

				Log.i(DownloadManager.TAG, "Retrying download: " + mUrl);
			}

			try {
				mOut.close();
			} catch (IOException e) {
				Log.e(DownloadManager.TAG, "TODO: HANDLE ME", e);
			}

			mManager.removeDownload(mUrl);
		}

		private static class AbortedException extends Exception {}
		private static class LocalIOException extends Exception
		{
			public LocalIOException(IOException e) {
				super(e);
			}
		}
	}
}

