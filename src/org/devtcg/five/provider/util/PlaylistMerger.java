package org.devtcg.five.provider.util;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;

public final class PlaylistMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	private final FiveProvider mProvider;

	public PlaylistMerger(FiveProvider provider)
	{
		super(provider.getDatabase(), Five.Music.Playlists.SQL.TABLE, Five.Music.Playlists.CONTENT_URI);
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

	private static void rowToContentValues(Cursor cursor, ContentValues values)
	{
		values.clear();
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Playlists._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Playlists._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Playlists.NAME, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Playlists.CREATED_DATE, values);
	}

	@Override
	public void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		mProvider.insertInternal(mTableUri, mTmpValues);
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		mProvider.updateInternal(mTableUri, mTmpValues, Five.Music.Playlists._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
