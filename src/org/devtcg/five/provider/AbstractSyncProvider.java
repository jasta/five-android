package org.devtcg.five.provider;

import java.io.File;

import org.devtcg.five.service.SyncContext;

import android.content.ContentProvider;
import android.content.Context;

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

	/* This is implemented through a hack to expose FiveSyncAdapter's constructor directly. */
//	public abstract AbstractSyncAdapter getSyncAdapter();

	protected abstract Iterable<? extends AbstractTableMerger> getMergers();

	public void merge(Context context, SyncContext syncContext)
	{
		Iterable<? extends AbstractTableMerger> mergers = getMergers();
		for (AbstractTableMerger merger: mergers)
		{
			merger.merge(context, syncContext, this, null);

			if (syncContext.hasCanceled() || syncContext.hasError())
				break;
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
