package org.devtcg.five.provider.util;

import java.util.HashMap;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;

public final class SongMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	private final HashMap<Long, Long> mArtistSyncIds = new HashMap<Long, Long>();
	private final HashMap<Long, Long> mAlbumSyncIds = new HashMap<Long, Long>();

	private final FiveProvider mProvider;

	public SongMerger(FiveProvider provider)
	{
		super(provider.getDatabase(), Five.Music.Songs.SQL.TABLE, Five.Music.Songs.CONTENT_URI);
		mProvider = provider;
	}

	@Override
	public void notifyChanges(Context context)
	{
		/* PlaylistSongs merger will do this for everyone after counts are updated. */
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
			long artistId = DatabaseUtils.longForQuery(getDatabase(),
				"SELECT _id FROM " + Five.Music.Artists.SQL.TABLE +
				" WHERE _sync_id=" + artistSyncId, null);
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
			long albumId = DatabaseUtils.longForQuery(getDatabase(),
				"SELECT _id FROM " + Five.Music.Albums.SQL.TABLE +
				" WHERE _sync_id=" + albumSyncId, null);
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
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Songs.MIME_TYPE, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs.SOURCE_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Songs.SIZE, values);

		values.put(Five.Music.Songs.ARTIST_ID, getArtistId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.Songs.ARTIST_ID))));
		values.put(Five.Music.Songs.ALBUM_ID, getAlbumId(diffs, cursor.getLong(
			cursor.getColumnIndexOrThrow(Five.Music.Songs.ALBUM_ID))));
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
		mProvider.updateInternal(mTableUri, mTmpValues, Five.Music.Songs._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
