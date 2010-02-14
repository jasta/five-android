/*
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
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

package org.devtcg.five.service;

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.activity.Settings;
import org.devtcg.five.provider.AbstractSyncAdapter;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;
import org.devtcg.five.provider.util.AcquireProvider;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.provider.util.Sources;
import org.devtcg.five.util.Stopwatch;
import org.devtcg.util.CancelableThread;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

public class MetaService extends Service
{
	public static final String TAG = "MetaService";

	private static final int NOTIF_SYNCING = 0;

	static volatile boolean sSyncing;
	SyncThread mSyncThread;

	PowerManager.WakeLock mWakeLock;

	NotificationManager mNM;

	final Handler mHandler = new Handler();

	public static void startSync(Context context)
	{
		context.startService(new Intent(Constants.ACTION_START_SYNC, null,
				context, MetaService.class));
	}

	public static void stopSync(Context context)
	{
		context.startService(new Intent(Constants.ACTION_STOP_SYNC, null,
				context, MetaService.class));
	}

	@Override
	public void onCreate()
	{
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

		mNM = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public void onStart(Intent intent, int startId)
	{
		String action = intent.getAction();

		if (Constants.ACTION_START_SYNC.equals(action))
		{
			if (sSyncing == true)
			{
				if (Constants.DEBUG)
					Log.e(Constants.TAG, "ACTION_START_SYNC, but already syncing");
			}
			else
			{
				sSyncing = true;
				mSyncThread = new SyncThread();
				mSyncThread.start();
			}
		}
		else if (Constants.ACTION_STOP_SYNC.equals(action))
		{
			if (sSyncing == false)
			{
				if (Constants.DEBUG)
					Log.e(Constants.TAG, "ACTION_STOP_SYNC, but not syncing...");
				stopSelf(startId);
			}
			else
			{
				sSyncing = false;
				mSyncThread.requestCancelAndWait();
			}
		}
	}

	public static void scheduleAutoSync(Context context)
	{
		SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
		rescheduleAutoSync(context, prefs.getLong("autosync",
				context.getResources().getInteger(R.integer.defaultAutoSyncInterval)));
	}

	public static void rescheduleAutoSync(Context context, long interval)
	{
		if (interval < 0)
			throw new IllegalArgumentException("Interval must be non-negative.");

		AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

		PendingIntent syncIntent = PendingIntent.getService(context, 0,
				new Intent(Constants.ACTION_START_SYNC, null, context, MetaService.class), 0);

		/* Cancel any previously scheduled auto-syncs. */
		am.cancel(syncIntent);

		/* Make sure that we actually have sources to sync. */
		if (Sources.isEmpty(context))
		{
			if (Constants.DEBUG)
				Log.i(Constants.TAG, "No auto-sync scheduling (no sources to sync)");
		}
		else
		{
			if (Constants.DEBUG)
				Log.i(Constants.TAG, "Rescheduling with auto sync interval of " + interval + " ms");

			am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime() + interval, interval, syncIntent);
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	public static boolean isSyncing()
	{
		return sSyncing;
	}

	private class SyncThread extends CancelableThread
	{
		private volatile SyncContext mContext;

		public SyncThread()
		{
			super("SyncThread");
		}

		public void run()
		{
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			mWakeLock.acquire();
			try {
				sendBeginSync();
				showNotification();

				SourceItem item = new SourceItem(Sources.getSources(MetaService.this));

				try {
					int count = item.getCount();

					if (count == 0)
						Log.w(TAG, "No sync sources.");
					else
					{
						Log.i(TAG, "Starting sync with " + count + " sources");

						while (hasCanceled() == false && item.moveToNext() == true)
						{
							long sourceId = item.getId();

							sendBeginSource(sourceId);
							SyncContext context = new SyncContext();
							context.observer = new SourceSyncObserver(sourceId);
							try {
								mContext = context;
								runSyncLoop(item);
								if (context.hasSuccess())
								    recordSuccess(item);

								if (context.hasError())
									context.observer.onStatusChanged(context.errorMessage);
								else
								    context.observer.onStatusChanged(null);
							} finally {
								mContext = null;
								sendEndSource(sourceId);
							}
						}
					}
				} finally {
					item.close();
				}
			} finally {
				cancelNotification();
				cleanupAndStopService();
				sendEndSync();
			}
		}

		private void runSyncLoop(SourceItem source)
		{
			AcquireProvider ap = AcquireProvider.getInstance();
			AcquireProvider.ProviderInterface client =
				ap.acquireProvider(getContentResolver(), Five.AUTHORITY);

			try {
				FiveProvider provider = (FiveProvider)client.getLocalContentProvider();
				provider.mSource = source;

				AbstractSyncAdapter adapter = provider.getSyncAdapter();

				Stopwatch watch = Stopwatch.getInstance();
				watch.start();
				adapter.runSyncLoop(mContext);
				watch.stopAndDebugElapsed(TAG, "runSyncLoop");
			} finally {
				ap.releaseProvider(getContentResolver(), client);
			}
		}

		private void recordSuccess(SourceItem source)
		{
			Log.d(TAG, "recordSuccess: newestSyncTime=" + mContext.newestSyncTime);

			/*
			 * Record the last successful sync time with the provider. This
			 * field is used only for display purposes. Normally during sync,
			 * the last modified since argument is derived from the latest
			 * synced change either in the temp provider or the main provider,
			 * or 0 if no data has been synced previously.
			 */
			ContentValues v = new ContentValues();
			v.put(Five.Sources.LAST_SYNC_TIME, System.currentTimeMillis());
			getContentResolver().update(source.getUri(), v, null, null);
		}

		public void cleanupAndStopService()
		{
			sSyncing = false;
			mSyncThread = null;

			Log.i(Constants.TAG, "Done syncing, stopping service...");
			stopSelf();

			mWakeLock.release();
		}

		@Override
		protected void onRequestCancel()
		{
			SyncContext context = mContext;
			if (context != null) {
				context.cancel();
			}
			interrupt();
		}

		private class SourceSyncObserver implements SyncObserver
		{
			private final Uri mSourceUri;
			private final ContentValues mTmpValues = new ContentValues();

			public SourceSyncObserver(long sourceId) {
				mSourceUri = Sources.makeUri(sourceId);
			}

			public void onStatusChanged(String statusMessage)
			{
				ContentValues values = mTmpValues;
				values.clear();
				values.put(Five.Sources.STATUS, statusMessage);
				getContentResolver().update(mSourceUri, values, null, null);
			}
		}
	}

	public void showNotification()
	{
		Notification n = new Notification(android.R.drawable.stat_notify_sync, null,
				System.currentTimeMillis());

		n.setLatestEventInfo(this, getString(R.string.syncing_notif_title),
				getString(R.string.syncing_notif_content), PendingIntent.getActivity(this,
						0, new Intent(this, Settings.class), 0));

		n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

		mNM.notify(NOTIF_SYNCING, n);
	}

	public void cancelNotification()
	{
		mNM.cancel(NOTIF_SYNCING);
	}

	public void sendBeginSync()
	{
		sendBroadcast(new Intent(Constants.ACTION_SYNC_BEGIN));
	}

	public void sendEndSync()
	{
		sendBroadcast(new Intent(Constants.ACTION_SYNC_END));
	}

	public void sendBeginSource(long sourceId)
	{
		sendBroadcast(new Intent(Constants.ACTION_SYNC_BEGIN)
				.putExtra(Constants.EXTRA_SOURCE_ID, sourceId));
	}

	public void sendEndSource(long sourceId)
	{
		sendBroadcast(new Intent(Constants.ACTION_SYNC_END)
				.putExtra(Constants.EXTRA_SOURCE_ID, sourceId));
	}
}
