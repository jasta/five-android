package org.devtcg.five.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Structured representation of the Five Meta Data ContentProvider.  
 */
public final class Five
{
	public static final String AUTHORITY = "org.devtcg.five";

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

		/** Server name, for identification purposes. */
		public static final String NAME = "name";

		/** Hostname or IP address. */
		public static final String HOST = "host";

		/** Listening port. */
		public static final String PORT = "port";

		/** Current sync revision (last time in seconds). */
		public static final String REVISION = "revision";

		public static final class SQL
		{
			public static final String TABLE = "sources";

			public static final String CREATE =
			  "CREATE TABLE " + TABLE + " (" +
			  "_id INTEGER PRIMARY KEY AUTOINCREMENT, " + 
			  NAME + " TEXT NOT NULL, " +
			  HOST + " TEXT UNIQUE NOT NULL, " +
			  PORT + " INTEGER NOT NULL, " +
			  REVISION + " INTEGER " +
			  ");";

			public static final String INSERT_DUMMY =
			  "INSERT INTO " + TABLE + " (" +
			  NAME + ", " +
			  HOST + ", " +
			  PORT + ", " +
			  REVISION +
			  ") VALUES (" +
			  "\"Home\", " +
			  "\"10.1.0.175\", " +
			  "5545, " +
			  "0 " +
			  ");";
			  
			public static final String DROP =
			  "DROP TABLE IF EXISTS " + TABLE;
		}
	}
	
	/**
	 * Provider to access actual cached content.
	 */
	public interface Cache extends BaseColumns
	{
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.content";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.content";

		/** Access URI. */
		public static final Uri CONTENT_URI =
		  Uri.parse("content://" + AUTHORITY + "/cache");
		
		/** Server source. */
		public static final String SOURCE_ID = "source_id";
	}
	
	/**
	 * Generic columns available for all types of media.
	 */
	public interface Content extends BaseColumns
	{
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.media";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.media";

		/** Access URI. */
		public static final Uri CONTENT_URI =
		  Uri.parse("content://" + AUTHORITY + "/content");

		/** Server source. */
		public static final String SOURCE_ID = "source_id";

		/** Raw media size in bytes. */
		public static final String SIZE = "size";

		/** Locally cached file path if available offline; otherwise, null. */
		public static final String CACHED = "cached_path";

		public static final class SQL
		{
			public static final String TABLE = "content";

			public static final String CREATE =
			  "CREATE TABLE " + TABLE + " (" +
			  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
			  SOURCE_ID + " INTEGER NOT NULL, " +
			  SIZE + " INTEGER NOT NULL, " +
			  CACHED + " TEXT " +
			  ");";

			public static final String DROP =
			  "DROP TABLE IF EXISTS " + TABLE;
		}
	}
	
	public interface Images extends BaseColumns
	{
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.image";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.image";
		
		/** Access URI. */
		public static final Uri CONTENT_URI =
		  Uri.parse("content://" + AUTHORITY + "/media/images");
		
		/** Key to access potentially cached or remote data. */
		public static final String CONTENT_SOURCE_ID = "content_source_id";
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

		public interface Songs extends BaseColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.song";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.song";
			
			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/songs");

			/** Key to access potentially cached or remote data. */
			public static final String CONTENT_SOURCE_ID = "content_source_id";
			public static final String CONTENT_ID = "content_id";

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

				public static final String CREATE =
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  CONTENT_SOURCE_ID + " INTEGER, " +
				  CONTENT_ID + " INTEGER, " +
				  TITLE + " TEXT NOT NULL, " +
				  ARTIST_ID + " INTEGER, " +
				  ALBUM_ID + " INTEGER, " +
				  LENGTH + " INTEGER, " +
				  TRACK + " INTEGER, " +
				  GENRE + " TEXT, " +
				  SET + " INTEGER, " +
				  DISCOVERY_DATE + " DATETIME, " +
				  LAST_PLAYED + " DATETIME " +
				  ");";

				public static final String DROP =
				  "DROP TABLE IF EXISTS " + TABLE;
			}
		}

		public interface Artists extends BaseColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.artist";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.artist";
			
			/** Access URI. */
			public static final Uri CONTENT_URI =
				Uri.parse("content://" + AUTHORITY + "/media/music/artists");

			/** Performing name. */
			public static final String NAME = "name";

			/** Band or performer photograph. */
			public static final String PHOTO_ID = "image_id";

			/** General musical style, if applicable. */
			public static final String GENRE = "genre";
			
			/** Date that this artist was first introduced into the collection. */
			public static final String DISCOVERY_DATE = "discovery_date";

			public static final class SQL
			{
				public static final String TABLE = "music_artists";

				public static final String CREATE =
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  NAME + " TEXT NOT NULL, " +
				  GENRE + " TEXT, " +
				  DISCOVERY_DATE + " DATETIME " +
				  ");";

				public static final String DROP =
				  "DROP TABLE IF EXISTS " + TABLE;
			}
		}

		public interface Albums extends BaseColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.album";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.album";
			
			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/albums");

			/** Album name. */
			public static final String NAME = "name";

			/** Artist. */
			public static final String ARTIST = "artist";
			public static final String ARTIST_ID = "artist_id";
			
			/** Album artwork / Cover photo. */
			public static final String ARTWORK_ID = "image_id";
			
			/** Original release date. */
			public static final String RELEASE_DATE = "release_date";
			
			/** TODO: Expand on this idea... */
			public static final String SET = "set";
			
			/** Date that this album was first introduced into the collection. */
			public static final String DISCOVERY_DATE = "discovery_date";

			public static final class SQL
			{
				public static final String TABLE = "music_albums";

				public static final String CREATE =
				  "CREATE TABLE " + TABLE + " (" +
				  _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				  NAME + " TEXT NOT NULL, " +
				  ARTIST_ID + " INTEGER, " +
				  RELEASE_DATE + " DATETIME, " +
				  DISCOVERY_DATE + " DATETIME " +
				  ");";

				public static final String DROP =
				  "DROP TABLE IF EXISTS " + TABLE;
			}
		}

		public interface Playlists extends BaseColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.playlists";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.playlists";

			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/playlists");

			/** User-prescribed name and description. */
			public static final String NAME = "name";

			/** Playlist create date (might be guessed). */
			public static final String CREATED_DATE = "created_date";
		}
		
		/** _ID is the same as {@link Songs}. */
		public interface PlaylistSongs extends BaseColumns
		{
			public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.five.music.playlists.songs";
			public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.five.music.playlists.songs";
			
			/** Access URI. */
			public static final Uri CONTENT_URI =
			  Uri.parse("content://" + AUTHORITY + "/media/music/playlists/songs");
			
			/** Playlist ID. */
			public static final String PLAYLIST = "playlist";
		}
	}
}
