/*
 * $Id$
 *
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

import java.util.ArrayList;
import java.util.List;

import org.devtcg.five.provider.AbstractSyncAdapter;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.FiveProvider;
import org.devtcg.five.provider.FiveSyncAdapter;
import org.devtcg.five.provider.util.AcquireProvider;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.provider.util.Sources;
import org.devtcg.five.util.Stopwatch;
import org.devtcg.util.CancelableThread;

import android.app.Service;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

public class MetaService extends Service
{
	public static final String TAG = "MetaService";

	volatile boolean mSyncing = false;
	SyncThread mSyncThread = null;

	IMetaObserverCallbackList mObservers;

	final SyncHandler mHandler = new SyncHandler();

	private PowerManager.WakeLock mWakeLock;

	@Override
	public void onCreate()
	{
		mObservers = new IMetaObserverCallbackList();

		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onDestroy()
	{
		mObservers.kill();
	}

	private final IMetaService.Stub mBinder = new IMetaService.Stub()
	{
		public void registerObserver(IMetaObserver observer)
		  throws RemoteException
		{
			/* Fill this new observer in our current state so they can play
			 * "catch-up". */
			if (mHandler.mSourceId >= 0)
			{
				try {
					observer.beginSync();
					observer.beginSource(mHandler.mSourceId);

					if (mHandler.mSourceN >= 0 && mHandler.mSourceD >= 0)
					{
						observer.updateProgress(mHandler.mSourceId,
						  mHandler.mSourceN, mHandler.mSourceD);
					}
				} catch (RemoteException e) {
					return;
				}
			}

			mObservers.register(observer);
		}

		public void unregisterObserver(IMetaObserver observer)
		  throws RemoteException
		{
			mObservers.unregister(observer);
		}

		public boolean isSyncing()
		  throws RemoteException
		{
			synchronized(MetaService.this)
			{
				return mSyncing;
			}
		}

		public List whichSyncing()
		  throws RemoteException
		{
			if (isSyncing() == false)
				return null;

			List which = new ArrayList(1);

			if (mHandler.mSourceId >= 0)
				which.add((Long)mHandler.mSourceId);

			return which;
		}

		public boolean startSync()
		  throws RemoteException
		{
			synchronized(MetaService.this)
			{
				if (mSyncing == true)
				{
					Log.w(MetaService.TAG, "startSync(): Already syncing...");
					return false;
				}

				mSyncing = true;
				mSyncThread = new SyncThread();
				mSyncThread.start();

				return true;
			}
		}

		public boolean stopSync()
		  throws RemoteException
		{
			synchronized(MetaService.this)
			{
				if (mSyncing == false)
				{
					Log.w(TAG, "stopSync(): Not syncing...");
					return false;
				}

				mSyncing = false;
				mSyncThread.requestCancelAndWait();

				return false;
			}
		}
	};

	private class SyncThread extends CancelableThread
	{
		private final SyncContext mContext;

		public SyncThread()
		{
			super("SyncThread");
			mContext = new SyncContext();
		}

		public void run()
		{
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			mWakeLock.acquire();
			try {
				mHandler.sendBeginSync();

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

							mHandler.sendBeginSource(sourceId);
							try {
								runSyncLoop(item);

								if (mContext.hasSuccess())
								    recordSuccess(item);
							} finally {
								mHandler.sendEndSource(sourceId);
							}
						}
					}
				} finally {
					item.close();
				}
			} finally {
				mHandler.sendEndSync();
				mWakeLock.release();
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

		    /* This happens when nothing new is detected. */
		    if (mContext.newestSyncTime == 0)
		        return;

			/*
			 * Record the most recent synced item as our revision anchor. Next
			 * time we sync, we'll search for entries newer than this.
			 *
			 * XXX: This introduces an unlikely bug where changes at the server
			 * between the merging of artist, album and song tables would not be
			 * detected on the next sync. This will be fixed in future revisions
			 * of this engine which rely on the Source REVISION column merely
			 * for user display. Each table will be versioned independently at
			 * that point.
			 */
            ContentValues v = new ContentValues();
            v.put(Five.Sources.REVISION, mContext.newestSyncTime);
            getContentResolver().update(source.getUri(), v, null, null);
		}

		@Override
		protected void onRequestCancel()
		{
			mContext.cancel();
			interrupt();
		}
	}

	public class SyncHandler extends Handler
	{
		public static final int MSG_BEGIN_SOURCE = 0;
		public static final int MSG_END_SOURCE = 1;
		public static final int MSG_UPDATE_PROGRESS = 2;
		public static final int MSG_BEGIN_SYNC = 3;
		public static final int MSG_END_SYNC = 4;

		/* Last known state messages to deliver to new registered observers
		 * so they can "catch up". */
		public volatile long mSourceId = -1;
		public volatile int mSourceN = -1;
		public volatile int mSourceD = -1;

		public void handleMessage(Message m)
		{
			switch (m.what)
			{
			case MSG_BEGIN_SYNC:
				mObservers.broadcastBeginSync();
				break;
			case MSG_END_SYNC:
				mObservers.broadcastEndSync();

				mSyncThread.joinUninterruptibly();

				synchronized(MetaService.this) {
					mSyncing = false;
					mSyncThread = null;
				}

				Log.i(TAG, "Done syncing, stopSelf()");
				stopSelf();

				break;
			case MSG_BEGIN_SOURCE:
				mSourceId = (Long)m.obj;
				mObservers.broadcastBeginSource((Long)m.obj);
				break;
			case MSG_END_SOURCE:
				mSourceId = -1;
				mObservers.broadcastEndSource((Long)m.obj);
				break;
			case MSG_UPDATE_PROGRESS:
				mSourceN = m.arg1;
				mSourceD = m.arg2;
				mObservers.broadcastUpdateProgress((Long)m.obj,
				  m.arg1, m.arg2);
				break;
			default:
				super.handleMessage(m);
			}
		}

		public void sendBeginSync()
		{
			sendMessage(obtainMessage(MSG_BEGIN_SYNC));
		}

		public void sendEndSync()
		{
			sendMessage(obtainMessage(MSG_END_SYNC));
		}

		public void sendBeginSource(long sourceId)
		{
			sendMessage(obtainMessage(MSG_BEGIN_SOURCE, (Long)sourceId));
		}

		public void sendEndSource(long sourceId)
		{
			sendMessage(obtainMessage(MSG_END_SOURCE, (Long)sourceId));
		}

		public void sendUpdateProgress(long sourceId, int N, int D)
		{
			sendMessage(obtainMessage(MSG_UPDATE_PROGRESS, N, D,
			  (Long)sourceId));
		}
	}
}
