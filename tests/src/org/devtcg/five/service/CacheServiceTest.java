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

package org.devtcg.five.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Random;

import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.Sources;

import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.test.ServiceTestCase;

public class CacheServiceTest extends ServiceTestCase<CacheService>
{
	public CacheServiceTest()
	{
		super(CacheService.class);
	}

	@Override
	protected void setUp()
	  throws Exception
	{
		super.setUp();
	}

	public void testPreconditions() {}
	
	private Intent getIntent()
	{
		return new Intent(getContext(), CacheService.class);
	}
	
	private ICacheService getInterface()
	{
		return ICacheService.Stub.asInterface(bindService(getIntent()));
	}
	
	public void testStartable()
	{
		startService(getIntent());
	}

	public void testBindable()
	{
		IBinder service = bindService(getIntent());
	}

	public void testGetStoragePolicy()
	  throws RemoteException
	{
		ICacheService service = getInterface();
		
		CachePolicy p = service.getStoragePolicy();
		assertEquals("Default cache policy should be 100MB (this is a lame test!)",
		  p.leaveFree, (100 * 1024 * 1024));
	}

	public void testSetStoragePolicy()
	  throws RemoteException
	{
		ICacheService service = getInterface();

		boolean result =
		  service.setStoragePolicy(new CachePolicy(100 * 1024 * 1024));

		assertTrue(result);
	}
	
	public void testRecallFromCache()
	{
		String[] query =
		  new String[] { Five.Content.CACHED_PATH,
		    Five.Content.CACHED_TIMESTAMP };

		Cursor c = getContext().getContentResolver()
		  .query(Five.Content.CONTENT_URI_CACHED, query, null, null, null);
		assertNotNull(c);

		int nrecords = c.getCount();
		assertTrue("Must have cached content to complete this test.",
		  nrecords > 0);

		int record = (new Random()).nextInt(nrecords);
		assertTrue(c.moveToPosition(record));

		String path = c.getString(0);

		assertFalse(c.isNull(1));
		long timestamp = c.getLong(1);
		assertTrue(timestamp > 0);

		assertTrue("Cached file " + path + " doesn't exist.",
		  (new File(path)).exists());

		c.close();
	}

	public void testDownloadStoreAndRelease()
	  throws RemoteException, IOException
	{
		ICacheService service = getInterface();

		String[] query =
		  new String[] { Five.Content.SOURCE_ID, Five.Content.CONTENT_ID,
		    Five.Content.SIZE };

		Cursor c = getContext().getContentResolver()
		  .query(Five.Content.CONTENT_URI, query, null, null, null);
		assertNotNull(c);

		int nrecords = c.getCount();
		assertTrue("Must have content synchronized to complete this test", 
		  nrecords > 0);

		/* TODO: More thorough test than just randomly picking a record
		 * to try... */
		int record = (new Random()).nextInt(nrecords);
		assertTrue(c.moveToPosition(record));

		long sourceId = c.getLong(0);
		long contentId = c.getLong(1);
		long size = c.getLong(2);

		ParcelFileDescriptor pd =
		  service.requestStorage(sourceId, contentId);
		assertNotNull("Request storage failed (unknown reason)", pd);

		FileOutputStream out =
		  new ParcelFileDescriptor.AutoCloseOutputStream(pd);
		assertNotNull(out);

		URL url = Sources.getContentURL(getContext().getContentResolver(),
		  sourceId, contentId);		
		assertNotNull(url);

		InputStream in = url.openStream();
		assertNotNull(in);

		byte[] b = new byte[1024];
		int n;
		long total = 0;

		while ((n = in.read(b)) >= 0)
		{
			out.write(b, 0, n);
			total += n;
		}

		out.close();
		in.close();

		assertTrue("Downloaded content does not match expected size of meta data (broken MetaService?)",
		  total == size);
		
		boolean released = service.releaseStorage(sourceId, contentId);
		assertTrue("Recently stored content did not release", released);
		
		c.close();
	}
}
