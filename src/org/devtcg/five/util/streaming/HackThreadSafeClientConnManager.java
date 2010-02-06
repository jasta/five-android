/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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
import java.net.Socket;

import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.DefaultClientConnection;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

/**
 * Hack to control the ClientConnection created for use with HttpClient,
 * ultimately to work around a bug in Harmony: HARMONY-6326.
 */
public class HackThreadSafeClientConnManager extends ThreadSafeClientConnManager
{
	public HackThreadSafeClientConnManager(HttpParams params, SchemeRegistry schreg)
	{
		super(params, schreg);
	}

	@Override
	protected ClientConnectionOperator createConnectionOperator(SchemeRegistry schreg)
	{
		return new HackDefaultClientConnectionOperator(schreg);
	}

	private static class HackDefaultClientConnectionOperator
			extends DefaultClientConnectionOperator {
		public HackDefaultClientConnectionOperator(SchemeRegistry schemes)
		{
			super(schemes);
		}

		@Override
		public OperatedClientConnection createConnection()
		{
			return new HackDefaultClientConnection();
		}

		private static class HackDefaultClientConnection extends DefaultClientConnection
		{
			public HackDefaultClientConnection()
			{
				super();
			}

			@Override
			public void shutdown() throws IOException
			{
				/*
				 * The work around for HARMONY-6326 is as simple as shutting
				 * down the socket prior to close.
				 */
				Socket sock = getSocket();
				try {
					try {
						sock.shutdownInput();
					} catch (IOException e) {}
					try {
						sock.shutdownOutput();
					} catch (IOException e) {}
				} catch (UnsupportedOperationException e) {}
				super.shutdown();
			}
		}
	}
}
