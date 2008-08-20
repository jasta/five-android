package org.devtcg.five.util;

import java.util.HashMap;

import org.devtcg.five.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Utility class to create activities which critically depend on a service 
 * connection.  Automatically retries to create the service connection again
 * if it fails.
 */
public abstract class ServiceActivity extends Activity
  implements ServiceConnection
{
	public static final String TAG = "ServiceActivity";

	private static final HashMap<Context, IBinder> mConnected =
	  new HashMap<Context, IBinder>();

	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		IBinder b = mConnected.get(this);

		if (b != null)
			onServiceConnected(null, b);
		else
		{
			if (bindService() == false)
				onServiceFatal();
		}
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

	protected void ejectConnection()
	{
		mConnected.remove(this);
	}
	
	protected abstract Intent getServiceIntent();
	
	public void onServiceConnected(ComponentName name, IBinder binder)
	{
		mConnected.put(this, binder);
	}
	
	public void onServiceDisconnected(ComponentName name)
	{
		mConnected.remove(this);
	}

	/**
	 * Fatal error attempting to either start or bind to the service specified by
	 * {@link getServiceIntent}.  Will not retry.  Default implementation is to
	 * throw up an error and finish().
	 */
	protected void onServiceFatal()
	{
		Log.e(TAG, "Unable to start service: " + getServiceIntent());

		ejectConnection();

		(new AlertDialog.Builder(this))
		  .setIcon(android.R.drawable.ic_dialog_alert)
		  .setTitle("Sorry!")
		  .setMessage(R.string.app_error_msg)
		  .create().show();

		finish();
	}
}
