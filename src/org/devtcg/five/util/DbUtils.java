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
