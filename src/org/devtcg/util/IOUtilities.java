package org.devtcg.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Small collection of common stream utilities.
 */
public class IOUtilities
{
	/**
	 * Close a stream, ignoring any thrown IOException.
	 */
	public static void close(Closeable stream)
	{
		try {
			stream.close();
		} catch (IOException e) {}
	}

	public static void copyStream(InputStream in, OutputStream out)
		throws IOException
	{
		copyStream(in, out, 4096);
	}

	public static void copyStream(InputStream in, OutputStream out, int bufferSize)
		throws IOException
	{
		byte[] b = new byte[bufferSize];
		int n;

		while ((n = in.read(b)) >= 0)
			out.write(b, 0, n);
	}
}
