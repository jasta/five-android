package org.devtcg.five.util;

import org.devtcg.five.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.util.Log;

/**
 * Utility class to create activities which critically depend on a service 
 * connection.
 */
public abstract class ServiceActivity extends Activity
  implements ServiceConnection
{
	public static final String TAG = "ServiceActivity";

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		if (bindService() == false)
			onServiceFatal();

		Log.d(TAG, "FOO bindService called...");
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		unbindService();
	}

	private boolean bindService()
	{
		Intent i = getServiceIntent();

		/* I don't remember why we start first, then bind.  I picked it up
		 * somewhere back in M5 days, so perhaps it no longer applies. */
		ComponentName name = startService(i);

		if (name == null)
			return false;

		boolean bound = bindService(new Intent().setComponent(name),
		  this, BIND_AUTO_CREATE);

		return bound;
	}
	
	private void unbindService()
	{
		unbindService(this);
	}
	
	protected abstract Intent getServiceIntent();
	
	/**
	 * Fatal error attempting to either start or bind to the service specified by
	 * {@link getServiceIntent}.  Will not retry.  Default implementation is to
	 * throw up an error and finish().
	 */
	protected void onServiceFatal()
	{
		Log.e(TAG, "Unable to start service: " + getServiceIntent());

		(new AlertDialog.Builder(this))
		  .setIcon(android.R.drawable.ic_dialog_alert)
		  .setTitle("Sorry!")
		  .setMessage(R.string.app_error_msg)
		  .create().show();

		finish();
	}
}
