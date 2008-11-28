package org.devtcg.five.activity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SourceCheckSettings extends Activity
{
	private TextView mMessage;
	
	private Handler mHandler = new Handler();
	private VerifyThread mThread;

	public static void actionCheckSettings(Activity context, Uri uri)
	{
		Intent i = new Intent(context, SourceCheckSettings.class);
		i.setData(uri);
		context.startActivityForResult(i, 1);
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		Source source = getSourceFromIntent(getIntent());
		if (source == null)
			throw new IllegalArgumentException("This activity needs a server source Uri");

		setContentView(R.layout.source_check_settings);
		
		Button cancel = (Button)findViewById(R.id.cancel);
		cancel.setVisibility(View.VISIBLE);

		findViewById(R.id.next).setVisibility(View.GONE);

		mMessage = (TextView)findViewById(R.id.message);
		mMessage.setText("Verifying Five server settings...");

		mThread = new VerifyThread(source);
		mThread.start();
	}

	@Override
	protected void onDestroy()
	{
		mThread.abort();
		mThread.waitForDeath();

		super.onDestroy();
	}

	private void showErrorDialog(final String msg)
	{
		mHandler.post(new Runnable() {
			public void run() {
				new AlertDialog.Builder(SourceCheckSettings.this)
				  .setTitle("Setup failed")
				  .setMessage(msg)
				  .setCancelable(true)
				  .setOnCancelListener(new OnCancelListener() {
					  public void onCancel(DialogInterface dialog)
					  {
						  finish();
					  }
				  })
				  .setPositiveButton("OK", new OnClickListener() {
					  public void onClick(DialogInterface dialog, int which)
					  {
						  finish();
					  }
				  })
				  .show();
			}
		});
	}

	private Source getSourceFromIntent(Intent i)
	{
		Uri data = i.getData();
		if (data == null)
			return null;
		
		String[] fields = { Five.Sources.HOST, Five.Sources.PORT };
		Cursor c = getContentResolver().query(data, fields,
		  null, null, null);

		try {
			if (c.moveToFirst() == false)
				return null;

			return new Source(c.getString(0), c.getInt(1));
		} finally {
			c.close();
		}
	}
	
	/* XXX: This should be a more thorough abstraction provided elsewhere
	 * and throughout the app. */
	private static class Source
	{
		String host;
		int port;
		
		public Source(String host, int port)
		{
			this.host = host;
			this.port = port;
		}
	}
	
	/* TODO: We should be using MetaService to provide a more sophisticated
	 * test, including authentication and proof that there is a database
	 * on the other side ready to be synced.  For now we just do a simple
	 * connection test. */
	private class VerifyThread extends Thread
	{
		private Source mSource;
		private Socket mSocket;
		
		private volatile boolean mAborted = false;

		public VerifyThread(Source source)
		{
			mSource = source;
			mSocket = new Socket();
		}
		
		public void abort()
		{
			mAborted = true;

			interrupt();

			try {
				mSocket.close();
			} catch (IOException e) {}
		}

		public void waitForDeath()
		{
			while (true)
			{
				try {
					join();
					break;
				} catch (InterruptedException e) {}
			}
		}

		public void run()
		{
			try {
				try {
					mSocket.connect(new InetSocketAddress(mSource.host,
					  mSource.port));
					setResult(RESULT_OK);
					finish();
				} catch (IOException e) {
					if (mAborted == false)
					{
						showErrorDialog("Error connecting to server: " + e.getMessage());
						throw e;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					if (mAborted == false)
						mSocket.close();
				} catch (IOException e) {}
			}
		}
	}
}
