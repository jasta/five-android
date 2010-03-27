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

package org.devtcg.five.provider;

import org.devtcg.five.provider.AbstractTableMerger.SyncableColumns;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Structured representation of the Five Meta Data ContentProvider.
 *
 * @todo This class needs to be refactored significantly. It's gotten way too
 *       muddled and confused...
 */
public final class Five
{
	public static final String AUTHORITY = "org.devtcg.five";

	public static Uri makeArtistPhotoUri(long id)
	{
		return Five.Music.Artists.CONTENT_URI.buildUpon()
				.appendEncodedPath(String.valueOf(id))
				.appendEncodedPath("photo")
				.build();
	}

	public static Uri makeAlbumArtworkUri(long id)
	{
		return Five.Music.Albums.CONTENT_URI.buildUpon()
				.appendPath(String.valueOf(id))
				.appendPath("artwork")
				.build();
	}

	public static Uri makeAlbumArtworkBigUri(long id)
	{
		return Five.Music.Albums.CONTENT_URI.buildUpon()
				.appendEncodedPath(String.valueOf(id))
				.appendEncodedPath("artwork")
				.appendEncodedPath("big")
				.build();
	}

	public static Uri makeArtistAlbumsUri(Uri artistUri)
	{
		return artistUri.buildUpon().appendEncodedPath("albums").build();
	}

	private static String makeCreateDeletedTablesSQL(String deletedTable)
	{
		return "CREATE TABLE " + deletedTable + " (" +
				SyncableColumns._ID + " INTEGER PRIMARY KEY, " +
				SyncableColumns._SYNC_ID + " INTEGER, " +
				SyncableColumns._SYNC_TIME + " BIGINT)";
	}

	/**
	 * Concrete synchronization source.  Under this implementation, a TCP
	 * server on the Internet.
	 */
	public interface Sources extends BaseColumns
	{
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.source";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.source";

		/** Access URI. */
		public static final Uri CONTENT_URI =
		  Uri.parse("content://" + AUTHORITY + "/sources");

		/** Hostname or IP address. */
		public static final String HOST = "host";

		/** Listening port. */
		public static final String PORT = "port";

		/**
		 * Hashed mutation of the password originally given by the user (cannot
		 * be decoded, sent verbatim to the server as an authentication token)
		 */
		public static final String PASSWORD = "password";

		/**
		 * Last successful sync time in milliseconds. This field should be used
		 * for display purposes only.
		 */
		public static final String LAST_SYNC_TIME = "revision";

		/**
		 * Current status as updated by the sync process. This is used a way to
		 * watch the sync progress.
		 */
		public static final String STATUS = "status";

		public static final class SQL
		{
			public static final String TABLE = "sources";

			public static final String CREATE =
			  "CREATE TABLE " + TABLE + " (" +
			  "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			  HOST + " TEXT UNIQUE NOT NULL, " +
			  PORT + " INTEGER NOT NULL, " +
			  PASSWORD + " TEXT NOT NULL, " +
			  LAST_SYNC_TIME + " INTEGER, " +
			  STATUS + " TEXT " +
			  ");";

//			public static final String INSERT_DUMMY =
//			  "INSERT INTO " + TABLE + " (" +
//			  NAME + ", " +
//			  HOST + ", " +
//			  PORT + ", " +
//			  REVISION +
//			  ") VALUES (" +
//			  "\"Home\", " +
//			  "\"10.1.0.175\", " +
//			  "5545, " +
//			  "0 " +
//			  ");";

