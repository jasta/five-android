package org.devtcg.five.provider;

import java.io.File;

import org.devtcg.five.service.SyncContext;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public abstract class AbstractSyncProvider extends ContentProvider
{
	private boolean mIsTemporary;
	private File mTemporaryPath;

	protected final boolean isTemporary()
	{
		return mIsTemporary;
	}

	protected final File getTemporaryPath()
	{
		return mTemporaryPath;
	}

	public void onDestroySyncInstance()
	{
		mTemporaryPath.delete();
	}

	public abstract void close();

	public abstract SQLiteDatabase getDatabase();

	public abstract AbstractSyncAdapter getSyncAdapter();

	public abstract AbstractSyncProvider getSyncInstance();

	protected abstract Iterable<? extends AbstractTableMerger> getMergers();

	public void merge(SyncContext syncContext, AbstractSyncProvider diffs)
	{
		SQLiteDatabase db = getDatabase();
		db.beginTransaction();
		try {
			Iterable<? extends AbstractTableMerger> mergers = getMergers();
			for (AbstractTableMerger merger: mergers)
			{
				merger.merge(getContext(), syncContext, diffs, null);

				if (syncContext.hasCanceled() || syncContext.hasError())
					break;
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	protected abstract Cursor queryInternal(Uri uri, String[] project, String selection,
		String[] selectionArgs, String sortOrder);
	protected abstract Uri insertInternal(Uri uri, ContentValues values);
	protected abstract int updateInternal(Uri uri, ContentValues values, String selection,
		String[] selectionArgs);
	protected abstract int deleteInternal(Uri uri, String selection, String[] selectionArgs);

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
		String sortOrder)
	{
		return queryInternal(uri, projection, selection, selectionArgs, sortOrder);
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		SQLiteDatabase db = getDatabase();
		db.beginTransaction();
		try {
			Uri ret = insertInternal(uri, values);
			db.setTransactionSuccessful();
			return ret;
		} finally {
			db.endTransaction();
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
	{
		SQLiteDatabase db = getDatabase();
		db.beginTransaction();
		try {
			int ret = updateInternal(uri, values, selection, selectionArgs);
			db.setTransactionSuccessful();
			return ret;
		} finally {
			db.endTransaction();
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		SQLiteDatabase db = getDatabase();
		db.beginTransaction();
		try {
			int ret = deleteInternal(uri, selection, selectionArgs);
			db.setTransactionSuccessful();
			return ret;
		} finally {
			db.endTransaction();
		}
	}

	protected static abstract class Creator<T extends AbstractSyncProvider>
	{
		public abstract T newInstance();

		public T getSyncInstance(File databasePath)
		{
			T instance = newInstance();
			instance.mIsTemporary = true;
			instance.mTemporaryPath = databasePath;
			return instance;
		}
	}
}
