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

import java.text.DateFormat;
import java.util.Date;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class SourceInfo extends Activity
{
	private static final String TAG = "SourceInfo";

	private static final String[] PROJECTION = {
	  Five.Sources._ID,
	  Five.Sources.NAME, Five.Sources.REVISION,
	  Five.Sources.HOST, Five.Sources.PORT,
	};

	private static final String[] PROJECTION_LOG = {
	  Five.SourcesLog._ID,
	  Five.SourcesLog.TIMESTAMP, Five.SourcesLog.TYPE,
	  Five.SourcesLog.MESSAGE,
	};

	private Cursor mCursor;
	private Cursor mCursorLog;

	private TextView mName;
	private TextView mURL;
	private TextView mStatus;
	private TextView mLastSuccess;
	private ListView mErrors;

    @Override
    public void onCreate(Bundle icicle)
    {
    	super.onCreate(icicle);
    	setContentView(R.layout.source_info);

    	mName = (TextView)findViewById(R.id.source_name);
    	mURL = (TextView)findViewById(R.id.source_url);
    	mStatus = (TextView)findViewById(R.id.source_status);
    	mLastSuccess = (TextView)findViewById(R.id.source_last_success);
    	mErrors = (ListView)findViewById(R.id.source_errors);

    	Uri uri = getIntent().getData();

    	mCursor = managedQuery(uri, PROJECTION, null, null);

    	/* XXX: This should be re-arranged to show up in onResume(). */
    	long rev = 0;
    	if (mCursor.first() == true)
    		rev = mCursor.getLong(2);

    	Uri uriLog = uri.buildUpon().appendPath("log").build();
    	mCursorLog = managedQuery(uriLog, PROJECTION_LOG,
    	  Five.SourcesLog.TIMESTAMP + " > " + rev, null,
    	  Five.SourcesLog.TIMESTAMP + " DESC");
    	
    	SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
    	  R.layout.source_log_item,
    	  mCursorLog,
    	  new String[] { Five.SourcesLog.TIMESTAMP, Five.SourcesLog.MESSAGE },
    	  new int[] { R.id.source_log_timestamp, R.id.source_log_message }
    	);

    	adapter.setViewBinder(new ViewBinder() {
			public boolean setViewValue(View view, Cursor c, int col)
			{
				if (c.getColumnIndex(Five.SourcesLog.TIMESTAMP) != col)
					return false;
				
				DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
				((TextView)view).setText(fmt.format(new Date(c.getLong(col) * 1000)));
				
				return true;
			}
    	});
    	
    	mErrors.setAdapter(adapter);
    }

    @Override
    public void onResume()
    {
    	super.onResume();
    	
    	if (mCursor.count() == 0)
    	{
    		Log.d(TAG, "Stale source, backing out...");
    		finish();
    	}
    	
    	mCursor.first();
    	
    	mName.setText(mCursor.getString(1));
    	mURL.setText("http://" + mCursor.getString(3) + ":" + mCursor.getInt(4) + "/sync");
  	  
    	mStatus.setText("Synchronized");
    	
    	long rev = mCursor.getLong(2);

    	if (rev > 0)
    	{
    		DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    		mLastSuccess.setText(fmt.format(new Date(rev * 1000)));
    	}
    	else
    	{
    		mLastSuccess.setText("Never");
    	}

    	if (mCursorLog.count() == 0)
    		((View)mErrors.getParent()).setVisibility(View.GONE);
    	else
    		((View)mErrors.getParent()).setVisibility(View.VISIBLE);
    }
}
