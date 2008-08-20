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

package org.devtcg.five.activity;

import java.util.HashMap;

import org.devtcg.five.R;
import org.devtcg.five.activity.SourceAddDialog.OnSourceAddListener;
import org.devtcg.five.provider.Five;
import org.devtcg.five.service.IMetaObserver;
import org.devtcg.five.service.IMetaService;
import org.devtcg.five.service.MetaService;
import org.devtcg.five.util.DateUtils;
import org.devtcg.five.util.ServiceActivity;
import org.devtcg.five.widget.StatefulListView;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class SourceList extends ServiceActivity
{
	private static final String TAG = "SourceList";

	private static final String[] QUERY_FIELDS = {
	  Five.Sources._ID, Five.Sources.NAME,
	  Five.Sources.REVISION, Five.Sources.LAST_ERROR };	

	private IMetaService mService = null;

	private Cursor mCursor;

	private Screen mScreen;
	private final ScreenNormal mScreenNormal = new ScreenNormal();
	private final ScreenNoSources mScreenNoSources = new ScreenNoSources();

	private final ProgressHandler mHandler = new ProgressHandler();

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		Intent intent = getIntentDefaulted();

		mCursor = managedQuery(intent.getData(), QUERY_FIELDS, null, null);
		assert mCursor != null;

		if (mCursor.getCount() == 0)
			setUI(mScreenNoSources);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mService = null;
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (mScreen == mScreenNormal && mService != null)
			mScreenNormal.watchService();
	}

	@Override
	protected void onPause()
	{
		if (mScreen == mScreenNormal && mService != null)
			mScreenNormal.unwatchService();

		super.onPause();
	}

	private Intent getIntentDefaulted()
	{
		Intent i = getIntent();

		if (i.getData() == null)
			i.setData(Five.Sources.CONTENT_URI);

		if (i.getAction() == null)
			i.setAction(Intent.ACTION_VIEW);

		return i;
	}

	public void setUI(Screen screen)
	{
		if (mScreen != null)
			mScreen.hide();

		screen.show();
		mScreen = screen;
	}

	@Override
	protected Intent getServiceIntent()
	{
		return new Intent(this, MetaService.class);
	}

	public void onServiceConnected(ComponentName name, IBinder binder)
	{
		mService = IMetaService.Stub.asInterface(binder);

		runOnUiThread(new Runnable() {
			public void run() {
				/* Don't clobber ScreenNoSources if its the current UI. */
				if (mScreen != null)
					return;

				setUI(mScreenNormal);
			}
		});
	}

	public void onServiceDisconnected(ComponentName name)
	{
		onServiceFatal();
	}

	private interface Screen
	{
		public int getLayout();
		public void show();
		public void hide();
	}

	/**
	 * Encapsulate logic to handle the normal screen.
	 */
	private class ScreenNormal implements Screen
	{
		private static final int LAYOUT = R.layout.source_list;
		private StatefulListView mList;
		private ProgressBar mSyncProgress;
		private Button mSyncAll;

		/** Cache of searches on `mList' to access row positions by database id. */
		private final HashMap<Long, Integer> mViewMapCache = 
		  new HashMap<Long, Integer>();
		private final HashMap<Long, String> mStatus =
		  new HashMap<Long, String>();

		public int getLayout() { return LAYOUT; }

		public void show()
		{
			setContentView(LAYOUT);

			mSyncProgress = (ProgressBar)findViewById(R.id.sync_progress);

			mSyncAll = (Button)findViewById(R.id.source_sync_all);
			mSyncAll.setOnClickListener(mSyncAllClick);

			mList = (StatefulListView)findViewById(android.R.id.list);
			SimpleCursorAdapter adapter = new SimpleCursorAdapter(SourceList.this,
			  R.layout.source_list_item, mCursor,
			  new String[] { Five.Sources.NAME, Five.Sources.REVISION },
			  new int[] { R.id.source_name, R.id.source_sync });
			adapter.setViewBinder(mListBinder);
			mList.setAdapter(adapter);
			mList.setOnItemClickListener(mSourceClick);

			watchService();
		}

		public void hide() {}

		public void setSourceStatus(long sourceId, String status)
		{
			if (status == null)
			{
				mStatus.remove(sourceId);
				((SimpleCursorAdapter)mList.getAdapter()).notifyDataSetChanged();
			}
			else
			{
				mStatus.put(sourceId, status);

				View v = mList.getChildFromId(sourceId);

				if (v != null)
					((TextView)v.findViewById(R.id.source_sync)).setText(status);
			}
		}

		private final ViewBinder mListBinder = new ViewBinder()
		{
			public boolean bindRevision(View v, Cursor c, int col)
			{
				TextView vv = (TextView)v;

				String status = mStatus.get(c.getInt(0));

				if (status != null)
				{
					vv.setText(status);
					return true;
				}
				
				String lasterr =
				  c.getString(c.getColumnIndexOrThrow(Five.Sources.LAST_ERROR));

				if (lasterr != null)
				{
					vv.setText("Critical error, click for details.");
					return true;
				}

				int rev = c.getInt(col);

				if (rev == 0)
					vv.setText("Never synchronized.");
				else
				{
					vv.setText("Last synchronized: " + 
					  DateUtils.formatTimeAgo(rev) + ".");
				}

				return true;
			}

			public boolean setViewValue(View v, Cursor c, int col)
			{
				if (QUERY_FIELDS[col] == Five.Sources.REVISION)
					return bindRevision(v, c, col);
				
				return false;
			}
		};

		private final OnClickListener mSyncAllClick = new OnClickListener()
		{
			public void onClick(View v)
			{
				assert mService != null;

				yesSyncing();

				try {
					mService.startSync();
				} catch (RemoteException e) {
					onServiceFatal();
				}
			}
		};
		
		private final OnItemClickListener mSourceClick = new OnItemClickListener()
		{
			public void onItemClick(AdapterView parent, View v, int pos, long id)
			{
				startActivity(new Intent(Intent.ACTION_VIEW,
				  ContentUris.withAppendedId(Five.Sources.CONTENT_URI, id)));
			}
		};
		
		protected void notSyncing()
		{
			mSyncProgress.setVisibility(View.INVISIBLE);
			mSyncAll.setEnabled(true);
		}

		protected void yesSyncing()
		{
			mSyncProgress.setVisibility(View.VISIBLE);
			mSyncAll.setEnabled(false);
		}

		public void watchService()
		{
			assert mService != null;

			try {
				if (mService.isSyncing() == false)
					notSyncing();
				else
					yesSyncing();

				mService.registerObserver(mSyncObserver);
			} catch (RemoteException e) {
				onServiceFatal();
			}
		}
		
		public void unwatchService()
		{
			assert mService != null;
			
			try {
				mService.unregisterObserver(mSyncObserver);
			} catch (RemoteException e) {
				onServiceFatal();
			}
		}

		private final IMetaObserver.Stub mSyncObserver = new IMetaObserver.Stub()
		{
			private static final int MAX_UPDATE_TIME_INTERVAL = 1000;
			private static final float MAX_UPDATE_PCT_INTERVAL = 0.03f;
			private long mLastUpdateTime = 0;
			private float mLastUpdateProgress = 0f;

			public void beginSync()
			{
				Log.i(TAG, "beginSync()");
				SourceList.this.runOnUiThread(new Runnable() {
					public void run() {
						yesSyncing();
					}
				});
			}

			public void endSync()
			{
				Log.i(TAG, "endSync()");
				SourceList.this.runOnUiThread(new Runnable() {
					public void run() {
						notSyncing();
					}
				});
			}

			public void beginSource(final long sourceId)
			{
				Log.i(TAG, "beginSource: " + sourceId);
				SourceList.this.runOnUiThread(new Runnable() {
					public void run() {
						setSourceStatus(sourceId, "Synchronizing...");
					}
				});
			}

			public void endSource(final long sourceId)
			{
				Log.i(TAG, "endSource: " + sourceId);
				SourceList.this.runOnUiThread(new Runnable() {
					public void run() {						
						setSourceStatus(sourceId, null);
					}
				});
			}
			
			public void updateProgress(long sourceId, int itemNo, int itemCount)
			{
				long time = System.currentTimeMillis();
				float progress = (float)itemNo / (float)itemCount;
				
				if (mLastUpdateTime + MAX_UPDATE_TIME_INTERVAL > time &&
				    mLastUpdateProgress + MAX_UPDATE_PCT_INTERVAL > progress)
					return;

				mLastUpdateTime = time;
				mLastUpdateProgress = progress;

				mHandler.sendProgress(sourceId, itemNo, itemCount);
			}
		};
	}

	/**
	 * Encapsulate logic to handle the initial screen presented without 
	 * sources. 
	 */
	private class ScreenNoSources implements Screen
	{
		private static final int LAYOUT = R.layout.source_list_empty;
		private Button mAddServer;

		public int getLayout() { return LAYOUT; }

		public void show()
		{
			setContentView(LAYOUT);
			mAddServer = (Button)findViewById(R.id.add_server);
			mAddServer.setOnClickListener(mAddServerClick);
		}
		
		public void hide()
		{
			mAddServer = null;
		}

		private final OnClickListener mAddServerClick = new OnClickListener()
		{
			public void onClick(View v)
			{
				SourceAddDialog d = new SourceAddDialog(SourceList.this,
				  mAddSource);
				d.show();
			}
		};

		private final OnSourceAddListener mAddSource = new OnSourceAddListener()
		{
			public void sourceAdd(SourceAddDialog dialog, String name,
			  String host, String password)
			{
				mCursor.requery();

				if (mCursor.getCount() > 0)
					setUI(mScreenNormal);
			}
		};
	}

	private class ProgressHandler extends Handler
	{
		public static final int MSG_UPDATE_PROGRESS = 0;

		@Override
		public void handleMessage(Message msg)
		{
			assert mScreen == mScreenNormal;

			float scale = ((float)msg.arg1 / (float)msg.arg2) * 100F;
			mScreenNormal.mSyncProgress.setProgress((int)scale);
			mScreenNormal.setSourceStatus((Long)msg.obj, "Synchronizing: " + msg.arg1 + " of " +
			  msg.arg2 + " items...");
		}

		public void sendProgress(long sourceId, int itemNo, int itemCount)
		{
			sendMessage(obtainMessage(MSG_UPDATE_PROGRESS,
			  itemNo, itemCount, (Long)sourceId));
		}
	}
}
