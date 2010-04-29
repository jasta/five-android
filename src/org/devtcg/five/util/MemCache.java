package org.devtcg.five.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class MemCache<K, V>
{
	private final HashMap<K, CacheValue<K, V>> mCache = new HashMap<K, CacheValue<K, V>>();
	private final ReferenceQueue<V> mRefQueue = new ReferenceQueue<V>();

	public V get(K key)
	{
		prune();
		CacheValue<K, V> ref = mCache.get(key);
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
		prune();
		mCache.put(key, new CacheValue<K, V>(key, value, mRefQueue));
	}

	public V remove(K key)
	{
		prune();
		CacheValue<K, V> ref = mCache.remove(key);
		if (ref != null)
		{
			V value = ref.get();
			if (value != null)
				return value;
		}
		return null;
	}

	public int size()
	{
		return mCache.size();
	}

	public void clear()
	{
		while (mRefQueue.poll() != null)
			/* Do nothing... */;

		mCache.clear();
	}

	@SuppressWarnings("unchecked")
	private void prune()
	{
		CacheValue<K, V> ref;
		while ((ref = (CacheValue<K, V>)mRefQueue.poll()) != null)
		{
			K key = ref.key.get();
			if (key != null)
				mCache.remove(key);
		}
	}

	/**
	 * @deprecated Do not use.
	 */
	public ReferenceQueue<V> getReferenceQueue()
	{
		return mRefQueue;
	}

	private static class CacheValue<Key, Value> extends SoftReference<Value>
	{
		/**
		 * Reference to the key that installed this value so we can prune
		 * entries when the value expires.
		 * <p>
		 * Must be weakly referenced for the case where the entry is removed
		 * from the cache, but still strongly referenced because of the
		 * reference queue.
		 */
		private final WeakReference<Key> key;

		public CacheValue(Key key, Value value, ReferenceQueue<? super Value> queue)
		{
			super(value, queue);
			this.key = new WeakReference<Key>(key);
		}
	}
}
