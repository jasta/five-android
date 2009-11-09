package org.devtcg.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

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

	/**
	 * Closes a socket in such a way that ensures release from other threads
	 * blocked on I/O from this socket. This is an upstream bug in Apache
	 * Harmony that makes this necessary.
	 */
	public static void closeSocket(Socket socket) throws IOException
	{
		try {
			socket.shutdownInput();
			socket.shutdownOutput();
		} catch (IOException e) {
			throw e;
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				throw e;
			}
		}
	}

	public static void closeSocketQuietly(Socket socket) {
		try {
			closeSocket(socket);
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
