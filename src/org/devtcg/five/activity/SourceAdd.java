package org.devtcg.five.activity;

import java.security.MessageDigest;
import java.text.ParseException;
import java.util.regex.Pattern;

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.service.MetaService;
import org.devtcg.five.util.StringUtils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SourceAdd extends Activity
{
	private static final int REQUEST_CHECK_SETTINGS = 1;

	private Button mNext;
	private EditText mHostname;
	private EditText mPassword;

	/* The source we're editing if called that way. */
	private Uri mExisting;

	public static void actionAddSource(Context context)
	{
		context.startActivity(new Intent(context, SourceAdd.class));
	}

	public static void actionEditSource(Context context, Uri sourceUri)
	{
		context.startActivity(new Intent(Intent.ACTION_EDIT, sourceUri, context, SourceAdd.class));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.source_add);

		mHostname = (EditText)findViewById(R.id.source_host);
		mPassword = (EditText)findViewById(R.id.source_password);

		mNext = (Button)findViewById(R.id.next);
		mNext.setOnClickListener(mNextClick);

		mHostname.requestFocus();

		/* Differentiates the add versus edit cases. */
		handleIntent(getIntent());
	}

	private void handleIntent(Intent intent)
	{
		if (Intent.ACTION_EDIT.equals(intent.getAction()))
		{
			SourceItem source = SourceItem.getInstance(this, intent.getData());
			if (source == null)
				throw new IllegalArgumentException("No source found at " + intent.getData());

			try {
				mExisting = intent.getData();
				mHostname.setText(source.getHostLabel());
				mPassword.setHint(R.string.existing_password);
			} finally {
				source.close();
			}
		}
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode,
      Intent data)
    {
		switch (requestCode)
		{
			case REQUEST_CHECK_SETTINGS:
				if (resultCode == RESULT_OK)
				{
					/*
					 * When the source is first added, automatically initiate a
					 * sync and return to the settings screen to observe.
					 */
					if (!Intent.ACTION_EDIT.equals(getIntent().getAction()))
						MetaService.startSync(this);

					finish();
				}
				break;
		}
    }

	private OnClickListener mNextClick = new OnClickListener()
	{
		private void parseHostAndPopulate(ContentValues values, String host)
				throws ParseException
		{
			int colon;
			if ((colon = host.indexOf(':')) >= 0)
			{
				String port = host.substring(colon + 1);

				if (colon == 0 || Pattern.matches("^\\d+$", port) == false)
					throw new ParseException("Unable to parse hostname, expected <hostname>[:<port>]", colon);

				values.put(Five.Sources.HOST, host.substring(0, colon));
				values.put(Five.Sources.PORT, Integer.parseInt(port));
			}
			else
			{
				values.put(Five.Sources.HOST, host);
				values.put(Five.Sources.PORT, Constants.DEFAULT_SERVER_PORT);
			}
		}

		private String hashString(String string)
		{
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				md.update(string.getBytes("UTF-8"));
				return StringUtils.byteArrayToHexString(md.digest());
			} catch (Exception e) {
				/*
				 * This shouldn't happen, both exceptions that can be thrown are
				 * related to unsupported algorithm or encoding, but we know
				 * confidently that Android does support what we've requested.
				 */
				throw new UnsupportedOperationException(e);
			}
		}

		private void hashPasswordAndPopulate(ContentValues values, String password)
		{
			values.put(Five.Sources.PASSWORD, hashString(password));
		}

		private void populateContentValues(ContentValues values)
				throws ParseException
		{
			parseHostAndPopulate(values, mHostname.getText().toString());

			if (mExisting == null || mPassword.getText().length() > 0)
				hashPasswordAndPopulate(values, mPassword.getText().toString());
		}

		public void onClick(View v)
		{
			ContentValues values = new ContentValues();

			try {
				populateContentValues(values);
			} catch (ParseException e) {
				Toast.makeText(SourceAdd.this, e.getMessage(),
				  Toast.LENGTH_LONG).show();
				return;
			}

			Uri uri;

			if (mExisting == null)
			{
				uri = getContentResolver()
				  .insert(Five.Sources.CONTENT_URI, values);

				/* If check settings fails, we'll be returned to this
				 * activity and the user will be clicking next multiple
				 * times until they get it right.  We want to make sure
				 * we're using update() not insert() for this case. */
				mExisting = uri;
			}
			else
			{
				uri = mExisting;
				getContentResolver().update(uri, values, null, null);
			}

			/* Store the result as if success, but as a convenience for
			 * the user we'll go ahead and check that there is a server
			 * waiting for us.  If not, we'll return back to this edit
			 * screen. */
			SourceCheckSettings.actionCheckSettings(SourceAdd.this, uri, REQUEST_CHECK_SETTINGS);
		}
	};
}
