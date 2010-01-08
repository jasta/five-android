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

package org.devtcg.five.util;

import java.lang.ref.SoftReference;
import java.util.HashMap;

import org.devtcg.five.widget.FastBitmapDrawable;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

/**
 * Simple mem cache utility for artist photos and album artwork.
 */
public class ImageMemCache
{
	private final HashMap<Long, SoftReference<FastBitmapDrawable>> mCache =
	  new HashMap<Long, SoftReference<FastBitmapDrawable>>();
	
	private FastBitmapDrawable mFallback;
	
	/* Special drawable indicating that the fallback should be used.  This
	 * is stored in the cache in place of the fallback itself so that
	 * retroactive changes can be made to the fallback parameter. */
	private final FastBitmapDrawable NULL_DRAWABLE =
	  new FastBitmapDrawable(null);

	public ImageMemCache() {}

	public ImageMemCache(FastBitmapDrawable fallback)
	{
		setFallback(fallback);
	}

	public ImageMemCache(Bitmap fallback)
	{
		setFallback(fallback);
	}
	
	/**
	 * Completely wipe the cache.
	 */
	public void purge()
	{
		mCache.clear();
	}

	/**
	 * The cached entries stored here represent Drawable primitives which
	 * can be attached to views and associated with an Android context.
	 * To avoid leaks, you must call cleanup to disassociate all 
	 * bitmap drawables.  This normally would occur during an activity's
	 * {@link Activity#onDestroy} method.
	 */
	public void cleanup()
	{
		if (mFallback != null)
			mFallback.setCallback(null);

		for (SoftReference<FastBitmapDrawable> ref: mCache.values())
		{
			FastBitmapDrawable d = ref.get();
			if (d != null)
				d.setCallback(null);
		}
	}

	/**
	 * Sets the fallback drawable to return from fetch methods when they would
	 * otherwise return null.  This mechanism is provided both as an
	 * optimization and a convenience.
	 */
	public void setFallback(FastBitmapDrawable fallback)
	{
		mFallback = fallback;
	}

	public void setFallback(Bitmap fallback)
	{
		setFallback(new FastBitmapDrawable(fallback));
	}

	public void setFallback(Resources r, int resId)
	{
		setFallback(BitmapFactory.decodeResource(r, resId));
	}
	
	public FastBitmapDrawable getFallback()
	{
		return mFallback;
	}

	/**
	 * Lookup an entry in the cache.  Returns null if the entry is not
	 * in the cache, however do note that it may also return the fallback
	 * drawable in the event that a fetch method previously returned
	 * it for this identifier.
	 */
	public FastBitmapDrawable get(Long id)
	{
		SoftReference<FastBitmapDrawable> ref = mCache.get(id);
		if (ref != null)
		{
			FastBitmapDrawable d = ref.get();
			if (d == NULL_DRAWABLE)
				return mFallback;
			
			return d;
		}

		return null;
	}
	
	/**
	 * Add an entry directly to the cache, replacing any existing record.
	 */
	public void put(Long id, FastBitmapDrawable d)
	{
		if (d == null)
			throw new IllegalArgumentException("Parameter `d' must not be null: perhaps you want NULL_DRAWABLE?");

		mCache.put(id, new SoftReference<FastBitmapDrawable>(d));
	}
	
	/**
	 * Remove an entry directly from the cache.
	 */
	public void remove(Long id)
	{
		mCache.remove(id);
	}

	protected FastBitmapDrawable internalFetch(Long id, Bitmap bmp)
	{
		if (bmp != null)
		{
			/* XXX: We're making a fairly large assumption here.  Fix soon. */
			bmp.setDensity(160);

			FastBitmapDrawable d = new FastBitmapDrawable(bmp);
			put(id, d);
			return d;
		}
		else
		{
			put(id, NULL_DRAWABLE);
			return mFallback;
		}				
	}

	public FastBitmapDrawable fetchFromDisk(Long id, String path)
	{
		FastBitmapDrawable cached = get(id);
		if (cached != null)
			return cached;

		return internalFetch(id, BitmapFactory.decodeFile(path));
	}

	protected FastBitmapDrawable internalFetchFromUri(Long id, Context ctx, Uri uri)
	{
		return internalFetch(id, BetterBitmapFactory.decodeUri(ctx, uri));
	}

	/**
	 * Load a FastBitmapDrawable either from the cache or from the
	 * content provider (storing the result in the cache for subsequent
	 * access).
	 */
	public FastBitmapDrawable fetchFromUri(Long id, Context ctx, Uri uri)
	{
		FastBitmapDrawable cached = get(id);
		if (cached != null)
			return cached;

		return internalFetchFromUri(id, ctx, uri);
	}

	/**
	 * Alternative to {@link fetchFromUri} which reads a path from a
	 * database column.  Normal Android convention applies where the
	 * literal value of the column should be a Uri reference which can
	 * be opened via a local content provider.
	 * 
	 * This method is a performance win over manually calling
	 * {@link fetchFromUri} as the cursor column won't be copied if
	 * a cache entry is found.
	 */
	public FastBitmapDrawable fetchFromDatabase(Long id, Context ctx,
	  Cursor c, int uriIdx)
	{
		FastBitmapDrawable cached = get(id);
		if (cached != null)
			return cached;

		String uri = c.getString(uriIdx);
		if (uri == null)
		{
			put(id, NULL_DRAWABLE);
			return mFallback;
		}

		return internalFetchFromUri(id, ctx, Uri.parse(uri));
	}
}
