/*
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
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

public final class Sources
{
	public static Uri makeUri(long sourceId)
	{
		return ContentUris.withAppendedId(Five.Sources.CONTENT_URI, sourceId);
	}

	public static Cursor getSources(Context context)
	{
		return context.getContentResolver().query(Five.Sources.CONTENT_URI,
			null, null, null, null);
	}

	public static void deleteSource(Context context, long sourceId)
	{
		/* TODO: Implement this by just gutting the tables and deleting /sdcard/five. */
		throw new UnsupportedOperationException();
	}

	public static boolean isEmpty(Context context)
	{
		Cursor cursor = getSources(context);
		try {
			return cursor.getCount() == 0;
		} finally {
			cursor.close();
		}
	}
}
