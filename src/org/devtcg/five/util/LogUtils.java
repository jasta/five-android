package org.devtcg.five.util;

import android.util.Log;

public class LogUtils
{
	public static boolean isLoggable(String tag, int level)
	{
		return Log.isLoggable("five_" + tag, level);
	}
}
