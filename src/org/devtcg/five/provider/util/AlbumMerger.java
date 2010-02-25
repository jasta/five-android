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
		super(provider.getDatabase(), Five.Music.Albums.SQL.TABLE,
				Five.Music.Albums.SQL.DELETED_TABLE,
				Five.Music.Albums.CONTENT_URI,
				Five.Music.Albums.CONTENT_DELETED_URI);
		mProvider = provider;
	}

	@Override
	public void notifyChanges(Context context)
	{
		/* PlaylistSongs merger will do this for everyone after counts are updated. */
	}

	@Override
	public void deleteRow(Context context, ContentProvider diffs, Cursor localCursor)
	{
		mProvider.deleteInternal(ContentUris.withAppendedId(mTableUri,
				localCursor.getLong(localCursor.getColumnIndexOrThrow(Five.Music.Artists._ID))),
				null, null);
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

	private void mergeImageColumns(Context context, Cursor cursor, long actualId)
	{
		ContentValues values = mTmpValues;
		values.clear();

		long tmpId = cursor.getLong(cursor.getColumnIndexOrThrow(Five.Music.Albums._ID));

		String thumbUri = cursor.getString(cursor.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK));
		if (thumbUri != null)
		{
			try {
				File imageFile = FiveProvider.getAlbumArtwork(tmpId, true);
				if (imageFile.renameTo(FiveProvider.getAlbumArtwork(actualId, false)))
					values.put(Five.Music.Albums.ARTWORK, Five.makeAlbumArtworkUri(actualId).toString());
			} catch (FileNotFoundException e) {
			}
		}

		String bigUri = cursor.getString(cursor.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK_BIG));
		if (bigUri != null)
		{
			try {
				File imageFile = FiveProvider.getLargeAlbumArtwork(tmpId, true);
				if (imageFile.renameTo(FiveProvider.getLargeAlbumArtwork(actualId, false)))
					values.put(Five.Music.Albums.ARTWORK_BIG, Five.makeAlbumArtworkBigUri(actualId).toString());
			} catch (FileNotFoundException e) {
			}
		}

		if (values.size() > 0)
		{
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
			mergeImageColumns(context, diffsCursor, ContentUris.parseId(uri));
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffs, diffsCursor, mTmpValues);
		mProvider.updateInternal(mTableUri, mTmpValues, Five.Music.Albums._ID + " = ?",
			new String[] { String.valueOf(id) });
		mergeImageColumns(context, diffsCursor, id);
	}
}
