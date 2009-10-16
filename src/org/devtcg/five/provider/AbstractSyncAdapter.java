package org.devtcg.five.provider;

import org.devtcg.five.service.SyncContext;

import android.content.Context;
import android.util.Log;

public abstract class AbstractSyncAdapter
{
	private static final String TAG = "AbstractSyncAdapter";

	/**
	 * Number of I/O errors that will be tolerated before we abort the sync with
	 * an error.
	 */
	private static int MAXIMUM_NETWORK_RETRIES = 3;

	/**
	 * Array of retry intervals. This length of time is slept between each
	 * successive retry.
	 */
	private static final int[] NETWORK_RETRY_DELAY = new int[] {
		5000, 30000, 50000
	};

	private final Context mContext;
	private final TemporarySyncProviderFactory<? extends AbstractSyncProvider> mFactory;

	public AbstractSyncAdapter(Context context, TemporarySyncProviderFactory<? extends AbstractSyncProvider> factory)
	{
		mContext = context;
		mFactory = factory;
	}

	public Context getContext()
	{
		return mContext;
	}

	public abstract void getServerDiffs(SyncContext context, AbstractSyncProvider serverDiffs);

	/**
	 * Run the main sync loop. Some effort was made to make this similar to
	 * Google's own sync engine for Android, and as such well generalized.
	 * My attempt has only partially succeeded and there is quite a bit of
	 * leak abstraction in this design taking advantage of the fact that we
	 * only have 1 provider to sync always.
	 */
	public void runSyncLoop(SyncContext context)
	{
		AbstractSyncProvider serverDiffs = mFactory.getSyncInstance(mContext);

		int maxTries = context.numberOfTries + MAXIMUM_NETWORK_RETRIES + 1;

		while (context.hasCanceled() == false && context.numberOfTries++ < maxTries)
		{
			Log.d(TAG, "Downloading server diffs...");

			/*
			 * Download all changes since our last sync from the server.
			 */
			getServerDiffs(context, serverDiffs);

			if (context.hasCanceled() == true)
				break;

			if (context.hasError())
			{
				/*
				 * If we are going to retry, apply a short delay while we still
				 * have the wake lock. This is designed as a way to avoid the
				 * more expensive AlarmManager to schedule a retry in such a
				 * short window of time. If we fail up to our allowed retry
				 * limit we will be forced to reschedule another try later.
				 */
				if (context.numberOfTries < maxTries)
				{
					try {
						int retryIndex = (MAXIMUM_NETWORK_RETRIES -
							(maxTries - context.numberOfTries));
						Thread.sleep(NETWORK_RETRY_DELAY[retryIndex]);
					} catch (InterruptedException e) {}
				}

				continue;
			}

			/*
			 * No more network work left, go ahead and fold the temporary
			 * serverDiffs provider into the main one.
			 */
			if (!context.moreRecordsToGet)
			{
				Log.d(TAG, "Downloaded records, merging...");
				serverDiffs.merge(mContext, context);
				Log.d(TAG, "Successfully merged " + context.getTotalRecordsProcessed() + " records!");
				break;
			}
		}

		serverDiffs.close();

		if (context.hasCanceled() == true)
			Log.i(TAG, "Sync canceled");
		else if (context.hasError())
			Log.i(TAG, "Sync aborted with errors, will try again later...");
		else
		{
			Log.i(TAG, "Sync completed successfully, total records processed: " +
				context.getTotalRecordsProcessed());
			serverDiffs.onDestroySyncInstance();
		}
	}

	public interface TemporarySyncProviderFactory<T extends AbstractSyncProvider>
	{
		public T getSyncInstance(Context context);
	}
}
