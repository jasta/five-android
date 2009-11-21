package org.devtcg.five.service;

/**
 * Holds meta information about a sync as its happening.
 */
public class SyncContext
{
	public int numberOfTries;
	public boolean moreRecordsToGet;

	public int numberOfInserts;
	public int numberOfDeletes;
	public int numberOfUpdates;

	/**
	 * Holds the largest (most recent) sync time of all the feeds being merged.
	 *
	 * @deprecated we derive this value from queries during sync now, so no need
	 *             to track it during sync and store it anywhere.
	 */
	public long newestSyncTime;

	private volatile boolean hasCanceled;
	public boolean networkError;
	public String errorMessage;

	/**
	 * Attached to from within getServerDiffs() to provide a way to immediately
	 * release blocking I/O operations.
	 */
	public volatile CancelTrigger trigger;

	/**
	 * Optional observer to track progress during synchronization.
	 */
	public SyncObserver observer;

	public int getTotalRecordsProcessed()
	{
		return numberOfInserts + numberOfDeletes + numberOfUpdates;
	}

	public boolean hasSuccess()
	{
		return hasCanceled() == false && hasError() == false;
	}

	public boolean hasCanceled()
	{
		return hasCanceled;
	}

	public void cancel()
	{
		hasCanceled = true;

		if (trigger != null)
			trigger.onCancel();
	}

	public boolean hasError()
	{
		return networkError;
	}

	public interface CancelTrigger
	{
		public void onCancel();
	}
}
