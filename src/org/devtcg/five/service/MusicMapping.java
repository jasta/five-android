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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.SourceLog;
import org.devtcg.syncml.model.DatabaseMapping;
import org.devtcg.syncml.protocol.SyncItem;
import org.devtcg.syncml.transport.SyncHttpConnection;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.util.Log;

public class MusicMapping implements DatabaseMapping
{
	private static final String TAG = "MusicMapping";
	
	private SyncHttpConnection mConn;
	private String mBaseUrl;

	protected MetaService.SyncHandler mHandler;
	protected ContentResolver mContent;
	protected long mSourceId;

	protected int mCounter = 0;

	protected long mLastAnchor;
	protected long mNextAnchor;

	/**
	 * Temporary mapping id from server to client database identifiers.  This
	 * is necessary because certain elements refer to others by id and the
	 * server can't be bothered to synchronize its updates with our Map
	 * commands.  See: http://code.google.com/p/five/issues/detail?id=42.
	 */
	protected HashMap<String, Long> mArtistMap = new HashMap<String, Long>();
	protected HashMap<String, Long> mAlbumMap = new HashMap<String, Long>();
	protected HashMap<String, Long> mSongMap = new HashMap<String, Long>();

	/**
	 * Total number of items synchronizing from server.
	 */
	protected int mNumChanges;

	private static final String mimePrefix = "application/x-fivedb-";

