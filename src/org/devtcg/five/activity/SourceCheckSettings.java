package org.devtcg.five.activity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.devtcg.five.R;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.util.CancelableThread;
import org.devtcg.util.IOUtilities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SourceCheckSettings extends Activity
{
	private TextView mMessage;
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

		SourceItem source = SourceItem.getInstance(getContentResolver().query(getIntent().getData(),
				null, null, null, null));
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
		mThread.requestCancelAndWait();
		super.onDestroy();
	}

	private void showErrorDialog(final String msg)
	{
		runOnUiThread(new Runnable() {
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

	/* TODO: We should be using MetaService to provide a more sophisticated
	 * test, including authentication and proof that there is a database
	 * on the other side ready to be synced.  For now we just do a simple
	 * connection test. */
	private class VerifyThread extends CancelableThread
	{
		private SourceItem mSource;
		private Socket mSocket;

		public VerifyThread(SourceItem source)
		{
			mSource = source;
			mSocket = new Socket();
		}

		@Override
		protected void onRequestCancel()
		{
			IOUtilities.closeSocketQuietly(mSocket);
			interrupt();
		}

		public void run()
		{
			try {
				mSocket.connect(new InetSocketAddress(mSource.getHost(),
						mSource.getPort()));

				if (hasCanceled())
						return;

				runOnUiThread(new Runnable() {
					public void run() {
						setResult(RESULT_OK);
						finish();
					}
				});
			} catch (IOException e) {
				if (!hasCanceled())
					showErrorDialog("Error connecting to server: " + e.getMessage());
			} finally {
				if (!hasCanceled())
					IOUtilities.closeSocketQuietly(mSocket);
			}
		}
	}
}
