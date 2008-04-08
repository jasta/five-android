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

package org.devtcg.five.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.Sources;
import org.devtcg.five.util.ThreadStoppable;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.util.Log;

public class ContentService extends Service
{
	private static final String TAG = "ContentService";

	private Map<String, DownloadThread> mDownloads =
	  new ConcurrentHashMap<String, DownloadThread>();

	private static final int DOWNLOAD_THREAD_DONE = 0;

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onDestroy()
	{
		Log.d(TAG, "onDestroy");

		for (DownloadThread t : mDownloads.values())
			t.interruptAndStop();

		mDownloads.clear();

		super.onDestroy();
	}

	private Handler mHandler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case DOWNLOAD_THREAD_DONE:
				DownloadThread t = mDownloads.remove(msg.obj);

				while (true)
				{
					try { t.join(); break; }
					catch (InterruptedException e) { }
				}

				if (mDownloads.isEmpty() == true)
					stopSelf();

				break;

			default:
				super.handleMessage(msg);
			}
		}
	};

	protected boolean isCacheStale(Uri cacheUri)
	{
		try {
			InputStream in = getContentResolver().openInputStream(cacheUri);
			in.close();
		} catch (Exception e) {
			Log.d(TAG, "Stale cache detected", e);
			return true;
		}

		return false;
	}

	private Uri getCacheHit(Cursor cont)
	{
		int idx = cont.getColumnIndex(Five.Content.CACHED_ID);

		if (cont.isNull(idx) == true)
			return null;

		Uri uri = ContentUris.withAppendedId(Five.Cache.CONTENT_URI, cont.getLong(idx));

		if (isCacheStale(uri) == true)
		{
			cont.updateToNull(idx);
			cont.commitUpdates();

			return null;
		}

		return uri;
	}

	private final IContentService.Stub mBinder = new IContentService.Stub()
	{
		private Cursor getContentCursor(long id)
		{
			Uri item = ContentUris.withAppendedId(Five.Content.CONTENT_URI, id);

			Cursor cursor =
			  getContentResolver().query(item,
			    new String[] { Five.Content._ID, 
				  Five.Content.CACHED_ID, Five.Content.SIZE,
			      Five.Content.SOURCE_ID, Five.Content.CONTENT_ID },
			    null, null, null);

			if (cursor.count() == 0)
			{
				cursor.close();
				return null;
			}

			return cursor;
		}

		public ContentState getContent(final long id, final IContentObserver callback)
		{
			Cursor cursor = getContentCursor(id);

			if (cursor == null)
			{
				Log.d(TAG, "Request for invalid content: " + id);
				return new ContentState(ContentState.NOT_FOUND);
			}

			cursor.first();

			long size = cursor.getLong(2);
			long sourceId = cursor.getLong(3);
			long remoteId = cursor.getLong(4);

			final Uri cacheHit = getCacheHit(cursor);

			cursor.close();

			final ContentState ret;

			if (cacheHit != null)
			{
				ret = new ContentState();

				ret.state = ContentState.IN_CACHE;
				ret.ready = size;
				ret.total = size;

				mHandler.post(new Runnable() {
					public void run()
					{
						try {
							callback.finished(id, cacheHit, ret);
						} catch (DeadObjectException e) {}
					}
				});
			}
			else
			{
				/* Ugh, lame. */
				String key = sourceId + "-" + remoteId;

				DownloadThread t = mDownloads.get(key);

				if (t == null)
				{
					t = new DownloadThread(mHandler, getContentResolver(), 
					  id, sourceId, remoteId, size, callback);
					mDownloads.put(key, t);
					t.start();
				}
				else
				{
					/* Already downloading, we'll just let this invocation
					 * know what's going on. */
					t.addObserver(callback);
				}

				ret = t.mState;
			}

			return ret;
		}

		public void stopDownload(long id)
		{
			Cursor c = getContentCursor(id);

			if (c == null)
			{
				Log.d(TAG, "No such content id: " + id);
				return;
			}

			c.first();
			
			long sourceId = c.getLong(3);
			long remoteId = c.getLong(4);

			/* Ugh, lame. */
			String key = sourceId + "-" + remoteId;

			Log.d(TAG, "stopDownload(" + key + ")");

			DownloadThread t = mDownloads.remove(key);

			if (t != null)
				t.interruptAndStop();

			if (mDownloads.isEmpty() == true)
				stopSelf();
		}
	};

	private static class DownloadThread extends ThreadStoppable
	{
		private ContentResolver mContent;
		private Handler mHandler;

		private long mLocalId;
		private long mSourceId;
		private long mContentId;

		private RemoteCallbackList<IContentObserver> mObservers =
		  new RemoteCallbackList<IContentObserver>();

		public ContentState mState;

		public DownloadThread(Handler h, ContentResolver content,
		  long id, long sourceId, long remoteId, long total, IContentObserver o)
		{
			mHandler = h;
			mContent = content;
			mLocalId = id;
			mSourceId = sourceId;
			mContentId = remoteId;
			mObservers.register(o);

			mState = new ContentState(ContentState.IN_PROCESS, 0, total);
		}

		public void addObserver(IContentObserver o)
		{
			mObservers.register(o);
		}

		public void run()
		{
			URL url = Sources.getContentURL(mContent, mSourceId, mContentId);

			try
			{
				Uri cache = downloadToCache(url);
				assert cache != null;

				commitToContentDB(cache);

				mState.state = ContentState.IN_CACHE;
				assert mState.ready == mState.total;

				broadcastFinished(cache);
			}
			catch (Exception e)
			{
				Log.d(TAG, "Error retrieving content: " + url, e);
				broadcastError(e.toString());
			}

			/* If we were interrupted we do not need to do any cleanup.  The
			 * caller will have done that for us at the time of interrupt. */
			if (isStopped() == false)
			{
				/* Let our handler know to clean up this thread. */
				Message msg = mHandler.obtainMessage(DOWNLOAD_THREAD_DONE,
				  mSourceId + "-" + mContentId);

				mHandler.sendMessage(msg);
			}

			mObservers.kill();
		}

		private void commitToContentDB(Uri cacheUri)
		{
			Uri contentUri = ContentUris.withAppendedId(Five.Content.CONTENT_URI, mLocalId);

			ContentValues v = new ContentValues();
			v.put(Five.Content.CACHED_ID, cacheUri.getLastPathSegment());

			mContent.update(contentUri, v, null, null);
		}

		private Uri downloadToCache(URL url)
		  throws Exception
		{
			/* Create the cache row so that we can open the stream. */
			ContentValues v = new ContentValues();

			v.put(Five.Cache.SOURCE_ID, mSourceId);
			v.put(Five.Cache.CONTENT_ID, mContentId);

			Uri cacheUri = mContent.insert(Five.Cache.CONTENT_URI, v);

			if (cacheUri == null)
				throw new IllegalStateException("Failed to insert cache row: download aborted");

			Log.d(TAG, "got cacheUri=" + cacheUri);
			
			OutputStream out = mContent.openOutputStream(cacheUri);
			InputStream in = url.openStream();

			byte[] b = new byte[1024];
			int n;
			
			long then = 0;
			
			/* TODO: Figure out a way to play nice. */
			mObservers.beginBroadcast();
			long interval = mObservers.getBroadcastItem(0).updateInterval();
			mObservers.finishBroadcast();
			
			while (mStopped == false && (n = in.read(b)) != -1)
			{
				mState.ready += n;

				if (mState.ready > mState.total)
					mState.total = -1;

				if (System.currentTimeMillis() >= then + interval)
				{
					broadcastUpdate(cacheUri);
					then = System.currentTimeMillis();
				}

				out.write(b, 0, n);
			}

			if (mStopped == true)
				throw new InterruptedException();

			/* TODO: Should we schedule a conflict with this item and ask it
			 * to be updated at next sync? */
			if (mState.total != mState.ready)
			{
				Log.d(TAG, "Warning, expected total does not match downloaded size (" + mState.ready + " / " + mState.total + ")");
				mState.total = mState.ready;
			}

			return cacheUri;
		}

		public void broadcastFinished(Uri cache)
		{
			int n = mObservers.beginBroadcast();

			while (n-- > 0)
			{
				try {
					mObservers.getBroadcastItem(n).finished(mLocalId, cache, mState);
				} catch (DeadObjectException e) { }
			}
			
			mObservers.finishBroadcast();
		}

		public void broadcastError(String msg)
		{
			int n = mObservers.beginBroadcast();

			while (n-- > 0)
			{
				try {
					mObservers.getBroadcastItem(n).error(mLocalId, msg);
				} catch (DeadObjectException e) { }
			}
			
			mObservers.finishBroadcast();			
		}

		public void broadcastUpdate(Uri cache)
		{
			int n = mObservers.beginBroadcast();

			while (n-- > 0)
			{
				try {
					mObservers.getBroadcastItem(n).updateProgress(mLocalId, cache, mState);
				} catch (DeadObjectException e) { }
			}
			
			mObservers.finishBroadcast();			
		}
	}
}
