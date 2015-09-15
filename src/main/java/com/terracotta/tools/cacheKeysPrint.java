package com.terracotta.tools;

import java.util.Iterator;
import java.util.List;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

public class cacheKeysPrint {

	public static void main(String args[]){
		int sleep = 1000;
		String name=null;
		try{
			if ( args.length > 0 ){
				name=args[0];
			}

			CacheManager cmgr = new CacheManager();
			String[] cname= cmgr.getCacheNames();
			if (name == null){
				for (int i=0; i< cname.length ; i++){
					Cache cache = cmgr.getCache(cname[i]);
					printKeys(cache);
				}
			}else{
				Cache cache = cmgr.getCache(name);

				printKeys(cache);
			}

		}catch(Exception ex){
			System.out.println(ex);
		}

	}

	public  static void printKeys(Cache cache) throws Exception{
		List<Object> cacheKeyList = cache.getKeys();
		System.out.println("Listing Keys for cache "+cache.getName() +" Size = "+cacheKeyList.size() );
		Iterator<Object> iterator = cacheKeyList.iterator();
		while (iterator.hasNext()) {
			System.out.println(iterator.next());
		}
		System.out.println("------------------------------------------------");
	}
}
