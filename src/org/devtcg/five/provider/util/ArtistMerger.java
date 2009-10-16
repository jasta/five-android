package org.devtcg.five.provider.util;

import org.devtcg.five.provider.AbstractTableMerger;
import org.devtcg.five.provider.Five;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;

public final class ArtistMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	public ArtistMerger()
	{
		super(Five.Music.Artists.SQL.TABLE, Five.Music.Artists.CONTENT_URI);
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

	private static void rowToContentValues(Cursor cursor, ContentValues values)
	{
		values.clear();
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Artists.MBID, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Artists.NAME, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists.DISCOVERY_DATE, values);
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
		context.getContentResolver().update(mTableUri, mTmpValues, Five.Music.Artists._ID + " = ?",
			new String[] { String.valueOf(id) });
	}
}
