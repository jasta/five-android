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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.devtcg.five.provider.util.AlbumMerger;
import org.devtcg.five.provider.util.ArtistMerger;
import org.devtcg.five.provider.util.PlaylistMerger;
import org.devtcg.five.provider.util.PlaylistSongMerger;
import org.devtcg.five.provider.util.SongMerger;
import org.devtcg.five.provider.util.SourceItem;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

public class FiveProvider extends AbstractSyncProvider
{
	private static final String TAG = "FiveProvider";

	/**
	 * Used by the sync provider instance to determine which source we are
	 * synchronizing.
	 */
	SourceItem mSource;

	DatabaseHelper mHelper;
	private static final String DATABASE_NAME = "five.db";
	private static final int DATABASE_VERSION = 32;

	private static final UriMatcher URI_MATCHER;
	private static final HashMap<String, String> sourcesMap;
	private static final HashMap<String, String> artistsMap;
	private static final HashMap<String, String> albumsMap;
	private static final HashMap<String, String> songsMap;

	private InsertHelper mArtistInserter;
	private InsertHelper mAlbumInserter;
	private InsertHelper mSongInserter;

	private static enum URIPatternIds
	{
		SOURCES, SOURCE, SOURCE_LOG,
		ARTISTS, ARTIST, ARTIST_PHOTO,
		ALBUMS, ALBUMS_BY_ARTIST, ALBUMS_WITH_ARTIST, ALBUMS_COMPLETE, ALBUM,
		  ALBUM_ARTWORK, ALBUM_ARTWORK_BIG,
		SONGS, SONGS_BY_ALBUM, SONGS_BY_ARTIST, SONGS_BY_ARTIST_ON_ALBUM, SONG,
		PLAYLISTS, PLAYLIST, SONGS_IN_PLAYLIST, SONG_IN_PLAYLIST,
		  PLAYLIST_SONGS,
		CACHE, CACHE_ITEMS_BY_SOURCE,
		ADJUST_COUNTS,
		;

		public static URIPatternIds get(int ordinal)
		{
			return values()[ordinal];
		}
	}

	private class DatabaseHelper extends SQLiteOpenHelper
	{
		public DatabaseHelper(Context ctx, String databaseName)
		{
			super(ctx, databaseName, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.CREATE);
//			db.execSQL(Five.Sources.SQL.INSERT_DUMMY);
			db.execSQL(Five.SourcesLog.SQL.CREATE);
			execIndex(db, Five.SourcesLog.SQL.INDEX);

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
				execIndex(db, Five.Music.Artists.SQL.INDEX);
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

		@Override
		public void onOpen(SQLiteDatabase db)
		{
			mArtistInserter = new InsertHelper(db, Five.Music.Artists.SQL.TABLE);
			mAlbumInserter = new InsertHelper(db, Five.Music.Albums.SQL.TABLE);
			mSongInserter = new InsertHelper(db, Five.Music.Songs.SQL.TABLE);
		}
	}

	private static final AbstractSyncProvider.Creator<FiveProvider> CREATOR =
		new AbstractSyncProvider.Creator<FiveProvider>()
	{
		@Override
		public FiveProvider newInstance()
		{
			return new FiveProvider();
		}
	};

	public static FiveProvider getSyncInstance(Context context, SourceItem source)
	{
		String dbName = "_sync-" + source.getId();
		File path = context.getDatabasePath(dbName);
		FiveProvider provider = CREATOR.getSyncInstance(path);
		provider.mSource = source;
		provider.mHelper = provider.new DatabaseHelper(context, dbName);
		return provider;
	}

	@Override
	public boolean onCreate()
	{
		mHelper = new DatabaseHelper(getContext(), DATABASE_NAME);
		return true;
	}

	@Override
	public void close()
	{
		mHelper.close();
	}

	@Override
	protected Iterable<? extends AbstractTableMerger> getMergers()
	{
		ArrayList<AbstractTableMerger> list = new ArrayList<AbstractTableMerger>(3);
		list.add(new ArtistMerger());
		list.add(new AlbumMerger());
		list.add(new SongMerger());
		list.add(new PlaylistMerger());
		list.add(new PlaylistSongMerger());
		return list;
	}