	public MusicMapping(SyncHttpConnection conn, String baseUrl,
	  ContentResolver content, MetaService.SyncHandler handler,
	  long sourceId, long lastAnchor)
	{
		mConn = conn;
		mBaseUrl = baseUrl;
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
			/* TODO: Uhh, don't delete *all* content... just the stuff
			 * from this source. */
			mContent.delete(Five.Content.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Artists.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Albums.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Songs.CONTENT_URI, null, null);
			mContent.delete(Five.Music.Playlists.CONTENT_URI, null, null);
		}
	}

	public void beginSyncRemote(int numChanges)
	{
		Log.i(TAG, "Preparing to receive " + numChanges + " changes...");
		mNumChanges = numChanges;
	}
	
	private int updateAlbumCounts(Cursor c)
	{
		int songs = 0;
		
		while (c.moveToNext() == true)
		{
			long id = c.getLong(0);

			Uri uri = ContentUris
			  .withAppendedId(Five.Music.Albums.CONTENT_URI, id);

			Uri csUri = uri.buildUpon()
			  .appendEncodedPath("songs").build();

			Cursor cs = mContent.query(csUri,
			  new String[] { Five.Music.Songs._ID }, null, null, null);

			try {
				int songsCount = cs.getCount();
				songs += songsCount;

				ContentValues uv = new ContentValues();
				uv.put(Five.Music.Albums.NUM_SONGS, songsCount);
				mContent.update(uri, uv, null, null);
			} finally {
				cs.close();
			}
		}

		return songs;
	}

	/**
	 * Update computed columns for artist and album listing NUM_ALBUMS and
	 * NUM_SONGS.  This is done at the end of the sync to avoid frequent
	 * recomputation.
	 */
	private void updateCounts()
	{
		Log.i(TAG, "Updating counts...");

		Cursor c = mContent.query(Five.Music.Artists.CONTENT_URI,
		  new String[] { Five.Music.Artists._ID }, null, null, null);

		try {
			while (c.moveToNext() == true)
			{
				long id = c.getLong(0);

				Uri uri = ContentUris
				  .withAppendedId(Five.Music.Artists.CONTENT_URI, id);

				Uri caUri = uri.buildUpon()
				  .appendEncodedPath("albums").build();

				Cursor ca = mContent.query(caUri,
				  new String[] { Five.Music.Albums._ID }, null, null, null);

				try {
					int albumsCnt = ca.getCount();
					updateAlbumCounts(ca);

					Uri csUri = uri.buildUpon()
					  .appendEncodedPath("songs").build();

					Cursor cs = mContent.query(csUri,
					  new String[] { Five.Music.Songs._ID }, null, null, null);

					int songsCnt = 0;
					
					try {
						songsCnt = cs.getCount();
					} finally {
						cs.close();
					}

					ContentValues uv = new ContentValues();
					uv.put(Five.Music.Artists.NUM_ALBUMS, albumsCnt);
					uv.put(Five.Music.Artists.NUM_SONGS, songsCnt);
					mContent.update(uri, uv, null, null);
				} finally {
					ca.close();
				}
			}
		} finally {
			c.close();
		}		

		Log.i(TAG, "Done!");
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
			
			updateCounts();

			/* XXX: This does nothing... */
			mLastAnchor = getNextAnchor();
		}
		
		mArtistMap.clear();
		mAlbumMap.clear();
		mSongMap.clear();
	}

	private void bumpCounter()
	{
		mCounter++;
	}

	private void notifyChange()
	{
		mHandler.sendUpdateProgress(mSourceId, mCounter, mNumChanges);
	}

	private static String getBaseType(String mime)
	{
		int typeIndex = mime.indexOf(mimePrefix);

		if (typeIndex != 0)
			return null;

		return mime.substring(typeIndex + mimePrefix.length());		
	}

	private Uri insertArtist(SyncItem item, MetaDataFormat meta)
	{
		ContentValues values = new ContentValues();

		values.put(Five.Music.Artists.NAME,
		  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));

		if (meta.hasValue(MetaDataFormat.ITEM_FIELD_MBID) == true)
		  values.put(Five.Music.Artists.MBID, meta.getString(MetaDataFormat.ITEM_FIELD_MBID));

		Uri uri = mContent.insert(Five.Music.Artists.CONTENT_URI, values);

		if (uri == null)
			return null;

		mArtistMap.put(item.getSourceId(),
		  Long.valueOf(uri.getLastPathSegment()));

		try 
		{
			HttpResponse resp = downloadArtistPhoto(item.getSourceId());

			Uri photo = uri.buildUpon().appendPath("photo").build();

			ContentValues v = new ContentValues();
			v.put(Five.Music.Artists.PHOTO, photo.toString());

			int n = mContent.update(uri, v, null, null);

			if (n > 0)
			{
				OutputStream out = mContent.openOutputStream(photo);
				HttpEntity ent = resp.getEntity();

				if (ent != null)
					connectIO(out, ent.getContent());
			}
		}
		catch (Exception e)
		{
			Log.d(TAG, "Failed to store artist photo for " + uri + ": " + e.toString());

			ContentValues v = new ContentValues();
			v.putNull(Five.Music.Artists.PHOTO);

			mContent.update(uri, v, null, null);
		}

		return uri;
	}
	
	private Uri insertAlbum(SyncItem item, MetaDataFormat meta)
	{
		ContentValues values = new ContentValues();

		values.put(Five.Music.Albums.NAME,
		  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));

		if (meta.hasValue(MetaDataFormat.ITEM_FIELD_MBID) == true)
			values.put(Five.Music.Albums.MBID, meta.getString(MetaDataFormat.ITEM_FIELD_MBID));

		if (meta.hasValue(MetaDataFormat.ITEM_FIELD_ARTIST) == true)
			values.put(Five.Music.Albums.ARTIST_ID, meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST));
		else if (meta.hasValue(MetaDataFormat.ITEM_FIELD_ARTIST_GUID) == true)
			values.put(Five.Music.Albums.ARTIST_ID, mArtistMap.get(meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST_GUID)));

		Uri uri = mContent.insert(Five.Music.Albums.CONTENT_URI, values);

		if (uri == null)
			return null;

		mAlbumMap.put(item.getSourceId(),
		  Long.valueOf(uri.getLastPathSegment()));

		try
		{
			HttpResponse artworkData = downloadAlbumArtwork(item.getSourceId());

			Uri artwork = uri.buildUpon().appendPath("artwork").build();
			Uri artworkBig = uri.buildUpon().appendPath("artwork").appendPath("big").build();

			ContentValues v = new ContentValues();
			v.put(Five.Music.Albums.ARTWORK_BIG, artworkBig.toString());
			v.put(Five.Music.Albums.ARTWORK, artwork.toString());

			int n = mContent.update(uri, v, null, null);

			if (n > 0)
			{
				OutputStream outBig = mContent.openOutputStream(artworkBig);
				HttpEntity ent = artworkData.getEntity();
				
				if (ent != null)
					connectIO(outBig, ent.getContent());

				InputStream inBig = null;
				inBig = mContent.openInputStream(artworkBig);

				OutputStream out = null;
				out = mContent.openOutputStream(artwork);

				try
				{
					scaleBitmapHack(inBig, 64, 64, out);
				}
				finally
				{
					if (out != null)
						out.close();

					if (inBig != null)
						inBig.close();
				}
			}
		}
		catch (Exception e)
		{
			Log.d(TAG, "Failed to store album artwork for " + uri + ": " + e.toString());

			ContentValues v = new ContentValues();
			v.putNull(Five.Music.Albums.ARTWORK_BIG);
			v.putNull(Five.Music.Albums.ARTWORK);

			mContent.update(uri, v, null, null);
		}
		
		return uri;
	}
	
	private Uri insertSongContent(SyncItem item, MetaDataFormat meta)
	{
		ContentValues values = new ContentValues();
		
		values.put(Five.Content.SIZE,
		  meta.getString(MetaDataFormat.ITEM_FIELD_SIZE));
		values.put(Five.Content.CONTENT_ID,
		  meta.getString(MetaDataFormat.ITEM_FIELD_CONTENT));
		values.put(Five.Content.SOURCE_ID, mSourceId);

		Uri uri = mContent.insert(Five.Content.CONTENT_URI, values);
		
		return uri;
	}
	
	private Uri insertSong(SyncItem item, MetaDataFormat meta)
	{
		Uri curi = insertSongContent(item, meta);

		if (curi == null)
		{
			Log.e(TAG, "Failed to insert content");
			return null;
		}

		ContentValues values = new ContentValues();

		/* And the meta data... */
		values.put(Five.Music.Songs.TITLE,
		  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));

		if (meta.hasValue(MetaDataFormat.ITEM_FIELD_ARTIST) == true)
			values.put(Five.Music.Songs.ARTIST_ID, meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST));
		else if (meta.hasValue(MetaDataFormat.ITEM_FIELD_ARTIST_GUID) == true)
			values.put(Five.Music.Songs.ARTIST_ID, mArtistMap.get(meta.getString(MetaDataFormat.ITEM_FIELD_ARTIST_GUID)));

		if (meta.hasValue(MetaDataFormat.ITEM_FIELD_ALBUM) == true)
			values.put(Five.Music.Songs.ALBUM_ID, meta.getString(MetaDataFormat.ITEM_FIELD_ALBUM));
		else if (meta.hasValue(MetaDataFormat.ITEM_FIELD_ALBUM_GUID) == true)
			values.put(Five.Music.Songs.ALBUM_ID, mAlbumMap.get(meta.getString(MetaDataFormat.ITEM_FIELD_ALBUM_GUID)));

		values.put(Five.Music.Songs.TRACK, meta.getString(MetaDataFormat.ITEM_FIELD_TRACK));
		values.put(Five.Music.Songs.LENGTH, meta.getString(MetaDataFormat.ITEM_FIELD_LENGTH));

		values.put(Five.Music.Songs.CONTENT_ID, curi.getLastPathSegment());

		Uri uri = mContent.insert(Five.Music.Songs.CONTENT_URI, values);

		if (uri == null)
			Log.d(TAG, "TODO: Rollback content entry!");
		else
		{
			mSongMap.put(item.getSourceId(),
			  Long.parseLong(uri.getLastPathSegment()));
		}

		return uri;
	}
	
	private int insertPlaylistSongs(Uri uri, SyncItem item,
	  MetaDataFormat meta)
	{
		String songs = meta.getString(MetaDataFormat.ITEM_FIELD_PLAYLIST_SONGS);
		if (songs == null)
			return -1;

		Uri songUri = uri.buildUpon()
		  .appendEncodedPath("songs").build();

		int n = 0;
		ContentValues values = new ContentValues();

		String[] songIds = songs.split(",");
		for (String idfield: songIds)
		{
			String[] fields = idfield.split(":");
			if (fields.length != 2)
			{
				Log.w(TAG, "Hmm, strange field for playlist song: " + idfield);
				continue;
			}

			values.clear();
			
			if (fields[0].equals("ID") == true)
				values.put(Five.Music.PlaylistSongs.SONG_ID, fields[1]);
			else
				values.put(Five.Music.PlaylistSongs.SONG_ID, mSongMap.get(fields[1]));

			values.put(Five.Music.PlaylistSongs.POSITION, n);

			if (mContent.insert(songUri, values) != null)
				n++;
		}

		return n;
	}

	private Uri insertPlaylist(SyncItem item, MetaDataFormat meta)
	{
		ContentValues values = new ContentValues();

		values.put(Five.Music.Playlists.NAME,
		  meta.getString(MetaDataFormat.ITEM_FIELD_NAME));		
		values.put(Five.Music.Playlists.CREATED_DATE,
		  meta.getString(MetaDataFormat.ITEM_FIELD_CREATED));

		Uri uri = mContent.insert(Five.Music.Playlists.CONTENT_URI, values);
		if (uri == null)
			return null;

		if (insertPlaylistSongs(uri, item, meta) == 0)
			Log.w(TAG, "TODO: Rollback!");

		return uri;
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

		Log.i(TAG, "Inserting item (" + item.getMimeType() + "; " +
		  item.getData().length + " bytes): " + item.getSourceId());

		MetaDataFormat meta;

		try
		{
			meta = new MetaDataFormat(new String(item.getData(), "UTF-8"));
		}
		catch (Exception e)
		{
			Log.d(TAG, "Failed to parse data=" + item.getData(), e);

			/* Not executed. */
			return 215;
		}

		Uri uri = null;

		if (format.equals("artist") == true)
			uri = insertArtist(item, meta);
		else if (format.equals("album") == true)
			uri = insertAlbum(item, meta);
		else if (format.equals("song") == true)
			uri = insertSong(item, meta);
		else if (format.equals("playlist") == true)
			uri = insertPlaylist(item, meta);
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
		throw new RuntimeException("TODO: update(item) STUB!");
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
			uri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, id);
		else if (format.equals("album") == true)
			uri = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, id);
		else if (format.equals("song") == true)
		{
			uri = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, id);

			Cursor c = mContent.query(uri,
			  new String[] { Five.Music.Songs.CONTENT_ID }, null, null, null);

			if (c.moveToFirst() == false)
				uri = null;
			else
			{
				Uri curi = ContentUris.withAppendedId(Five.Content.CONTENT_URI, c.getLong(0));
				mContent.delete(curi, null, null);
			}

			c.close();
		}
		else if (format.equals("playlist") == true)
			uri = ContentUris.withAppendedId(Five.Music.Playlists.CONTENT_URI, id);
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

	public static boolean scaleBitmapHack(InputStream in, int w, int h, OutputStream out)
	{
		Bitmap src = BitmapFactory.decodeStream(in);

		Bitmap dst = Bitmap.createBitmap(w, h, src.getConfig());
		Canvas tmp = new Canvas(dst);
		tmp.drawBitmap(src, null, new Rect(0, 0, w, h),
		  new Paint(Paint.FILTER_BITMAP_FLAG));

		return dst.compress(CompressFormat.JPEG, 75, out);
	}

	public HttpResponse reuseClientOpenStream(String url)
	  throws IOException
	{
		HttpClient client = mConn.getHttpClient();
		HttpGet get = new HttpGet(url);

		HttpResponse resp = client.execute(get);
		int status = resp.getStatusLine().getStatusCode();
		
		HttpEntity ent = resp.getEntity();

		if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
		{
			ent.consumeContent();
			throw new EOFException("Unexpected HTTP status " + status);
		}

		return resp;
	}

	public HttpResponse downloadAlbumArtwork(String id)
	  throws IOException
	{
		return reuseClientOpenStream(mBaseUrl + "/meta/music/album/" + id + "/artwork/large");
	}

	public HttpResponse downloadArtistPhoto(String id)
	  throws IOException
	{
		return reuseClientOpenStream(mBaseUrl + "/meta/music/artist/" + id + "/photo/thumb");
	}
	
	public static void connectIO(OutputStream out, InputStream in)
	  throws IOException
	{
		connectIO(out, in, 4096);
	}
	
	public static void connectIO(OutputStream out, InputStream in, int blksize)
	  throws IOException
	{
		if (out == null)
			throw new IllegalArgumentException("OutputStream is null");

		if (in == null)
			throw new IllegalArgumentException("InputStream is null");

		byte[] b = new byte[blksize];
		int n;

		try 
		{
			while ((n = in.read(b)) != -1)
				out.write(b,0 , n);		
		}
		finally
		{
			IOException re = null;
			
			try {
				out.close();
			} catch (IOException e) { re = e; }
			
			in.close();

			if (re != null)
				throw re;
		}
	}
	
	static class MetaDataFormat
	{
		protected HashMap<String, String> mData =
		  new HashMap<String, String>();

		public static final String ITEM_FIELD_NAME = "N";
		public static final String ITEM_FIELD_MBID = "MBID";
		public static final String ITEM_FIELD_GENRE = "GENRE";
		public static final String ITEM_FIELD_DISCOVERY = "DISCOVERY";
		public static final String ITEM_FIELD_PHOTO = "PHOTO";
		public static final String ITEM_FIELD_ARTWORK = "ARTWORK";
		public static final String ITEM_FIELD_RELEASE = "RELEASE";
		public static final String ITEM_FIELD_ARTIST = "ARTIST";
		public static final String ITEM_FIELD_ARTIST_GUID = "ARTIST_GUID";
		public static final String ITEM_FIELD_CONTENT = "CONTENT";
		public static final String ITEM_FIELD_ALBUM = "ALBUM";
		public static final String ITEM_FIELD_ALBUM_GUID = "ALBUM_GUID";
		public static final String ITEM_FIELD_LENGTH = "LENGTH";
		public static final String ITEM_FIELD_TRACK = "TRACK";
		public static final String ITEM_FIELD_SIZE = "SIZE";
		public static final String ITEM_FIELD_CREATED = "CREATED";
		public static final String ITEM_FIELD_PLAYLIST_SONGS = "SONGS";

		public MetaDataFormat(String data)
		  throws ParseException
		{
			Scanner scanner = new Scanner(data);

			for (int pos = 0; scanner.hasNextLine() == true; pos++)
			{
				String line = scanner.nextLine();

				String keyvalue[] = line.split(":", 2);

				if (keyvalue.length < 2)
					throw new IllegalArgumentException("Parse error on line '" + line + "'");

				mData.put(keyvalue[0], keyvalue[1]);
			}
		}

		public boolean hasValue(String key)
		{
			return mData.containsKey(key);
		}

		public String getString(String key)
		{
			return mData.get(key);
		}
	}
}
