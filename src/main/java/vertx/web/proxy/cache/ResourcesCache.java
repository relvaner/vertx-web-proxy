package vertx.web.proxy.cache;

import static vertx.web.proxy.ProxyLogger.logger;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class ResourcesCache {
	protected final String alias;
	
	protected final String redisHost;
	protected final int redisPort;
	
	protected JedisPool jedisPool;
	
	public ResourcesCache(String alias, String redisHost, int redisPort) {
		super();
		
		this.alias = alias;
		this.redisHost = redisHost;
		this.redisPort = redisPort;
	}
	
	public void init() {
		logger().info(String.format("Redis & RabbitMQ - Service started..."));
		jedisPool = new JedisPool(redisHost, redisPort);
		clear();
	}
	
	public void destroy() {
		jedisPool.close();
		logger().info(String.format("Redis & RabbitMQ - Service stopped..."));
	}
	
	/*
	public Set<String> getKeys() {
		Set<String> result = null;
		
		Jedis jedis = jedisPool.getResource();
		result = jedis.smembers(alias+"-keys");
		jedis.close();
		
		return result;
	}
	
	public boolean hasKey(String key) {
		boolean result = false;
		
		Jedis jedis = jedisPool.getResource();
		result = jedis.sismember(alias+"-keys", key.toString());
		jedis.close();
		
		return result;
	}
	
	public long count( ) {
		long result = 0;
		
		Jedis jedis = jedisPool.getResource();
		result = jedis.scard(alias+"-keys");
		jedis.close();
		
		return result;
	}
	*/
	
	public byte[] getResourceBytes(String key){
		byte[] result = null;
		
		Jedis jedis = jedisPool.getResource();
		result = jedis.get(new String(alias+"-bytes-"+key.toString()).getBytes());
		jedis.close();
		
		return result;
	}
	
	public ResourceInfo getResourceInfo(String key){
		ResourceInfo result = null;
		
		Jedis jedis = jedisPool.getResource();
		String value = jedis.get(alias+"-"+key.toString());
		jedis.close();
		if (value!=null)
			try {
				result = new ObjectMapper().readValue(value, ResourceInfo.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
		
		return result;
	}
	
	public void set(String key, byte[] resourceBytes, ResourceInfo resourceInfo) {
		String json = null;
		try {
			json = new ObjectMapper().writeValueAsString(resourceInfo);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		
		if (json!=null) {
			Jedis jedis = jedisPool.getResource();
			jedis.set(alias+"-"+key.toString(), json);
			jedis.set(new String(alias+"-bytes-"+key.toString()).getBytes(), resourceBytes);
			jedis.sadd(alias+"-keys", key.toString());
			jedis.close();
		}
	}
	
	/*
	public boolean del(String key) {
		Jedis jedis = jedisPool.getResource();
		long result1 = jedis.del(alias+"-"+key.toString());
		long result2 = jedis.del(new String(alias+"-bytes-"+key.toString()).getBytes());
		jedis.srem(alias+"-keys", key.toString());
		jedis.close();
		
		return result1>0 && result2>0;
	}
	*/
	
	public void clear() {
		Jedis jedis = jedisPool.getResource();
		
		System.out.println("CLEAR REDIS: "+jedis.flushAll());
		/*
		Set<String> keys = jedis.keys(alias+"-*");
		for (String key: keys)
			jedis.del(key);
		jedis.del(alias+"-keys");
		*/
		jedis.close();
	}
}
