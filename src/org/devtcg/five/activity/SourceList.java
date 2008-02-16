package org.devtcg.five.activity;

import java.util.HashMap;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.service.IMetaObserver;
import org.devtcg.five.service.IMetaService;
import org.devtcg.five.service.MetaService;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class SourceList extends Activity
{
	private static final String TAG = "SourceList";
	
	private static final int MENU_SYNC = Menu.FIRST;
	
	private SimpleCursorAdapter mListAdapter;
	private Cursor mCursor;

	private static final String[] PROJECTION = new String[] {
	  Five.Sources._ID, Five.Sources.NAME,
	  Five.Sources.REVISION, Five.Sources.LAST_ERROR };
	
	private IMetaService mService;
	
	private ProgressBar mProgress;
	
	private HashMap<Integer, String> mStatus = new HashMap<Integer, String>();
	
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

    @Override
    public void onCreate(Bundle icicle)
    {
    	Log.d(TAG, "!!!!!! onCreate");
    	
        super.onCreate(icicle);
        setContentView(R.layout.source_list);

        Intent intent = getIntent();
        if (intent.getData() == null)
        	intent.setData(Five.Sources.CONTENT_URI);

        if (intent.getAction() == null)
        	intent.setAction(Intent.VIEW_ACTION);

        mCursor = managedQuery(intent.getData(), PROJECTION, null, null);
        assert mCursor != null;

        ListView list = (ListView)findViewById(android.R.id.list);

        View footer = getViewInflate().inflate(R.layout.source_footer,
          null, false, null);
        list.addFooterView(footer, null, false);

        mProgress = (ProgressBar)footer.findViewById(R.id.sync_progress);

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
							revText.setText("Never synchronized");
						else
							revText.setText("Last synchronized: " + DateUtils.formatTimeAgo(rev));
					}
				}

				return true;
			}
        });
        
        list.setAdapter(mListAdapter);

        if (mCursor.count() > 0)
        	findViewById(android.R.id.empty).setVisibility(View.GONE);
    }
    
    @Override
    public void onResume()
    {
    	Log.d(TAG, "!!!!!! onResume");
    	
    	Intent meta = new Intent(this, MetaService.class);
    	boolean bound = false;

    	if (startService(meta, null) != null)
    	{
    		if (bindService(meta, mConnection, 0) == true)
    			bound = true;
    	}

    	if (bound == false)
    		Log.e(TAG, "Failed to bind to MetaService");

    	super.onResume();
    }

    @Override
    public void onPause()
    {
    	Log.d(TAG, "!!!!!! onPause");
    	
    	try { mService.unregisterObserver(mObserver); }
    	catch (DeadObjectException e) { }
    	
    	unbindService(mConnection);
    	mService = null;
    	
    	super.onPause();    	
    }

    private ServiceConnection mConnection = new ServiceConnection()
    {
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			mService = IMetaService.Stub.asInterface(service);
			
			Log.d(TAG, "Attempting to register with service...");

			try
			{
				mService.registerObserver(mObserver);
			}
			catch (DeadObjectException e)
			{
				Log.e(TAG, "What the hell happened here?", e);
				mService = null;
			}
		}

		public void onServiceDisconnected(ComponentName name)
		{
			Log.d(TAG, "onServiceDisconnected: Where did it go?  Should we retry?  Hmm.");
			mService = null;
		}
    };
    
    private IMetaObserver.Stub mObserver = new IMetaObserver.Stub()
    {
    	private long lastUpdate = 0;
    	
		public void beginSync()
		{
			Log.d(TAG, "beginSync");
		}

		public void endSync()
		{
			Log.d(TAG, "endSync");

			mHandler.post(new Runnable()
			{
				public void run()
				{
					mProgress.setProgress(0);
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

			if (lastUpdate + 1500 > time)
				return;

			lastUpdate = time;
			Message msg = mHandler.obtainMessage(sourceId, itemNo, itemCount);
			mHandler.sendMessage(msg);
		}
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	
    	menu.add(0, MENU_SYNC, "Synchronize");
    	
    	return true;
    }

    protected void menuSync()
    {
    	Log.i(TAG, "menuSync(), here we go!");

		try
		{
			mService.startSync();
		}
		catch (DeadObjectException e)
		{
			mService = null;
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