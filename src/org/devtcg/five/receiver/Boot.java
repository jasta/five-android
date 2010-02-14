package org.devtcg.five.receiver;

import org.devtcg.five.service.MetaService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Boot receiver to set up the auto-sync alarm.  That's all, sigh.
 */
public class Boot extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		MetaService.scheduleAutoSync(context);
	}
}
