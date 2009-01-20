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

package org.devtcg.five.music.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.devtcg.five.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

/**
 * Simple extension of BitmapFactory to provide a few extra convenience
 * methods.
 */
public class BetterBitmapFactory extends BitmapFactory
{
	public static Bitmap decodeUri(Context ctx, String uri)
	{
		if (uri == null)
			return null;
		
		return decodeUri(ctx, Uri.parse(uri));
	}
	
	/**
	 * Decode a Uri as a bitmap, as ImageView.setImageURI would do.
	 */
	public static Bitmap decodeUri(Context ctx, Uri uri)
	{
		InputStream in = null;

		try {
			in = ctx.getContentResolver().openInputStream(uri);
			return decodeStream(in);
		} catch (IOException e) {
			return null;
		} finally {
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {}
		}
	}

	/**
	 * Decode a Uri as a bitmap, using the supplied resource drawable
	 * as a fallback. 
	 */
	public static Bitmap decodeUriWithFallback(Context ctx, String uri, int resId)
	{
		if (uri == null)
			return decodeResource(ctx.getResources(), resId);
		else
		{
			Bitmap r = decodeUri(ctx, Uri.parse(uri));

			if (r == null)
				r = decodeResource(ctx.getResources(), resId);

			return r;
		}		
	}
	
	public static Bitmap decodeUriWithFallback(Context ctx, String uri, Bitmap fallback)
	{
		if (uri == null)
			return fallback;
		else
		{
			Bitmap r = decodeUri(ctx, Uri.parse(uri));

			if (r == null)
				return fallback;

			return r;
		}
	}
}
