package org.devtcg.five.activity;

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.service.MetaService;
import org.devtcg.five.widget.ServerPreference;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;

public class Settings extends PreferenceActivity
{
	private static final String KEY_SERVER = "server";
	private static final String KEY_AUTOSYNC = "autosync";

	private ServerPreference mServerPref;
	private Preference mAutosyncPref;

	public static void show(Context context)
	{
		context.startActivity(new Intent(context, Settings.class));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);

		mServerPref = (ServerPreference)findPreference(KEY_SERVER);
		mAutosyncPref = findPreference(KEY_AUTOSYNC);

		mServerPref.init();
	}

	@Override
	protected void onResume()
	{
		mAutosyncPref.setEnabled(mServerPref.isEmpty() == false);
		super.onResume();
	}

	@Override
	protected void onDestroy()
	{
		mServerPref.cleanup();
		super.onDestroy();
	}

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
				startService(new Intent(Constants.ACTION_START_SYNC, null,
						this, MetaService.class));
				return true;

			case R.id.stop_sync:
				startService(new Intent(Constants.ACTION_STOP_SYNC, null,
						this, MetaService.class));
				return true;

			default:
				return false;
		}
	}
}
