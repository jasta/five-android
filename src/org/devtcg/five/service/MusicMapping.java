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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Scanner;

import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.SourceLog;
import org.devtcg.syncml.model.DatabaseMapping;
import org.devtcg.syncml.protocol.SyncItem;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Base64Utils;
import android.util.Log;

public class MusicMapping implements DatabaseMapping
{
	private static final String TAG = "MusicMapping";
	
	protected Handler mHandler;
	protected ContentResolver mContent;
	protected long mSourceId;
	
	protected int mCounter = 0;
	
	protected long mLastAnchor;
	protected long mNextAnchor;

	/**
	 * Temporary mapping id from server to client database identifiers.  This
	 * is necessary because certain elements refer to others by id and the server
	 * can't be bothered to synchronize its updates with our Map commands.
	 */
	protected HashMap<String, Long> mArtistMap = new HashMap<String, Long>();
	protected HashMap<String, Long> mAlbumMap = new HashMap<String, Long>();

	/**
	 * Total number of items synchronizing from server.
	 */
	protected int mNumChanges;
	
	private static final String mimePrefix = "application/x-fivedb-";
	
	public MusicMapping(ContentResolver content, Handler handler, long sourceId, long lastAnchor)
	{
		mContent = content;
		mHandler = handler;
		mSourceId = sourceId;
		mLastAnchor = lastAnchor;
		mNextAnchor = System.currentTimeMillis() / 1000;

		if (mNextAnchor <= mLastAnchor)
			throw new IllegalArgumentException("Last anchor may not meet or exceed the current time");
	}

	public String getName()
	{
		return "music";
	}

	public String getType()
	{
		return "application/x-fivedb";
	}

	public long getLastAnchor()
	{
		return mLastAnchor;
	}

	public long getNextAnchor()
	{
		return mNextAnchor;
	}

	public void setLastAnchor(long anchor)
	{
	}
	
	public void setNextAnchor(long anchor)
	{
		/* TODO: This shouldn't be part of the interface. */
	}

	public void beginSyncLocal(int code, long last, long next)
	{
		Log.i(TAG, "starting sync, code=" + code);
		
		/* Slow refresh from server: delete all our local content first. */
		if (code == 210)
		{
			mContent.delete(Five.Cache.CONTENT_URI, Five.Cache.SOURCE_ID + '=' + mSourceId, null);
			mContent.delete(Five.Content.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Artists.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Albums.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Songs.CONTENT_URI, null, null);
		}
	}

	public void beginSyncRemote(int numChanges)
	{
		Log.i(TAG, "Preparing to receive " + numChanges + " changes...");
		mNumChanges = numChanges;
	}

	public void endSync(boolean updateAnchor)
	{
		if (updateAnchor == true)
		{
			/* Successful sync, yay! */
			ContentValues v = new ContentValues();
			v.put(Five.Sources.REVISION, getNextAnchor());
			
			Uri source = ContentUris.withAppendedId(Five.Sources.CONTENT_URI, mSourceId);
			mContent.update(source, v, null, null);

			/* XXX: This does nothing... */
			mLastAnchor = getNextAnchor();
		}
		
		mArtistMap.clear();
		mAlbumMap.clear();
	}
	
	private void bumpCounter()
	{
		mCounter++;
	}
	
	private void notifyChange()
	{
		Message msg = mHandler.obtainMessage(MetaService.MSG_UPDATE_PROGRESS, mCounter, mNumChanges);
		mHandler.sendMessage(msg);
	}

	private static String getBaseType(String mime)
	{
		int typeIndex = mime.indexOf(mimePrefix);

		if (typeIndex != 0)
			return null;

		return mime.substring(typeIndex + mimePrefix.length());		
	}

