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
import org.devtcg.five.provider.Five;
import org.devtcg.five.service.IMetaObserver;
import org.devtcg.five.service.IMetaService;
import org.devtcg.five.service.MetaService;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.os.*;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.animation.*;
import android.view.animation.Animation.AnimationListener;
import android.widget.*;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class SourceList extends Activity
{
	private static final String TAG = "SourceList";

	private static final int MENU_SYNC = Menu.FIRST;

	private SimpleCursorAdapter mListAdapter;
	private Cursor mCursor;

	private static final String[] PROJECTION = {
	  Five.Sources._ID, Five.Sources.NAME,
	  Five.Sources.REVISION, Five.Sources.LAST_ERROR };

	private IMetaService mService;

	private ViewSwitcher mSwitcher;
	private ProgressBar mProgress;
	private Button mSyncAll;
	private boolean mSyncing;

	private HashMap<Integer, String> mStatus = new HashMap<Integer, String>();

    @Override
    public void onCreate(Bundle icicle)
    {
    	Log.d(TAG, "!!!!!! onCreate");

        super.onCreate(icicle);
        setTitle(R.string.source_list_title);
        setContentView(R.layout.source_list);

        /* Work around a lame bug in M5 that causes the second progress bar animation to
         * not work correctly. */
        ((ViewGroup)findViewById(R.id.source_list_top)).setAnimationCacheEnabled(false);

        mSwitcher = (ViewSwitcher)findViewById(R.id.loading_switcher);

        mSwitcher.setInAnimation(AnimationUtils.loadAnimation(this,
          android.R.anim.fade_in));
        mSwitcher.setOutAnimation(AnimationUtils.loadAnimation(this,
          android.R.anim.fade_out));

        Intent intent = getIntent();
        if (intent.getData() == null)
        	intent.setData(Five.Sources.CONTENT_URI);

        if (intent.getAction() == null)
        	intent.setAction(Intent.VIEW_ACTION);

        mCursor = managedQuery(intent.getData(), PROJECTION, null, null);
        assert mCursor != null;

        ListView list = (ListView)findViewById(android.R.id.list);

//        View footer = getViewInflate().inflate(R.layout.source_footer,
//          null, false, null);
//        list.addFooterView(footer, null, false);

        mProgress = (ProgressBar)findViewById(R.id.sync_progress);
        mSyncAll = (Button)findViewById(R.id.source_sync_all);

        mSyncAll.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				menuSync();
			}
        });

        mListAdapter = new SimpleCursorAdapter(this,
        	R.layout.source_list_item,
        	mCursor,
        	new String[] { Five.Sources.NAME, Five.Sources.REVISION },
        	new int[] { R.id.source_name, R.id.source_sync }
        );

        mListAdapter.setViewBinder(new ViewBinder()
        {
			public boolean setViewValue(View view, Cursor cursor, int column)
			{
				if (cursor.getColumnIndex(Five.Sources.REVISION) != column)
					return false;

				TextView revText = (TextView)view;
				String status = mStatus.get(cursor.getInt(0));

				if (status != null)
				{
					revText.setText(status);
				}
				else
				{
					String lasterr = cursor.getString(cursor.getColumnIndex(Five.Sources.LAST_ERROR));

					if (lasterr != null)
						revText.setText("Critical error, click for details.");
					else
					{
						int rev = cursor.getInt(column);

						if (rev == 0)
							revText.setText("Never synchronized.");
						else
						{
							revText.setText("Last synchronized: " +
							  DateUtils.formatTimeAgo(rev) + ".");
						}
					}
				}

				return true;
			}
        });
        
        list.setOnItemClickListener(mOnClick);
        list.setAdapter(mListAdapter);
    }
    
    @Override
    public void onResume()
    {
    	/* We don't know if we're syncing until we connect to the service. */
    	mSyncing = false;
    	mSwitcher.setDisplayedChild(0);

    	Log.d(TAG, "!!!!!! onResume");

        if (mCursor.count() > 0)
        {
        	findViewById(android.R.id.empty).setVisibility(View.GONE);
        	findViewById(android.R.id.list).setVisibility(View.VISIBLE);
        }
        else
        {
        	findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
        	findViewById(android.R.id.list).setVisibility(View.GONE);
        }

        bindService();
    	super.onResume();
    }
    
    private void bindService()
    {
    	Intent meta = new Intent(this, MetaService.class);

    	try
    	{
    		ComponentName name = startService(meta, null);
    		
    		if (name == null)
    			throw new IllegalStateException("Couldn't start service");

    		boolean bound = bindService(new Intent().setComponent(name),
   		      mConnection, BIND_AUTO_CREATE);
    		
    		if (bound == false)
    			throw new IllegalStateException("Couldn't bind to service");
    	}
    	catch (IllegalStateException e)
    	{
   			mSwitcher.showNext();

    		Log.e(TAG, "Damn", e);
    		Toast.makeText(this, "CRITICAL: Failure to connect to service",
    		  Toast.LENGTH_LONG).show();
    	}    	
    }
    
    private void unbindService()
    {
    	if (mService != null)
    	{
    		try { mService.unregisterObserver(mObserver); }
    		catch (DeadObjectException e) { }
    	}

    	unbindService(mConnection);
    	mService = null;    	
    }

    @Override
    public void onPause()
    {
    	Log.d(TAG, "!!!!!! onPause");

    	unbindService();
    	super.onPause();    	
    }

    private void presentUI()
    {
    	if (mSwitcher.getDisplayedChild() == 0)
    		mSwitcher.showNext();
    }

    private Runnable mPresentUI = new Runnable()
    {
		public void run()
		{
			presentUI();
		}
    };
    
	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			float scale = ((float)msg.arg1 / (float)msg.arg2) * 100F;
			mProgress.setProgress((int)scale);

			mStatus.put(msg.what, "Synchronizing: " + msg.arg1 + " of " + msg.arg2 + " items...");
			mListAdapter.notifyDataSetChanged();
		}
	};

    private ServiceConnection mConnection = new ServiceConnection()
    {
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			mService = IMetaService.Stub.asInterface(service);

			Log.d(TAG, "Attempting to register with service...");

			try
			{
				if (mService.isSyncing() == false)
					mHandler.post(mPresentUI);

				mService.registerObserver(mObserver);
			}
			catch (DeadObjectException e)
			{
				Log.e(TAG, "What the hell happened here?", e);
				mService = null;

				mHandler.post(mPresentUI);
			}
		}

		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "onServiceDisconnected: Where did it go?  Should we retry?  Hmm.");
			mService = null;

			mHandler.post(mPresentUI);
		}
    };
    
    private IMetaObserver.Stub mObserver = new IMetaObserver.Stub()
    {
    	private long lastUpdateTime = 0;
    	private float lastUpdateProgress = 0.00f;
    	
		public void beginSync()
		{
			Log.d(TAG, "beginSync");
			
			mHandler.post(new Runnable() {
				public void run()
				{
					if (mSyncing == false)
						startSyncUI(false);

					presentUI();
				}
			});
		}

		public void endSync()
		{
			Log.d(TAG, "endSync");

			mHandler.post(new Runnable() {
				public void run()
				{
					stopSyncUI();
				}
			});
		}

		public void beginSource(final int sourceId)
		{
			Log.d(TAG, "beginSource: " + sourceId);

			mHandler.post(new Runnable() {
				public void run()
				{
					mStatus.put(sourceId, "Synchronizing...");
					mListAdapter.notifyDataSetChanged();
				}
			});
		}

		public void endSource(final int sourceId)
		{
			Log.d(TAG, "endSource: " + sourceId);

			mHandler.post(new Runnable() {
				public void run()
				{
					mStatus.remove(sourceId);
					mListAdapter.notifyDataSetChanged();
				}
			});
		}

		public void updateProgress(int sourceId, int itemNo, int itemCount)
		{
			Log.d(TAG, "updateProgress: " + sourceId + " (" + itemNo + " / " + itemCount + ")");

			long time = System.currentTimeMillis();
			float progress = (float)itemNo / (float)itemCount;

			if (lastUpdateTime + 1500 > time &&
			    lastUpdateProgress + 0.03f > progress)
				return;

			lastUpdateTime = time;
			lastUpdateProgress = progress;
			
			Message msg = mHandler.obtainMessage(sourceId, itemNo, itemCount);
			mHandler.sendMessage(msg);
		}
    };
    
    private AdapterView.OnItemClickListener mOnClick = new AdapterView.OnItemClickListener()
    {
		public void onItemClick(AdapterView parent, View v, int pos, long id)
		{
			startActivity(new Intent(Intent.VIEW_ACTION,
			  ContentUris.withAppendedId(Five.Sources.CONTENT_URI, id)));
		}
    };
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	
    	menu.add(0, MENU_SYNC, "Synchronize");
    	
    	return true;
    }

    protected void startSyncUI()
    {
    	startSyncUI(true);
    }

    protected void startSyncUI(boolean animation)
    {
    	mSyncing = true;
    	mSyncAll.setEnabled(false);

    	mProgress.setProgress(0);

    	if (animation == true)
    	{
    		mProgress.startAnimation(AnimationUtils.loadAnimation(this,
    		  android.R.anim.fade_in));
    	}

    	mProgress.setVisibility(View.VISIBLE);
    }

    protected void stopSyncUI()
    {
    	mSyncing = false;
    	mSyncAll.setEnabled(true);

    	mProgress.startAnimation(AnimationUtils.loadAnimation(this,
    	  android.R.anim.fade_out));
    	mProgress.setVisibility(View.INVISIBLE);
    }
    
    protected void menuSync()
    {
    	Log.i(TAG, "menuSync(), here we go!");

    	if (mSyncing == true)
    	{
    		Toast.makeText(this, "Already synchronizing...", Toast.LENGTH_SHORT).show();
    		return;
    	}

    	startSyncUI();

		try
		{
			mService.startSync();
		}
		catch (DeadObjectException e)
		{
			mService = null;
			stopSyncUI();
		}
    }
    
    @Override
    public boolean onOptionsItemSelected(Menu.Item item)
    {
    	switch (item.getId())
    	{
    	case MENU_SYNC:
    		menuSync();
    		return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }

    public static class DateUtils
    {
    	public static String formatTimeAgo(long epoch)
    	{
    		long now = System.currentTimeMillis() / 1000;

    		if (now < epoch)
    			throw new IllegalArgumentException("Supplied time must be in the past");

    		long diff = now - epoch;

    		String[] units = { "d", "h", "m" };
    		int values[] = { 86400, 3600, 60 };
    		int digits[] = { 0, 0, 0 };

    		for (int offs = 0; offs < values.length; offs++)
    		{    			
    			digits[offs] = (int)diff / values[offs];
    			diff -= digits[offs] * values[offs];
    			
    			if (diff == 0)
    				break;
    		}

    		StringBuilder ret = new StringBuilder();
    		
    		for (int i = 0; i < digits.length; i++)
    		{
    			if (digits[i] > 0)
    				ret.append(digits[i]).append(units[i]).append(' ');
    		}
    		
    		if (ret.length() == 0)
    		{
    			if (diff > 0)
    				ret.append(diff).append("s ago");
    			else
    				ret.append("now");
    		}
    		else
    			ret.append("ago");

    		return ret.toString();
    	}
    }
}
