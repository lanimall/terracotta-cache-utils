package com.terracotta.tools.utils;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Fabien Sanglier
 *         singleton
 */
public class CacheFactory {
    private static Logger log = LoggerFactory.getLogger(CacheFactory.class);

    public static final String ENV_CACHE_CONFIGPATH = "ehcache.config.path";
    private URL ehcacheURL;

    private CacheFactory() {
        String configLocationToLoad = null;
        if (null != System.getProperty(ENV_CACHE_CONFIGPATH)) {
            configLocationToLoad = System.getProperty(ENV_CACHE_CONFIGPATH);
        }

        if (null != configLocationToLoad) {
            if (configLocationToLoad.indexOf("classpath:") > -1) {
                ehcacheURL = CacheFactory.class.getClassLoader().getResource(configLocationToLoad.substring("classpath:".length()));
            } else {
                try {
                    //if no protocol is supplied, default to "file:" protocol (Since protocol is mandatory for URL)
                    if (configLocationToLoad.indexOf(':') == -1)
                        configLocationToLoad = "file:" + configLocationToLoad;

                    ehcacheURL = new URL(configLocationToLoad);
                } catch (MalformedURLException e) {
                    log.error("Could not create a valid URL from " + configLocationToLoad, e);
                    ehcacheURL = null;
                }
            }
        } else {
            log.warn("No config location specified...");
            ehcacheURL = null;
        }
    }

    private static class CacheFactoryHolder {
        public static CacheFactory cacheManagerDecorator = new CacheFactory();
    }

    public static CacheFactory getInstance() {
        return CacheFactoryHolder.cacheManagerDecorator;
    }

    public CacheManager getCacheManager() {
        if (null != ehcacheURL)
            return CacheManager.create(ehcacheURL);
        else
            return CacheManager.getInstance();
    }

    public Cache getCache(String cacheName) {
        Cache ehCacheCache = null;

        if (log.isDebugEnabled()) {
            log.debug("Retrieving cache for cacheName: " + cacheName);
        }

        ehCacheCache = getCacheManager().getCache(cacheName);
        if (ehCacheCache == null) {
            log.error("Unable to retrieve cache " + cacheName + "from CacheManager.");
        }

        return ehCacheCache;
    }
}