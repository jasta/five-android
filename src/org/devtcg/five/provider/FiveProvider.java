package org.devtcg.five.provider;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentProviderDatabaseHelper;
import android.content.ContentURIParser;
import android.content.ContentValues;
import android.content.QueryBuilder;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ContentURI;
import android.util.Log;

public class FiveProvider extends ContentProvider
{
	private static final String TAG = "FiveProvider";

	private SQLiteDatabase mDB;
	private static final String DATABASE_NAME = "five.db";
	private static final int DATABASE_VERSION = 7;

	private static final ContentURIParser URI_MATCHER;
	private static final HashMap<String, String> sourcesMap;

	private static enum URIPatternIds
	{
		SOURCES, SOURCE,
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

	private static class DatabaseHelper extends ContentProviderDatabaseHelper
	{
		@Override
		public void onCreate(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.CREATE);
			db.execSQL(Five.Sources.SQL.INSERT_DUMMY);

			db.execSQL(Five.Content.SQL.CREATE);
			db.execSQL(Five.Music.Artists.SQL.CREATE);
			db.execSQL(Five.Music.Albums.SQL.CREATE);
			db.execSQL(Five.Music.Songs.SQL.CREATE);
		}

		private void onDrop(SQLiteDatabase db)
		{
			db.execSQL(Five.Sources.SQL.DROP);
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
	public Cursor query(ContentURI uri, String[] projection, String selection,
			String[] selectionArgs, String groupBy, String having,
			String sortOrder)
	{
		QueryBuilder qb = new QueryBuilder();

		switch (URIPatternIds.get(URI_MATCHER.match(uri)))
		{
		case SOURCES:
			qb.setTables(Five.Sources.SQL.TABLE);
			qb.setProjectionMap(sourcesMap);
			break;
			
		case SOURCE:
			qb.setTables(Five.Sources.SQL.TABLE);
			qb.appendWhere("_id=" + uri.getPathLeaf());
			break;
			
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
		
		Cursor c = qb.query(mDB, projection, selection, selectionArgs, groupBy, having, sortOrder);
		
		c.setNotificationUri(getContext().getContentResolver(), uri);
		
		return c;
	}
	
	private int updateSource(ContentURI uri, URIPatternIds type, ContentValues v,
	  String sel, String[] selArgs)
	{
		String id = "_id=" + uri.getPathLeaf();

		if (sel == null)
			sel = id;
		else
			sel = id + " AND (" + sel + ")";

		int ret = mDB.update(Five.Sources.SQL.TABLE, v, sel, selArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		
		return ret;
	}

	@Override
	public int update(ContentURI uri, ContentValues values, String selection,
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
	
	private ContentURI insertSource(ContentURI uri, URIPatternIds type, ContentValues v)
	{
		return null;
	}
	
	private ContentURI insertContent(ContentURI uri, URIPatternIds tyep, ContentValues v)
	{
		if (v.containsKey(Five.Content.SOURCE_ID) == false)
			throw new IllegalArgumentException("SOURCE_ID cannot be NULL");
		
		long id = mDB.insert(Five.Content.SQL.TABLE, Five.Content.SIZE, v);
		
		if (id == -1)
			return null;
		
		ContentURI ret = Five.Content.CONTENT_URI.addId(id);
		getContext().getContentResolver().notifyChange(ret, null);
		
		return ret;
	}

	private ContentURI insertArtist(ContentURI uri, URIPatternIds type, ContentValues v)
	{
		long id = mDB.insert(Five.Music.Artists.SQL.TABLE, Five.Music.Artists.NAME, v);
		
		if (id == -1)
			return null;
		
		ContentURI ret = Five.Music.Artists.CONTENT_URI.addId(id);
		getContext().getContentResolver().notifyChange(ret, null);
		return ret;
	}

	private ContentURI insertAlbum(ContentURI uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Albums.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");
		
		long id = mDB.insert(Five.Music.Albums.SQL.TABLE, Five.Music.Albums.NAME, v);
		
		if (id == -1)
			return null;
		
		ContentURI ret = Five.Music.Albums.CONTENT_URI.addId(id);
		getContext().getContentResolver().notifyChange(ret, null);

		long artistId = v.getAsLong(Five.Music.Albums.ARTIST_ID);
		ContentURI artistUri = Five.Music.Artists.CONTENT_URI.addId(artistId);

		getContext().getContentResolver().notifyChange(artistUri, null);

		return ret;
	}

	private ContentURI insertSong(ContentURI uri, URIPatternIds type, ContentValues v)
	{
		if (v.containsKey(Five.Music.Songs.ARTIST_ID) == false)
			throw new IllegalArgumentException("ARTIST_ID cannot be NULL");

		if (v.containsKey(Five.Music.Songs.ALBUM_ID) == false)
			throw new IllegalArgumentException("ALBUM_ID cannot be NULL");
		
		if (v.containsKey(Five.Music.Songs.CONTENT_ID) == false)
			throw new IllegalArgumentException("CONTENT_ID cannot be NULL");

		long id = mDB.insert(Five.Music.Songs.SQL.TABLE, Five.Music.Songs.TITLE, v);

		if (id == -1)
			return null;

		ContentURI ret = Five.Music.Songs.CONTENT_URI.addId(id);
		getContext().getContentResolver().notifyChange(ret, null);

		long artistId = v.getAsLong(Five.Music.Songs.ARTIST_ID);
		ContentURI artistUri = Five.Music.Artists.CONTENT_URI.addId(artistId);

		getContext().getContentResolver().notifyChange(artistUri, null);

		long albumId = v.getAsLong(Five.Music.Songs.ALBUM_ID);
		ContentURI albumUri = Five.Music.Albums.CONTENT_URI.addId(albumId);

		getContext().getContentResolver().notifyChange(albumUri, null);

		long contentId = v.getAsLong(Five.Music.Songs.CONTENT_ID);
		ContentURI contentUri = Five.Music.Songs.CONTENT_URI.addId(contentId);

		getContext().getContentResolver().notifyChange(contentUri, null);
		
		return ret;
	}

	@Override
	public ContentURI insert(ContentURI uri, ContentValues values)
	{
		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));
		
		switch (type)
		{
		case SOURCES:
			return insertSource(uri, type, values);
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

	private int deleteSource(ContentURI uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = mDB.delete(Five.Sources.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteContent(ContentURI uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = mDB.delete(Five.Content.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteArtist(ContentURI uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = mDB.delete(Five.Music.Artists.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteAlbum(ContentURI uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = mDB.delete(Five.Music.Albums.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	private int deleteSong(ContentURI uri, URIPatternIds type, 
	  String selection, String[] selectionArgs)
	{
		int count;

		count = mDB.delete(Five.Music.Songs.SQL.TABLE, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public int delete(ContentURI uri, String selection, String[] selectionArgs)
	{
		URIPatternIds type = URIPatternIds.get(URI_MATCHER.match(uri));

		switch (type)
		{
		case SOURCES:
			return deleteSource(uri, type, selection, selectionArgs);
		case CONTENT:
			return deleteContent(uri, type, selection, selectionArgs);
		case ARTISTS:
			return deleteArtist(uri, type, selection, selectionArgs);
		case ALBUMS:
			return deleteAlbum(uri, type, selection, selectionArgs);
		case SONGS:
			return deleteSong(uri, type, selection, selectionArgs);
		default:
			throw new IllegalArgumentException("Cannot delete URI: " + uri);
		}
	}

	@Override
	public String getType(ContentURI uri)
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
		URI_MATCHER = new ContentURIParser(ContentURIParser.NO_MATCH);
		URI_MATCHER.addURI(Five.AUTHORITY, "sources", URIPatternIds.SOURCES.ordinal());
		URI_MATCHER.addURI(Five.AUTHORITY, "sources/#", URIPatternIds.SOURCE.ordinal());

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
		sourcesMap.put(Five.Sources._ID, Five.Sources._ID);
		sourcesMap.put(Five.Sources.HOST, Five.Sources.HOST);
		sourcesMap.put(Five.Sources.NAME, Five.Sources.NAME);
		sourcesMap.put(Five.Sources.PORT, Five.Sources.PORT);
		sourcesMap.put(Five.Sources.REVISION, Five.Sources.REVISION);
	}
}
