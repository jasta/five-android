package org.devtcg.five.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

class BulkDeleteQuery
{
	private static final String TAG = "BulkDeleteQuery";

	public static final int DEFAULT_BUFFER_CAPACITY = 500;

	private final ContentResolver mContent;
	private final Uri mUri;
	private final String mColumn;

	private StringBuilder mWhere;
	private final String[] mBuffer;
	private int mSize = 0;
	
	public BulkDeleteQuery(Context context, Uri uri, String column)
	{
		this(context, uri, column, DEFAULT_BUFFER_CAPACITY);
	}

	public BulkDeleteQuery(Context context, Uri uri, String column,
	  int capacity)
	{
		mContent = context.getContentResolver();
		mUri = uri;
		mColumn = column;
		mBuffer = new String[capacity];		
		startWhere();
	}
	
	public void delete(long where)
	{
		delete(String.valueOf(where));
	}

	public void delete(String where)
	{
		mBuffer[mSize++] = where;
		mWhere.append("?,");

		if (mSize >= mBuffer.length)
			execute();
	}

	private void startWhere()
	{
		mWhere = new StringBuilder(mColumn + " IN (");
	}

	private void terminateWhere()
	{
		mWhere.setCharAt(mWhere.length() - 1, ')');
	}

	public void execute()
	{
		if (mSize == 0)
			return;
		
		terminateWhere();

		Log.i(TAG, "Executing on " + mUri + " (deleting " + mSize + ")");

		String[] buf;

		/* If we haven't filled our buffer, we have to allocate a new array
		 * of the appropriate size to avoid a SQLite exception. */
		if (mSize < mBuffer.length)
		{
			buf = new String[mSize];
			System.arraycopy(mBuffer, 0, buf, 0, mSize);
		}
		else
		{
			buf = mBuffer;
		}

		mContent.delete(mUri, mWhere.toString(), buf);

		startWhere();
		mSize = 0;
	}
}
