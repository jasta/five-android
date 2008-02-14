package org.devtcg.five.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ContentService extends Service
{
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
}
