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
