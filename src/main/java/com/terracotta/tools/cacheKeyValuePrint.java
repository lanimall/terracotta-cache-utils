package com.terracotta.tools;

import java.util.Iterator;
import java.util.List;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.commons.lang3.ClassUtils;

public class cacheKeyValuePrint {

	public static void main(String args[]){

		String cacheName = null;
		String key=null;
		try{

			if ( args.length == 0){
				System.out.println("Usage <cache name> [key]");
				System.exit(1);
			}
			if ( args.length == 1 ){
				cacheName = args[0];

			}else	
				if ( args.length == 2 ){
					cacheName = args[0];
					key = args[1];
				}else{

				}

			CacheManager cmgr = new CacheManager();
			Cache cache = cmgr.getCache(cacheName);
			if (cache == null){
				System.out.println("Cache "+cacheName+ "not found.");
				System.exit(1);
			}	

			if (key == null){
				printAllValues(cache);
			}else{
				printKeyValue(cache,key);
			}




		}catch (Exception ex){
			System.out.println(ex);
		}

	}


	private static void printAllValues(Cache cache){
		List<Object> cacheKeyList = cache.getKeys();
		System.out.println("Listing Keys and Values for cache "+cache.getName() +" Size = "+cacheKeyList.size() );
		Iterator<Object> iterator = cacheKeyList.iterator();
		while (iterator.hasNext()) {
			printKeyValue(cache, iterator.next());
		}

	}

	private static void printKeyValue(Cache cache, Object key){
		Element e = cache.get(key);
		if( e != null){
			printValue(e);
		}else{
			System.out.println("Key not found "+key);
		}

	}



	private static void printValue(Element e){
		if (e !=  null){
			Object val = e.getObjectValue();
			if ( ClassUtils.isPrimitiveOrWrapper(val.getClass()) || ( val instanceof String ) ){
				System.out.println("Key="+e.getObjectKey()+" Value = "+ e.getObjectValue());
			}
			if ( e.getObjectValue() instanceof List ){
				System.out.println("Key="+e.getObjectKey() + "Value is of type List");
				List<Object> cacheValueList =(List) e.getObjectValue();
				Iterator<Object> iterator = cacheValueList.iterator();
				while (iterator.hasNext()) {
					Object lv = iterator.next();
					if ( ClassUtils.isPrimitiveOrWrapper(lv.getClass())  || ( val instanceof String ) ){
						System.out.println(lv);
					}
				
				
				}
			}
			if (e.getObjectValue() == null){
				System.out.println("Value is null");
			}




		}

	}
	
}