	public int insert(SyncItem item)
	{
		bumpCounter();

		String mime = item.getMimeType();
		String format = getBaseType(mime);

		if (format == null)
		{
			SourceLog.insertLog(mContent, (int)mSourceId, Five.SourcesLog.TYPE_WARNING,
			  "Unknown MIME type from server: " + mime);
			return 404;
		}

		Log.i(TAG, "Inserting item (" + mime + "): " + item.getSourceId());
		MetaDataFormat meta = new MetaDataFormat(item.getData());

		Uri uri = null;
		ContentValues values = new ContentValues();

		if (format.equals("artist") == true)
		{
			values.put(Five.Music.Artists.NAME, meta.getValue("N"));			
//			values.put(Five.Music.Artists.GENRE, meta.getValue("GENRE"));
//			values.put(Five.Music.Artists.PHOTO_ID, meta.getValue("ARTWORK"));

			uri = mContent.insert(Five.Music.Artists.CONTENT_URI, values);

			if (uri != null)
			{
				mArtistMap.put(item.getSourceId(),
				  Long.valueOf(uri.getLastPathSegment()));
				
				if (meta.hasValue("PHOTO") == true)
				{
					Uri photo = uri.buildUpon().appendPath("photo").build();

					ContentValues v = new ContentValues();
					v.put(Five.Music.Artists.PHOTO, photo.toString());
					
					int n = mContent.update(uri, v, null, null);

					if (n > 0)
					{
						try
						{
							OutputStream out = mContent.openOutputStream(photo);
							out.write(meta.getBytes("PHOTO"));
							out.close();
						}
						catch (IOException e)
						{
							Log.d(TAG, "Failed to store artist photo", e);
						}
					}
					else
					{
						Log.d(TAG, "Failed to create " + photo.toString() + ", huh?");
					}
				}
			}
		}
		else if (format.equals("album") == true)
		{
			values.put(Five.Music.Albums.NAME, meta.getValue("N"));

			if (meta.hasValue("ARTIST") == true)
				values.put(Five.Music.Albums.ARTIST_ID, meta.getValue("ARTIST"));
			else if (meta.hasValue("ARTIST_GUID") == true)
				values.put(Five.Music.Albums.ARTIST_ID, mArtistMap.get(meta.getValue("ARTIST_GUID")));

			uri = mContent.insert(Five.Music.Albums.CONTENT_URI, values);

			if (uri != null)
			{
				mAlbumMap.put(item.getSourceId(),
				  Long.valueOf(uri.getLastPathSegment()));

				if (meta.hasValue("ARTWORK") == true)
				{
					Uri artwork = uri.buildUpon().appendPath("artwork").build();

					ContentValues v = new ContentValues();
					v.put(Five.Music.Albums.ARTWORK, artwork.toString());
					
					int n = mContent.update(uri, v, null, null);

					if (n > 0)
					{
						try
						{
							OutputStream out = mContent.openOutputStream(artwork);
							out.write(meta.getBytes("ARTWORK"));
							out.close();
						}
						catch (IOException e)
						{
							Log.d(TAG, "Failed to store album artwork", e);
						}
					}
					else
					{
						Log.d(TAG, "Failed to create " + artwork.toString() + ", huh?");
					}
				}
			}
		}
		else if (format.equals("song") == true)
		{
			/* Create a media entry. */
			ContentValues cvalues = new ContentValues();
			cvalues.put(Five.Content.SIZE, meta.getValue("SIZE"));
			cvalues.put(Five.Content.CONTENT_ID, meta.getValue("CONTENT"));
			cvalues.put(Five.Content.SOURCE_ID, mSourceId);

			Uri curi = mContent.insert(Five.Content.CONTENT_URI, cvalues);

			if (curi == null)
			{
				Log.e(TAG, "Failed to insert content");
				return 400;
			}

			/* And the meta data... */
			values.put(Five.Music.Songs.TITLE, meta.getValue("N"));

			if (meta.hasValue("ARTIST") == true)
				values.put(Five.Music.Songs.ARTIST_ID, meta.getValue("ARTIST"));
			else if (meta.hasValue("ARTIST_GUID") == true)
				values.put(Five.Music.Songs.ARTIST_ID, mArtistMap.get(meta.getValue("ARTIST_GUID")));

			if (meta.hasValue("ALBUM") == true)
				values.put(Five.Music.Songs.ALBUM_ID, meta.getValue("ALBUM"));
			else if (meta.hasValue("ALBUM_GUID") == true)
				values.put(Five.Music.Songs.ALBUM_ID, mAlbumMap.get(meta.getValue("ALBUM_GUID")));

			values.put(Five.Music.Songs.TRACK, meta.getValue("TRACK"));
			values.put(Five.Music.Songs.LENGTH, meta.getValue("LENGTH"));

			values.put(Five.Music.Songs.CONTENT_ID, curi.getLastPathSegment());
			values.put(Five.Music.Songs.CONTENT_SOURCE_ID, mSourceId);

			uri = mContent.insert(Five.Music.Songs.CONTENT_URI, values);

			if (uri == null)
			{
				/* TODO: Rollback the inserted content entry. */
			}
		}
		else
		{
			Log.e(TAG, "Unknown mime type: " + mime);
			return 400;
		}
		
		if (uri == null)
		{
			Log.e(TAG, "Failed to insert meta data");
			return 400;
		}
		
		item.setTargetId(Long.valueOf(uri.getLastPathSegment()));

		notifyChange();

		return 201;
	}

