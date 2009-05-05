package org.devtcg.util;

import java.io.Closeable;
import java.io.IOException;

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
}
