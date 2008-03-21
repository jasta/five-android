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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.text.ParseException;
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
	
	private static void logFailure(String data, String path)
	{
		File f = new File(path);
		
		try
		{
			FileOutputStream out = new FileOutputStream(f);
			out.write(data.getBytes());
			out.close();
		}
		catch (Exception e)
		{
			Log.d(TAG, "Damn", e);
		}
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

		Log.i(TAG, "Inserting item (" + mime + "; " + item.getData().length() + " bytes): " + item.getSourceId());
		
		MetaDataFormat meta;

		try
		{
			meta = new MetaDataFormat(item.getData());
		}
		catch (ParseException e)
		{
			logFailure(item.getData(),
			  "/sdcard/five/" + mSourceId + "-" + item.getSourceId() + ".item");
			Log.d(TAG, "Failed to parse data=" + item.getData());

			/* Not executed. */
			return 215;
		}

		Uri uri = null;
		ContentValues values = new ContentValues();

		if (format.equals("artist") == true)
		{
			values.put(Five.Music.Artists.NAME,
			  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));
			
//			values.put(Five.Music.Artists.GENRE, meta.getValue("GENRE"));
//			values.put(Five.Music.Artists.PHOTO_ID, meta.getValue("ARTWORK"));

			uri = mContent.insert(Five.Music.Artists.CONTENT_URI, values);

			if (uri != null)
			{
				mArtistMap.put(item.getSourceId(),
				  Long.valueOf(uri.getLastPathSegment()));
				
				byte[] photoData = meta.getBytes(MetaDataFormat.ITEM_FIELD_PHOTO);
				
				if (photoData != null)
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
							out.write(photoData);
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
			values.put(Five.Music.Albums.NAME,
			  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));

			if (meta.hasValue("ARTIST") == true)
				values.put(Five.Music.Albums.ARTIST_ID, meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST));
			else if (meta.hasValue("ARTIST_GUID") == true)
				values.put(Five.Music.Albums.ARTIST_ID, mArtistMap.get(meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST_GUID)));

			uri = mContent.insert(Five.Music.Albums.CONTENT_URI, values);

			if (uri != null)
			{
				mAlbumMap.put(item.getSourceId(),
				  Long.valueOf(uri.getLastPathSegment()));
				
				byte[] artworkData = meta.getBytes(MetaDataFormat.ITEM_FIELD_ARTWORK);

				if (artworkData != null)
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
							out.write(artworkData);
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
			
			cvalues.put(Five.Content.SIZE,
			  meta.getString(MetaDataFormat.ITEM_FIELD_SIZE));
			cvalues.put(Five.Content.CONTENT_ID,
			  meta.getString(MetaDataFormat.ITEM_FIELD_CONTENT));
			cvalues.put(Five.Content.SOURCE_ID, mSourceId);

			Uri curi = mContent.insert(Five.Content.CONTENT_URI, cvalues);

			if (curi == null)
			{
				Log.e(TAG, "Failed to insert content");
				return 400;
			}

			/* And the meta data... */
			values.put(Five.Music.Songs.TITLE,
			  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));

			if (meta.hasValue("ARTIST") == true)
				values.put(Five.Music.Songs.ARTIST_ID, meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST));
			else if (meta.hasValue("ARTIST_GUID") == true)
				values.put(Five.Music.Songs.ARTIST_ID, mArtistMap.get(meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST_GUID)));

			if (meta.hasValue("ALBUM") == true)
				values.put(Five.Music.Songs.ALBUM_ID, meta.getString(MetaDataFormat.ITEM_FIELD_ALBUM));
			else if (meta.hasValue("ALBUM_GUID") == true)
				values.put(Five.Music.Songs.ALBUM_ID, mAlbumMap.get(meta.getString(MetaDataFormat.ITEM_FIELD_ALBUM_GUID)));

			values.put(Five.Music.Songs.TRACK, meta.getString(MetaDataFormat.ITEM_FIELD_TRACK));
			values.put(Five.Music.Songs.LENGTH, meta.getString(MetaDataFormat.ITEM_FIELD_LENGTH));

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
		private HashMap<Integer, byte[]> mData =
		  new HashMap<Integer, byte[]>();
		  
		private HashMap<String, byte[]> mDataCustom;
		
		public static final int ITEM_FIELD_CUSTOM = 0;
		public static final int ITEM_FIELD_NAME = 1;
		public static final int ITEM_FIELD_GENRE = 2;
		public static final int ITEM_FIELD_DISCOVERY = 3;
		public static final int ITEM_FIELD_PHOTO = 10;
		public static final int ITEM_FIELD_ARTWORK = 20;
		public static final int ITEM_FIELD_RELEASE = 21;
		public static final int ITEM_FIELD_ARTIST = 30;
		public static final int ITEM_FIELD_ARTIST_GUID = 31;
		public static final int ITEM_FIELD_CONTENT = 40;
		public static final int ITEM_FIELD_ALBUM = 41;
		public static final int ITEM_FIELD_ALBUM_GUID = 42;
		public static final int ITEM_FIELD_LENGTH = 43;
		public static final int ITEM_FIELD_TRACK = 44;
		public static final int ITEM_FIELD_SIZE = 45;

		public MetaDataFormat(String data)
		  throws ParseException
		{
			DataInputStream stream =
			  new DataInputStream(new ByteArrayInputStream(data.getBytes()));

			int pos = 0;

			byte[] value = new byte[1024];

			while (true)
			{
				try
				{
					int field;
					byte[] name = null;

					try
					{
						field = stream.readInt();
						pos += 4;
					}
					catch (EOFException e)
					{
						break;
					}

					/* XXX: This feature is not currently used by the server. */
					if (field == ITEM_FIELD_CUSTOM)
					{
						int nameLen = stream.readInt();
						name = new byte[nameLen];

						stream.readFully(name);
					}

					int valueLen = stream.readInt();

					if (valueLen > value.length)
					{
						int newLen = value.length;
						
						while (newLen < valueLen)
							newLen *= 2;
						
						value = new byte[newLen];
					}

					stream.readFully(value, 0, valueLen);

					if (field == ITEM_FIELD_CUSTOM)
						mDataCustom.put(String.valueOf(name), value);
					else
						mData.put(field, value);
				}
				catch (IOException e)
				{
					try { stream.close(); } catch (Exception ein) { }
					throw new ParseException("Insufficient data; bogus data item", pos);
				}
			}

			try { stream.close(); } catch (Exception e) { }

			if (mData.isEmpty() == true)
				throw new ParseException("No keys found in meta data", pos);
		}
		
		public boolean hasValue(Integer key)
		{
			return mData.containsKey(key);
		}
		
		public boolean hasValue(String key)
		{
			return mDataCustom.containsKey(key);
		}

		public String getString(Integer key)
		{
			return String.valueOf(mData.get(key));
		}
		
		public String getString(String key)
		{
			return String.valueOf(mDataCustom.get(key));
		}
		
		public byte[] getBytes(Integer key)
		{
			return mData.get(key);
		}
		
		public byte[] getBytes(String key)
		{
			return mDataCustom.get(key);
		}
	}
}
