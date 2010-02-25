/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.provider;

import org.devtcg.five.service.SyncContext;
import org.devtcg.five.util.Stopwatch;

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

	private final AbstractSyncProvider mProvider;

	private final Context mContext;

	public AbstractSyncAdapter(Context context, AbstractSyncProvider provider)
	{
		mContext = context;
		mProvider = provider;
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
		AbstractSyncProvider serverDiffs = mProvider.getSyncInstance();

		int maxTries = context.numberOfTries + MAXIMUM_NETWORK_RETRIES + 1;

		Stopwatch watch = new Stopwatch();

		while (context.hasCanceled() == false && context.numberOfTries++ < maxTries)
		{
			if (context.observer != null)
				context.observer.onStatusChanged("Downloading changes...");

			Log.d(TAG, "Downloading server diffs...");

			watch.start();

			/*
			 * Download all changes since our last sync from the server.
			 */
			getServerDiffs(context, serverDiffs);

			watch.stopAndDebugElapsed(TAG, "getServerDiffs");

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
				if (context.observer != null)
					context.observer.onStatusChanged("Merging changes...");

				Log.d(TAG, "Downloaded records, merging...");
				watch.start();
				mProvider.merge(context, serverDiffs);
				watch.stopAndDebugElapsed(TAG, "serverDiffs.merge");

				if (!context.hasError())
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
			Log.i(TAG, "Sync completed successfully, processed " +
					context.numberOfDeletes + " deletes, " +
					context.numberOfInserts + " inserts, and " +
					context.numberOfUpdates + " updates");
			serverDiffs.onDestroySyncInstance();
		}
	}
}
