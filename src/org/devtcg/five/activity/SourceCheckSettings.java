package org.devtcg.five.activity;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.util.AuthHelper;
import org.devtcg.five.util.streaming.FailfastHttpClient;
import org.devtcg.util.CancelableThread;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SourceCheckSettings extends Activity
{
	private static final String HEADER_FIVE_VERSION = "X-Five-Version";

	private TextView mMessage;
	private VerifyThread mThread;
	private boolean mThreadSent;

	public static void actionCheckSettings(Activity context, Uri uri, int requestCode)
	{
		Intent i = new Intent(context, SourceCheckSettings.class);
		i.setData(uri);
		context.startActivityForResult(i, requestCode);
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

		mThread = (VerifyThread)getLastNonConfigurationInstance();
		if (mThread != null)
			mThread.setActivity(this);
		else
		{
			mThread = new VerifyThread(this, source);
			mThread.start();
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{
		mThreadSent = true;
		mThread.setActivity(null);
		return mThread;
	}

	@Override
	protected void onDestroy()
	{
		if (!mThreadSent)
		{
			mThread.requestCancel();
			mThread.setActivity(null);
		}

		mThread = null;

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

	private static class VerifyThread extends CancelableThread
	{
		private SourceCheckSettings mActivity;
		private final HttpGet mRequest;

		private static final FailfastHttpClient mClient = FailfastHttpClient.newInstance(null);

		public VerifyThread(SourceCheckSettings activity, SourceItem source)
		{
			mActivity = activity;

			try {
				mRequest = new HttpGet(source.getServerInfoUrl());
				AuthHelper.setCredentials(mClient, source);
			} finally {
				source.close();
			}
		}

		public void setActivity(SourceCheckSettings activity)
		{
			synchronized(this) {
				mActivity = activity;
				if (activity != null)
					notify();
			}
		}

		private SourceCheckSettings getActivity()
		{
			/*
			 * We assume that anywhere calling getActivity() will be in a
			 * non-canceled path which means that the activity must be either
			 * non-null, or the activity is recreating itself so it will be
			 * non-null very soon.
			 */
			synchronized(this) {
				while (hasCanceled() == false && mActivity == null)
				{
					try {
						wait();
					} catch (InterruptedException e) {}
				}

				return mActivity;
			}
		}

		@Override
		protected void onRequestCancel()
		{
			mRequest.abort();

			synchronized(this) {
				/* Unblock getActivity() if necessary. */
				notify();
			}
		}

		public void run()
		{
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			HttpEntity entity = null;

			try {
				HttpResponse response = mClient.execute(mRequest);
				entity = response.getEntity();

				if (hasCanceled())
					return;

				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED)
					throw new IOException("invalid password");

				Header versionHead = response.getFirstHeader(HEADER_FIVE_VERSION);
				if (versionHead == null)
					throw new IOException("not a five server");

				Log.i(Constants.TAG, mRequest.getRequestLine().getUri() +
						" reports five version: " + versionHead.getValue());

				final SourceCheckSettings activity = getActivity();
				if (activity != null)
				{
					activity.runOnUiThread(new Runnable() {
						public void run() {
							activity.setResult(RESULT_OK);
							activity.finish();
						}
					});
				}
			} catch (IOException e) {
				if (!hasCanceled())
				{
					SourceCheckSettings activity = getActivity();
					if (activity != null)
						activity.showErrorDialog("Error connecting to server: " + e.getMessage());
				}
			} finally {
				if (entity != null)
				{
					try {
						entity.consumeContent();
					} catch (IOException e) {}
				}
			}
		}
	}
}