			public static final String DROP =
			  "DROP TABLE IF EXISTS " + TABLE;
		}
	}

//	/**
//	 * Generic columns available for all types of media.
//	 *
//	 * @deprecated this abstraction away from the Songs table is complicated and
//	 *             unnecessary, and will therefore be removed in the future.
//	 */
//	public interface Content extends SyncableColumns
//	{
//		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.media";
//		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.media";
//
//		/** Access URI. */
//		public static final Uri CONTENT_URI =
//		  Uri.parse("content://" + AUTHORITY + "/content");
//
//		/** Access URI for exclusively cached entries. */
//		public static final Uri CONTENT_URI_CACHED =
//		  Uri.parse("content://" + AUTHORITY + "/cache");
//
//		/**
//		 * Server content ID.  This is the item's GUID at the server.  Not
//		 * to be confused by similarly named fields which key into this
//		 * table.
//		 */
//		public static final String CONTENT_ID = "content_id";
//
//		/** Server source. */
//		public static final String SOURCE_ID = "source_id";
//
//		/** Media meta data. */
//		public static final String MIME_TYPE = "mime_type";
//
//		/** Raw media size in bytes. */
//		public static final String SIZE = "size";
//
//		/** Timestamp of the cached entry, if present. */
//		public static final String CACHED_TIMESTAMP = "cached_timestamp";
//
//		/** Reference to cache table if cached; otherwise NULL. */
//		public static final String CACHED_PATH = "cached_path";
//
//		public static final class SQL
//		{
//			public static final String TABLE = "content";
//
//			public static final String CREATE =
//			  "CREATE TABLE " + TABLE + " (" +
//			  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
//			  _SYNC_ID + " INTEGER, " +
//			  _SYNC_TIME + " BIGINT, " +
//			  CONTENT_ID + " INTEGER NOT NULL, " +
//			  SOURCE_ID + " INTEGER NOT NULL, " +
//			  MIME_TYPE + " TEXT NOT NULL, " +
//			  SIZE + " INTEGER NOT NULL, " +
//			  CACHED_TIMESTAMP + " INTEGER, " +
//			  CACHED_PATH + " TEXT " +
//			  ");";
//
//			public static final String[] INDEX =
//			{
//				"CREATE UNIQUE INDEX " +
//			  	  TABLE + "_" + SOURCE_ID + "_" + CONTENT_ID +
//			  	  " ON " + TABLE + " (" +
//			  	  SOURCE_ID + ", " +
//			  	  CONTENT_ID + " " +
//			  	");",
//			  	"CREATE INDEX " +
//			  	  TABLE + "_" + CACHED_TIMESTAMP +
//			  	  " ON " + TABLE + " (" +
//			  	  CACHED_TIMESTAMP +
//			  	");",
//			  	"CREATE UNIQUE INDEX " +
//			  	  TABLE + "_" + _SYNC_ID +
//			  	  " ON " + TABLE + " (" +
//			  	  _SYNC_ID +
//			  	");",
//			};
//
//			public static final String DROP =
//			  "DROP TABLE IF EXISTS " + TABLE + ";";
//		}
//	}

	public interface Images extends BaseColumns
	{
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.image";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.image";

		/** Access URI. */
		public static final Uri CONTENT_URI =
		  Uri.parse("content://" + AUTHORITY + "/media/images");

		/** Key to access potentially cached or remote data. */
		public static final String CONTENT_ID = "content_id";

		/** Title, if any; otherwise, filename. */
		public static final String TITLE = "title";

		/** Image dimension: width. */
		public static final String NATIVE_WIDTH = "width";

		/** Image dimension: height. */
		public static final String NATIVE_HEIGHT = "height";
	}

	public static final class Music
	{
		/**
		 * Last time an item (album, song, playlist, ...) was listened to
		 * on the device (client-side).
		 */
		public static final String LAST_PLAYED = "last_played";

		public interface Songs extends SyncableColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.song";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.song";

			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/songs");

			public static final Uri CONTENT_DELETED_URI =
				  Uri.parse("content://" + AUTHORITY + "/media/music/songs/deleted");

			/**
			 * Server source, used to join Content and Songs during sync
			 * merging.
			 * <p>
			 * XXX: This is broken by AbstractTableMerger, which only cares
			 * about _SYNC_ID and not jointly _SYNC_ID and SOURCE_ID.
			 */
			public static final String SOURCE_ID = "source_id";

			/** Media meta data. */
			public static final String MIME_TYPE = "mime_type";

