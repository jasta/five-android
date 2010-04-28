package org.devtcg.five.util;

import java.io.File;
import java.io.FileNotFoundException;
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

	/**
	 * Creates a directory if it does not already exist. Throws an exception if
	 * unable to do so.
	 *
	 * @param file Path to create.
	 * @throws FileNotFoundException
	 *             If the directory could not be made or the entry already
	 *             exists but is not a directory.
	 */
	public static void mkdirIfNecessary(File file) throws FileNotFoundException
	{
		if (file.exists())
			return;

		if (!file.isDirectory() || !file.mkdirs())
		{
			throw new FileNotFoundException("Could not create directory: " +
					file.getAbsolutePath());
		}
	}
}
