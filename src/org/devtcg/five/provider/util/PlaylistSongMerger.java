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

package org.devtcg.five.provider.util;

import java.util.HashMap;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

public final class PlaylistSongMerger extends AbstractTableMerger
{
	private static final String TAG = "PlaylistSongMerger";

	private final ContentValues mTmpValues = new ContentValues();

	private final HashMap<Long, Long> mPlaylistSyncIds = new HashMap<Long, Long>();
	private final HashMap<Long, Long> mSongSyncIds = new HashMap<Long, Long>();

	private final FiveProvider mProvider;

	public PlaylistSongMerger(FiveProvider provider)
	{
		super(provider.getDatabase(), Five.Music.PlaylistSongs.SQL.TABLE, Five.Music.PlaylistSongs.CONTENT_URI);
		mProvider = provider;
	}

	@Override
	public void notifyChanges(Context context)
	{
		ContentResolver cr = context.getContentResolver();

		Log.i(TAG, "Updating counts...");

		cr.update(Five.Music.AdjustCounts.CONTENT_URI,
		  null, null, null);

		Log.i(TAG, "Done!");

		cr.notifyChange(Five.Music.Artists.CONTENT_URI, null);
		cr.notifyChange(Five.Music.Albums.CONTENT_URI, null);
		cr.notifyChange(Five.Music.Songs.CONTENT_URI, null);
		cr.notifyChange(Five.Music.Playlists.CONTENT_URI, null);
		cr.notifyChange(Five.Music.PlaylistSongs.CONTENT_URI, null);
	}

	@Override
	public void deleteRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		throw new UnsupportedOperationException();
	}

	private long getPlaylistId(ContentProvider diffs, long playlistSyncId)
	{
		Long cache = mPlaylistSyncIds.get(playlistSyncId);
		if (cache != null)
			return cache;
		else
		{
			long playlistId = DatabaseUtils.longForQuery(getDatabase(),
				"SELECT _id FROM " + Five.Music.Playlists.SQL.TABLE +
				" WHERE _sync_id=" + playlistSyncId, null);
			mPlaylistSyncIds.put(playlistSyncId, playlistId);
			return playlistId;
		}
	}

	private long getSongId(ContentProvider diffs, long songSyncId)
	{
		Long cache = mSongSyncIds.get(songSyncId);
		if (cache != null)
			return cache;
		else
		{
			long songId = DatabaseUtils.longForQuery(getDatabase(),
				"SELECT _id FROM " + Five.Music.Songs.SQL.TABLE +
				" WHERE _sync_id=" + songSyncId, null);
			mSongSyncIds.put(songSyncId, songId);
			return songId;
		}
	}

	private void rowToContentValues(ContentProvider diffs,
		Cursor cursor, ContentValues values)
	{
		values.clear();
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.PlaylistSongs._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.PlaylistSongs._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.PlaylistSongs.POSITION, values);

		values.put(Five.Music.PlaylistSongs.PLAYLIST_ID, getPlaylistId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.PlaylistSongs.PLAYLIST_ID))));
		values.put(Five.Music.PlaylistSongs.SONG_ID, getSongId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.PlaylistSongs.SONG_ID))));
	}

	@Override
	public void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		rowToContentValues(diffs, diffsCursor, mTmpValues);
		mProvider.insertInternal(mTableUri, mTmpValues);
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffs, diffsCursor, mTmpValues);
		mProvider.updateInternal(mTableUri, mTmpValues, Five.Music.PlaylistSongs._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
