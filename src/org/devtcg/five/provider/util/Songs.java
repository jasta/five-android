package org.devtcg.five.provider.util;

import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class Songs
{
	public static Uri makeUri(long songId)
	{
		return ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI, songId);
	}

	public static Cursor getSong(Context context, long songId)
	{
		return context.getContentResolver().query(makeUri(songId),
				null, null, null, null);
	}
}
