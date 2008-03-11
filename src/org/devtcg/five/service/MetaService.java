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
import java.util.Iterator;

import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.SourceLog;
import org.devtcg.five.util.ThreadStoppable;
import org.devtcg.syncml.protocol.SyncAuthInfo;
import org.devtcg.syncml.protocol.SyncSession;
import org.devtcg.syncml.transport.SyncConnection;
import org.devtcg.syncml.transport.SyncHttpConnection;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class MetaService extends Service
{
	private static final String TAG = "MetaService";

	private ThreadStoppable mSyncThread;
	private int mSyncSource = -1;
	private int mSyncProgressN = -1;
	private int mSyncProgressD = -1;
	
	private final MetaObservable mObservable = new MetaObservable();
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onDestroy()
	{
		Log.d(TAG, "onDestroy()...");

		if (syncIsActive() == true)
		{
			/* Should gracefully shut down and notify observing activities. */ 
			syncShutdown();
		}

		mObservable.unregisterAll();
		
		super.onDestroy();
	}

	public static final int MSG_BEGIN_SOURCE = 0;
	public static final int MSG_END_SOURCE = 1;
	public static final int MSG_UPDATE_PROGRESS = 2;
	public static final int MSG_BEGIN_SYNC = 3;
	public static final int MSG_END_SYNC = 4;

	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case MSG_BEGIN_SYNC:
				mObservable.notifyBeginSync();
				break;
			case MSG_END_SYNC:
				mObservable.notifyEndSync();

				try { mSyncThread.join(); }
				catch (InterruptedException e) {}

				mSyncThread = null;

				Log.d(TAG, "Done syncing, calling stopSelf()...");
				stopSelf();

				break;
			case MSG_BEGIN_SOURCE:
				mSyncSource = msg.arg1;
				mObservable.notifyBeginSource(msg.arg1);
				break;
			case MSG_END_SOURCE:
				mSyncSource = -1;
				mObservable.notifyEndSource(msg.arg1);
				break;
			case MSG_UPDATE_PROGRESS:
				mSyncProgressN = msg.arg1;
				mSyncProgressD = msg.arg2;
				mObservable.notifyUpdateProgress(mSyncSource, msg.arg1, msg.arg2);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

	private final IMetaService.Stub mBinder = new IMetaService.Stub()
	{
		public void registerObserver(IMetaObserver observer)
		{
			/* Notify this new observer that we're already mid-way through a sync. */
			if (mSyncSource != -1)
			{
				try
				{
					observer.beginSync();
					observer.beginSource(mSyncSource);

					if (mSyncProgressN != -1 && mSyncProgressD != -1)
						observer.updateProgress(mSyncSource, mSyncProgressN, mSyncProgressD);
				}
				catch (DeadObjectException e)
				{
					Log.d(TAG, "Leaving so soon?", e);
					return;
				}
			}

			mObservable.registerObserver(observer);
		}

		public void unregisterObserver(IMetaObserver observer)
		{
			mObservable.unregisterObserver(observer);
		}

		public boolean startSync()
		{
			if (syncIsActive() == true)
				return false;

			if (mSyncThread == null)
				mSyncThread = new SyncThread(getContentResolver(), mHandler);
			
			mSyncThread.start();
			return true;
		}

		public boolean stopSync()
		{
			if (syncIsActive() == false)
			{
				Log.w(TAG, "stopSync() invoked, but the sync thread is not running.");
				return false;
			}

			syncShutdown();

			return true;
		}

		public boolean isSyncing()
		{
			return syncIsActive();
		}
	};

	protected boolean syncIsActive()
	{
		if (mSyncThread != null && mSyncThread.isAlive() == true)
			return true;
		
		return false;
	}
	
	protected void syncShutdown()
	{
		if (mSyncThread == null)
			return;

		mSyncThread.interruptAndStop();
		mSyncThread = null;		
	}

	private static class SyncThread extends ThreadStoppable
	{
		private ContentResolver mContent;
		private Handler mHandler;

		public SyncThread(ContentResolver cr, Handler handler)
		{
			mContent = cr;
			mHandler = handler;
		}

		public void run()
		{
			Message msg;

			Cursor c = mContent.query(Five.Sources.CONTENT_URI,
			  new String[] { Five.Sources._ID, Five.Sources.NAME,
			    Five.Sources.HOST, Five.Sources.PORT, Five.Sources.REVISION },
			  null, null, null);

			int n;

			if ((n = c.count()) == 0)
			{
				Log.w(TAG, "No sources?");
				return;
			}

			Log.i(TAG, "Starting sync with " + n + " sources");
			
			msg = mHandler.obtainMessage(MSG_BEGIN_SYNC);
			mHandler.sendMessage(msg);

			while (c.next() == true)
			{
				String url = "http://" + c.getString(2) + ":" + c.getInt(3) + "/sync";
				
				Log.i(TAG, "Synchronizing with " + c.getString(1) +
				  " (" + url + "), " +
				  "currently at revision " + c.getInt(4) + "...");

				SyncConnection server = new SyncHttpConnection(url);
				
				SyncAuthInfo info = 
				  SyncAuthInfo.getInstance(SyncAuthInfo.Auth.NONE);
				
				server.setAuthentication(info);
				server.setSourceURI("IMEI:" + android.os.SystemProperties.get(android.telephony.TelephonyProperties.PROPERTY_IMEI));
				
				SyncSession sess = new SyncSession(server);
				sess.open();

				int sourceId = c.getInt(0);
				
				msg = mHandler.obtainMessage(MSG_BEGIN_SOURCE, sourceId, 0);
				mHandler.sendMessage(msg);

				try
				{
					MusicMapping db;
					int code;
					
					if (c.getInt(4) == 0)
						code = SyncSession.ALERT_CODE_REFRESH_FROM_SERVER;
					else
						code = SyncSession.ALERT_CODE_ONE_WAY_FROM_SERVER;
					
					db = new MusicMapping(mContent, mHandler, sourceId,
					  c.getInt(4));
					
					sess.sync(db, code);
				}
				catch (Exception e)
				{
					Log.d(TAG, "Sync failed", e);

					String log = e.getMessage();
					
					if (log == null)
						log = e.toString();
					
					/* Insert a log line so that the activity can display persistent
					 * error information to the user. */
					SourceLog.insertLog(mContent, sourceId, Five.SourcesLog.TYPE_ERROR, log);
				}
				
				sess.close();

				msg = mHandler.obtainMessage(MSG_END_SOURCE, sourceId, 0);
				mHandler.sendMessage(msg);
			}

			msg = mHandler.obtainMessage(MSG_END_SYNC);
			mHandler.sendMessage(msg);

			c.close();
		}
	}

	private static class MetaObservable
	{
		protected final ArrayList<IMetaObserver> mObservers = new ArrayList<IMetaObserver>(1);

		public MetaObservable()
		{	
		}

		public void registerObserver(IMetaObserver observer)
		{
			mObservers.add(observer);
		}

		public void unregisterObserver(IMetaObserver observer)
		{
			mObservers.remove(observer);
		}
		
		public void unregisterAll()
		{
			mObservers.clear();
		}

		public void notifyBeginSync()
		{
			Iterator<IMetaObserver> i = mObservers.iterator();
			
			while (i.hasNext() == true)
			{
				IMetaObserver o = i.next();

				try
				{
					o.beginSync();
				}
				catch (DeadObjectException e)
				{
					i.remove();
				}
			}
		}

		public void notifyEndSync()
		{
			Iterator<IMetaObserver> i = mObservers.iterator();
			
			while (i.hasNext() == true)
			{
				IMetaObserver o = i.next();

				try
				{
					o.endSync();
				}
				catch (DeadObjectException e)
				{
					i.remove();
				}
			}
		}

		public void notifyBeginSource(int sourceId)
		{
			Iterator<IMetaObserver> i = mObservers.iterator();

			while (i.hasNext() == true)
			{
				IMetaObserver o = i.next();

				try
				{
					o.beginSource(sourceId);
				}
				catch (DeadObjectException e)
				{
					i.remove();
				}
			}
		}

		public void notifyEndSource(int sourceId)
		{
			Iterator<IMetaObserver> i = mObservers.iterator();
			
			while (i.hasNext() == true)
			{
				IMetaObserver o = i.next();

				try
				{
					o.endSource(sourceId);
				}
				catch (DeadObjectException e)
				{
					i.remove();
				}
			}
		}
		
		public void notifyUpdateProgress(int sourceId, int n, int d)
		{
			Iterator<IMetaObserver> i = mObservers.iterator();
			
			while (i.hasNext() == true)
			{
				IMetaObserver o = i.next();

				try
				{
					o.updateProgress(sourceId, n, d);
				}
				catch (DeadObjectException e)
				{
					i.remove();
				}
			}
		}
	}
}
