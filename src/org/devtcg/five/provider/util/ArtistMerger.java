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
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public final class ArtistMerger extends AbstractTableMerger
{
	private final ContentValues mTmpValues = new ContentValues();

	public ArtistMerger(SQLiteDatabase db)
	{
		super(db, Five.Music.Artists.SQL.TABLE, Five.Music.Artists.CONTENT_URI);
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
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists._SYNC_ID, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists._SYNC_TIME, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Artists.MBID, values);
		DatabaseUtils.cursorStringToContentValues(cursor, Five.Music.Artists.NAME, values);
		DatabaseUtils.cursorLongToContentValues(cursor, Five.Music.Artists.DISCOVERY_DATE, values);
	}

	private void mergePhotoColumn(Context context, Cursor cursor, long id)
	{
		String photoUri = cursor.getString(cursor.getColumnIndexOrThrow(Five.Music.Artists.PHOTO));
		if (photoUri != null)
		{
			try {
				File photoFile = FiveProvider.getArtistPhoto(id, true);
				if (photoFile.renameTo(FiveProvider.getArtistPhoto(id, false)) == false)
					return;
			} catch (FileNotFoundException e) {
				return;
			}

			ContentValues values = mTmpValues;
			values.clear();
			values.put(Five.Music.Artists.PHOTO,
				Five.Music.Artists.CONTENT_URI.buildUpon()
					.appendPath(String.valueOf(id))
					.appendPath("photo")
					.build().toString());
			context.getContentResolver().update(ContentUris.withAppendedId(mTableUri, id),
				values, null, null);
		}
	}

	@Override
	public void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		Uri uri = context.getContentResolver().insert(mTableUri, mTmpValues);
		if (uri != null)
			mergePhotoColumn(context, diffsCursor, ContentUris.parseId(uri));
	}

	@Override
	public void updateRow(Context context, ContentProvider diffs, long id, Cursor diffsCursor)
	{
		rowToContentValues(diffsCursor, mTmpValues);
		context.getContentResolver().update(mTableUri, mTmpValues, Five.Music.Artists._ID + " = ?",
			new String[] { String.valueOf(id) });
		mergePhotoColumn(context, diffsCursor, id);
	}
}
