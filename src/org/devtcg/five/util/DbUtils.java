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

package org.devtcg.five.util;

import android.database.Cursor;

public class DbUtils
{
	public static long cursorForLong(Cursor cursor, long defaultValue)
	{
		if (cursor != null)
		{
			try {
				if (cursor.moveToFirst())
					return cursor.getLong(0);
			} finally {
				cursor.close();
			}
		}

		return defaultValue;
	}
}