	public int update(SyncItem item)
	{
		return 0;
	}

	public int delete(SyncItem item)
	{
		bumpCounter();
		
		String mime = item.getMimeType();
		String format = getBaseType(mime);

		if (format == null)
		{
			SourceLog.insertLog(mContent, (int)mSourceId, Five.SourcesLog.TYPE_WARNING,
			  "Unknown MIME type from server: " + mime);
			return 211;
		}

		Uri uri;
		long id = Long.valueOf(item.getTargetId());

		if (format.equals("artist") == true)
		{
			uri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, id);
		}
		else if (format.equals("album") == true)
		{
			uri = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, id);
		}
		else if (format.equals("song") == true)
		{
			uri = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, id);
			
			Cursor c = mContent.query(uri, new String[] { Five.Music.Songs.CONTENT_ID },
			  null, null, null);

			if (c.first() == false)
				uri = null;
			else
			{
				Uri curi = ContentUris.withAppendedId(Five.Content.CONTENT_URI, c.getLong(0));
				mContent.delete(curi, null, null);
			}

			c.close();
		}
		else
		{
			SourceLog.insertLog(mContent, (int)mSourceId, Five.SourcesLog.TYPE_WARNING,
			  "Unknown MIME type from server: " + mime);
			return 211;
		}

		if (uri == null || mContent.delete(uri, null, null) == 0)
		{
			SourceLog.insertLog(mContent, (int)mSourceId, Five.SourcesLog.TYPE_WARNING,
			  "Delete request failed: no such object found of type " + mime + " with id " + id);
			return 211;
		}
		
		notifyChange();
		
		return 200;
	}
	
	private static class MetaDataFormat
	{
		private HashMap<String, String> mData =
		  new HashMap<String, String>();

		public MetaDataFormat(String data)
		{
			Scanner scanner = new Scanner(data);
			
			while (scanner.hasNextLine() == true)
			{
				String line = scanner.nextLine();
				
				String keyvalue[] = line.split(":", 2);
				
				if (keyvalue.length < 2)
					throw new IllegalArgumentException("Parse error on line '" + line + "'");
				
				String old = mData.put(keyvalue[0], keyvalue[1]);
				
				if (old != null)
					Log.d(TAG, "Encountered unusual meta data input for key '" + keyvalue[0] + "'");
			}

			if (mData.isEmpty() == true)
				throw new IllegalArgumentException("No keys found in meta data");
		}

		public boolean hasValue(String key)
		{
			return mData.containsKey(key);
		}

		public String getValue(String key)
		{
			return mData.get(key);
		}

		public byte[] getBytes(String key)
		{
			/* TODO: This format should be binary, not Base64 encoded! */
			return Base64Utils.decodeBase64(mData.get(key));
		}
	}
}
