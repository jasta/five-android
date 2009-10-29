package org.devtcg.five.provider.util;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

public final class PlaylistMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	public PlaylistMerger(SQLiteDatabase db)
	{
		super(db, Five.Music.Playlists.SQL.TABLE, Five.Music.Playlists.CONTENT_URI);
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
		context.getContentResolver().insert(mTableUri, mTmpValues);
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		context.getContentResolver().update(mTableUri, mTmpValues, Five.Music.Playlists._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