			/** Raw media size in bytes. */
			public static final String SIZE = "size";

			/** Timestamp of the cached entry, if present. */
			public static final String CACHED_TIMESTAMP = "cached_timestamp";

			/** Reference to cache table if cached; otherwise NULL. */
			public static final String CACHED_PATH = "cached_path";

			/** MusicBrainz identifier. */
			public static final String MBID = "mbid";

			/** Title / Song / Content description. */
			public static final String TITLE = "title";

			/** Album / Movie (soundtrack, etc) / Show title. */
			public static final String ALBUM = "album";
			public static final String ALBUM_ID = "album_id";

			/** Lead performer / Soloist / Composer. */
			public static final String ARTIST = "artist";
			public static final String ARTIST_ID = "artist_id";

			/** Running time in seconds. */
			public static final String LENGTH = "length";

			/** Average or estimated bitrate. */
			public static final String BITRATE = "bitrate";

			/** Track number / Position in set. */
			public static final String TRACK = "track_num";

			/** Set number, if in one. */
			public static final String SET = "set_num";

			/** Music style / Content type. */
			public static final String GENRE = "genre";

			/** Date that this song was first introduced into the collection. */
			public static final String DISCOVERY_DATE = "discovery_date";

			public static final class SQL
			{
				public static final String TABLE = "music_songs";
				public static final String DELETED_TABLE = "music_songs_deleted";

				public static final String[] CREATE = {
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  _SYNC_ID + " INTEGER, " +
				  _SYNC_TIME + " BIGINT, " +
				  SOURCE_ID + " INTEGER, " +
				  MIME_TYPE + " TEXT NOT NULL, " +
				  SIZE + " INTEGER NOT NULL, " +
				  CACHED_TIMESTAMP + " INTEGER, " +
				  CACHED_PATH + " TEXT, " +
				  MBID + " INTEGER, " +
				  TITLE + " TEXT COLLATE UNICODE NOT NULL, " +
				  ARTIST_ID + " INTEGER NOT NULL, " +
				  ALBUM_ID + " INTEGER, " +
				  LENGTH + " INTEGER NOT NULL, " +
				  BITRATE + " INTEGER, " +
				  TRACK + " INTEGER, " +
				  GENRE + " TEXT, " +
				  SET + " INTEGER, " +
				  DISCOVERY_DATE + " DATETIME, " +
				  LAST_PLAYED + " DATETIME " +
				  ");",
				  makeCreateDeletedTablesSQL(DELETED_TABLE),
				};

				public static final String[] INDEX = {
					"CREATE INDEX " +
				  	  TABLE + "_" + ARTIST_ID +
				  	  " ON " + TABLE + " (" + ARTIST_ID + ");",
				  	"CREATE INDEX " +
				  	  TABLE + "_" + ALBUM_ID +
				  	  " ON " + TABLE + " (" + ALBUM_ID + ");",
				  	"CREATE INDEX " +
				  	  TABLE + "_" + CACHED_TIMESTAMP +
				  	  " ON " + TABLE + " (" +
				  	  CACHED_TIMESTAMP +
				  	");",
				  	"CREATE UNIQUE INDEX " +
				  	  TABLE + "_" + _SYNC_ID +
				  	  " ON " + TABLE + " (" +
				  	  _SYNC_ID +
				  	");",
				};

				public static final String[] DROP = {
				  "DROP TABLE IF EXISTS " + TABLE,
				  "DROP TABLE IF EXISTS " + DELETED_TABLE,
				};
			}
		}

		public interface Artists extends SyncableColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.artist";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.artist";

			/** Access URI. */
			public static final Uri CONTENT_URI =
				Uri.parse("content://" + AUTHORITY + "/media/music/artists");

			public static final Uri CONTENT_DELETED_URI =
				Uri.parse("content://" + AUTHORITY + "/media/music/artists/deleted");

			/** MusicBrainz identifier. */
			public static final String MBID = "mbid";