	private static String getSecondToLastPathSegment(Uri uri)
	{
		List<String> segments = uri.getPathSegments();
		int size;

		if ((size = segments.size()) < 2)
			throw new IllegalArgumentException("URI is not long enough to have a second-to-last path");

		return segments.get(size - 2);
	}

	private static List<Long> getNumericPathSegments(Uri uri)
	{
		List<String> segments = uri.getPathSegments();
		ArrayList<Long> numeric = new ArrayList<Long>(3);

		for (String segment: segments)
		{
			try {
				numeric.add(Long.parseLong(segment));
			} catch (NumberFormatException e) {}
		}

		return numeric;
	}

	private static void checkWritePermission()
	{
		if (Binder.getCallingPid() != Process.myPid())
			throw new SecurityException("Write access is not permitted.");
	}

	/*-***********************************************************************/

	private static File getSdCardPath(String path)
	{
		return new File("/sdcard/five/" + path);
	}

	private static void ensureSdCardPath(File file)
	  throws FileNotFoundException
	{
		if (file.exists() == true)
			return;

		if (file.mkdirs() == false)
		{
			throw new FileNotFoundException("Could not create cache directory: " +
				file.getAbsolutePath());
		}
	}

    private static int stringModeToInt(Uri uri, String mode)
	  throws FileNotFoundException
	{
		int modeBits;
		if ("r".equals(mode))
		{
			modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
		}
		else if ("w".equals(mode) || "wt".equals(mode))
		{
			modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
					| ParcelFileDescriptor.MODE_CREATE
					| ParcelFileDescriptor.MODE_TRUNCATE;
		}
		else if ("wa".equals(mode))
		{
			modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
					| ParcelFileDescriptor.MODE_CREATE
					| ParcelFileDescriptor.MODE_APPEND;
		}
		else if ("rw".equals(mode))
		{
			modeBits = ParcelFileDescriptor.MODE_READ_WRITE
					| ParcelFileDescriptor.MODE_CREATE;
		}
		else if ("rwt".equals(mode))
		{
			modeBits = ParcelFileDescriptor.MODE_READ_WRITE
					| ParcelFileDescriptor.MODE_CREATE
					| ParcelFileDescriptor.MODE_TRUNCATE;
		}
		else
		{
			throw new FileNotFoundException("Bad mode for " + uri + ": " + mode);
		}

		return modeBits;
	}

	public static File getArtistPhoto(long id, boolean temporary) throws FileNotFoundException
	{
		File path = getSdCardPath("music/artist/");

		if (path.exists() == false)
			ensureSdCardPath(path);

		if (temporary == true)
			return new File(path, id + ".tmp");
		else
			return new File(path, String.valueOf(id));
	}

	public static File getAlbumArtwork(long id, boolean temporary) throws FileNotFoundException
	{
		return getAlbumArtwork(id, URIPatternIds.ALBUM_ARTWORK, temporary);
	}

	public static File getLargeAlbumArtwork(long id, boolean temporary) throws FileNotFoundException
	{
		return getAlbumArtwork(id, URIPatternIds.ALBUM_ARTWORK_BIG, temporary);
	}

