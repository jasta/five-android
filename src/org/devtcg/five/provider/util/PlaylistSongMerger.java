package org.devtcg.five.provider.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

public final class PlaylistSongMerger extends AbstractTableMerger
{
	private static final String TAG = "PlaylistSongMerger";

	private final ContentValues mTmpValues = new ContentValues();

	private final HashMap<Long, Long> mPlaylistSyncIds = new HashMap<Long, Long>();
	private final HashMap<Long, Long> mSongSyncIds = new HashMap<Long, Long>();

	public PlaylistSongMerger(SQLiteDatabase db)
	{
		super(db, Five.Music.PlaylistSongs.SQL.TABLE, Five.Music.PlaylistSongs.CONTENT_URI);
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
			Cursor playlistCursor = diffs.query(Five.Music.Playlists.CONTENT_URI,
				new String[] { Five.Music.Playlists._ID },
				Five.Music.Playlists._SYNC_ID + " = " + playlistSyncId, null, null);
			long playlistId = -1;
			try {
				if (playlistCursor.moveToFirst() == true)
					playlistId = playlistCursor.getLong(0);
			} finally {
				playlistCursor.close();
			}
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
			Cursor songCursor = diffs.query(Five.Music.Songs.CONTENT_URI,
				new String[] { Five.Music.Songs._ID },
				Five.Music.Songs._SYNC_ID + " = " + songSyncId, null, null);
			long songId = -1;
			try {
				if (songCursor.moveToFirst() == true)
					songId = songCursor.getLong(0);
			} finally {
				songCursor.close();
			}
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
		context.getContentResolver().insert(mTableUri, mTmpValues);
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffs, diffsCursor, mTmpValues);
		context.getContentResolver().update(mTableUri, mTmpValues, Five.Music.PlaylistSongs._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
