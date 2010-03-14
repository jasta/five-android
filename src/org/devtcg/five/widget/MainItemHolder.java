/**
 *
 */
package org.devtcg.five.widget;

import org.devtcg.five.R;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

public abstract class MainItemHolder
{
	static final int SECOND_LAYER_ID = 1;

	public long id;
	public int position;
	public Uri badgeUri;

	public final ImageView badgeView;
	public final TransitionDrawable badgeTransition;
	public boolean badgeNeedsRevealing;

	public int defaultBadgeResource;

	public MainItemHolder(View view, int defaultBadgeResource)
	{
		badgeView = (ImageView)view.findViewById(R.id.badge);
		this.defaultBadgeResource = defaultBadgeResource;

		Resources res = view.getContext().getResources();

		badgeTransition = new TransitionDrawable(new Drawable[] {
			res.getDrawable(defaultBadgeResource),
			res.getDrawable(defaultBadgeResource),
		});

		badgeTransition.setCrossFadeEnabled(true);
		badgeTransition.setId(1, SECOND_LAYER_ID);
	}

	public void bindTo(long id, int position, Uri badgeUri)
	{
		this.id = id;
		this.position = position;
		this.badgeUri = badgeUri;
	}
}
