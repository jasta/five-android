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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.devtcg.syncml.protocol.SyncPackage;

public class SyncHttpTransport extends SyncTransport
{
	protected HttpClient mClient;
	protected HttpPost mLastMethod;
	protected HttpEntity mLastEntity;
	protected String mURI;

	private InputStream mResponse;

	public SyncHttpTransport(String uri)
	{
		this(uri, uri);
	}

	public SyncHttpTransport(String uri, String name)
	{
		super(name);
		mURI = uri;
	}

	public void open()
	{
		if (mOpened == true)
			return;

		mOpened = true;
		mClient = new DefaultHttpClient();
	}

	public void close()
	{
		if (mOpened == false)
			return;

		/* XXX */
		mOpened = false;

		if (mLastMethod != null)
			mLastMethod.abort();

		mClient.getConnectionManager().shutdown();
		mClient = null;
	}

	public void sendPackage(SyncPackage msg)
	  throws Exception
	{
		/* Lame :) */
//		if (mLastMethod != null)
//			mLastMethod.abort();

		SMLWbxmlByteArrayOutputStream out = new SMLWbxmlByteArrayOutputStream();

		XmlSerializer xs = SyncML.createSerializer();
		xs.setOutput(out, null);
		msg.write(xs);

		HttpPost post = null;

		post = new HttpPost(mURI);

		ByteArrayEntity en = new ByteArrayEntity(out.toByteArrayAvoidCopy());
		en.setContentType("application/vnd.syncml+wbxml");
		post.setEntity(en);
		
		mLastEntity = null;

		try {
			HttpResponse resp = mClient.execute(post);
			mLastEntity = resp.getEntity();
			mLastMethod = post;
		} catch (Exception e) {
			if (mLastEntity != null)
				mLastEntity.getContent().close();

			throw e;
		}
	}

	public InputStream recvPackage()
	  throws Exception
	{
		if (mLastMethod == null)
			throw new IllegalStateException("You must call sendPackage before you can receive a response");

		return mLastEntity.getContent();
	}

	public void releaseConnection()
	{
		if (mLastMethod != null)
		{
			mLastMethod.abort();
			mLastMethod = null;
		}
	}

	/**
	 * Accessor which permits callers to reuse the HTTP connection pool
	 * managed by this SyncML session.  This is useful for downloading
	 * out-of-band (non-SyncML) data from the server as part of the
	 * synchronization process.
	 */
	public HttpClient getHttpClient()
	{
		if (mOpened == false)
			throw new IllegalStateException("Open this SyncTransport first");

		if (mLastMethod != null)
			throw new IllegalStateException("Invoke releaseConnection first");

		return mClient;
	}

	/* XXX: Hack to fixup kxml2.  This should be patched upstream. */
	private static class SMLWbxmlByteArrayOutputStream extends ByteArrayOutputStream
	{
		public SMLWbxmlByteArrayOutputStream()
		{
			super();
		}

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
}
