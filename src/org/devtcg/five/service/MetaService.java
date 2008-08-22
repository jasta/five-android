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

import java.util.concurrent.atomic.AtomicBoolean;

import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.SourceLog;
import org.devtcg.syncml.transport.SyncHttpConnection;
import org.devtcg.syncml.protocol.SyncAuthInfo;
import org.devtcg.syncml.protocol.SyncSession;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;

public class MetaService extends Service
{
	public static final String TAG = "MetaService";

	volatile boolean mSyncing = false;
	SyncThread mSyncThread = null;

	final MetaServiceCallbackList mObservers =
	  new MetaServiceCallbackList();

	final SyncHandler mHandler = new SyncHandler();

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

		public boolean startSync()
		  throws RemoteException
		{
			synchronized(MetaService.this)
			{
				if (mSyncing == true)
				{
					Log.w(TAG, "startSync(): Already syncing...");
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
				mSyncThread.shutdown();

				return false;
			}
		}
	};

	private class SyncThread extends Thread
	{
		private final String[] QUERY_FIELDS = new String[] {
		  Five.Sources._ID, Five.Sources.NAME,
		  Five.Sources.HOST, Five.Sources.PORT, Five.Sources.REVISION };

		private SyncSession mActive = null;

		public void syncSource(long sourceId, String name,
		  String host, int port, long revision)
		{
			String base = "http://" + host + ":" + port;
			String url = base + "/sync";

			Log.i(TAG, "Synchronizing with " + name + " (" + url + "), " +
			  "currently at revision " + revision + "...");

			SyncHttpConnection server = new SyncHttpConnection(url);

			SyncAuthInfo info = 
			  SyncAuthInfo.getInstance(SyncAuthInfo.Auth.NONE);

			server.setAuthentication(info);

			TelephonyManager tm = (TelephonyManager)MetaService.this
			  .getSystemService(TELEPHONY_SERVICE);

			server.setSourceURI("IMEI:" + tm.getDeviceId());

			SyncSession sess = new SyncSession(server);

			synchronized(this)
			{
				mActive = sess;
				sess.open();
			}

			if (mSyncing == true)
			{
				try {
					MusicMapping db;
					int code;

					if (revision == 0)
						code = SyncSession.ALERT_CODE_REFRESH_FROM_SERVER;
					else
						code = SyncSession.ALERT_CODE_ONE_WAY_FROM_SERVER;

					db = new MusicMapping(server, base, getContentResolver(),
					  mHandler, sourceId, revision);

					sess.sync(db, code);
				} catch (Exception e) {
					/* Check if canceled before we decide to log as an
					 * error. */
					if (mSyncing == true)
					{
						Log.e(TAG, "Sync failed!", e);
					
						String log;

						if ((log = e.getMessage()) != null)
							log = e.toString();

						SourceLog.insertLog(getContentResolver(), sourceId,
						  Five.SourcesLog.TYPE_ERROR, log);
					}
				}
			}

			synchronized(this)
			{
				mActive = null;
				sess.close();
			}
		}

		public void run()
		{
			mHandler.sendBeginSync();

			ContentResolver cr = getContentResolver();

			Cursor c = cr.query(Five.Sources.CONTENT_URI, QUERY_FIELDS,
			  null, null, null);

			int n;

			if ((n = c.getCount()) == 0)
				Log.w(TAG, "No sync sources.");
			else
			{
				Log.i(TAG, "Starting sync with " + n + " sources");

				while (mSyncing == true && c.moveToNext() == true)
				{
					mHandler.sendBeginSource(c.getLong(0));

					syncSource(c.getLong(0), c.getString(1),
					  c.getString(2), c.getInt(3), c.getLong(4));

					mHandler.sendEndSource(c.getLong(0));
				}
			}

			mHandler.sendEndSync();
		}

		public void shutdown()
		{
			interrupt();

			synchronized(this)
			{
				if (mActive != null)
					mActive.close();

				mActive = null;
			}
		}
	}

	public class SyncHandler extends Handler
	{
		public static final int MSG_BEGIN_SOURCE = 0;
		public static final int MSG_END_SOURCE = 1;
		public static final int MSG_UPDATE_PROGRESS = 2;
		public static final int MSG_BEGIN_SYNC = 3;
		public static final int MSG_END_SYNC = 4;
		
		public void handleMessage(Message m)
		{
			switch (m.what)
			{
			case MSG_BEGIN_SYNC:
				mObservers.broadcastBeginSync();
				break;
			case MSG_END_SYNC:
				mObservers.broadcastEndSync();

				while (true)
				{
					try {
						mSyncThread.join();
						break;
					} catch (InterruptedException e) {}
				}
				
				synchronized(MetaService.this) {
					mSyncing = false;
					mSyncThread = null;
				}

				Log.i(TAG, "Done syncing, stopSelf()");
				stopSelf();

				break;
			case MSG_BEGIN_SOURCE:
				mObservers.broadcastBeginSource((Long)m.obj);
				break;
			case MSG_END_SOURCE:
				mObservers.broadcastEndSource((Long)m.obj);
				break;
			case MSG_UPDATE_PROGRESS:
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
	
	private static class MetaServiceCallbackList
	  extends RemoteCallbackList<IMetaObserver>
	{
		public MetaServiceCallbackList()
		{
			super();
		}
		
		public void broadcastBeginSync()
		{
			int n = beginBroadcast();
			
			for (int i = 0; i < n; i++)
			{
				try {
					getBroadcastItem(i).beginSync();
				} catch (RemoteException e) {}
			}

			finishBroadcast();
		}
		
		public void broadcastEndSync()
		{
			int n = beginBroadcast();
			
			for (int i = 0; i < n; i++)
			{
				try {
					getBroadcastItem(i).endSync();
				} catch (RemoteException e) {}
			}

			finishBroadcast();			
		}

		public void broadcastBeginSource(long sourceId)
		{
			int n = beginBroadcast();
			
			for (int i = 0; i < n; i++)
			{
				try {
					getBroadcastItem(i).beginSource(sourceId);
				} catch (RemoteException e) {}
			}

			finishBroadcast();
		}

		public void broadcastEndSource(long sourceId)
		{
			int n = beginBroadcast();
			
			for (int i = 0; i < n; i++)
			{
				try {
					getBroadcastItem(i).endSource(sourceId);
				} catch (RemoteException e) {}
			}

			finishBroadcast();
		}

		public void broadcastUpdateProgress(long sourceId, int N, int D)
		{
			int n = beginBroadcast();
			
			for (int i = 0; i < n; i++)
			{
				try {
					getBroadcastItem(i).updateProgress(sourceId, N, D);
				} catch (RemoteException e) {}
			}

			finishBroadcast();
		}
	}
}