			/** Performing name (significant portion for sort purposes; excludes "The"). */
			public static final String NAME = "name";

			/** Full name, including prefix. */
			public static final String FULL_NAME = "full_name";

			/** Leading text not included for sorting purposes. */
			public static final String NAME_PREFIX = "name_prefix";

			/** Band or performer photograph (content URI). */
			public static final String PHOTO = "photo";

			/** General musical style, if applicable. */
			public static final String GENRE = "genre";

			/** Date that this artist was first introduced into the collection. */
			public static final String DISCOVERY_DATE = "discovery_date";

			/** Number of unique albums belonging to this artist in the
			 * collection. */
			public static final String NUM_ALBUMS = "num_albums";

			/** Number of songs belonging to this artist. */
			public static final String NUM_SONGS = "num_songs";

			public static final class SQL
			{
				public static final String TABLE = "music_artists";
				public static final String DELETED_TABLE = TABLE + "_deleted";

				public static final String[] CREATE = {
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  _SYNC_ID + " INTEGER, " +
				  _SYNC_TIME + " BIGINT, " +
				  MBID + " INTEGER, " +
				  NAME + " TEXT COLLATE UNICODE NOT NULL, " +
				  NAME_PREFIX + " TEXT, " +
				  PHOTO + " TEXT, " +
				  GENRE + " TEXT, " +
				  DISCOVERY_DATE + " DATETIME, " +
				  NUM_ALBUMS + " INTEGER, " +
				  NUM_SONGS + " INTEGER " +
				  ")",
				  makeCreateDeletedTablesSQL(DELETED_TABLE),
				};

				public static final String[] INDEX  = {
			  	  "CREATE UNIQUE INDEX " +
			  	    TABLE + "_" + _SYNC_ID +
			  	    " ON " + TABLE + " (" +
			  	    _SYNC_ID +
			  	  ");",
				};