	private static File getAlbumArtwork(long id, URIPatternIds type, boolean temporary)
		throws FileNotFoundException
	{
		File path = getSdCardPath("music/album/");

		if (path.exists() == false)
			ensureSdCardPath(path);

		String filename;

		if (type == URIPatternIds.ALBUM_ARTWORK)
			filename = String.valueOf(id);
		else
			filename = id + "-big";

		if (temporary == true)
			return new File(path, filename + ".tmp");
		else
			return new File(path, filename);
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
	  throws FileNotFoundException
	{
		File file;

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case ALBUM_ARTWORK:
		case ALBUM_ARTWORK_BIG:
			String albumId = uri.getPathSegments().get(3);
			file = getAlbumArtwork(Long.parseLong(albumId), type, isTemporary());
			return ParcelFileDescriptor.open(file, stringModeToInt(uri, mode));

		case ARTIST_PHOTO:
			String artistId = getSecondToLastPathSegment(uri);
			file = getArtistPhoto(Long.parseLong(artistId), isTemporary());
			return ParcelFileDescriptor.open(file, stringModeToInt(uri, mode));

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

		case PLAYLIST_SONGS:
			qb.setTables(Five.Music.PlaylistSongs.SQL.TABLE);
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
		case SONGS_BY_ARTIST_ON_ALBUM:
			qb.setTables(Five.Music.Songs.SQL.TABLE);

			if (type == URIPatternIds.SONGS_BY_ALBUM)
				qb.appendWhere("album_id=" + getSecondToLastPathSegment(uri));
			else /* if (type == URIPatternIds.SONGS_BY_ARTIST_ON_ALBUM) */
			{
				List<Long> segs = getNumericPathSegments(uri);
				qb.appendWhere("artist_id=" + segs.get(0) +
				  " AND album_id=" + segs.get(1));
			}

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

		case ALBUM:
		case ALBUMS:
		case ALBUMS_BY_ARTIST:
		case ALBUMS_COMPLETE:
			qb.setTables(Five.Music.Albums.SQL.TABLE + " a " +
			  "LEFT JOIN " + Five.Music.Artists.SQL.TABLE + " artists " +
			  "ON artists." + Five.Music.Artists._ID + " = a." + Five.Music.Albums.ARTIST_ID);

			if (type == URIPatternIds.ALBUM)
				qb.appendWhere("a._id=" + uri.getLastPathSegment());
			else if (type == URIPatternIds.ALBUMS_BY_ARTIST)
				qb.appendWhere("a.artist_id=" + getSecondToLastPathSegment(uri));
			else if (type == URIPatternIds.ALBUMS_COMPLETE)
				qb.appendWhere("a.num_songs > 3");

			qb.setProjectionMap(albumsMap);
			break;

		case ALBUMS_WITH_ARTIST:
			qb.setTables(Five.Music.Songs.SQL.TABLE + " s " +
			  "LEFT JOIN " + Five.Music.Albums.SQL.TABLE + " a " +
			  "ON a." + Five.Music.Albums._ID + " = s." + Five.Music.Songs.ALBUM_ID + " " +
			  "LEFT JOIN " + Five.Music.Artists.SQL.TABLE + " artists " +
			  "ON artists." + Five.Music.Artists._ID + " = a." + Five.Music.Albums.ARTIST_ID);

			qb.appendWhere("s.artist_id=" + getSecondToLastPathSegment(uri));

			HashMap<String, String> proj = new HashMap<String, String>(albumsMap);
			proj.put(Five.Music.Albums.NUM_SONGS, "COUNT(*) AS " + Five.Music.Albums.NUM_SONGS);
			qb.setProjectionMap(proj);

			groupBy = "a." + Five.Music.Albums._ID;

			break;

		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		SQLiteDatabase db = mHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, null, sortOrder);
		if (isTemporary() == false)
			c.setNotificationUri(getContext().getContentResolver(), uri);

		return c;
	}

	/*-***********************************************************************/

	private int updateSong(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		switch (type)
		{
			case SONG:
				custom = extendWhere(sel, Five.Music.Songs._ID + '=' + uri.getLastPathSegment());
				break;

			case SONGS:
				custom = sel;
				break;

			default:
				throw new IllegalArgumentException();
		}

		int ret = db.update(Five.Music.Songs.SQL.TABLE, v, custom, selArgs);
//		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	private int updateAlbum(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Music.Albums._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Music.Albums.SQL.TABLE, v, custom, selArgs);
//		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	private int updateArtist(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Music.Artists._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Music.Artists.SQL.TABLE, v, custom, selArgs);
//		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	private int updatePlaylist(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Music.Playlists._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Music.Playlists.SQL.TABLE, v, custom, selArgs);
//		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	private int updateSource(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String custom;

		custom = extendWhere(sel, Five.Sources._ID + '=' + uri.getLastPathSegment());

		int ret = db.update(Five.Sources.SQL.TABLE, v, custom, selArgs);

		if (isTemporary() == false)
			getContext().getContentResolver().notifyChange(Five.Sources.CONTENT_URI, null);
//		getContext().getContentResolver().notifyChange(uri, null);

		return ret;
	}

