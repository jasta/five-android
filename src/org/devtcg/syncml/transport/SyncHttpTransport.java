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

package org.devtcg.syncml.transport;

import java.net.*;
import java.io.*;

import org.xmlpull.v1.*;
import org.kxml2.wap.syncml.SyncML;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;

import org.devtcg.five.music.util.streaming.FailfastHttpClient;
import org.devtcg.syncml.protocol.SyncPackage;
import org.devtcg.syncml.protocol.SyncSession;
import org.devtcg.util.IOUtilities;

import android.util.Log;

public class SyncHttpTransport extends SyncTransport
{
	private static final String TAG = "SyncHttpTransport";

	protected HttpClient mClient;
	protected HttpPost mLastMethod;
	protected HttpEntity mLastEntity;
	protected String mURI;

	/* Special buffer used to efficiently hold the serialized 
	 * SyncML package request. */
	private SMLWbxmlByteArrayOutputStream mOut;

	public SyncHttpTransport(String uri)
	{
		this(uri, uri);
	}

	public SyncHttpTransport(String uri, String name)
	{
		super(name);
		mURI = uri;
		mClient = SyncHttpClient.getInstance();
		mOut = new SMLWbxmlByteArrayOutputStream(SyncSession.MAX_MSG_SIZE);
	}
	
	/* Leaky abstraction, lets the caller re-use this connection for
	 * other sidebar requests. */
	public HttpClient getHttpClient()
	{
		return mClient;
	}

	@Override
	public void release()
	{
		mClient.getConnectionManager().shutdown();
		mClient = null;
	}

	@Override
	public SyncPackage sendMessage(SyncPackage msg)
	  throws IOException, XmlPullParserException
	{
		mOut.reset();

		XmlSerializer xs = SyncML.createSerializer();
		xs.setOutput(mOut, null);
		msg.write(xs);

		HttpPost post = new HttpPost(mURI);

		ByteArrayEntity en =
		  new ByteArrayEntity(mOut.toByteArrayAvoidCopy());
		en.setContentType("application/vnd.syncml+wbxml");
		post.setEntity(en);

		InputStream in = null;

		try {
			HttpResponse resp = mClient.execute(post);
			HttpEntity ent = resp.getEntity();
			if (ent == null)
				throw new IOException("No entity?");

			if ((in = ent.getContent()) == null)
				throw new IOException("No content?");

			return new SyncPackage(msg.getSession(), in);
		} finally {
			if (in != null)
				IOUtilities.close(in);
		}
	}

	/* XXX: Hack to fixup kxml2.  This should be patched upstream. */
	private static class SMLWbxmlByteArrayOutputStream extends ByteArrayOutputStream
	{
		public SMLWbxmlByteArrayOutputStream() { super(); }
		public SMLWbxmlByteArrayOutputStream(int size) { super(size); }

		@Override
		public void write(int oneByte)
		{
			/* Instead of writing an unknown public ID, write the SYNCML11 id
			 * so that libsyncml can read it. */
			if (count == 1 && oneByte == 0x01)
			{
				super.write(0x9f);
				super.write(0x53);
			}
			else
			{
				super.write(oneByte);
			}
		}

		public byte[] toByteArrayAvoidCopy()
		{
			return buf;
		}
	}

	private static class SyncHttpClient extends DefaultHttpClient
	{
		private static final int CONNECT_TIMEOUT = 60000;
		private static final int READ_TIMEOUT = 60000;

		public SyncHttpClient(ClientConnectionManager conman,
		  HttpParams params)
		{
			super(conman, params);
		}

		@Override
		protected HttpRequestRetryHandler createHttpRequestRetryHandler()
		{
			return new RetryHarderHandler();
		}

		public static SyncHttpClient getInstance()
		{
			HttpParams params = new BasicHttpParams();

			// Turn off stale checking.  Our connections break all the time anyway,
			// and it's not worth it to pay the penalty of checking every time.
			HttpConnectionParams.setStaleCheckingEnabled(params, false);

			// Default connection and socket timeout of 1 minute.  Tweak to taste.
			HttpConnectionParams.setConnectionTimeout(params, CONNECT_TIMEOUT);
			HttpConnectionParams.setSoTimeout(params, READ_TIMEOUT);
			HttpConnectionParams.setSocketBufferSize(params, 8192);

			// Don't handle redirects -- return them to the caller.  Our code
			// often wants to re-POST after a redirect, which we must do ourselves.
			HttpClientParams.setRedirecting(params, false);

			SchemeRegistry schemeRegistry = new SchemeRegistry();
			schemeRegistry.register(new Scheme("http",
			  PlainSocketFactory.getSocketFactory(), 80));
			ClientConnectionManager manager =
			  new ThreadSafeClientConnManager(params, schemeRegistry);

			return new SyncHttpClient(manager, params);
		}
		
		private static class RetryHarderHandler
		  extends DefaultHttpRequestRetryHandler
		{
			private static final int INTERVALS[] =
			  { 30, 60, 120, 240, 480 };

			public RetryHarderHandler()
			{
				super(5, true);
			}

			@Override
			public boolean retryRequest(IOException exception,
			  int executionCount, HttpContext context)
			{
				boolean retry =
				  super.retryRequest(exception, executionCount, context);
				
				if (retry == true)
				{
					try {
						int waitTime = INTERVALS[executionCount - 1];
						Log.i(TAG, "Waiting " + waitTime + "ms to retry SyncML HTTP connection");
						Thread.sleep(waitTime);
					} catch (InterruptedException e) {}
				}

				return retry;
			}
		}
	}
}