				public static final String[] DROP = {
				  "DROP TABLE IF EXISTS " + TABLE,
				  "DROP TABLE IF EXISTS " + DELETED_TABLE,
				};
			}
		}

		public interface Albums extends SyncableColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.album";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.album";

			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/albums");

			public static final Uri CONTENT_DELETED_URI =
				  Uri.parse("content://" + AUTHORITY + "/media/music/albums/deleted");

			/** Access URI for "complete" albums only. */
			public static final Uri CONTENT_URI_COMPLETE =
			  Uri.parse("content://" + AUTHORITY + "/media/music/albums/complete");

			/** MusicBrainz identifier. */
			public static final String MBID = "mbid";

			/** Album name (less prefix). */
			public static final String NAME = "name";

			/** Full name, including prefix. */
			public static final String FULL_NAME = "full_name";

			/** Leading text not included for sorting purposes. */
			public static final String NAME_PREFIX = "name_prefix";

			/** Artist. */
			public static final String ARTIST = "artist";
			public static final String ARTIST_ID = "artist_id";

			/** Album artwork / Cover photo (content URI). */
			public static final String ARTWORK = "artwork";
			public static final String ARTWORK_BIG = "artwork_big";

			/** Original release date. */
			public static final String RELEASE_DATE = "release_date";

			/** TODO: Expand on this idea... */
			public static final String SET = "set";

			/** Date that this album was first introduced into the collection. */
			public static final String DISCOVERY_DATE = "discovery_date";

			/** Number of songs belonging to this album. */
			public static final String NUM_SONGS = "num_songs";

			public static final class SQL
			{
				public static final String TABLE = "music_albums";
				public static final String DELETED_TABLE = "music_albums_deleted";

				public static final String[] CREATE = {
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  _SYNC_ID + " INTEGER, " +
				  _SYNC_TIME + " BIGINT, " +
				  MBID + " INTEGER, " +
				  NAME + " TEXT COLLATE UNICODE NOT NULL, " +
				  NAME_PREFIX + " TEXT, " +
				  ARTIST_ID + " INTEGER, " +
				  ARTWORK + " TEXT, " +
				  ARTWORK_BIG + " TEXT, " +
				  RELEASE_DATE + " DATETIME, " +
				  DISCOVERY_DATE + " DATETIME, " +
				  NUM_SONGS + " INTEGER " +
				  ")",
				  makeCreateDeletedTablesSQL(DELETED_TABLE),
				};

				public static final String[] INDEX = {
					"CREATE INDEX " +
				  	  TABLE + "_" + ARTIST_ID +
				  	  " ON " + TABLE + " (" + ARTIST_ID + ");",
				  	"CREATE UNIQUE INDEX " +
				  	  TABLE + "_" + _SYNC_ID +
				  	  " ON " + TABLE + " (" +
				  	  _SYNC_ID +
				  	");",
				};

				public static final String[] DROP = {
				  "DROP TABLE IF EXISTS " + TABLE,
				  "DROP TABLE IF EXISTS " + DELETED_TABLE,
				};
			}
		}

		public interface Playlists extends SyncableColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.playlists";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.playlists";

			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/playlists");

			public static final Uri CONTENT_DELETED_URI =
				  Uri.parse("content://" + AUTHORITY + "/media/music/playlists/deleted");

			/** User-prescribed name and description. */
			public static final String NAME = "name";

			/** Playlist create date (might be guessed). */
			public static final String CREATED_DATE = "created_date";

			/** Number of songs in this play queue. */
			public static final String NUM_SONGS = "num_songs";

			public static final class SQL
			{
				public static final String TABLE = "music_playlists";
				public static final String DELETED_TABLE = "music_playlists_deleted";

				public static final String[] CREATE = {
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  _SYNC_ID + " INTEGER, " +
				  _SYNC_TIME + " BIGINT, " +
				  NAME + " TEXT COLLATE UNICODE NOT NULL, " +
				  CREATED_DATE + " DATETIME, " +
				  NUM_SONGS + " INTEGER " +
				  ");",
				  makeCreateDeletedTablesSQL(DELETED_TABLE),
				};

				public static final String[] DROP = {
				  "DROP TABLE IF EXISTS " + TABLE,
				  "DROP TABLE IF EXISTS " + DELETED_TABLE,
				};
			}
		}

		/** _ID is the same as {@link Songs}. */
		public interface PlaylistSongs extends SyncableColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.playlists.songs";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.playlists.songs";

			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/playlists/songs");

			public static final Uri CONTENT_DELETED_URI =
				  Uri.parse("content://" + AUTHORITY + "/media/music/playlists/songs/deleted");

			/** Playlist ID. */
			public static final String PLAYLIST_ID = "playlist_id";

			/** Position in playlist. */
			public static final String POSITION = "position";

			/** Reference to song. */
			public static final String SONG_ID = "song_id";

			public static final class SQL
			{
				public static final String TABLE = "music_playlist_songs";
				public static final String DELETED_TABLE = "music_playlist_songs_deleted";

				public static final String[] CREATE = {
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  _SYNC_ID + " INTEGER, " +
				  _SYNC_TIME + " BIGINT, " +
				  PLAYLIST_ID + " INTEGER NOT NULL, " +
				  SONG_ID + " INTEGER NOT NULL, " +
				  POSITION + " INTEGER NOT NULL " +
				  ");",
				  makeCreateDeletedTablesSQL(DELETED_TABLE),
				};

				public static final String[] INDEX = {
				  "CREATE INDEX " +
				    TABLE + "_" + PLAYLIST_ID +
				    " ON " + TABLE + " (" + PLAYLIST_ID + ");",
				};

				public static final String[] DROP = {
				  "DROP TABLE IF EXISTS " + TABLE,
				  "DROP TABLE IF EXISTS " + DELETED_TABLE,
				};
			}
		}

		public interface AdjustCounts
		{
			/**
			 * Efficiently updates NUM_ALBUMS, NUM_SONGS on Artists, Albums,
			 * and Playlists.
			 */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/adjust_counts");
		}
	}
}
