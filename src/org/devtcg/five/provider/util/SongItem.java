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

import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class SongItem extends AbstractDAOItem
{
	private int mColumnSyncId;
	private int mColumnCachePath;
	private int mColumnSize;
	private int mColumnSourceId;
	private int mColumnMimeType;

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
		mColumnSyncId = cursor.getColumnIndex(Five.Music.Songs._SYNC_ID);
		mColumnCachePath = cursor.getColumnIndex(Five.Music.Songs.CACHED_PATH);
		mColumnSize = cursor.getColumnIndex(Five.Music.Songs.SIZE);
		mColumnSourceId = cursor.getColumnIndex(Five.Music.Songs.SOURCE_ID);
		mColumnMimeType = cursor.getColumnIndex(Five.Music.Songs.MIME_TYPE);
	}

	public Uri getUri()
	{
		return ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, getId());
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

	public String getMimeType()
	{
		return mCursor.getString(mColumnMimeType);
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