	private void updateCount(SQLiteDatabase db, String updateSQL,
	  String countsSQL)
	{
		db.beginTransaction();

		SQLiteStatement updateStmt = null;

		try {
			updateStmt = db.compileStatement(updateSQL);

			Cursor counts = db.rawQuery(countsSQL, null);

			try {
				while (counts.moveToNext() == true)
				{
					long _id = counts.getLong(0);
					long count = counts.getLong(1);

					updateStmt.bindLong(1, count);
					updateStmt.bindLong(2, _id);
					updateStmt.execute();
				}
			} finally {
				counts.close();
			}

			db.setTransactionSuccessful();
		} finally {
			if (updateStmt != null)
				updateStmt.close();

			db.endTransaction();
		}
	}

	private int updateCounts(SQLiteDatabase db, Uri uri, URIPatternIds type,
	  ContentValues v, String sel, String[] args)
	{
		db.beginTransaction();

		try {
			updateCount(db, "UPDATE music_artists SET num_songs = ? WHERE _id = ?",
			  "SELECT artist_id, COUNT(*) FROM music_songs GROUP BY artist_id");
			updateCount(db, "UPDATE music_artists SET num_albums = ? WHERE _id = ?",
			  "SELECT artist_id, COUNT(*) FROM (SELECT artist_id FROM music_songs GROUP BY artist_id, album_id) GROUP BY artist_id");
			updateCount(db, "UPDATE music_albums SET num_songs = ? WHERE _id = ?",
			  "SELECT album_id, COUNT(*) FROM music_songs GROUP BY album_id");
			updateCount(db, "UPDATE music_playlists SET num_songs = ? WHERE _id = ?",
			  "SELECT playlist_id, COUNT(*) FROM music_playlist_songs GROUP BY playlist_id");

//			/* Now delete all the empty containers that are left. */
//			delete(Five.Music.Playlists.CONTENT_URI, "num_songs=0", null);
//			delete(Five.Music.Albums.CONTENT_URI, "num_songs=0", null);
//			delete(Five.Music.Artists.CONTENT_URI, "num_songs=0", null);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		return 1;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
	  String[] selectionArgs)
	{
		checkWritePermission();

		SQLiteDatabase db = mHelper.getWritableDatabase();

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SONG:
		case SONGS:
			return updateSong(db, uri, type, values, selection, selectionArgs);
		case ALBUM:
			return updateAlbum(db, uri, type, values, selection, selectionArgs);
		case ARTIST:
			return updateArtist(db, uri, type, values, selection, selectionArgs);
		case PLAYLIST:
			return updatePlaylist(db, uri, type, values, selection, selectionArgs);
		case SOURCE:
			return updateSource(db, uri, type, values, selection, selectionArgs);
		case ADJUST_COUNTS:
			return updateCounts(db, uri, type, values, selection, selectionArgs);
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

		if (isTemporary() == false)
			getContext().getContentResolver().notifyChange(Five.Sources.CONTENT_URI, null);

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

//		getContext().getContentResolver().notifyChange(ret, null);

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

		if (v.containsKey(Five.Music.Artists.NUM_ALBUMS) == false)
			v.put(Five.Music.Artists.NUM_ALBUMS, 0);

		if (v.containsKey(Five.Music.Artists.NUM_SONGS) == false)
			v.put(Five.Music.Artists.NUM_SONGS, 0);

		adjustNameWithPrefix(v);

		long id = mArtistInserter.insert(v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, id);
		return ret;
	}

	private Uri insertAlbum(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Albums.NAME) == false)
			throw new IllegalArgumentException("NAME cannot be NULL");

		if (v.containsKey(Five.Music.Albums.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");

		if (v.containsKey(Five.Music.Albums.NUM_SONGS) == false)
			v.put(Five.Music.Albums.NUM_SONGS, 0);

		adjustNameWithPrefix(v);

		long id = mAlbumInserter.insert(v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, id);

//		long artistId = v.getAsLong(Five.Music.Albums.ARTIST_ID);
//		Uri artistUri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, artistId);
//
//		getContext().getContentResolver().notifyChange(artistUri, null);

		return ret;
	}

	private Uri insertSong(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Albums.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");

		long id = mSongInserter.insert(v);

		if (id == -1)
			return null;

		Uri ret = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, id);

//		long artistId = v.getAsLong(Five.Music.Songs.ARTIST_ID);
//		Uri artistUri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, artistId);
//
//		getContext().getContentResolver().notifyChange(artistUri, null);
//
//		if (v.containsKey(Five.Music.Songs.ALBUM_ID) == true)
//		{
//			long albumId = v.getAsLong(Five.Music.Songs.ALBUM_ID);
//			Uri albumUri = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, albumId);
//
//			getContext().getContentResolver().notifyChange(albumUri, null);
//		}
//
//		long contentId = v.getAsLong(Five.Music.Songs.CONTENT_ID);
//		Uri Uri = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, contentId);
//
//		getContext().getContentResolver().notifyChange(Uri, null);

		return ret;
	}

