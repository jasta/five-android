package org.devtcg.five.provider.util;

import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class SongItem extends AbstractDAOItem
{
	private int mColumnId;
	private int mColumnSyncId;
	private int mColumnCachePath;
	private int mColumnSize;
	private int mColumnSourceId;

	public static SongItem getInstance(Context context, Uri uri)
	{
		return CREATOR.newInstance(context, uri);
	}

	public static SongItem getInstance(Cursor cursor)
	{
		return CREATOR.newInstance(cursor);
	}

	public SongItem(Cursor cursor)
	{
		super(cursor);

		/* XXX: We don't currently support all columns.  Finish later. */
		mColumnId = cursor.getColumnIndex(Five.Music.Songs._ID);
		mColumnSyncId = cursor.getColumnIndex(Five.Music.Songs._SYNC_ID);
		mColumnCachePath = cursor.getColumnIndex(Five.Music.Songs.CACHED_PATH);
		mColumnSize = cursor.getColumnIndex(Five.Music.Songs.SIZE);
		mColumnSourceId = cursor.getColumnIndex(Five.Music.Songs.SOURCE_ID);
	}

	public Uri getUri()
	{
		return ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, getId());
	}

	public long getId()
	{
		return mCursor.getLong(mColumnId);
	}

	public long getSyncId()
	{
		return mCursor.getLong(mColumnSyncId);
	}

	public String getCachePath()
	{
		return mCursor.getString(mColumnCachePath);
	}

	public long getSize()
	{
		return mCursor.getLong(mColumnSize);
	}

	public long getSourceId()
	{
		return mCursor.getLong(mColumnSourceId);
	}

	private static final AbstractDAOItem.Creator<SongItem> CREATOR =
		new AbstractDAOItem.Creator<SongItem>()
	{
		@Override
		public SongItem init(Cursor cursor)
		{
			return new SongItem(cursor);
		}
	};
}
