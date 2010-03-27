/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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

package org.devtcg.five;

import java.io.File;

import android.os.Build;
import android.os.Environment;

public interface Constants
{
	/**
	 * Global application debug flag.
	 *
	 * @note This flag is not consistently honored. It was introduced well after
	 *       development started.
	 */
	public static final boolean DEBUG = true;

	/**
	 * True if the current platform version is before 2.0; false if eclair or
	 * later.
	 * <p>
	 *
	 * @note Using Build.VERSION.SDK to support API Level 3 and below. API 4 was
	 *       the first to add SDK_INT and VERSION_CODES.
	 */
	public static final boolean PRE_ECLAIR = (Integer.parseInt(Build.VERSION.SDK) <= 4);

	/**
	 * Generic logging tag to use for various Five components.
	 */
	public static final String TAG = "Five";

	public static final File sBaseStorageDir = new File(Environment.getExternalStorageDirectory(), "five");
	public static final File sMetaStorageDir = new File(sBaseStorageDir, "music");
	public static final File sAlbumArtworkDir = new File(sMetaStorageDir, "album");
	public static final File sArtistPhotoDir = new File(sMetaStorageDir, "artist");
	public static final File sCacheDir = new File(sBaseStorageDir, "cache");

	public static final String ACTION_SYNC_BEGIN = "org.devtcg.five.intent.action.SYNC_BEGIN";
	public static final String ACTION_SYNC_END = "org.devtcg.five.intent.action.SYNC_END";

	public static final String EXTRA_SOURCE_ID = "org.devtcg.five.intent.extra.SOURCE_ID";

	/**
	 * Default server port.
	 */
	public static final int DEFAULT_SERVER_PORT = 5545;

	/**
	 * Broadcast to MetaService to begin a sync.
	 */
	public static final String ACTION_START_SYNC = "org.devtcg.five.intent.action.START_SYNC";

	/**
	 * Broadcast to MetaService to request that a currently running sync
	 * cancels.
	 */
	public static final String ACTION_STOP_SYNC = "org.devtcg.five.intent.action.STOP_SYNC";

	/**
	 * Boolean flag honored by Settings to immediately start SourceAdd to ease
	 * the out-of-the-box set-up experience.
	 */
	public static final String EXTRA_START_SOURCE_ADD = "org.devtcg.five.intent.extra.START_SOURCE_ADD";

	/*
	 * These extras are passed between activities to avoid excessive queries.
	 * Surely there's a better way, but there's a lot of legacy here that I'd
	 * rather not disturb at the moment.
	 */
	public static final String EXTRA_ARTIST_ID = "org.devtcg.five.intent.extra.ARTIST_ID";
	public static final String EXTRA_ARTIST_NAME = "org.devtcg.five.intent.extra.ARTIST_NAME";
	public static final String EXTRA_ARTIST_PHOTO = "org.devtcg.five.intent.extra.ARTIST_PHOTO";
	public static final String EXTRA_ALBUM_ID = "org.devtcg.five.intent.extra.ALBUM_ID";
	public static final String EXTRA_ALBUM_NAME = "org.devtcg.five.intent.extra.ALBUM_NAME";
	public static final String EXTRA_ALBUM_ARTWORK_THUMB = "org.devtcg.five.intent.extra.ALBUM_ARTWORK_THUMB";
	public static final String EXTRA_ALBUM_ARTWORK_LARGE = "org.devtcg.five.intent.extra.ALBUM_ARTWORK_LARGE";
	public static final String EXTRA_PLAYLIST_ID = "org.devtcg.five.intent.extra.PLAYLIST_ID";
	public static final String EXTRA_PLAYLIST_NAME = "org.devtcg.five.intent.extra.PLAYLIST_NAME";
	public static final String EXTRA_PLAYQUEUE = "org.devtcg.five.intent.extra.PLAYQUEUE";
	public static final String EXTRA_ALL_ALBUMS = "org.devtcg.five.intent.extra.ALL_ALBUMS";
	public static final String EXTRA_SONG_ID = "org.devtcg.five.intent.extra.SONG_ID";
	public static final String EXTRA_SONG_TITLE = "org.devtcg.five.intent.extra.SONG_TITLE";
	public static final String EXTRA_SONG_LENGTH = "org.devtcg.five.intent.extra.SONG_LENGTH";
	public static final String EXTRA_PLAYLIST_POSITION = "org.devtcg.five.intent.extra.PLAYLIST_POSITION";
	public static final String EXTRA_PLAYLIST_LENGTH = "org.devtcg.five.intent.extra.PLAYLIST_LENGTH";
}
