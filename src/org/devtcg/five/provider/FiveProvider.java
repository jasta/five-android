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

package org.devtcg.five.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentProvider;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentValues;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

public class FiveProvider extends ContentProvider
{
	private static final String TAG = "FiveProvider";

	private DatabaseHelper mHelper;
	private static final String DATABASE_NAME = "five.db";
	private static final int DATABASE_VERSION = 25;

	private static final UriMatcher URI_MATCHER;
	private static final HashMap<String, String> sourcesMap;
	private static final HashMap<String, String> artistsMap;
	private static final HashMap<String, String> albumsMap;
	private static final HashMap<String, String> songsMap;

	private static enum URIPatternIds
	{
		SOURCES, SOURCE, SOURCE_LOG,
		ARTISTS, ARTIST, ARTIST_PHOTO,
		ALBUMS, ALBUMS_BY_ARTIST, ALBUMS_WITH_ARTIST, ALBUMS_COMPLETE, ALBUM,
		  ALBUM_ARTWORK, ALBUM_ARTWORK_BIG,
		SONGS, SONGS_BY_ALBUM, SONGS_BY_ARTIST, SONG,
		PLAYLISTS, PLAYLIST, SONGS_IN_PLAYLIST, SONG_IN_PLAYLIST,
		CONTENT, CONTENT_ITEM, CONTENT_ITEM_BY_SOURCE,
		CACHE, CACHE_ITEMS_BY_SOURCE
		;

		public static URIPatternIds get(int ordinal)
		{
			return values()[ordinal];
		}
	}

	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		public DatabaseHelper(Context ctx)
		{
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.CREATE);
//			db.execSQL(Five.Sources.SQL.INSERT_DUMMY);
			db.execSQL(Five.SourcesLog.SQL.CREATE);
			execIndex(db, Five.SourcesLog.SQL.INDEX);

