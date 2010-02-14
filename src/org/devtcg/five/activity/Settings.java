/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.service.MetaService;
import org.devtcg.five.widget.ServerPreference;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	private static final String KEY_SERVER = "server";
	private static final String KEY_AUTOSYNC = "autosync";

	private ServerPreference mServerPref;
	private ListPreference mAutosyncPref;

	public static void show(Context context)
	{
		context.startActivity(new Intent(context, Settings.class));
	}

	/**
	 * Special case which asks us to keep a sensible back stack while
	 * mechanizing the user toward the initial SourceAdd screen.
	 */
	public static void showThenStartSourceAdd(Context context)
	{
		context.startActivity(new Intent(context, Settings.class)
				.putExtra(Constants.EXTRA_START_SOURCE_ADD, true));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);

		mServerPref = (ServerPreference)findPreference(KEY_SERVER);
		mAutosyncPref = (ListPreference)findPreference(KEY_AUTOSYNC);

		mServerPref.init();

		/* Only here to help with the OOBE. */
		if (getIntent().getBooleanExtra(Constants.EXTRA_START_SOURCE_ADD, false) &&
				mServerPref.isEmpty())
			SourceAdd.actionAddSource(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		IntentFilter filter = new IntentFilter();
		filter.addAction(Constants.ACTION_SYNC_BEGIN);
		filter.addAction(Constants.ACTION_SYNC_END);
		registerReceiver(mSyncListener, filter);

		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);

		mAutosyncPref.setEnabled(mServerPref.isEmpty() == false);
		updateSummaries();
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		unregisterReceiver(mSyncListener);

		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onDestroy()
	{
		mServerPref.cleanup();
		super.onDestroy();
	}

	public void updateSummaries()
	{
		mAutosyncPref.setSummary(mAutosyncPref.getEntry());
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(KEY_AUTOSYNC))
		{
			MetaService.rescheduleAutoSync(this, Long.parseLong(mAutosyncPref.getValue()));
			updateSummaries();
		}
	}

	private final BroadcastReceiver mSyncListener = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			/*
			 * We don't really care what message was delivered, just check with
			 * our global to figure out what's going on right now. Note that
			 * ServerPreference also makes calls to MetaService.isSyncing()
			 * whenever the cursor its watching changes. The setIsSyncing call
			 * here is necessary to avoid a potential race condition.
			 */
			mServerPref.setIsSyncing(MetaService.isSyncing());
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.settings, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		boolean isSyncing = MetaService.isSyncing();
		menu.findItem(R.id.start_sync).setVisible(!isSyncing);
		menu.findItem(R.id.stop_sync).setVisible(isSyncing);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.start_sync:
				MetaService.startSync(this);
				return true;

			case R.id.stop_sync:
				MetaService.stopSync(this);
				return true;

			default:
				return false;
		}
	}
}
