package org.devtcg.five.provider.util;

import java.util.HashMap;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;

public final class AlbumMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	private final HashMap<Long, Long> mArtistSyncIds = new HashMap<Long, Long>();

	public AlbumMerger()
	{
		super(Five.Music.Albums.SQL.TABLE, Five.Music.Albums.CONTENT_URI);
	}

	@Override
	public void notifyChanges(Context context)
	{
		/* SongMerger will do this for everyone after counts are updated. */
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

	private void rowToContentValues(ContentProvider diffs,
		Cursor cursor, ContentValues values)
	{
		values.clear();
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Albums._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Albums._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Albums.MBID, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Albums.NAME, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Albums.DISCOVERY_DATE, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Albums.RELEASE_DATE, values);

		values.put(Five.Music.Albums.ARTIST_ID, getArtistId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.Albums.ARTIST_ID))));
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
		context.getContentResolver().update(mTableUri, mTmpValues, Five.Music.Albums._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
