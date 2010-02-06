/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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

package org.devtcg.five.provider.util;

import java.lang.reflect.Method;

import org.devtcg.five.Constants;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;

/**
 * Abstraction to acquire a local content provider reference. Android 2.0
 * introduces public APIs for this, but pre-2.0 can only be supported using
 * reflection.
 */
public abstract class AcquireProvider
{
	public static AcquireProvider getInstance()
	{
		if (Constants.PRE_ECLAIR)
			return ThroughReflection.Holder.sInstance;
		else
			return DirectAccess.Holder.sInstance;
	}

	public abstract ProviderInterface acquireProvider(ContentResolver cr, String authority);
	public abstract void releaseProvider(ContentResolver cr, ProviderInterface provider);

	public interface ProviderInterface
	{
		public ContentProvider getLocalContentProvider();
	}

	private static class DirectAccess extends AcquireProvider
	{
		private static class Holder
		{
			private static final DirectAccess sInstance = new DirectAccess();
		}

		private DirectAccess() {}

		@Override
		public ProviderInterface acquireProvider(ContentResolver cr, String authority)
		{
			ContentProviderClient client = cr.acquireContentProviderClient(authority);
			return new DirectProviderInterface(client);
		}

		@Override
		public void releaseProvider(ContentResolver cr, ProviderInterface provider)
		{
			((DirectProviderInterface)provider).client.release();
		}

		private static class DirectProviderInterface implements ProviderInterface
		{
			private final ContentProviderClient client;

			public DirectProviderInterface(ContentProviderClient client)
			{
				this.client = client;
			}

			public ContentProvider getLocalContentProvider()
			{
				return client.getLocalContentProvider();
			}
		}
	}

	private static class ThroughReflection extends AcquireProvider
	{
		private final Class<?> icpClass;

		private static class Holder
		{
			private static final ThroughReflection sInstance = new ThroughReflection();
		}

		private ThroughReflection()
		{
			try {
				icpClass = Class.forName("android.content.IContentProvider");
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public ProviderInterface acquireProvider(ContentResolver cr, String authority)
		{
			try {
				Method acquireMethod = cr.getClass().getMethod("acquireProvider",
					new Class[] { String.class });

				Object icp = acquireMethod.invoke(cr, new Object[] { authority });
				return new ReflectionProviderInterface(icp);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void releaseProvider(ContentResolver cr, ProviderInterface provider)
		{
			try {
				Method releaseMethod = cr.getClass().getMethod("releaseProvider",
					new Class[] { icpClass });

				Object icp = ((ReflectionProviderInterface)provider).icp;
				releaseMethod.invoke(cr, new Object[] { icp });
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private class ReflectionProviderInterface implements ProviderInterface
		{
			private final Object icp;

			public ReflectionProviderInterface(Object icp)
			{
				this.icp = icp;
			}

			public ContentProvider getLocalContentProvider()
			{
				try {
					Method localProviderMethod =
						ContentProvider.class.getMethod("coerceToLocalContentProvider",
							new Class[] { icpClass });

					Object provider = localProviderMethod.invoke(null, new Object[] { icp });
					return (ContentProvider)provider;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
