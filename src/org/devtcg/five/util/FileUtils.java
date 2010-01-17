package org.devtcg.five.util;

import java.io.File;
import java.io.IOException;

public class FileUtils
{
	/**
	 * Empties the contents of a directory, but does not delete the directory
	 * itself.
	 *
	 * @throws IOException
	 *             if this function failed to delete any files in the directory.
	 */
    public static void emptyDirectory(File path) throws IOException
    {
        if (!path.exists())
            throw new IllegalArgumentException(path + " does not exist");

        if (!path.isDirectory())
            throw new IllegalArgumentException(path + " is not a directory");

        File[] files = path.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            deleteFileOrDirectory(file);
        }
    }

	/**
	 * Deletes a directory recursively.
	 *
	 * @throws IOException
	 *             if this function failed to delete any files in the directory
	 *             or the directory itself.
	 */
    public static void deleteDirectory(File path) throws IOException
    {
    	emptyDirectory(path);
    	if (!path.delete())
    		throw new IOException("failed to delete directory: " + path);
    }

	/**
	 * Delete a file or directory. If a directory, deletes all files underneath
	 * and the directory itself.
	 *
	 * @throws IOException
	 *             if this function failed to delete the file
	 *
	 * @see #emptyDirectory
	 */
    public static void deleteFileOrDirectory(File path) throws IOException
    {
    	if (path.isDirectory())
    		deleteDirectory(path);
    	else if (path.exists())
    	{
    		if (!path.delete())
    			throw new IOException("failed to delete file: " + path);
    	}
    }
}
