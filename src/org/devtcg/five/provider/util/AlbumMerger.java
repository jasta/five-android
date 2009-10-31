package org.devtcg.five.provider.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;

public final class AlbumMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	private final HashMap<Long, Long> mArtistSyncIds = new HashMap<Long, Long>();

	private final FiveProvider mProvider;

	public AlbumMerger(FiveProvider provider)
	{
		super(provider.getDatabase(), Five.Music.Albums.SQL.TABLE, Five.Music.Albums.CONTENT_URI);
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

	private void mergeImageColumn(Context context, Cursor cursor, long actualId)
	{
		String imageUri = cursor.getString(cursor.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK));
		if (imageUri != null)
		{
			try {
				long tmpId = cursor.getLong(cursor.getColumnIndexOrThrow(Five.Music.Albums._ID));
				File imageFile = FiveProvider.getAlbumArtwork(tmpId, true);
				if (imageFile.renameTo(FiveProvider.getAlbumArtwork(actualId, false)) == false)
					return;
			} catch (FileNotFoundException e) {
				return;
			}

			ContentValues values = mTmpValues;
			values.clear();
			values.put(Five.Music.Albums.ARTWORK,
				Five.Music.Albums.CONTENT_URI.buildUpon()
					.appendPath(String.valueOf(actualId))
					.appendPath("artwork")
					.build().toString());
			mProvider.updateInternal(ContentUris.withAppendedId(mTableUri, actualId),
				values, null, null);
		}
	}

	@Override
	public void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		rowToContentValues(diffs, diffsCursor, mTmpValues);
		Uri uri = mProvider.insertInternal(mTableUri, mTmpValues);
		if (uri != null)
			mergeImageColumn(context, diffsCursor, ContentUris.parseId(uri));
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffs, diffsCursor, mTmpValues);
		mProvider.updateInternal(mTableUri, mTmpValues, Five.Music.Albums._ID + " = ?",
			new String[] { String.valueOf(id) });
		mergeImageColumn(context, diffsCursor, id);
	}
}
