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

import java.net.MalformedURLException;
import java.net.URL;

import org.devtcg.five.provider.Five;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public final class Sources
{
	/**
	 * Convenience method to access the HTTP URL to download content from
	 * this service.  Accesses the database to perform this operation.
	 * 
	 * @param ctx
	 *   Activity context (to use {@link android.context#getContentResolver()}).
	 *   
	 * @param sourceId
	 * @param contentId
	 * 
	 * @return
	 *   URL 
	 */
	public static URL getContentURL(ContentResolver content,
	  long sourceId, long contentId)
	{
		Uri sourceUri = ContentUris.withAppendedId(Five.Sources.CONTENT_URI, sourceId);

		Cursor c = content.query(sourceUri,
		  new String[] { Five.Sources.HOST, Five.Sources.PORT },
		  null, null, null);

		try {
			if (c.getCount() == 0)
				throw new IllegalArgumentException("No source record found for id=" + sourceId);

			c.moveToFirst();

			StringBuilder url = new StringBuilder();

			url.append("http://");
			url.append(c.getString(0));
			url.append(':');
			url.append(c.getString(1));
			url.append("/content/");
			url.append(contentId);

			try {
				return new URL(url.toString());
			} catch (MalformedURLException e) {
				throw new IllegalStateException("Database in inconsistent state: " + url);
			}
		} finally {
			c.close();
		}
	}
}
