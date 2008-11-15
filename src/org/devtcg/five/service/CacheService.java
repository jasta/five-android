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
import java.io.FileNotFoundException;

import org.devtcg.five.provider.Five;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.StatFs;
import android.util.Log;

public class CacheService extends Service
  implements MediaScannerConnection.MediaScannerConnectionClient
{
	public static final String TAG = "CacheService";

	/** Default cache policy is to leave 100MB free always. */
	static final CachePolicy mPolicy = new CachePolicy(100 * 1024 * 1024);
	
	private Handler mHandler = new Handler();

	private MediaScannerConnection mMediaScanner;

	@Override
	public void onCreate()
	{
		super.onCreate();

		mMediaScanner = new MediaScannerConnection(this, this);
		mMediaScanner.connect();
	}

	@Override
	public void onDestroy()
	{
		mMediaScanner.disconnect();

		super.onDestroy();
	}

	public void onMediaScannerConnected() {}

	public void onScanCompleted(String path, Uri uri)
	{
		Log.i(TAG, "onScanCompleted: path=" + path + ", uri=" + uri);
	}

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
		    Five.Content.CACHED_PATH, Five.Content.MIME_TYPE };
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

		private boolean deleteSufficientSpace(File sdcard, long size)
		{
			ContentResolver cr = null;
			Cursor c = null;

OUTER:
			while (true)
			{
				StatFs fs = new StatFs(sdcard.getAbsolutePath());

				long freeBytes = (long)fs.getAvailableBlocks() * fs.getBlockSize();
				long necessary = mPolicy.leaveFree - (freeBytes + size);

				if (necessary <= 0)
					return true;

				Log.i(TAG, "Hunting for cache entries to delete (need " + necessary + " more bytes)...");

				/* Initialize lazy so that for the common case of there
				 * being enough space we don't have to perform a query. */
				if (cr == null)
				{
					cr = getContentResolver();
					c = cr.query(Five.Content.CONTENT_URI,
					  new String[] { Five.Content._ID, 
					    Five.Content.CACHED_PATH, Five.Content.SIZE },
					  Five.Content.CACHED_PATH + " IS NOT NULL", null,
					  Five.Content.CACHED_TIMESTAMP + " ASC");
				}

				/* Inner loop here to avoid calling StatFs for each cached
				 * entry delete.  Only call it again to confirm what we
				 * gathered from the database. */
				while (necessary >= 0)
				{
					if (c.moveToNext() == false)
						break OUTER;

					File f = new File(c.getString(1));

					if (f.exists() == true)
					{
						/* The file's size might differ from the databases as we
						 * may have an uncommitted, partial cache hit. */
						long cachedSize = f.length();

						if (f.delete() == true)
							necessary -= cachedSize;
					}

					/* Eliminate this entry from the cache. */
					Uri contentUri = 
					  ContentUris.withAppendedId(Five.Content.CONTENT_URI,
					    c.getLong(0));
					ContentValues cv = new ContentValues();
					cv.putNull(Five.Content.CACHED_TIMESTAMP);
					cv.putNull(Five.Content.CACHED_PATH);
					cr.update(contentUri, cv, null, null);
				}
			}

			if (c != null)
				c.close();

			return false;
		}
		
		private String getExtensionFromMimeType(String mime)
		{
			if (mime.equals("audio/mpeg") == true)
				return "mp3";
			else if (mime.equals("application/ogg") == true)
				return "ogg";

			throw new IllegalArgumentException("Unknown mime type " + mime);
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
		  String mime, long size)
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

			if (deleteSufficientSpace(sdcard, size) == false)
				throw new OutOfSpaceException();

			String path = sdcard.getAbsolutePath() + "/five/cache/" +
			  sourceId + '/' + contentId +
			  '.' + getExtensionFromMimeType(mime);

			return path;
		}
		
		public String requestStorageAsPath(long sourceId, long contentId)
		  throws RemoteException
		{
			Cursor c = getContentCursor(sourceId, contentId);
			
			try {
				if (c.moveToFirst() == false)
					throw new IllegalArgumentException("Invalid content");
				
				long size = c.getLong(c.getColumnIndexOrThrow(Five.Content.SIZE));
				String mime = c.getString(c.getColumnIndexOrThrow(Five.Content.MIME_TYPE));

				String path = makeStorage(sourceId, contentId, mime, size);

				ContentValues cv = new ContentValues();
				cv.put(Five.Content.CACHED_TIMESTAMP,
				  System.currentTimeMillis());
				cv.put(Five.Content.CACHED_PATH, path);
				updateContentRow(sourceId, contentId, cv);
				
				/* Lame way to make sure the file and path gets created. */
				Uri uri = makeContentUri(sourceId, contentId);
				ParcelFileDescriptor pd =
				  getContentResolver().openFileDescriptor(uri, "rw");
				pd.close();

				return path;
			} catch (Exception e) {
				logError(e);
				return null;
			} finally {
				c.close();
			}			
		}

		public ParcelFileDescriptor requestStorage(long sourceId,
		  long contentId)
		  throws RemoteException
		{
			String path = requestStorageAsPath(sourceId, contentId);

			try {
				return ParcelFileDescriptor.open(new File(path),
				  ParcelFileDescriptor.MODE_READ_WRITE |
				  ParcelFileDescriptor.MODE_CREATE |
				  ParcelFileDescriptor.MODE_WORLD_READABLE);
			} catch (FileNotFoundException e) {
				logError(e);
				return null;
			}
		}

		public void commitStorage(long sourceId, long contentId)
		  throws RemoteException
		{
			Cursor c = getContentCursor(sourceId, contentId);

			try {
				if (c.moveToFirst() == false)
					throw new IllegalArgumentException("Invalid content");

				final String path = c.getString(c.getColumnIndexOrThrow
				  (Five.Content.CACHED_PATH));
				
				final String mime = c.getString(c.getColumnIndexOrThrow
				  (Five.Content.MIME_TYPE));

				if (mMediaScanner.isConnected() == true)
					mMediaScanner.scanFile(path, mime);
			} finally {
				c.close();
			}
		}

		public boolean releaseStorage(long sourceId, long contentId)
		  throws RemoteException
		{
			Cursor c = getContentCursor(sourceId, contentId);

			try {
				if (c.moveToFirst() == false)
					throw new IllegalArgumentException("Invalid content");

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
