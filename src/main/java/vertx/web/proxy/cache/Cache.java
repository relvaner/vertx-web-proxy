/*
 * Copyright (c) 2015-2017, David A. Bauer. All rights reserved.
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

/*
 *  See: https://github.com/relvaner/actor4j-core/tree/master/src/main/java/actor4j/core/utils
 *  Changes for thread safety, David A. Bauer
 */
public interface Cache<K, V> {
	public V get(K key);
	public V put(K key, V value, int size);
	public void remove(K key);
	public void clear();
	
	public void gc(long maxTime);
}
