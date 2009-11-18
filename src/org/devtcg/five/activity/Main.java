/*
 * $Id$
 *
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.activity;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.util.PlaylistServiceActivity;
import org.devtcg.five.util.Song;
import org.devtcg.five.util.PlaylistServiceActivity.TrackChangeListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class Main extends PlaylistServiceActivity
  implements OnClickListener, TrackChangeListener
{
	private static final String TAG = "Main";

	private View mNowPlaying;
	private TextView mTitle;
	private TextView mArtist;

	public static void show(Context context)
	{
		context.startActivity(new Intent(context, Main.class));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setTrackChangeListener(this);
	}

	@Override
	protected void onInitUI()
	{
		setContentView(R.layout.music_library);

		mNowPlaying = findViewById(R.id.nowplaying);
		mTitle = (TextView)findViewById(R.id.title);
		mArtist = (TextView)findViewById(R.id.artist);

		findViewById(R.id.artists_button).setOnClickListener(this);
		findViewById(R.id.albums_button).setOnClickListener(this);
		findViewById(R.id.settings_button).setOnClickListener(this);
		findViewById(R.id.playlists_button).setOnClickListener(this);
		findViewById(R.id.nowplaying).setOnClickListener(this);
	}

	public void onTrackChange(long songId)
	{
		Log.d(TAG, "songId=" + songId);

		if (songId >= 0)
		{
			Song s = new Song(this, songId);
			mTitle.setText(s.title);
			mArtist.setText(s.artist);
			mNowPlaying.setVisibility(View.VISIBLE);
		}
		else
		{
			mNowPlaying.setVisibility(View.INVISIBLE);
		}
	}

	public void onClick(View v)
	{
		switch (v.getId())
		{
		case R.id.artists_button:
			ArtistList.show(this);
			break;
		case R.id.albums_button:
			AlbumList.show(this);
			break;
		case R.id.settings_button:
			Settings.show(this);
			break;
		case R.id.playlists_button:
			PlaylistList.show(this);
			break;
		case R.id.nowplaying:
			Player.show(this);
			break;
		}
	}
}