			db.execSQL(Five.Content.SQL.CREATE);
			execIndex(db, Five.Content.SQL.INDEX);
			db.execSQL(Five.Music.Artists.SQL.CREATE);
			db.execSQL(Five.Music.Albums.SQL.CREATE);
			execIndex(db, Five.Music.Albums.SQL.INDEX);
			db.execSQL(Five.Music.Songs.SQL.CREATE);
			execIndex(db, Five.Music.Songs.SQL.INDEX);
			db.execSQL(Five.Music.Playlists.SQL.CREATE);
			db.execSQL(Five.Music.PlaylistSongs.SQL.CREATE);
			execIndex(db, Five.Music.PlaylistSongs.SQL.INDEX);
		}

		private void execIndex(SQLiteDatabase db, String[] idx)
		{
			for (int i = 0; i < idx.length; i++)
				db.execSQL(idx[i]);
		}

		private void onDrop(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.DROP);
			db.execSQL(Five.SourcesLog.SQL.DROP);
			
			db.execSQL(Five.Content.SQL.DROP);
			db.execSQL(Five.Music.Artists.SQL.DROP);
			db.execSQL(Five.Music.Albums.SQL.DROP);
			db.execSQL(Five.Music.Songs.SQL.DROP);
			db.execSQL(Five.Music.Playlists.SQL.DROP);
			db.execSQL(Five.Music.PlaylistSongs.SQL.DROP);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			if (oldVersion == 17 && newVersion == 18)
			{
				Log.w(TAG, "Attempting to upgrade to " + newVersion);
				execIndex(db, Five.SourcesLog.SQL.INDEX);
				execIndex(db, Five.Content.SQL.INDEX);
				execIndex(db, Five.Music.Albums.SQL.INDEX);
				execIndex(db, Five.Music.Songs.SQL.INDEX);				
			}
			else
			{
				Log.w(TAG, "Version too old, wiping out database contents...");
				onDrop(db);
				onCreate(db);
			}
		}
	}

	@Override
	public boolean onCreate()
	{
		mHelper = new DatabaseHelper(getContext());
		return true;
	}

	private static String getSecondToLastPathSegment(Uri uri)
	{
		List<String> segments = uri.getPathSegments();
		int size;
		
		if ((size = segments.size()) < 2)
			throw new IllegalArgumentException("URI is not long enough to have a second-to-last path");
		
		return segments.get(size - 2);
	}

	/*-***********************************************************************/

	private static StringBuilder ensureSdCardPath(String path)
	  throws FileNotFoundException
	{
		StringBuilder b = new StringBuilder("/sdcard/five/");

		b.append(path);
		File file = new File(b.toString());
		
		if (file.exists() == true)
			return b;

		if (file.mkdirs() == false)
			throw new FileNotFoundException("Could not create cache directory: " + b.toString());

		return b;
	}

	private static int stringModeToInt(String mode)
	  throws FileNotFoundException
	{
		if (mode.equals("rw") == true)
		{
			return ParcelFileDescriptor.MODE_CREATE | 
			  ParcelFileDescriptor.MODE_READ_WRITE |
			  ParcelFileDescriptor.MODE_TRUNCATE;
		}
		else
		{
			return ParcelFileDescriptor.MODE_READ_ONLY;
		}
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
	  throws FileNotFoundException
	{
		File file;
		StringBuilder path;
		int modeint;

		SQLiteDatabase db = mHelper.getReadableDatabase();

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case CONTENT_ITEM:
		case CONTENT_ITEM_BY_SOURCE:
			String where;
			String[] args;
			
			if (type == URIPatternIds.CONTENT_ITEM_BY_SOURCE)
			{
				List<String> segments = uri.getPathSegments();
				String sourceId = segments.get(1);
				String contentId = segments.get(3);

				where = Five.Content.SOURCE_ID + " = ? AND " +
				  Five.Content.CONTENT_ID + " = ?";
				args = new String[] { sourceId, contentId };
			}
			else /* if (type == URIPatternIds.CONTENT_ITEM) */
			{
				where = Five.Content._ID + " = ?";
				args = new String[] { uri.getLastPathSegment() };
			}

			Cursor c = db.query(Five.Content.SQL.TABLE,
			  new String[] { Five.Content.SOURCE_ID, Five.Content.CACHED_PATH },
			  where, args, null, null, null);

			try {
				if (c.moveToFirst() == false)
					return null;
			
				ensureSdCardPath("cache/" + c.getLong(0) + "/");

				file = new File(c.getString(1));
				modeint = stringModeToInt(mode);

				Log.i(TAG, "Opening " + file.getAbsolutePath() + " in mode " + mode);

				return ParcelFileDescriptor.open(file, modeint);
			} finally {
				c.close();
			}

		case ALBUM_ARTWORK:
		case ALBUM_ARTWORK_BIG:
			String albumId = uri.getPathSegments().get(3);

			path = ensureSdCardPath("music/album/");

			String filename;

			if (type == URIPatternIds.ALBUM_ARTWORK)
				filename = path.append(albumId).toString();
			else
				filename = path.append(albumId).append("-big").toString();

			file = new File(filename);
			modeint = stringModeToInt(mode);

			return ParcelFileDescriptor.open(file, modeint);

		case ARTIST_PHOTO:
			String artistId = getSecondToLastPathSegment(uri);

			path = ensureSdCardPath("music/artist/");

			file = new File(path.append(artistId).toString());
			modeint = stringModeToInt(mode);

			return ParcelFileDescriptor.open(file, modeint);			

		default:
			throw new IllegalArgumentException("Unknown URL " + uri);
		}
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder)
	{
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		String groupBy = null;

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case CACHE_ITEMS_BY_SOURCE:
			qb.setTables(Five.Content.SQL.TABLE);
			qb.appendWhere(Five.Content.SOURCE_ID + "=" +
			  getSecondToLastPathSegment(uri));
			qb.appendWhere(Five.Content.CACHED_PATH + " IS NOT NULL");
			break;

		case CACHE:
			qb.setTables(Five.Content.SQL.TABLE);
			qb.appendWhere(Five.Content.CACHED_PATH + " IS NOT NULL");
			break;
			
		case CONTENT:
			qb.setTables(Five.Content.SQL.TABLE);
			break;

		case CONTENT_ITEM:
			qb.setTables(Five.Content.SQL.TABLE);			
			qb.appendWhere("_id=" + uri.getLastPathSegment());
			break;
			
		case CONTENT_ITEM_BY_SOURCE:
			List<String> segments = uri.getPathSegments();
			qb.setTables(Five.Content.SQL.TABLE);
			qb.appendWhere(Five.Content.SOURCE_ID + "=" + segments.get(1) + " AND " +
			  Five.Content.CONTENT_ID + "=" + segments.get(3));
			break;

		case SOURCES:
			qb.setTables(Five.Sources.SQL.TABLE + " s " +
			  "LEFT JOIN " + Five.SourcesLog.SQL.TABLE + " sl " +
			  "ON sl.source_id = s._id " +
			  "AND sl.type = " + Five.SourcesLog.TYPE_ERROR + " " +
			  "AND sl.timestamp > s.revision");
			qb.setProjectionMap(sourcesMap);
			groupBy = "s._id";
			break;

		case SOURCE:
			qb.setTables(Five.Sources.SQL.TABLE);
			qb.appendWhere("_id=" + uri.getLastPathSegment());
			break;
			
		case SOURCE_LOG:
			qb.setTables(Five.SourcesLog.SQL.TABLE);
			qb.appendWhere("source_id=" + uri.getPathSegments().get(1));
			break;
			
		case PLAYLISTS:
			qb.setTables(Five.Music.Playlists.SQL.TABLE);
			break;

		case PLAYLIST:
			qb.setTables(Five.Music.Playlists.SQL.TABLE);
			qb.appendWhere("_id=" + uri.getLastPathSegment());
			break;

		case SONGS_IN_PLAYLIST:
			qb.setTables(Five.Music.PlaylistSongs.SQL.TABLE + " ps " +
			  "LEFT JOIN " + Five.Music.Songs.SQL.TABLE + " s " +
			  "ON s." + Five.Music.Songs._ID + " = ps." + Five.Music.PlaylistSongs.SONG_ID);
			qb.appendWhere("ps.playlist_id=" + getSecondToLastPathSegment(uri));
			qb.setProjectionMap(songsMap);

			if (sortOrder == null)
				sortOrder = "ps.position ASC";

			break;

		case SONGS:
			qb.setTables(Five.Music.Songs.SQL.TABLE);
			break;

		case SONG:
			qb.setTables(Five.Music.Songs.SQL.TABLE);
			qb.appendWhere("_id=" + uri.getLastPathSegment());
			break;

		case SONGS_BY_ARTIST:
			qb.setTables(Five.Music.Songs.SQL.TABLE);
			qb.appendWhere("artist_id=" + getSecondToLastPathSegment(uri));
//			qb.setProjectionMap(songsMap);

			if (sortOrder == null)
				sortOrder = "title ASC";

			break;

		case SONGS_BY_ALBUM:
			qb.setTables(Five.Music.Songs.SQL.TABLE);
			qb.appendWhere("album_id=" + getSecondToLastPathSegment(uri));
//			qb.setProjectionMap(songsMap);

			if (sortOrder == null)
				sortOrder = "track_num ASC, title ASC";

			break;

		case ARTISTS:
			qb.setTables(Five.Music.Artists.SQL.TABLE);
			qb.setProjectionMap(artistsMap);
			break;

		case ARTIST:
			qb.setTables(Five.Music.Artists.SQL.TABLE);
			qb.appendWhere("_id=" + uri.getLastPathSegment());
			qb.setProjectionMap(artistsMap);
			break;

		case ALBUMS_COMPLETE:
			qb.setTables(Five.Music.Albums.SQL.TABLE + " a ");
			qb.appendWhere("num_songs > 3");
			qb.setProjectionMap(albumsMap);
			break;

		case ALBUM:
		case ALBUMS:
		case ALBUMS_BY_ARTIST:
			qb.setTables(Five.Music.Albums.SQL.TABLE + " a " +
			  "LEFT JOIN " + Five.Music.Artists.SQL.TABLE + " artists " +
			  "ON artists." + Five.Music.Artists._ID + " = a." + Five.Music.Albums.ARTIST_ID);

			if (type == URIPatternIds.ALBUM)
				qb.appendWhere("a._id=" + uri.getLastPathSegment());
			else if (type == URIPatternIds.ALBUMS_BY_ARTIST)
				qb.appendWhere("a.artist_id=" + getSecondToLastPathSegment(uri));

			qb.setProjectionMap(albumsMap);
			break;

		case ALBUMS_WITH_ARTIST:
			qb.setTables(Five.Music.Songs.SQL.TABLE + " s " + 
			  "LEFT JOIN " + Five.Music.Albums.SQL.TABLE + " a " + 
			  "ON a." + Five.Music.Albums._ID + " = s." + Five.Music.Songs.ALBUM_ID + " " +
			  "LEFT JOIN " + Five.Music.Artists.SQL.TABLE + " artists " +
			  "ON artists." + Five.Music.Artists._ID + " = a." + Five.Music.Albums.ARTIST_ID);

			qb.appendWhere("s.artist_id=" + getSecondToLastPathSegment(uri));

			Map<String, String> proj = (Map<String, String>)albumsMap.clone();
			proj.put(Five.Music.Albums.NUM_SONGS, "COUNT(*) AS " + Five.Music.Albums.NUM_SONGS);
			qb.setProjectionMap(proj);

			groupBy = "a." + Five.Music.Albums._ID;

			break;

		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		SQLiteDatabase db = mHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, null, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), uri);

		return c;
	}

	/*-***********************************************************************/

	private int updateAlbum(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Music.Albums._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Music.Albums.SQL.TABLE, v, custom, selArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}
	
	private int updateArtist(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Music.Artists._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Music.Artists.SQL.TABLE, v, custom, selArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}
	
	private int updateSource(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Sources._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Sources.SQL.TABLE, v, custom, selArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	private int updateContent(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		if (type == URIPatternIds.CONTENT_ITEM)
			custom = extendWhere(sel, Five.Content._ID + '=' + uri.getLastPathSegment());			
		else
		{
			List<String> segments = uri.getPathSegments();
			custom = extendWhere(sel,
			  Five.Content.SOURCE_ID + '=' + segments.get(1) + " AND " +
			  Five.Content.CONTENT_ID + '=' + segments.get(3));
		}

		int ret = db.update(Five.Content.SQL.TABLE, v, custom, selArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
	  String[] selectionArgs)
	{		
		SQLiteDatabase db = mHelper.getWritableDatabase();

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));
		
		switch (type)
		{
		case ALBUM:
			return updateAlbum(db, uri, type, values, selection, selectionArgs);
		case ARTIST:
			return updateArtist(db, uri, type, values, selection, selectionArgs);
		case SOURCE:
			return updateSource(db, uri, type, values, selection, selectionArgs);
		case CONTENT_ITEM:
		case CONTENT_ITEM_BY_SOURCE:
			return updateContent(db, uri, type, values, selection, selectionArgs);
		default:
			throw new IllegalArgumentException("Cannot update URI: " + uri);
		}
	}
	
	/*-***********************************************************************/

	private Uri insertSource(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Sources.HOST) == false)
			throw new IllegalArgumentException("HOST cannot be NULL");

		if (v.containsKey(Five.Sources.PORT) == false)
			throw new IllegalArgumentException("PORT cannot be NULL");

		if (v.containsKey(Five.Sources.REVISION) == false)
			v.put(Five.Sources.REVISION, 0);

		long id = db.insert(Five.Sources.SQL.TABLE, Five.Sources.HOST, v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Sources.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);

		return ret;
	}

	private Uri insertSourceLog(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		String sourceId = uri.getPathSegments().get(1);  

		if (v.containsKey(Five.SourcesLog.SOURCE_ID) == true)
			throw new IllegalArgumentException("SOURCE_ID must be provided through the URI, not the columns");
		
		v.put(Five.SourcesLog.SOURCE_ID, sourceId);

		if (v.containsKey(Five.SourcesLog.TIMESTAMP) == false)
			v.put(Five.SourcesLog.TIMESTAMP, System.currentTimeMillis() / 1000);

		long id = db.insert(Five.SourcesLog.SQL.TABLE, Five.SourcesLog.SOURCE_ID, v);

		if (id == -1)
			return null;

		Uri ret = Five.Sources.CONTENT_URI.buildUpon()
		  .appendPath(sourceId)
		  .appendPath("log")
		  .appendPath(String.valueOf(id))
		  .build();
		
		getContext().getContentResolver().notifyChange(ret, null);

		return ret;
	}
	