	private Uri insertPlaylist(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Playlists.NAME) == false)
			throw new IllegalArgumentException("NAME cannot be NULL");

		if (v.containsKey(Five.Music.Playlists.NUM_SONGS) == false)
			v.put(Five.Music.Playlists.NUM_SONGS, 0);

		long id = db.insert(Five.Music.Playlists.SQL.TABLE,
		  Five.Music.Playlists.NAME, v);

		if (id == -1)
			return null;

		Uri playlistUri = ContentUris
		  .withAppendedId(Five.Music.Playlists.CONTENT_URI, id);

		return playlistUri;
	}

	private Uri insertPlaylistSongs(SQLiteDatabase db, Uri uri, URIPatternIds type, ContentValues v)
	{
		/* TODO: Maybe lack of POSITION means append? */
		if (v.containsKey(Five.Music.PlaylistSongs.POSITION) == false)
			throw new IllegalArgumentException("POSITION cannot be NULL");

		if (v.containsKey(Five.Music.PlaylistSongs.SONG_ID) == false)
			throw new IllegalArgumentException("SONG_ID cannot be NULL");

		if (type == URIPatternIds.SONGS_IN_PLAYLIST)
		{
			v.put(Five.Music.PlaylistSongs.PLAYLIST_ID,
				getSecondToLastPathSegment(uri));
		}

		if (v.containsKey(Five.Music.PlaylistSongs.PLAYLIST_ID) == false)
			throw new IllegalArgumentException("PLAYLIST_ID cannot be NULL");

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
		checkWritePermission();

		SQLiteDatabase db = mHelper.getWritableDatabase();

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
			return insertSource(db, uri, type, values);
		case SOURCE_LOG:
			return insertSourceLog(db, uri, type, values);
		case ARTISTS:
			return insertArtist(db, uri, type, values);
		case ALBUMS:
			return insertAlbum(db, uri, type, values);
		case SONGS:
			return insertSong(db, uri, type, values);
		case PLAYLISTS:
			return insertPlaylist(db, uri, type, values);
		case SONGS_IN_PLAYLIST:
		case PLAYLIST_SONGS:
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
		String custom;
		int count;

		switch (type)
		{
		case SOURCES:
			custom = selection;
			break;

		case SOURCE:
			StringBuilder where = new StringBuilder();
			where.append(Five.Sources._ID).append('=').append(uri.getLastPathSegment());

			if (TextUtils.isEmpty(selection) == false)
				where.append(" AND (").append(selection).append(')');

			custom = where.toString();
			break;

		default:
			throw new IllegalArgumentException("Cannot delete source URI: " + uri);
		}

		count = db.delete(Five.Sources.SQL.TABLE, custom, selectionArgs);

		if (isTemporary() == false)
			getContext().getContentResolver().notifyChange(Five.Sources.CONTENT_URI, null);

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
//		getContext().getContentResolver().notifyChange(uri, null);

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
//		getContext().getContentResolver().notifyChange(uri, null);

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
//		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deletePlaylists(SQLiteDatabase db, Uri uri, URIPatternIds type,
	  String selection, String[] selectionArgs)
	{
		String custom;
		int count;

		switch (type)
		{
		case PLAYLISTS:
			custom = selection;

			Cursor c = db.query(Five.Music.Playlists.SQL.TABLE,
			  new String[] { Five.Music.Playlists._ID },
			  custom, selectionArgs, null, null, null);

			break;

		case PLAYLIST:
			custom = extendWhere(selection,
			  Five.Music.Playlists._ID + '=' + uri.getLastPathSegment());
			break;

		default:
			throw new IllegalArgumentException("Cannot delete playlist URI: " + uri);
		}

		count = db.delete(Five.Music.Playlists.SQL.TABLE, custom, selectionArgs);
//		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deletePlaylistSongs(SQLiteDatabase db, Uri uri, URIPatternIds type,
	  String selection, String[] selectionArgs)
	{
		List<Long> numsegs = null;
		String custom = selection;
		int count;

		if (type != URIPatternIds.PLAYLIST_SONGS)
		{
			numsegs = getNumericPathSegments(uri);
			custom = extendWhere(custom,
			  Five.Music.PlaylistSongs.PLAYLIST_ID + '=' + numsegs.get(0));
		}

		switch (type)
		{
		case PLAYLIST_SONGS:
			break;
		case SONGS_IN_PLAYLIST:
			break;
		case SONG_IN_PLAYLIST:
			custom = extendWhere(custom,
			  Five.Music.PlaylistSongs.SONG_ID + '=' + numsegs.get(1));
			break;
		default:
			throw new IllegalArgumentException("Cannot delete playlist URI: " + uri);
		}

		count = db.delete(Five.Music.PlaylistSongs.SQL.TABLE, custom, selectionArgs);
//		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		checkWritePermission();

		SQLiteDatabase db = mHelper.getWritableDatabase();

		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
		case SOURCE:
			return deleteSource(db, uri, type, selection, selectionArgs);
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
		case PLAYLIST_SONGS:
		case SONGS_IN_PLAYLIST:
			return deletePlaylistSongs(db, uri, type, selection, selectionArgs);
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

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists", URIPatternIds.ARTISTS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#", URIPatternIds.ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/albums", URIPatternIds.ALBUMS_WITH_ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/albums/#/songs", URIPatternIds.SONGS_BY_ARTIST_ON_ALBUM.ordinal());
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
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/playlists/songs", URIPatternIds.PLAYLIST_SONGS.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/adjust_counts", URIPatternIds.ADJUST_COUNTS.ordinal());

		sourcesMap = new HashMap<String, String>();
		sourcesMap.put(Five.Sources._ID, "s." + Five.Sources._ID + " AS " + Five.Sources._ID);
		sourcesMap.put(Five.Sources.HOST, "s." + Five.Sources.HOST + " AS " + Five.Sources.HOST);
		sourcesMap.put(Five.Sources.NAME, "s." + Five.Sources.NAME + " AS " + Five.Sources.NAME);
		sourcesMap.put(Five.Sources.PORT, "s." + Five.Sources.PORT + " AS " + Five.Sources.PORT);
		sourcesMap.put(Five.Sources.REVISION, "s." + Five.Sources.REVISION + " AS " + Five.Sources.REVISION);
		sourcesMap.put(Five.Sources.LAST_ERROR, "sl." + Five.SourcesLog.MESSAGE + " AS " + Five.Sources.LAST_ERROR);

		artistsMap = new HashMap<String, String>();
		artistsMap.put(Five.Music.Artists.MBID, Five.Music.Artists.MBID);
		artistsMap.put(Five.Music.Artists._ID, Five.Music.Artists._ID);
		artistsMap.put(Five.Music.Artists._SYNC_ID, Five.Music.Artists._SYNC_ID);
		artistsMap.put(Five.Music.Artists._SYNC_TIME, Five.Music.Artists._SYNC_TIME);
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
		albumsMap.put(Five.Music.Albums._SYNC_ID, "a." + Five.Music.Albums._SYNC_ID + " AS " + Five.Music.Albums._SYNC_ID);
		albumsMap.put(Five.Music.Albums._SYNC_TIME, "a." + Five.Music.Albums._SYNC_TIME + " AS " + Five.Music.Albums._SYNC_TIME);
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
		songsMap.put(Five.Music.Songs._SYNC_ID, "s." + Five.Music.Songs._SYNC_ID + " AS " + Five.Music.Songs._SYNC_ID);
		songsMap.put(Five.Music.Songs._SYNC_TIME, "s." + Five.Music.Songs._SYNC_TIME + " AS " + Five.Music.Songs._SYNC_TIME);
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
