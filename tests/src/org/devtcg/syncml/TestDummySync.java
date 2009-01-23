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

package org.devtcg.syncml;

import org.devtcg.syncml.model.*;
import org.devtcg.syncml.protocol.*;
import org.devtcg.syncml.transport.*;
import junit.framework.*;
import java.io.*;
import java.net.*;

public class TestDummySync extends TestCase
{
	public static final String BASE_URL = "http://jasta.dyndns.org:5545";
	public static final String SYNC_URL = BASE_URL + "/sync";
	public static final String META_URL = BASE_URL + "/meta";

	public TestDummySync(String s)
	{
		super(s);
	}

	public static Test suite()
	{
		return new TestSuite(TestDummySync.class);
	}

	public void testDummySync()
	{
		SyncHttpConnection server = new SyncHttpConnection(SYNC_URL);

		server.setAuthentication(SyncAuthInfo.getInstance(SyncAuthInfo.Auth.NONE));
		server.setSourceURI("IMEI:1234");

		SyncSession sess = new SyncSession(server);
		sess.open();

		try
		{
			TestMapping db = new TestMapping(server);
			sess.sync(db, SyncSession.ALERT_CODE_REFRESH_FROM_SERVER);
			assertTrue(true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail(e.toString());
		}

		sess.close();
	}

	private static class TestMapping implements DatabaseMapping
	{
		private SyncHttpConnection mConn;
		private long mNextId = 1;

		public TestMapping(SyncHttpConnection conn)
		{
			mConn = conn;
		}

		public String getName()
		{
			return "music";
		}

		public String getType()
		{
			return "application/x-fivedb";
		}

		public long getLastAnchor()
		{
			return 0;
		}

		public long getNextAnchor()
		{
			return System.currentTimeMillis() / 1000;
		}

		public void beginSyncLocal(int code, long last, long next)
		{
			System.out.println("Starting sync, code=" + code);
		}

		public void beginSyncRemote(int numChanges)
		{
			System.out.println("Preparing to receive " + numChanges + " changes...");
		}

		public void endSync(boolean updateAnchor)
		{
			System.out.println("Phew, it's over!");
		}

		public int insert(SyncItem item)
		{
			String id = item.getSourceId();
			String mime = item.getMimeType();

			System.out.println("Inserting " + id + " [" + mime + "]: " + item.getData().length  + " bytes...");

			item.setTargetId(mNextId++);

			try
			{
				if (mime.equals("application/x-fivedb-album") == true)
					downloadAlbumArtwork(id);
				else if (mime.equals("application/x-fivedb-artist") == true)
					downloadArtistPhoto(id);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				fail(e.toString());
			}

			return 201;
		}

		public int update(SyncItem item)
		{
			String id = item.getTargetId();
			String mime = item.getMimeType();

			System.out.println("Replacing " + id + " [" + mime + "]: " + item.getData().length  + " bytes...");

			return 200;
		}

		public int delete(SyncItem item)
		{
			String id = item.getTargetId();
			String mime = item.getMimeType();

			System.out.println("Deleting " + id + " [" + mime + "]: " + item.getData().length  + " bytes...");

			return 200;
		}

		public void downloadToDisk(String url, String filename)
		  throws IOException
		{
//			InputStream in = null;
//			FileOutputStream out = null;
//
//			HttpClient client = mConn.getHttpClient();
//			GetMethod get = new GetMethod(url);
//			
//			try 
//			{
//				int status = client.executeMethod(get);
//
//				if (status != HttpStatus.SC_OK)
//					return;
//
//				in = get.getResponseBodyAsStream();
//				
//				out = new FileOutputStream(filename);
//				
//				byte[] b = new byte[4096];
//				int n;
//
//				while ((n = in.read(b)) != -1)
//					out.write(b, 0, n);
//			}
//			finally
//			{
//				if (out != null)
//					out.close();
//
//				if (in != null)
//					in.close();
//
//				get.releaseConnection();
//			}
		}

		public void downloadAlbumArtwork(String id)
		  throws IOException
		{
			downloadToDisk(META_URL + "/music/album/" + id + "/artwork/large",
			  "album-" + id + ".jpg");
		}

		public void downloadArtistPhoto(String id)
		  throws IOException
		{
			downloadToDisk(META_URL + "/music/artist/" + id + "/photo/thumb",
			  "artist-" + id + ".jpg");
		}
	}
}
