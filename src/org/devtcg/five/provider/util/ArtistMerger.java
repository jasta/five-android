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

public final class ArtistMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	private final FiveProvider mProvider;

	public ArtistMerger(FiveProvider provider)
	{
		super(provider.getDatabase(), Five.Music.Artists.SQL.TABLE,
				Five.Music.Artists.SQL.DELETED_TABLE,
				Five.Music.Artists.CONTENT_URI,
				Five.Music.Artists.CONTENT_DELETED_URI);
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

	private static void rowToContentValues(Cursor cursor, ContentValues values)
	{
		values.clear();
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Artists.MBID, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Artists.NAME, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists.DISCOVERY_DATE, values);
	}

	private void mergePhotoColumn(Context context, Cursor cursor, long actualId)
	{
		String photoUri = cursor.getString(cursor.getColumnIndexOrThrow(Five.Music.Artists.PHOTO));
		if (photoUri != null)
		{
			try {
				long tmpId = cursor.getLong(cursor.getColumnIndexOrThrow(Five.Music.Artists._ID));
				File photoFile = FiveProvider.getArtistPhoto(tmpId, true);
				if (photoFile.renameTo(FiveProvider.getArtistPhoto(actualId, false)) == false)
					return;
			} catch (FileNotFoundException e) {
				return;
			}

			ContentValues values = mTmpValues;
			values.clear();
			values.put(Five.Music.Artists.PHOTO, Five.makeArtistPhotoUri(actualId).toString());
			mProvider.updateInternal(ContentUris.withAppendedId(mTableUri, actualId),
				values, null, null);
		}
	}

	@Override
	public void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		Uri uri = mProvider.insertInternal(mTableUri, mTmpValues);
		if (uri != null)
			mergePhotoColumn(context, diffsCursor, ContentUris.parseId(uri));
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		mProvider.updateInternal(mTableUri, mTmpValues, Five.Music.Artists._ID + " = ?",
			new String[] { String.valueOf(id) });
		mergePhotoColumn(context, diffsCursor, id);
	}
}