//	private Uri insertCache(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
//	{
//		if (v.containsKey(Five.Cache.SOURCE_ID) == false)
//			throw new IllegalArgumentException("SOURCE_ID cannot be NULL");
//
//		if (v.containsKey(Five.Cache.CONTENT_ID) == false)
//			throw new IllegalArgumentException("CONTENT_ID cannot be NULL");
//
//		Cursor c = db.query(Five.Cache.SQL.TABLE,
//		  new String[] { Five.Cache._ID },
//		  Five.Cache.SOURCE_ID + '=' + v.getAsLong(Five.Cache.SOURCE_ID) + " AND " +
//		  Five.Cache.CONTENT_ID + '=' + v.getAsLong(Five.Cache.CONTENT_ID),
//		  null, null, null, null);
//
//		int rows;
//		long id;
//
//		if ((rows = c.getCount()) == 0)
//		{
//			v.put(Five.Cache.PATH,
//			  "/sdcard/five/cache/" +
//			  v.getAsLong(Five.Cache.SOURCE_ID) + "/" + 
//			  v.getAsLong(Five.Cache.CONTENT_ID));
//
//			id = db.insert(Five.Cache.SQL.TABLE, Five.Cache.SOURCE_ID, v);
//		}
//		else
//		{
//			c.moveToFirst();
//			id = c.getLong(0);
//			c.close();
//		}
//
//		if (id == -1)
//			return null;
//
//		Uri ret = ContentUris.withAppendedId(Five.Cache.CONTENT_URI, id);
//
//		if (rows == 0)
//			getContext().getContentResolver().notifyChange(ret, null);
//
//		return ret;
//	}
	
	private Uri insertContent(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Content.SOURCE_ID) == false)
			throw new IllegalArgumentException("SOURCE_ID cannot be NULL");

		if (v.containsKey(Five.Content.CONTENT_ID) == false)
			throw new IllegalArgumentException("CONTENT_ID cannot be NULL");

		long id = db.insert(Five.Content.SQL.TABLE, Five.Content.SIZE, v);
		
		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Content.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);

		return ret;
	}
	
	private boolean adjustNameWithPrefix(ContentValues v)
	{
		String name = v.getAsString(Five.Music.Artists.NAME);

		if (name.startsWith("The ") == true)
		{
			v.put(Five.Music.Artists.NAME, name.substring(4));
			v.put(Five.Music.Artists.NAME_PREFIX, "The ");
			
			return true;
		}
		
		return false;
	}

	private Uri insertArtist(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Artists.NAME) == false)
			throw new IllegalArgumentException("NAME cannot be NULL");
		
		adjustNameWithPrefix(v);

		long id = db.insert(Five.Music.Artists.SQL.TABLE, Five.Music.Artists.NAME, v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);
		return ret;
	}

	private Uri insertAlbum(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Albums.NAME) == false)
			throw new IllegalArgumentException("NAME cannot be NULL");

		if (v.containsKey(Five.Music.Albums.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");

		adjustNameWithPrefix(v);

		long id = db.insert(Five.Music.Albums.SQL.TABLE, Five.Music.Albums.NAME, v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);
		
		long artistId = v.getAsLong(Five.Music.Albums.ARTIST_ID);
		Uri artistUri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, artistId);

		getContext().getContentResolver().notifyChange(artistUri, null);

		return ret;
	}

	private Uri insertSong(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Songs.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");

		if (v.containsKey(Five.Music.Songs.CONTENT_ID) == false)
			throw new IllegalArgumentException("CONTENT_ID cannot be NULL");

		long id = db.insert(Five.Music.Songs.SQL.TABLE, Five.Music.Songs.TITLE, v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);

		long artistId = v.getAsLong(Five.Music.Songs.ARTIST_ID);
		Uri artistUri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, artistId);

		getContext().getContentResolver().notifyChange(artistUri, null);

		if (v.containsKey(Five.Music.Songs.ALBUM_ID) == true)
		{
			long albumId = v.getAsLong(Five.Music.Songs.ALBUM_ID);
			Uri albumUri = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, albumId);

			getContext().getContentResolver().notifyChange(albumUri, null);
		}

		long contentId = v.getAsLong(Five.Music.Songs.CONTENT_ID);
		Uri Uri = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, contentId);

		getContext().getContentResolver().notifyChange(Uri, null);
		
		return ret;
	}
	
	private Uri insertPlaylist(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Playlists.NAME) == false)
			throw new IllegalArgumentException("NAME cannot be NULL");
		
		long id = db.insert(Five.Music.Playlists.SQL.TABLE,
		  Five.Music.Playlists.NAME, v);
		
		if (id == -1)
			return null;
		
		Uri playlistUri = ContentUris
		  .withAppendedId(Five.Music.Playlists.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(playlistUri, null);
		
		return playlistUri;
	}

	private Uri insertPlaylistSongs(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		/* TODO: Maybe lack of POSITION means append? */
		if (v.containsKey(Five.Music.PlaylistSongs.POSITION) == false)
			throw new IllegalArgumentException("POSITION cannot be NULL");

		if (v.containsKey(Five.Music.PlaylistSongs.SONG_ID) == false)
			throw new IllegalArgumentException("SONG_ID cannot be NULL");

		if (v.containsKey(Five.Music.PlaylistSongs.PLAYLIST_ID) == true)
			throw new IllegalArgumentException("PLAYLIST_ID must be NULL (use the proper URI for this insertion)");

		v.put(Five.Music.PlaylistSongs.PLAYLIST_ID,
		  getSecondToLastPathSegment(uri));

		/* TODO: Check that the inserted POSITION doesn't require that we
		 * reposition other songs. */
		long id = db.insert(Five.Music.PlaylistSongs.SQL.TABLE,
		  Five.Music.PlaylistSongs.PLAYLIST_ID, v);

		Uri playlistSongUri = uri.buildUpon()
		  .appendEncodedPath(v.getAsString(Five.Music.PlaylistSongs.POSITION))
		  .build();

		return playlistSongUri;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		SQLiteDatabase db = mHelper.getWritableDatabase();

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
			return insertSource(db, uri, type, values);
		case SOURCE_LOG:
			return insertSourceLog(db, uri, type, values);
//		case CACHE:
//			return insertCache(db, uri, type, values);
		case CONTENT:
			return insertContent(db, uri, type, values);
		case ARTISTS:
			return insertArtist(db, uri, type, values);
		case ALBUMS:
			return insertAlbum(db, uri, type, values);
		case SONGS:
			return insertSong(db, uri, type, values);
		case PLAYLISTS:
			return insertPlaylist(db, uri, type, values);
		case SONGS_IN_PLAYLIST:
			return insertPlaylistSongs(db, uri, type, values);
		default:
			throw new IllegalArgumentException("Cannot insert URI: " + uri);
		}
	}

	/*-***********************************************************************/

	private static String extendWhere(String old, String[] add)
	{
		StringBuilder ret = new StringBuilder();

		int length = add.length;

		if (length > 0)
		{
			ret.append("(" + add[0] + ")");

			for (int i = 1; i < length; i++)
			{
				ret.append(" AND (");
				ret.append(add[i]);
				ret.append(')');
			}
		}

		if (TextUtils.isEmpty(old) == false)
			ret.append(" AND (").append(old).append(')');

		return ret.toString();
	}

	private static String extendWhere(String old, String add)
	{
		return extendWhere(old, new String[] { add });
	}

	private int deleteSource(SQLiteDatabase db, Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = db.delete(Five.Sources.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}
	
//	private int deleteCache(Uri uri, URIPatternIds type,
//	  String selection, String[] selectionArgs)
//	{
//		String custom;
//		int count;
//		
//		switch (type)
//		{
//		case CACHE:
//			custom = selection;
//			break;
//
//		case CACHE_ITEM:
//			custom = extendWhere(selection, Five.Cache._ID + '=' + uri.getLastPathSegment());
//			break;
//
//		case CACHE_ITEMS_BY_SOURCE:
//			custom = extendWhere(selection, Five.Cache.SOURCE_ID + '=' + getSecondToLastPathSegment(uri));
//			break;
//
//		default:
//			throw new IllegalArgumentException("Cannot delete content URI: " + uri);
//		}
//		
//		SQLiteDatabase db = mHelper.getReadableDatabase();
//		count = db.delete(Five.Cache.SQL.TABLE, custom, selectionArgs);
//		getContext().getContentResolver().notifyChange(uri, null);
//
//		return count;
//	}
	
	private int deleteContent(SQLiteDatabase db, Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		String custom;
		int count;
	
		/* Make sure we delete any associated cache. */
		Cursor c = query(uri, new String[] { Five.Content.CACHED_PATH },
		  selection, selectionArgs, null);

		while (c.moveToNext() == true)
		{
			String path = c.getString(0);

			if (path != null)
				(new File(path)).delete();
		}

		c.close();

		/* Now it's safe to delete the row. */
		switch (type)
		{
		case CONTENT:
			custom = selection;
			break;

		case CONTENT_ITEM:
			custom = extendWhere(selection, Five.Content._ID + '=' + uri.getLastPathSegment());
			break;

		case CONTENT_ITEM_BY_SOURCE:
			List<String> segments = uri.getPathSegments();
			custom = extendWhere(selection,
			  Five.Content.SOURCE_ID + '=' + segments.get(1) + " AND " +
			  Five.Content.CONTENT_ID + '=' + segments.get(3));
			break;

		default:
			throw new IllegalArgumentException("Cannot delete content URI: " + uri);
		}

		count = db.delete(Five.Content.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteArtist(SQLiteDatabase db, Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		String custom;
		int count;

		switch (type)
		{
		case ARTISTS:
			custom = selection;
			break;

		case ARTIST:
			StringBuilder where = new StringBuilder();
			where.append(Five.Music.Artists._ID).append('=').append(uri.getLastPathSegment());
			
			if (TextUtils.isEmpty(selection) == false)
				where.append(" AND (").append(selection).append(')');

			custom = where.toString();			
			break;

		default:
			throw new IllegalArgumentException("Cannot delete artist URI: " + uri);
		}

		count = db.delete(Five.Music.Artists.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteAlbum(SQLiteDatabase db, Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		String custom;
		int count;
		
		switch (type)
		{
		case ALBUMS:
			custom = selection;
			break;
			
		case ALBUM:
			StringBuilder where = new StringBuilder();
			where.append(Five.Music.Albums._ID).append('=').append(uri.getLastPathSegment());
			
			if (TextUtils.isEmpty(selection) == false)
				where.append(" AND (").append(selection).append(')');
			
			custom = where.toString();
			break;
			
		default:
			throw new IllegalArgumentException("Cannot delete album URI: " + uri);
		}
		
		count = db.delete(Five.Music.Albums.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteSong(SQLiteDatabase db, Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		String custom;
		int count;
		switch (type)
		{
		case SONGS:
			custom = selection;
			break;

		case SONG:
			StringBuilder where = new StringBuilder();
			where.append(Five.Music.Songs._ID).append('=').append(uri.getLastPathSegment());
			
			if (TextUtils.isEmpty(selection) == false)
				where.append(" AND (").append(selection).append(')');
			
			custom = where.toString();	
			break;

		default:
			throw new IllegalArgumentException("Cannot delete song URI: " + uri);
		}

		count = db.delete(Five.Music.Songs.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}
	
	private int deletePlaylists(SQLiteDatabase db, Uri uri, URIPatternIds type,
	  String selection, String[] selectionArgs)
	{
		String custom;
		String[] _ids;
		int count;

		switch (type)
		{
		case PLAYLISTS:
			custom = selection;

			Cursor c = db.query(Five.Music.Playlists.SQL.TABLE,
			  new String[] { Five.Music.Playlists._ID },
			  custom, selectionArgs, null, null, null);

			try {
				_ids = new String[c.getCount()];

				for (int i = 0; c.moveToNext() == true; i++)
					_ids[i] = c.getString(0);
			} finally {
				c.close();
			}

			break;

		case PLAYLIST:
			_ids = new String[] { uri.getLastPathSegment() };			
			custom = extendWhere(selection,
			  Five.Music.Playlists._ID + '=' + _ids[0]);
			break;

		default:
			throw new IllegalArgumentException("Cannot delete playlist URI: " + uri);
		}

		for (String id: _ids)
		{
			db.delete(Five.Music.PlaylistSongs.SQL.TABLE,
			  Five.Music.PlaylistSongs.PLAYLIST_ID + "=" + id, null);
		}

		count = db.delete(Five.Music.Playlists.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;		
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		SQLiteDatabase db = mHelper.getWritableDatabase();
		
		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
			return deleteSource(db, uri, type, selection, selectionArgs);
//		case CACHE:
//		case CACHE_ITEMS_BY_SOURCE:
//			return deleteCache(uri, type, selection, selectionArgs); 
		case CONTENT:
		case CONTENT_ITEM:
		case CONTENT_ITEM_BY_SOURCE:
			return deleteContent(db, uri, type, selection, selectionArgs);
		case ARTISTS:
		case ARTIST:
			return deleteArtist(db, uri, type, selection, selectionArgs);
		case ALBUMS:
		case ALBUM:
			return deleteAlbum(db, uri, type, selection, selectionArgs);
		case SONGS:
		case SONG:
			return deleteSong(db, uri, type, selection, selectionArgs);
		case PLAYLISTS:
		case PLAYLIST:
			return deletePlaylists(db, uri, type, selection, selectionArgs);
//		case SONG_IN_PLAYLIST:
//			return deletePlaylistSong(db, uri, type, selection, selectionArgs);
		default:
			throw new IllegalArgumentException("Cannot delete URI: " + uri);
		}
	}

	/*-***********************************************************************/

	@Override
	public String getType(Uri uri)
	{
		switch (URIPatternIds.get(URI_MATCHER.match(uri)))
		{
		case CACHE:
			return Five.Content.CONTENT_TYPE;
		case SOURCES:
			return Five.Sources.CONTENT_TYPE;
		case SOURCE:
			return Five.Sources.CONTENT_ITEM_TYPE;
		case ARTISTS:
			return Five.Music.Artists.CONTENT_TYPE;
		case ARTIST:
			return Five.Music.Artists.CONTENT_ITEM_TYPE;
		case ALBUMS:
		case ALBUMS_BY_ARTIST:
			return Five.Music.Albums.CONTENT_TYPE;
		case ALBUM:
			return Five.Music.Albums.CONTENT_ITEM_TYPE;
		case SONGS:
		case SONGS_BY_ALBUM:
		case SONGS_BY_ARTIST:
			return Five.Music.Songs.CONTENT_TYPE;
		case SONG:
			return Five.Music.Songs.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}

	/*-***********************************************************************/

	static
	{
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(Five.AUTHORITY, "sources", URIPatternIds.SOURCES.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#", URIPatternIds.SOURCE.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#/log", URIPatternIds.SOURCE_LOG.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#/content/#", URIPatternIds.CONTENT_ITEM_BY_SOURCE.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "content", URIPatternIds.CONTENT.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "content/#", URIPatternIds.CONTENT_ITEM.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "cache", URIPatternIds.CACHE.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#/cache", URIPatternIds.CACHE_ITEMS_BY_SOURCE.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists", URIPatternIds.ARTISTS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#", URIPatternIds.ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/albums", URIPatternIds.ALBUMS_WITH_ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/songs", URIPatternIds.SONGS_BY_ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/photo", URIPatternIds.ARTIST_PHOTO.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums", URIPatternIds.ALBUMS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/complete", URIPatternIds.ALBUMS_COMPLETE.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/#", URIPatternIds.ALBUM.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/#/songs", URIPatternIds.SONGS_BY_ALBUM.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/#/artwork", URIPatternIds.ALBUM_ARTWORK.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/#/artwork/big", URIPatternIds.ALBUM_ARTWORK_BIG.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/songs", URIPatternIds.SONGS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/songs/#", URIPatternIds.SONG.ordinal());
		
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/playlists", URIPatternIds.PLAYLISTS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/playlists/#", URIPatternIds.PLAYLIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/playlists/#/songs", URIPatternIds.SONGS_IN_PLAYLIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/playlists/#/song/#", URIPatternIds.SONG_IN_PLAYLIST.ordinal());

		sourcesMap = new HashMap<String, String>();
		sourcesMap.put(Five.Sources._ID, "s." + Five.Sources._ID + " AS " + Five.Sources._ID);
		sourcesMap.put(Five.Sources.HOST, "s." + Five.Sources.HOST + " AS " + Five.Sources.HOST);
		sourcesMap.put(Five.Sources.NAME, "s." + Five.Sources.NAME + " AS " + Five.Sources.NAME);
		sourcesMap.put(Five.Sources.PORT, "s." + Five.Sources.PORT + " AS " + Five.Sources.PORT);
		sourcesMap.put(Five.Sources.REVISION, "s." + Five.Sources.REVISION + " AS " + Five.Sources.REVISION);
		sourcesMap.put(Five.Sources.LAST_ERROR, "sl." + Five.SourcesLog.MESSAGE + " AS " + Five.Sources.LAST_ERROR);

		artistsMap = new HashMap<String, String>();
		artistsMap.put(Five.Music.Artists.MBID, "a." + Five.Music.Artists.MBID + " AS " + Five.Music.Artists.MBID);
		artistsMap.put(Five.Music.Artists._ID, Five.Music.Artists._ID);
		artistsMap.put(Five.Music.Artists.DISCOVERY_DATE, Five.Music.Artists.DISCOVERY_DATE);
		artistsMap.put(Five.Music.Artists.GENRE, Five.Music.Artists.GENRE);
		artistsMap.put(Five.Music.Artists.NAME, Five.Music.Artists.NAME);
		artistsMap.put(Five.Music.Artists.NAME_PREFIX, Five.Music.Artists.NAME_PREFIX);
		artistsMap.put(Five.Music.Artists.FULL_NAME, "IFNULL(" + Five.Music.Artists.NAME_PREFIX + ", \"\") || " + Five.Music.Artists.NAME + " AS " + Five.Music.Artists.FULL_NAME);
		artistsMap.put(Five.Music.Artists.PHOTO, Five.Music.Artists.PHOTO);
		artistsMap.put(Five.Music.Artists.NUM_ALBUMS, Five.Music.Artists.NUM_ALBUMS);
		artistsMap.put(Five.Music.Artists.NUM_SONGS, Five.Music.Artists.NUM_SONGS);

		albumsMap = new HashMap<String, String>();
		albumsMap.put(Five.Music.Albums._ID, "a." + Five.Music.Albums._ID + " AS " + Five.Music.Albums._ID);
		albumsMap.put(Five.Music.Albums.MBID, "a." + Five.Music.Albums.MBID + " AS " + Five.Music.Albums.MBID);
		albumsMap.put(Five.Music.Albums.ARTIST_ID, "a." + Five.Music.Albums.ARTIST_ID + " AS " + Five.Music.Albums.ARTIST_ID);
		albumsMap.put(Five.Music.Albums.ARTIST, "artists." + Five.Music.Artists.NAME + " AS " + Five.Music.Albums.ARTIST);
		albumsMap.put(Five.Music.Albums.ARTWORK, "a." + Five.Music.Albums.ARTWORK + " AS " + Five.Music.Albums.ARTWORK);
		albumsMap.put(Five.Music.Albums.ARTWORK_BIG, "a." + Five.Music.Albums.ARTWORK_BIG + " AS " + Five.Music.Albums.ARTWORK_BIG);
		albumsMap.put(Five.Music.Albums.DISCOVERY_DATE, "a." + Five.Music.Albums.DISCOVERY_DATE + " AS " + Five.Music.Albums.DISCOVERY_DATE);
		albumsMap.put(Five.Music.Albums.NAME, "a." + Five.Music.Albums.NAME + " AS " + Five.Music.Albums.NAME);
		albumsMap.put(Five.Music.Albums.NAME_PREFIX, "a." + Five.Music.Albums.NAME_PREFIX + " AS " + Five.Music.Albums.NAME_PREFIX);
		albumsMap.put(Five.Music.Albums.FULL_NAME, "IFNULL(a." + Five.Music.Albums.NAME_PREFIX + ", \"\") || a." + Five.Music.Albums.NAME + " AS " + Five.Music.Albums.FULL_NAME);
		albumsMap.put(Five.Music.Albums.RELEASE_DATE, "a." + Five.Music.Albums.RELEASE_DATE + " AS " + Five.Music.Albums.RELEASE_DATE);
		albumsMap.put(Five.Music.Albums.NUM_SONGS, "a." + Five.Music.Albums.NUM_SONGS + " AS " + Five.Music.Albums.NUM_SONGS);
		
		songsMap = new HashMap<String, String>();
		songsMap.put(Five.Music.Songs._ID, "s." + Five.Music.Songs._ID + " AS " + Five.Music.Songs._ID);
		songsMap.put(Five.Music.Songs.MBID, "s." + Five.Music.Songs.MBID + " AS " + Five.Music.Songs.MBID);
		songsMap.put(Five.Music.Songs.TITLE, "s." + Five.Music.Songs.TITLE + " AS " + Five.Music.Songs.TITLE);
		songsMap.put(Five.Music.Songs.ALBUM, "s." + Five.Music.Songs.ALBUM + " AS " + Five.Music.Songs.ALBUM);
		songsMap.put(Five.Music.Songs.ALBUM_ID, "s." + Five.Music.Songs.ALBUM_ID + " AS " + Five.Music.Songs.ALBUM_ID);
		songsMap.put(Five.Music.Songs.ARTIST, "s." + Five.Music.Songs.ARTIST + " AS " + Five.Music.Songs.ARTIST);
		songsMap.put(Five.Music.Songs.ARTIST_ID, "s." + Five.Music.Songs.ARTIST_ID + " AS " + Five.Music.Songs.ARTIST_ID);
		songsMap.put(Five.Music.Songs.LENGTH, "s." + Five.Music.Songs.LENGTH + " AS " + Five.Music.Songs.LENGTH);
		songsMap.put(Five.Music.Songs.TRACK, "s." + Five.Music.Songs.TRACK + " AS " + Five.Music.Songs.TRACK);
		songsMap.put(Five.Music.Songs.SET, "s." + Five.Music.Songs.SET + " AS " + Five.Music.Songs.SET);
		songsMap.put(Five.Music.Songs.GENRE, "s." + Five.Music.Songs.GENRE + " AS " + Five.Music.Songs.GENRE);
		songsMap.put(Five.Music.Songs.DISCOVERY_DATE, "s." + Five.Music.Songs.DISCOVERY_DATE + " AS " + Five.Music.Songs.DISCOVERY_DATE);
	}
}
 
