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

package org.devtcg.five.music.util.streaming;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;

import org.apache.http.HttpServerConnection;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;

import android.util.Log;
import android.os.Process;

public abstract class LocalHttpServer extends Thread
{
	public static final String TAG = "LocalHttpServer";

	protected final HashSet<WorkerThread> mWorkers =
	  new HashSet<WorkerThread>();

	protected ServerSocket mSocket;
	protected HttpParams mParams;
	private HttpRequestHandler mReqHandler;
	
	public LocalHttpServer()
	{
		mParams = new BasicHttpParams();
		mParams
		  .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
		  .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
		  .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,
		    false)
		  .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);

		setDaemon(true);
	}

	public LocalHttpServer(int port)
	  throws IOException
	{
		this();
		bind(port);
	}

	public void bind(int port)
	  throws IOException
	{
		bind(new InetSocketAddress(InetAddress.getLocalHost(), port));
	}

	public void bind(InetSocketAddress addr)
	  throws IOException
	{
		mSocket = new ServerSocket();
		mSocket.bind(addr);
		Log.i(TAG, "Bound to port " + mSocket.getLocalPort());
	}

	public void setRequestHandler(HttpRequestHandler handler)
	{
		mReqHandler = handler;
	}

	public int getPort()
	{
		if (mSocket == null)
			throw new IllegalStateException("Not bound.");

		return mSocket.getLocalPort();
	}

	public void reset()
	{
		WorkerThread[] workersCopy;
		
		synchronized(mWorkers) {
			/* Copied because shutdown() will try to access mWorkers. */
			workersCopy =
			  mWorkers.toArray(new WorkerThread[mWorkers.size()]);
		}

		for (WorkerThread t: workersCopy)
		{
			t.shutdown();
			t.joinUninterruptibly();
		}

		assert mWorkers.isEmpty() == true;
	}

	public void shutdown()
	{
		reset();
		interrupt();

		try {
			mSocket.close();
		} catch (IOException e) {}
	}

	public void run()
	{
		if (mReqHandler == null)
			throw new IllegalStateException("Request handler not set.");
		
		if (mSocket == null)
			throw new IllegalStateException("Not bound.");
		
		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

		while (Thread.interrupted() == false)
		{
			try {
				Socket sock = mSocket.accept();
				DefaultHttpServerConnection conn =
				  new DefaultHttpServerConnection();

				conn.bind(sock, mParams);

				BasicHttpProcessor proc = new BasicHttpProcessor();
				proc.addInterceptor(new ResponseContent());
				proc.addInterceptor(new ResponseConnControl());

				HttpRequestHandlerRegistry reg =
				  new HttpRequestHandlerRegistry();				
				reg.register("*", mReqHandler);

				HttpService svc = new HttpService(proc,
				  new DefaultConnectionReuseStrategy(),
				  new DefaultHttpResponseFactory());

				svc.setParams(mParams);
				svc.setHandlerResolver(reg);

				WorkerThread t;

				synchronized(mWorkers) {
					t = new WorkerThread(svc, conn);					
					mWorkers.add(t);
				}

				t.setDaemon(true);
				t.start();
			} catch (IOException e) {
				Log.e(TAG, "I/O error initializing connection thread: " +
				  e.getMessage());
				break;
			}
		}
	}

	private class WorkerThread extends Thread
	{
		private HttpService mService;
		private HttpServerConnection mConn;

		public WorkerThread(HttpService svc, HttpServerConnection conn)
		{
			super();
			mService = svc;
			mConn = conn;
		}

		public void run()
		{
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			HttpContext ctx = new BasicHttpContext(null);

			try {
				while (isInterrupted() == false && mConn.isOpen())
					mService.handleRequest(mConn, ctx);
			} catch (Exception e) {
				Log.e(TAG, "HTTP server disrupted: " + e.toString());
			} finally {
				if (Thread.interrupted() == false) {
					try {
						mConn.shutdown();
					} catch (IOException e) {}

					synchronized(mWorkers) {
						mWorkers.remove(this);
					}
				}
			}
		}

		public void shutdown()
		{
			interrupt();

			try {
				mConn.shutdown();
			} catch (IOException e) {}
		}

		public void joinUninterruptibly()
		{
			while (true)
			{
				try {
					join();
					break;
				} catch (InterruptedException e) {}
			}
		}
	}
}
