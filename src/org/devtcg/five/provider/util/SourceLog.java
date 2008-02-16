package org.devtcg.five.provider.util;

import org.devtcg.five.provider.Five;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

public final class SourceLog
{
	protected static Uri buildUri(int sourceId)
	{
		return Five.Sources.CONTENT_URI.buildUpon()
		  .appendPath(String.valueOf(sourceId))
		  .appendPath("log")
		  .build();
	}
	
	protected static Uri buildUri(int sourceId, int id)
	{
		return buildUri(sourceId).buildUpon().appendPath(String.valueOf(id)).build();
	}
	
	public static Uri insertLog(ContentResolver c, int sourceId, int type, String msg)
	{
		ContentValues v = new ContentValues();
		
		v.put(Five.SourcesLog.TYPE, type);
		v.put(Five.SourcesLog.MESSAGE, msg);

		/* TODO: We need to place a limit on the number of records which can
		 * be held in the source log table at any given time.  That code 
		 * should probably be implemented here. */
		Uri uri = c.insert(buildUri(sourceId), v);

		return uri;
	}
}
