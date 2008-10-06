/*
 * $Id$
 *
 * Copyright (C) 2006 Josh Guilfoyle <jasta@devtcg.org>
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

import java.io.File;

import org.devtcg.five.provider.Five;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.StatFs;
import android.util.Log;

public class CacheService extends Service
{
	public static final String TAG = "CacheService";

	/** Default cache policy is to leave 100MB free always. */
	static final CachePolicy mPolicy = new CachePolicy(100 * 1024 * 1024);

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	private Uri makeContentUri(long sourceId, long contentId)
	{
		return Five.Sources.CONTENT_URI.buildUpon()
		  .appendPath(String.valueOf(sourceId))
		  .appendEncodedPath("content")
		  .appendPath(String.valueOf(contentId))
		  .build();
	}

	private Cursor getContentCursor(long sourceId, long contentId)
	{
		Uri uri = makeContentUri(sourceId, contentId);
		String fields[] =
		  new String[] { Five.Content._ID, Five.Content.SIZE,
		    Five.Content.CACHED_PATH };
		return getContentResolver().query(uri, fields, null, null, null);
	}
	
	private int updateContentRow(long sourceId, long contentId,
	  ContentValues values)
	{
		Uri uri = makeContentUri(sourceId, contentId);
		return getContentResolver().update(uri, values, null, null);
	}

	private final ICacheService.Stub mBinder = new ICacheService.Stub()
	{
		public CachePolicy getStoragePolicy()
		  throws RemoteException
		{
			return mPolicy;
		}

		public boolean setStoragePolicy(CachePolicy p)
		  throws RemoteException
		{
			Log.e(TAG, "setStoragePolicy not currently supported");
			return false;
		}
		
		private void logError(String err)
		{
			/* TODO: Log to a content provider or something.  Errors in this
			 * service are critical and the user must have some way to
			 * see them. */
			Log.e(TAG, err);
		}

		private void logError(Throwable e)
		{
			Log.e(TAG, "Exception", e);
		}

		private boolean deleteSufficientSpace(File sdcard)
		{
			ContentResolver cr = null;
			Cursor c = null;

OUTER:			
			while (true)
			{
				/* XXX: Currently undocumented interface, but we need the
				 * punch through to stat(). */
				StatFs fs = new StatFs(sdcard.getAbsolutePath());

				long freeBytes = fs.getAvailableBlocks() * fs.getBlockSize();
				long necessary = mPolicy.leaveFree - freeBytes;

				if (necessary <= 0)
					return true;

				/* Initialize lazy so that for the common case of there
				 * being enough space we don't have to perform a query. */
				if (cr == null)
				{
					cr = getContentResolver();
					c = cr.query(Five.Content.CONTENT_URI,
					  new String[] { Five.Content.CACHED_PATH,
					    Five.Content.SIZE },
					  Five.Content.CACHED_PATH + " IS NOT NULL", null,
					  Five.Content.CACHED_TIMESTAMP + " ASC");

					if (c.moveToFirst() == false)
						break;
				}

				/* Inner loop here to avoid calling StatFs for each cached
				 * entry delete.  Only call it again to confirm what we
				 * gathered from the database. */
				while (necessary >= 0)
				{
					if (c.moveToNext() == false)
						break OUTER;

					if (new File(c.getString(0)).delete() == true)
						necessary -= c.getLong(1);
				}
			}

			if (c != null)
				c.close();

			return false;
		}
		
		/**
		 * Attempt to carve out sufficient storage from the storage card.
		 * 
		 * @param size
		 *   Required amount of available space.
		 * 
		 * @return
		 *   Filename for storage.
		 */
		private String makeStorage(long sourceId, long contentId,
		  long size)
		  throws CacheAllocationException
		{
			String state = Environment.getExternalStorageState();
			
			if (state.equals(Environment.MEDIA_MOUNTED) == false)
				throw new NoStorageCardException();
			
			File sdcard = Environment.getExternalStorageDirectory();
			
			/* I don't fully trust that getExternalStorageState() is
			 * correct... */
			if (sdcard.exists() == false)
				throw new NoStorageCardException();

			if (deleteSufficientSpace(sdcard) == false)
				throw new OutOfSpaceException();

			String path = sdcard.getAbsolutePath() + "/five/cache/" +
			  sourceId + "/" + contentId;

			return path;
		}
		
		public ParcelFileDescriptor requestStorage(long sourceId,
		  long contentId)
		  throws RemoteException
		{
			Cursor c = getContentCursor(sourceId, contentId);
			
			if (c.getCount() == 0)
			{
				logError("Invalid content: source=" + sourceId + 
				  ", contentId=" + contentId);
				return null;
			}

			try {
				c.moveToFirst();
				long size = c.getLong(c.getColumnIndexOrThrow(Five.Content.SIZE));

				String path = makeStorage(sourceId, contentId, size);

				ContentValues cv = new ContentValues();
				cv.put(Five.Content.CACHED_TIMESTAMP,
				  System.currentTimeMillis());
				cv.put(Five.Content.CACHED_PATH, path);
				updateContentRow(sourceId, contentId, cv);

				Uri uri = makeContentUri(sourceId, contentId);
				return getContentResolver().openFileDescriptor(uri, "rw");
			} catch (Exception e) {
				logError(e);
				return null;
			} finally {
				c.close();
			}
		}

		public boolean releaseStorage(long sourceId, long contentId)
		  throws RemoteException
		{
			Cursor c = getContentCursor(sourceId, contentId);

			if (c.getCount() == 0)
			{
				logError("Invalid content: source=" + sourceId + 
				  ", contentId=" + contentId);
				return false;
			}

			try {
				c.moveToFirst();
				String path = c.getString(c.getColumnIndexOrThrow(Five.Content.CACHED_PATH));

				if (path == null)
					return false;

				if ((new File(path)).delete() == false)
					logError("Unable to delete " + path);

				ContentValues cv = new ContentValues();
				cv.putNull(Five.Content.CACHED_TIMESTAMP);
				cv.putNull(Five.Content.CACHED_PATH);
				updateContentRow(sourceId, contentId, cv);
			
				return true;
			} finally {
				c.close();
			}
		}
	};

	private static class CacheAllocationException extends Exception
	{
		public CacheAllocationException() { super(); }
		public CacheAllocationException(String msg) { super(msg); }
	}

	private static class OutOfSpaceException extends CacheAllocationException
	{
		public OutOfSpaceException() {
			super("Available storage card space has been exhausted");
		}
	}

	private static class NoStorageCardException extends CacheAllocationException
	{
		public NoStorageCardException() {
			super("No storage card mounted");
		}
	}
}
