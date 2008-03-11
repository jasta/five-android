/*
 * $Id$
 *
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

public final class SourceLog
{
	protected static Uri buildUri(long sourceId)
	{
		return Five.Sources.CONTENT_URI.buildUpon()
		  .appendPath(String.valueOf(sourceId))
		  .appendPath("log")
		  .build();
	}
	
	protected static Uri buildUri(long sourceId, int id)
	{
		return buildUri(sourceId).buildUpon().appendPath(String.valueOf(id)).build();
	}
	
	public static Uri insertLog(ContentResolver c, long sourceId, int type, String msg)
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
