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

import java.util.HashMap;

import android.content.ContentProvider;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentUris;
import android.content.UriMatcher;
import android.content.ContentValues;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class FiveProvider extends ContentProvider
{
	private static final String TAG = "FiveProvider";

	private SQLiteDatabase mDB;
	private static final String DATABASE_NAME = "five.db";
	private static final int DATABASE_VERSION = 12;

	private static final UriMatcher URI_MATCHER;
	private static final HashMap<String, String> sourcesMap;

	private static enum URIPatternIds
	{
		SOURCES, SOURCE, SOURCE_LOG,
		ARTISTS, ARTIST,
		ALBUMS, ALBUMS_BY_ARTIST, ALBUM,
		SONGS, SONGS_BY_ALBUM, SONGS_BY_ARTIST, SONG,
		CONTENT, CONTENT_ITEM
		;

		public static URIPatternIds get(int ordinal)
		{
			return values()[ordinal];
		}
	}

	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		@Override
		public void onCreate(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.CREATE);
			db.execSQL(Five.Sources.SQL.INSERT_DUMMY);
			db.execSQL(Five.SourcesLog.SQL.CREATE);

			db.execSQL(Five.Content.SQL.CREATE);
			db.execSQL(Five.Music.Artists.SQL.CREATE);
			db.execSQL(Five.Music.Albums.SQL.CREATE);
			db.execSQL(Five.Music.Songs.SQL.CREATE);
		}

		private void onDrop(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.DROP);
			db.execSQL(Five.SourcesLog.SQL.DROP);
			
			db.execSQL(Five.Content.SQL.DROP);
			db.execSQL(Five.Music.Artists.SQL.DROP);
			db.execSQL(Five.Music.Albums.SQL.DROP);
			db.execSQL(Five.Music.Songs.SQL.DROP);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			Log.w(TAG, "Version too old, wiping out database contents...");
			onDrop(db);
			onCreate(db);
		}
	}

	@Override
	public boolean onCreate()
	{
		DatabaseHelper dbh = new DatabaseHelper();
		mDB = dbh.openDatabase(getContext(), DATABASE_NAME, null, DATABASE_VERSION);

		return (mDB == null) ? false : true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder)
	{
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		String groupBy = null;

		switch (URIPatternIds.get(URI_MATCHER.match(uri)))
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
			
		case SONGS:
			qb.setTables(Five.Music.Songs.SQL.TABLE);
			break;
			
		case SONG:
			qb.setTables(Five.Music.Songs.SQL.TABLE);
			qb.appendWhere("_id=" + uri.getLastPathSegment());
			break;
			
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		Cursor c = qb.query(mDB, projection, selection, selectionArgs, groupBy, null, sortOrder);

		c.setNotificationUri(getContext().getContentResolver(), uri);

		return c;
	}
	
	private int updateSource(Uri uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String id = "_id=" + uri.getLastPathSegment();

		if (sel == null)
			sel = id;
		else
			sel = id + " AND (" + sel + ")";

		int ret = mDB.update(Five.Sources.SQL.TABLE, v, sel, selArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		
		return ret;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
	  String[] selectionArgs)
	{		
		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));
		
		switch (type)
		{
		case SOURCE:
			return updateSource(uri, type, values, selection, selectionArgs);
		default:
			throw new IllegalArgumentException("Cannot insert URI: " + uri);
		}
	}
	
	private Uri insertSource(Uri uri, URIPatternIds type, ContentValues v)
	{
		return null;
	}
	
	private Uri insertSourceLog(Uri uri, URIPatternIds type, ContentValues v)
	{
		String sourceId = uri.getPathSegments().get(1);  

		if (v.containsKey(Five.SourcesLog.SOURCE_ID) == true)
			throw new IllegalArgumentException("SOURCE_ID must be provided through the URI, not the columns");
		
		v.put(Five.SourcesLog.SOURCE_ID, sourceId);

		if (v.containsKey(Five.SourcesLog.TIMESTAMP) == false)
			v.put(Five.SourcesLog.TIMESTAMP, System.currentTimeMillis() / 1000);

		long id = mDB.insert(Five.SourcesLog.SQL.TABLE, Five.SourcesLog.SOURCE_ID, v);

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
	
	private Uri insertContent(Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Content.SOURCE_ID) == false)
			throw new IllegalArgumentException("SOURCE_ID cannot be NULL");
		
		long id = mDB.insert(Five.Content.SQL.TABLE, Five.Content.SIZE, v);
		
		if (id == -1)
			return null;
		
		Uri ret = ContentUris.withAppendedId(Five.Content.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);
		
		return ret;
	}

	private Uri insertArtist(Uri uri, URIPatternIds type, ContentValues v)
	{
		long id = mDB.insert(Five.Music.Artists.SQL.TABLE, Five.Music.Artists.NAME, v);
		
		if (id == -1)
			return null;
		
		Uri ret = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);
		return ret;
	}

	private Uri insertAlbum(Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Albums.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");
		
		long id = mDB.insert(Five.Music.Albums.SQL.TABLE, Five.Music.Albums.NAME, v);
		
		if (id == -1)
			return null;
		
		Uri ret = ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, id);
		getContext().getContentResolver().notifyChange(ret, null);

		long artistId = v.getAsLong(Five.Music.Albums.ARTIST_ID);
		Uri artistUri = ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, artistId);

		getContext().getContentResolver().notifyChange(artistUri, null);

		return ret;
	}

	private Uri insertSong(Uri uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Songs.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");

		if (v.containsKey(Five.Music.Songs.CONTENT_ID) == false)
			throw new IllegalArgumentException("CONTENT_ID cannot be NULL");

		long id = mDB.insert(Five.Music.Songs.SQL.TABLE, Five.Music.Songs.TITLE, v);

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

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
			return insertSource(uri, type, values);
		case SOURCE_LOG:
			return insertSourceLog(uri, type, values);
		case CONTENT_ITEM:
			return insertContent(uri, type, values);
		case ARTISTS:
			return insertArtist(uri, type, values);
		case ALBUMS:
			return insertAlbum(uri, type, values);
		case SONGS:
			return insertSong(uri, type, values);
		default:
			throw new IllegalArgumentException("Cannot insert URI: " + uri);
		}
	}

	private int deleteSource(Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = mDB.delete(Five.Sources.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteContent(Uri uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		String custom;
		int count;
		
		switch (type)
		{
		case CONTENT:
			custom = selection;
			break;
			
		case CONTENT_ITEM:
			StringBuilder where = new StringBuilder();
			where.append(Five.Content._ID).append('=').append(uri.getLastPathSegment());
			
			if (TextUtils.isEmpty(selection) == false)
				where.append(" AND (").append(selection).append(')');

			custom = where.toString();			
			break;
			
		default:
			throw new IllegalArgumentException("Cannot delete content URI: " + uri);
		}

		count = mDB.delete(Five.Content.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}
	
	private int deleteArtist(Uri uri, URIPatternIds type, 
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

		count = mDB.delete(Five.Music.Artists.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteAlbum(Uri uri, URIPatternIds type, 
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
		
		count = mDB.delete(Five.Music.Albums.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteSong(Uri uri, URIPatternIds type, 
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

		count = mDB.delete(Five.Music.Songs.SQL.TABLE, custom, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
			return deleteSource(uri, type, selection, selectionArgs);
		case CONTENT:
		case CONTENT_ITEM:
			return deleteContent(uri, type, selection, selectionArgs);
		case ARTISTS:
		case ARTIST:
			return deleteArtist(uri, type, selection, selectionArgs);
		case ALBUMS:
		case ALBUM:
			return deleteAlbum(uri, type, selection, selectionArgs);
		case SONGS:
		case SONG:
			return deleteSong(uri, type, selection, selectionArgs);
		default:
			throw new IllegalArgumentException("Cannot delete URI: " + uri);
		}
	}

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
	
	static
	{
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(Five.AUTHORITY, "sources", URIPatternIds.SOURCES.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#", URIPatternIds.SOURCE.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#/log", URIPatternIds.SOURCE_LOG.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "content", URIPatternIds.CONTENT.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "content/#", URIPatternIds.CONTENT_ITEM.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists", URIPatternIds.ARTISTS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#", URIPatternIds.ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/albums", URIPatternIds.ALBUMS_BY_ARTIST.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/artists/#/songs", URIPatternIds.SONGS_BY_ARTIST.ordinal());
		
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums", URIPatternIds.ALBUMS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/#", URIPatternIds.ALBUM.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/albums/#/songs", URIPatternIds.SONGS_BY_ALBUM.ordinal());

		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/songs", URIPatternIds.SONGS.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "media/music/songs/#", URIPatternIds.SONG.ordinal());
		
		sourcesMap = new HashMap<String, String>();
		sourcesMap.put(Five.Sources._ID, "s." + Five.Sources._ID + " AS " + Five.Sources._ID);
		sourcesMap.put(Five.Sources.HOST, "s." + Five.Sources.HOST + " AS " + Five.Sources.HOST);
		sourcesMap.put(Five.Sources.NAME, "s." + Five.Sources.NAME + " AS " + Five.Sources.NAME);
		sourcesMap.put(Five.Sources.PORT, "s." + Five.Sources.PORT + " AS " + Five.Sources.PORT);
		sourcesMap.put(Five.Sources.REVISION, "s." + Five.Sources.REVISION + " AS " + Five.Sources.REVISION);
		sourcesMap.put(Five.Sources.LAST_ERROR, "sl." + Five.SourcesLog.MESSAGE + " AS " + Five.Sources.LAST_ERROR);
	}
}
