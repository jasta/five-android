package org.devtcg.five.util;

import java.lang.ref.SoftReference;
import java.util.HashMap;

public class MemCache<K, V>
{
	private final HashMap<K, SoftReference<V>> mCache = new HashMap<K, SoftReference<V>>();

	public V get(K key)
	{
		SoftReference<V> ref = mCache.get(key);
		if (ref != null)
		{
			V value = ref.get();
			if (value != null)
				return value;
			else
				mCache.remove(key);
		}
		return null;
	}

	public void put(K key, V value)
	{
		mCache.put(key, new SoftReference<V>(value));
	}

	public V remove(K key)
	{
		SoftReference<V> ref = mCache.remove(key);
		if (ref != null)
		{
			V value = ref.get();
			if (value != null)
				return value;
		}
		return null;
	}

	public void clear()
	{
		mCache.clear();
	}
}
