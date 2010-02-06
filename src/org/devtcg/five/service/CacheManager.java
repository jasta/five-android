/*
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

import java.io.File;
import java.io.IOException;

import org.devtcg.five.provider.Five;
import org.devtcg.five.util.FileUtils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

/**
 * Mechanism for managing cached content.
 *
 * TODO: Implement this using configurable CachePolicy's.  For now it is
 * hardcoded with a policy which attempts to leave 100MB free on the storage
 * card for other applications.
 */
public class CacheManager
{
	private static final String TAG = "CacheManager";

	private static CacheManager INSTANCE;

	private Context mContext;

	/* XXX: The default policy (which is not configurable, sadly) is to
	 * attempt to leave 100MB free.  This should be made far more robust
	 * and flexible in the future. */
	private static final int POLICY_LEAVE_FREE = 100 * 1024 * 1024;

	private CacheManager(Context ctx)
	{
		mContext = ctx;
	}

	public synchronized static CacheManager getInstance(Context ctx)
	{
		if (INSTANCE == null)
			INSTANCE = new CacheManager(ctx);

		return INSTANCE;
	}

	private String getContentWhereClause(long sourceId, long contentId)
	{
		return Five.Music.Songs.SOURCE_ID + " = " + sourceId + " AND " +
			Five.Music.Songs._SYNC_ID + " = " + contentId;
	}

	private Cursor getContentCursor(long sourceId, long contentId)
	{
		String fields[] =
		  new String[] { Five.Music.Songs._ID, Five.Music.Songs.SIZE,
		    Five.Music.Songs.CACHED_PATH, Five.Music.Songs.MIME_TYPE };
		return mContext.getContentResolver()
		  .query(Five.Music.Songs.CONTENT_URI, fields,
			  getContentWhereClause(sourceId, contentId), null, null);
	}

	private int updateContentRow(long sourceId, long contentId,
	  ContentValues values)
	{
		return mContext.getContentResolver().update(Five.Music.Songs.CONTENT_URI, values,
			getContentWhereClause(sourceId, contentId), null);
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
			long necessary = POLICY_LEAVE_FREE - (freeBytes + size);

			if (necessary <= 0)
				return true;

			Log.i(TAG, "Hunting for cache entries to delete (need " + necessary + " more bytes)...");

			/* Initialize lazy so that for the common case of there
			 * being enough space we don't have to perform a query. */
			if (cr == null)
			{
				cr = mContext.getContentResolver();
				c = cr.query(Five.Music.Songs.CONTENT_URI,
				  new String[] { Five.Music.Songs._ID,
				    Five.Music.Songs.CACHED_PATH, Five.Music.Songs.SIZE },
				  Five.Music.Songs.CACHED_PATH + " IS NOT NULL", null,
				  Five.Music.Songs.CACHED_TIMESTAMP + " ASC");
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
				  ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI,
				    c.getLong(0));
				ContentValues cv = new ContentValues();
				cv.putNull(Five.Music.Songs.CACHED_TIMESTAMP);
				cv.putNull(Five.Music.Songs.CACHED_PATH);
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
		else if (mime.equals("audio/mp4a-latm") == true)
			return "m4a";
		else if (mime.equals("audio/mp4") == true)
			return "mp4";

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

		if (sdcard.exists() == false)
			throw new NoStorageCardException();

		if (deleteSufficientSpace(sdcard, size) == false)
			throw new OutOfSpaceException();

		String basePath = sdcard.getAbsolutePath() + "/five/cache/" + sourceId;
		File basePathFile = new File(basePath);

		if (basePathFile.exists() == false)
		{
			if (basePathFile.mkdirs() == false)
				throw new CacheAllocationException("Could not create cache directory: " + basePath);
		}

		return basePath + '/' + contentId + '.' +
		  getExtensionFromMimeType(mime);
	}

	/**
	 * Request storage space for a new content item.  If this entry is
	 * already committed to cache then the cached entry will be
	 * truncated.  Caller is responsible for calling either
	 * {@link #commitStorage} or {@link #releaseStorage} when finished.
	 *
	 * @throws IllegalStateException
	 *   Illegal state exception is thrown if another active CacheEntry
	 *   object has been provided but not released.
	 *
	 * @return
	 *   The path to the allocated storage.
	 */
	public String requestStorage(long sourceId, long contentId)
	  throws CacheAllocationException
	{
		Cursor c = getContentCursor(sourceId, contentId);

		try {
			if (c.moveToFirst() == false)
				throw new IllegalArgumentException("Invalid content");

			long size = c.getLong(c.getColumnIndexOrThrow(Five.Music.Songs.SIZE));
			String mime = c.getString(c.getColumnIndexOrThrow(Five.Music.Songs.MIME_TYPE));

			String path = makeStorage(sourceId, contentId, mime, size);

			ContentValues cv = new ContentValues();
			cv.put(Five.Music.Songs.CACHED_TIMESTAMP,
			  System.currentTimeMillis());
			cv.put(Five.Music.Songs.CACHED_PATH, path);
			updateContentRow(sourceId, contentId, cv);

			return path;
		} finally {
			c.close();
		}
	}

	/**
	 * Commit cached content to disk.  This indicates that the file is fully
	 * downloaded and that the cached entry should be tidied.
	 */
	public void commitStorage(long sourceId, long contentId)
	{
		/* XXX. */
	}

	/**
	 * Inform the cache manager that an entry can be purged.  This may not
	 * result in an immediate release of resources depending on the current
	 * storage policy.  Likewise, cached entries may be purged
	 * automatically by the cache manager without having been explicitly
	 * released.
	 *
	 * XXX: This method is not currently used.
	 */
	public void releaseStorage(long sourceId, long contentId)
	{
		throw new RuntimeException("Not implemented");
	}

	/**
	 * Delete all meta data. This is a special case during first-time set up to
	 * ensure that any previous installations data is removed before proceeding.
	 */
	public void wipeAll() throws IOException
	{
		File fiveStorage = new File(Environment.getExternalStorageDirectory(), "five");
		if (fiveStorage.exists())
			FileUtils.deleteDirectory(fiveStorage);
	}

	public static class CacheAllocationException extends Exception
	{
		public CacheAllocationException() { super(); }
		public CacheAllocationException(String msg) { super(msg); }
	}

	public static class OutOfSpaceException extends CacheAllocationException
	{
		public OutOfSpaceException() {
			super("Available storage card space has been exhausted");
		}
	}

	public static class NoStorageCardException extends CacheAllocationException
	{
		public NoStorageCardException() {
			super("No storage card mounted");
		}
	}
}
