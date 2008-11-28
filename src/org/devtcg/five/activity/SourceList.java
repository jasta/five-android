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
import java.util.List;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.service.IMetaObserver;
import org.devtcg.five.service.IMetaService;
import org.devtcg.five.service.MetaService;
import org.devtcg.five.util.DateUtils;
import org.devtcg.five.util.ServiceActivity;
import org.devtcg.five.widget.EfficientCursorAdapter;
import org.devtcg.five.widget.StatefulListView;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class SourceList extends ServiceActivity<IMetaService>
{
	private static final String TAG = "SourceList";

	private static final String[] QUERY_FIELDS = {
	  Five.Sources._ID, Five.Sources.NAME,
	  Five.Sources.REVISION, Five.Sources.LAST_ERROR };
	
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

		mCursor = managedQuery(intent.getData(), QUERY_FIELDS,
		  null, null, null);
		assert mCursor != null;

		if (mCursor.getCount() == 0)
		{
			showUI(true);
			setUI(mScreenNoSources);
		}
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

	/* TODO: We currently don't take advantage of this feature. */
	protected void onInitUI() {}

	protected void onAttached()
	{
		/* Don't clobber ScreenNoSources if its the current UI. */
		if (mScreen != null)
			return;

		setUI(mScreenNormal);
	}
	
	protected void onDetached()
	{
		if (mScreen == mScreenNormal)
			mScreenNormal.unwatchService();

		setUI(null);
	}

	public void setUI(Screen screen)
	{
		if (mScreen != null)
			mScreen.hide();

		if (screen != null)
			screen.show();

		mScreen = screen;
	}

	@Override
	protected Intent getServiceIntent()
	{
		return new Intent(this, MetaService.class);
	}
	
	protected IMetaService getServiceInterface(IBinder service)
	{
		return IMetaService.Stub.asInterface(service);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if (mScreen != null)
			return mScreen.onCreateOptionsMenu(menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		if (mScreen != null)
			return mScreen.onPrepareOptionsMenu(menu);
		
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (mScreen != null)
			return mScreen.onOptionsItemSelected(item);
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	  ContextMenu.ContextMenuInfo menuInfo)
	{
		if (mScreen != null)
			mScreen.onCreateContextMenu(menu, v, menuInfo);

		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		if (mScreen != null)
			return mScreen.onContextItemSelected(item);

		return super.onContextItemSelected(item);
	}

	private interface Screen
	{
		public int getLayout();
		public void show();
		public void hide();
		public boolean onCreateOptionsMenu(Menu menu);
		public boolean onPrepareOptionsMenu(Menu menu);
		public boolean onOptionsItemSelected(MenuItem item);
		public void onCreateContextMenu(ContextMenu menu, View v,
		  ContextMenu.ContextMenuInfo menuInfo);
		public boolean onContextItemSelected(MenuItem item);
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
		private boolean mSyncing = false;

		protected static final int MENU_SYNC = Menu.FIRST;
		protected static final int MENU_CANCEL = Menu.FIRST + 1;
		
		protected static final int MENU_EDIT = Menu.FIRST + 2;
		protected static final int MENU_REMOVE = Menu.FIRST + 3;

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
			EfficientCursorAdapter adapter = new EfficientCursorAdapter(SourceList.this,
			  R.layout.source_list_item, mCursor,
			  new String[] { Five.Sources.NAME, Five.Sources.REVISION },
			  new int[] { R.id.source_name, R.id.source_sync });
			adapter.setViewBinder(mListBinder);
			mList.setAdapter(adapter);
			mList.setOnItemClickListener(mSourceClick);
			registerForContextMenu(mList);

			watchService();
		}

		public void hide() {}

		public boolean onCreateOptionsMenu(Menu menu)
		{
			return true;
		}

		public boolean onPrepareOptionsMenu(Menu menu)
		{
			menu.clear();

			if (mSyncing == true)
				menu.add(0, MENU_CANCEL, Menu.NONE, "Stop Sync");
			else
				menu.add(0, MENU_SYNC, Menu.NONE, "Synchronize");

			return true;			
		}
		
		public boolean onOptionsItemSelected(MenuItem item)
		{
			switch (item.getItemId())
			{
			case MENU_SYNC:
				mSyncAll.performClick();
				return true;
			case MENU_CANCEL:
				menuCancelSync();
				return true;
			}
			
			return false;
		}

		public void onCreateContextMenu(ContextMenu menu, View v,
		  ContextMenu.ContextMenuInfo menuInfo)
		{
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;

			if (info.id == -1)
				return;

			Cursor c = (Cursor)mList.getAdapter().getItem(info.position);
			String name =
			  c.getString(c.getColumnIndexOrThrow(Five.Sources.NAME));
			menu.setHeaderTitle(name);

			menu.add(0, MENU_EDIT, Menu.NONE, "Edit source");
			menu.add(0, MENU_REMOVE, Menu.NONE, "Remove");
		}

		public boolean onContextItemSelected(MenuItem item)
		{
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();

			switch (item.getItemId())
			{
			case MENU_EDIT:
				SourceAdd.actionEditSource(SourceList.this, info.id);
				return true;
			case MENU_REMOVE:
				Uri uri = ContentUris.withAppendedId(Five.Sources.CONTENT_URI,
				  info.id);
				getContentResolver().delete(uri, null, null);
				
				mCursor.requery();
				
				if (mCursor.getCount() == 0)
					setUI(mScreenNoSources);
				
				return true;
			}

			return false;
		}

		public void setSourceStatus(long sourceId, String status)
		{
			if (status == null)
			{
				mStatus.remove(sourceId);
				((EfficientCursorAdapter)mList.getAdapter()).notifyDataSetChanged();
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

				String status = mStatus.get(c.getLong(0));

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

		private void menuCancelSync()
		{
			assert mService != null;

			notSyncing();

			try {
				mService.stopSync();
			} catch (RemoteException e) {
				onServiceFatal();
			}
		}

		private final OnItemClickListener mSourceClick = new OnItemClickListener()
		{
			public void onItemClick(AdapterView<?> parent, View v, int pos, long id)
			{
				Intent i = new Intent(SourceList.this, SourceInfo.class);
				i.setData(ContentUris.withAppendedId(Five.Sources.CONTENT_URI, id));
				
				startActivity(i);
			}
		};
		
		protected void notSyncing()
		{
			mSyncing = false;
			mSyncProgress.setProgress(0);
			mSyncProgress.setVisibility(View.INVISIBLE);
			mSyncAll.setEnabled(true);

			/* Clear any temporary sync-related status. */
			if (mStatus.size() > 0)
			{
				mStatus.clear();
				((EfficientCursorAdapter)mList.getAdapter())
				  .notifyDataSetChanged();
			}
		}

		protected void yesSyncing()
		{
			mSyncing = true;
			mSyncProgress.setVisibility(View.VISIBLE);
			mSyncAll.setEnabled(false);
		}

		protected void yesSyncing(List which)
		{
			yesSyncing();
			
			mStatus.clear();

			for (Object o: which)
				setSourceStatus((Long)o, "Synchronizing...");
		}

		public void watchService()
		{
			assert mService != null;

			try {
				if (mService.isSyncing() == false)
					notSyncing();
				else
					yesSyncing(mService.whichSyncing());

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

		protected static final int MENU_ADD_SERVER = Menu.FIRST;

		public int getLayout() { return LAYOUT; }

		public void show()
		{
			setContentView(LAYOUT);
			mAddServer = (Button)findViewById(R.id.next);
			mAddServer.setOnClickListener(mAddServerClick);
		}

		public void hide() {}

		public boolean onCreateOptionsMenu(Menu menu)
		{
			menu.add(0, MENU_ADD_SERVER, Menu.NONE, "Add Server");

			return true;
		}
		
		public boolean onPrepareOptionsMenu(Menu menu)
		{
			return true;
		}
		
		public boolean onOptionsItemSelected(MenuItem item)
		{
			switch (item.getItemId())
			{
			case MENU_ADD_SERVER:
				mAddServer.performClick();
				return true;
			}
			
			return false;
		}

		public boolean onContextItemSelected(MenuItem item)
		{
			return false;
		}

		public void onCreateContextMenu(ContextMenu menu, View v,
		  ContextMenuInfo menuInfo) {}

		private final OnClickListener mAddServerClick = new OnClickListener()
		{
			public void onClick(View v)
			{
				SourceAdd.actionAddSource(SourceList.this);
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
