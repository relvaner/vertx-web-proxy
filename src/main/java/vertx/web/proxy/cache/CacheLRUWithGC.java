/*
 * Copyright (c) 2015-2019, David A. Bauer. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vertx.web.proxy.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.SortedMap;

/*
 *  See: https://github.com/relvaner/actor4j-core/tree/master/src/main/java/actor4j/core/utils
 *  Changes for thread safety, David A. Bauer
 */
public abstract class CacheLRUWithGC<K, V> implements Cache<K, V>  {
	protected static class Pair<V> {
		public final V value;
		public final long timestamp;
		
		public Pair(V value, long timestamp) {
			this.value = value;
			this.timestamp = timestamp;
		}
	}
	
	protected final Map<K, Pair<V>> map;
	protected final SortedMap<Long, K> lru;
	
	protected final int maxSize;
	protected final AtomicInteger size;
	
	protected final Lock lock;
	
	public CacheLRUWithGC(int maxSize) {
		map = new ConcurrentHashMap<>();
		lru = new ConcurrentSkipListMap<>();
		
		this.maxSize = maxSize;
		size = new AtomicInteger(0);
		
		lock = new ReentrantLock();
	}
		
	public Map<K, Pair<V>> getMap() {
		return map;
	}
	
	public SortedMap<Long, K> getLru() {
		return lru;
	}
	
	public AtomicInteger getSize() {
		return size;
	}
	
	protected abstract int determineSize(K key);

	@Override
	public V get(K key) {
		V result = null;
		try {
			lock.lock();
			Pair<V> pair = map.get(key);
			if (pair!=null) {
				lru.remove(pair.timestamp);
				map.put(key, new Pair<V>(pair.value, System.nanoTime()));
				lru.put(pair.timestamp, key);
				result = pair.value;
			}
		} finally {
			lock.unlock();
		}
		
		return result;
	}
	
	@Override
	public V put(K key, V value, int size) {
		V result = null;
		try {
			lock.lock();
			long timestamp = System.nanoTime();
			Pair<V> pair = map.put(key, new Pair<V>(value, timestamp));
			if (pair==null) {
				resize();
				lru.put(timestamp, key);
				this.size.addAndGet(size);
			}
			else {
				lru.remove(pair.timestamp);
				lru.put(timestamp, key);
				result = pair.value;
			}
		} finally {
			lock.unlock();
		}
		
		return result;
	}
	
	public void remove(K key) {
		try {
			lock.lock();
			Pair<V> pair = map.get(key);
			lru.remove(pair.timestamp);
			map.remove(key);
			size.addAndGet(-determineSize(key));
		} finally {
			lock.unlock();
		}
	}
	
	public void clear() {
		try {
			lock.lock();
			map.clear();
			lru.clear();
			size.set(0);
		} finally {
			lock.unlock();
		}
	}
	
	// used only by put
	protected void resize() {
		if (size.get()>maxSize) {
			long timestamp = lru.firstKey();
			K key = lru.get(timestamp);
			size.addAndGet(-determineSize(key));
			map.remove(key);
			lru.remove(timestamp);
		}
	}
	
	@Override
	public void gc(long maxTime) {
		long currentTime = System.currentTimeMillis();
		try {
			lock.lock();
			Iterator<Entry<Long, K>> iterator = lru.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<Long, K> entry = iterator.next();
				if (currentTime-entry.getKey()/1_000_000>maxTime) {
					map.remove(entry.getValue());
					iterator.remove();
				}
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return "CacheLRUWithGC [map=" + map + ", lru=" + lru + ", size=" + size + "]";
	}
}