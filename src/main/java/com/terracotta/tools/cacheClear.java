package com.terracotta.tools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.InvalidOptionSpecificationException;
import com.lexicalscope.jewel.cli.Option;
import com.terracotta.tools.utils.AppConstants;
import com.terracotta.tools.utils.BaseAppParams;
import com.terracotta.tools.utils.CacheFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class cacheClear {
    private static Logger log = LoggerFactory.getLogger(cacheClear.class);

    private static final int CHECK_ITERATION_LIMIT_DEFAULT = 2;
    private static final long CHECK_ITERATION_SLEEP_DEFAULT = 2000;

    private final AppParams runParams;

    public cacheClear(final AppParams params) {
        this.runParams = params;
    }

    public void run() throws Exception {
        if (runParams.getCacheNamesCSV() == null || "".equals(runParams.getCacheNamesCSV())) {
            throw new Exception("No cache name defined. Doing nothing.");
        } else {
            if (runParams.getCacheKeysCSV() == null || "".equals(runParams.getCacheKeysCSV())) {
                throw new Exception("No cache key(s) specified. Doing nothing.");
            } else {
                System.out.println("-----------------------------------------------------------------");
                System.out.println("Start cacheClear at " + new Date() + "\n");

                String[] cname;
                if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheNamesCSV())) {
                    System.out.println("Requested to get size for all caches...");
                    cname = CacheFactory.getInstance().getCacheManager().getCacheNames();
                } else {
                    cname = runParams.getCacheNames();
                }

                //perform operation
                for (int i = 0; i < cname.length; i++) {
                    Cache cache = CacheFactory.getInstance().getCacheManager().getCache(cname[i]);

                    if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheKeysCSV())) {
                        clearAllCacheEntries(cache);

                        System.out.println("Checking cache sizes now...");
                        new cacheSize(cache.getName(), 1000, CHECK_ITERATION_LIMIT_DEFAULT).run();
                    } else {
                        clearSelectedKeys(cache, runParams.getCacheKeys());

                        System.out.println("Now, checking if requested keys are in cache...");
                        int it = 0;
                        while (it < CHECK_ITERATION_LIMIT_DEFAULT) {
                            System.out.println(String.format("---------------- Iteration %d ----------------", it + 1));
                            new cachePrint(cache.getName(), runParams.getCacheKeysCSV(), runParams.getCacheKeysType().getTypeString()).run();
                            Thread.sleep(CHECK_ITERATION_SLEEP_DEFAULT);
                            it++;
                        }
                    }
                }
            }
        }
    }

    private void clearSelectedKeys(Cache cache, Object[] keys) throws Exception {
        if (null != cache && null != keys && keys.length > 0) {
            int beforeRemoveSize = cache.getSize();
            System.out.println(String.format("Cache %s -- Current Size = %d", cache.getName(), beforeRemoveSize));
            System.out.println(String.format("Cache %s -- About to clear keys %s (type = %s)", cache.getName(), Arrays.deepToString(keys), runParams.getCacheKeysType().getTypeString()));

            List keyList = Arrays.asList(keys);

            //check first if requested keys are in cache currently
            List requestedKeysInCache = getKeysInCache(cache, keyList);
            if (null == requestedKeysInCache || requestedKeysInCache.size() == 0) {
                System.out.println(String.format("Cache %s -- Requested keys are not in cache -- doing nothing. ", cache.getName(), Arrays.deepToString(keys)));
                return;
            }

            //remove stage
            cache.removeAll(keyList);

            //verification stage
            int loop = 0;
            int allRemovedCount = 0;
            while (allRemovedCount < keys.length && loop++ <= 20) {
                allRemovedCount = 0;
                Map<Object, Element> inCache = cache.getAll(keyList);
                for (Object inCacheKey : inCache.keySet()) {
                    if (null == inCache.get(inCacheKey)) {
                        allRemovedCount++;
                    }
                }

                Thread.sleep(1000);
            }

            //if after 20+ seconds we still get the key, something must be wrong...
            if (allRemovedCount < keys.length && loop >= 20) {
                List unclearedKeys = getKeysInCache(cache, keyList);
                if (unclearedKeys.size() > 0) {
                    throw new Exception(String.format("Cache %s - Unable to clear keys %s", cache.getName(), Arrays.deepToString(unclearedKeys.toArray())));
                }
            }

            System.out.println(String.format("Cache %s: Clearing success -- Following keys removed: %s", cache.getName(), Arrays.deepToString(keys)));
            System.out.println("------------------------------------------------");
        } else {
            throw new Exception("Cache or key is null...doing nothing.");
        }
    }

    private List getKeysInCache(Cache cache, List keyList) {
        List unclearedKeys = new ArrayList();
        Map<Object, Element> inCache = cache.getAll(keyList);
        for (Object inCacheKey : inCache.keySet()) {
            if (null != inCache.get(inCacheKey)) {
                unclearedKeys.add(inCacheKey);
            }
        }
        return unclearedKeys;
    }

    private void clearAllCacheEntries(Cache cache) throws Exception {
        if (null != cache) {
            int beforeRemoveSize = cache.getSize();
            System.out.println("Clearing cache " + cache.getName() + " - Current size:" + beforeRemoveSize);

            if (beforeRemoveSize == 0) {
                System.out.println(cache.getName() + " is already empty...");
                return;
            }

            cache.removeAll();

            int afterRemoveSize;
            int loop = 0;
            while (beforeRemoveSize == (afterRemoveSize = cache.getSize()) && loop++ <= 20) {
                Thread.sleep(1000);
            }

            //if after 20+ seconds, the cache size hasn't changed, something must be wrong...
            if (beforeRemoveSize == afterRemoveSize) {
                throw new Exception("Unable to clear cache.");
            }

            if (afterRemoveSize > 0) {
                System.out.println("Cleared some entries in " + cache.getName() + "... but cache is not empty. Probably new entries were added at the same time.");
            }

            System.out.println(cache.getName() + ": Final Size " + cache.getSize());
            System.out.println("------------------------------------------------");
        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheClear launcher = new cacheClear(params);

                launcher.run();

                System.exit(0);
            } catch (Exception e) {
                log.error("", e);
            } finally {
                CacheFactory.getInstance().getCacheManager().shutdown();
            }
        } catch (ArgumentValidationException e) {
            System.out.println(e.getMessage());
        } catch (InvalidOptionSpecificationException e) {
            System.out.println(e.getMessage());
        }

        System.exit(1);
    }

    public static class AppParams extends BaseAppParams {
        private String cacheNamesCSV;
        private String cacheKeysCSV;
        private String cacheKeysType;

        public AppParams() {
        }

        public String getCacheNamesCSV() {
            return cacheNamesCSV;
        }

        public String[] getCacheNames() {
            String[] names = null;
            if (null != cacheNamesCSV) {
                names = cacheNamesCSV.split(",");
            }
            return names;
        }

        @Option(longName = "caches", description = "comma-separated cache names, or keyword \"all\" to include all caches")
        public void setCacheNames(String cacheNamesCSV) {
            this.cacheNamesCSV = cacheNamesCSV;
        }

        public String getCacheKeysCSV() {
            return cacheKeysCSV;
        }

        @Option(longName = "keys", description = "comma-separated cache keys, or keyword \"all\" to include all keys")
        public void setCacheKeys(String cacheKeys) {
            this.cacheKeysCSV = cacheKeys;
        }

        public Object[] getCacheKeys() {
            Object[] keys = null;
            if (null != cacheKeysCSV) {
                String[] strKeys = cacheKeysCSV.split(",");

                //try to create a new array if casting is desired
                AppConstants.KeyPrimitiveType keyType = getCacheKeysType();
                if (keyType != AppConstants.KeyPrimitiveType.StringType) {
                    keys = keyType.createArray(strKeys.length);
                    for (int i = 0; i < strKeys.length; i++) {
                        keys[i] = keyType.castToObjectFromString(strKeys[i]);
                    }
                } else {
                    keys = strKeys;
                }
            }
            return keys;
        }

        public AppConstants.KeyPrimitiveType getCacheKeysType() {
            return AppConstants.KeyPrimitiveType.getPrimitiveType(cacheKeysType);
        }

        @Option(defaultValue = "String", longName = "keysType", description = "Specifies the object type to cast the provided keys to")
        public void setCacheKeysType(String cacheKeysType) {
            this.cacheKeysType = cacheKeysType;
        }
    }
}
