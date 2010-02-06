/*
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

package org.devtcg.five.util.streaming;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import android.util.Log;

public class LocalHttpServerTest extends TestCase
{
	public static final String TAG = "LocalHttpServerTest";

	public MyHttpServer startMyHttpServer()
	  throws IOException
	{
		int port = 1024 + (new Random()).nextInt(1000);

		Log.v(TAG, "Starting server on port " + port);

		MyHttpServer server = new MyHttpServer(port);
		server.start();

		return server;
	}

	public void testServerQuick()
	  throws IOException
	{
		LocalHttpServer server = startMyHttpServer();
		server.shutdown();
	}

	public void testServerResponse()
	  throws IOException
	{
		LocalHttpServer server = startMyHttpServer();

		try {
			String url = "http://127.0.0.1:" + server.getPort() + "/testing/1/2/3";
			HttpClient cli = new DefaultHttpClient();
			HttpGet method = new HttpGet(url);

			HttpResponse resp = cli.execute(method);

			StatusLine status = resp.getStatusLine();
			assertEquals(status.getStatusCode(), HttpStatus.SC_OK);

			HttpEntity ent = resp.getEntity();
			assertNotNull(ent);

			InputStream in = ent.getContent();

			byte[] b = new byte[2048];
			int n;
			long recvd = 0;

			while ((n = in.read(b)) >= 0)
				recvd += n;

			in.close();

			assertTrue(recvd > 0);
		} finally {
			server.shutdown();
		}
	}

	public static class MyHttpServer extends LocalHttpServer
	{
		public MyHttpServer(int port)
		  throws IOException
		{
			super(port);
			setRequestHandler(mHttpHandler);
		}

		private final HttpRequestHandler mHttpHandler = new HttpRequestHandler()
		{
			public void handle(HttpRequest request, HttpResponse response,
			  HttpContext context)
			  throws HttpException, IOException
			{
				RequestLine reqLine = request.getRequestLine();
				Log.v(TAG, "reqLine=" + reqLine);

				String method = reqLine.getMethod().toUpperCase(Locale.ENGLISH);
				if (method.equals("GET") == false)
				{
					throw new MethodNotSupportedException(method +
					  " method not supported");
				}

				response.setEntity(new MyBoilerPlateEntity());
				response.setStatusCode(HttpStatus.SC_OK);
			}
		};

		public static class MyBoilerPlateEntity extends EntityTemplate
		{
			public MyBoilerPlateEntity()
			{
				super(new ContentProducer() {
					public void writeTo(OutputStream out)
					  throws IOException
					{
						OutputStreamWriter w =
						  new OutputStreamWriter(out, "UTF-8");

						w.write("<html><body>");
						w.write("<h1>Success!</h1>");
						w.write("</body></html>");
						w.flush();
					}
				});

				setContentType("text/html; charset=UTF-8");
			}
		}
	}
}
