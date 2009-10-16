package org.devtcg.five.provider.util;

import java.util.HashMap;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

public final class SongMerger extends AbstractTableMerger
{
	private static final String TAG = "SongMerger";

	private final ContentValues mTmpValues = new ContentValues();

	private final HashMap<Long, Long> mArtistSyncIds = new HashMap<Long, Long>();
	private final HashMap<Long, Long> mAlbumSyncIds = new HashMap<Long, Long>();

	public SongMerger()
	{
		super(Five.Music.Songs.SQL.TABLE, Five.Music.Songs.CONTENT_URI);
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
	}

	@Override
	public void deleteRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		throw new UnsupportedOperationException();
	}

	private long getArtistId(ContentProvider diffs, long artistSyncId)
	{
		Long cache = mArtistSyncIds.get(artistSyncId);
		if (cache != null)
			return cache;
		else
		{
			Cursor artistCursor = diffs.query(Five.Music.Artists.CONTENT_URI,
				new String[] { Five.Music.Artists._ID },
				Five.Music.Artists._SYNC_ID + " = " + artistSyncId, null, null);
			long artistId = -1;
			try {
				if (artistCursor.moveToFirst() == true)
					artistId = artistCursor.getLong(0);
			} finally {
				artistCursor.close();
			}
			mArtistSyncIds.put(artistSyncId, artistId);
			return artistId;
		}
	}

	private long getAlbumId(ContentProvider diffs, long albumSyncId)
	{
		Long cache = mAlbumSyncIds.get(albumSyncId);
		if (cache != null)
			return cache;
		else
		{
			Cursor albumCursor = diffs.query(Five.Music.Albums.CONTENT_URI,
				new String[] { Five.Music.Albums._ID },
				"a." + Five.Music.Albums._SYNC_ID + " = " + albumSyncId,
				null, null);
			long albumId = -1;
			try {
				if (albumCursor.moveToFirst() == true)
					albumId = albumCursor.getLong(0);
			} finally {
				albumCursor.close();
			}
			mAlbumSyncIds.put(albumSyncId, albumId);
			return albumId;
		}
	}

	private void rowToContentValues(ContentProvider diffs,
		Cursor cursor, ContentValues values)
	{
		values.clear();
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Songs.MBID, values);
		DatabaseUtils.cursorIntToContentValues(cursor, Five.Music.Songs.BITRATE, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs.LENGTH, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Songs.TITLE, values);
		DatabaseUtils.cursorIntToContentValues(cursor, Five.Music.Songs.TRACK, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs.DISCOVERY_DATE, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs.CONTENT_ID, values);

		values.put(Five.Music.Songs.ARTIST_ID, getArtistId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.Songs.ARTIST_ID))));
		values.put(Five.Music.Songs.ALBUM_ID, getAlbumId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.Songs.ALBUM_ID))));
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
		context.getContentResolver().update(mTableUri, mTmpValues, Five.Music.Songs._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
