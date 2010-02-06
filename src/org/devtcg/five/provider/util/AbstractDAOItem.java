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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class AbstractDAOItem
{
	protected final Cursor mCursor;

	public AbstractDAOItem(Cursor cursor)
	{
		if (cursor == null)
			throw new IllegalArgumentException("Cursor must not be null");

		mCursor = cursor;
	}

	public void close()
	{
		mCursor.close();
	}

	public boolean moveToFirst()
	{
		return mCursor.moveToFirst();
	}

	public boolean moveToNext()
	{
		return mCursor.moveToNext();
	}

	public int getCount()
	{
		return mCursor.getCount();
	}

	public boolean isEmpty()
	{
		return mCursor.getCount() == 0;
	}

	public Cursor getCursor()
	{
		return mCursor;
	}

	protected static abstract class Creator<T extends AbstractDAOItem>
	{
		public T newInstance(Context context, Uri uri)
		{
			return newInstance(context.getContentResolver().query(uri, null, null, null, null));
		}

		public T newInstance(Cursor cursor)
		{
			if (cursor == null)
				return null;

			if (cursor.moveToFirst() == false)
			{
				cursor.close();
				return null;
			}

			return init(cursor);
		}

		public abstract T init(Cursor cursor);
	}
}
