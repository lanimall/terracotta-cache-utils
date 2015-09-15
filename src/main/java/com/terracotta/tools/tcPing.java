package com.terracotta.tools;

import com.terracotta.tools.utils.CacheFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.toolkit.nonstop.NonStopToolkitInstantiationException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;

public class tcPing {
    private static Logger log = LoggerFactory.getLogger(tcPing.class);

    private final int step;
    private final String uid;
    private final int count;
    private long shutdownMillis = -1;
    private String cacheName;
    private boolean createdCacheManager = false;

    private static final String TERRACOTTA_PING_CACHE = "terracottaPing";
    private static final String STRIPE_TEST_CACHE = "stripeTest";

    public tcPing(int step, String uid, int count, long shutdownMillis, String cacheName) {
        this.step = step;
        this.uid = uid;
        this.count = count;
        this.shutdownMillis = shutdownMillis;
        if (STRIPE_TEST_CACHE.equals(cacheName)) {
            this.cacheName = cacheName;
        }
        else {
            this.cacheName = TERRACOTTA_PING_CACHE;
        }
    }

    private static void usage() {
        log.info("Usage tcPing  [1/2] [unique id] [number of elements] [timeout in ms] [terracottaPing/stripeTest]");
        log.info("tcPing needs to be executed in a 2 step process. The first argument 1 is to load and 2 is retrieve and validate");
    }

    public static void main(String args[]) {
        int step = 1;
        String uid = "";
        int count = 1000;
        long shutdownMillis = -1;
        String cacheName = null;
        // Parse Input Arguments
        if (args.length < 3) {
            usage();
            System.exit(1);
        } else {
            step = Integer.parseInt(args[0]);
            uid = args[1];
            count = Integer.parseInt(args[2]);
            if (args.length > 3) shutdownMillis = Long.parseLong(args[3]);
            if (args.length > 4) cacheName = args[4];
        }

        System.exit(new tcPing(step, uid, count, shutdownMillis, cacheName).run());
    }

    public int run() {
        int returnCode;
        double msPerObject = 5;
        long minimumWaitMs = 1000 * 60;
        long shutdownTimeout = (this.shutdownMillis > 0) ? this.shutdownMillis :
                (long) (minimumWaitMs + this.count * msPerObject);
        ExecutorService executor = Executors.newCachedThreadPool();

        FutureTask<Integer> futureTask = new FutureTask<Integer>(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                try {
                    doRun();
                    return 0;
                } finally {
                    CacheFactory.getInstance().getCacheManager().shutdown();
                }
            }
        });
        log.info("Submitting task and waiting " + shutdownTimeout + " ms before shutting down...");
        executor.submit(futureTask);
        try {
            returnCode = futureTask.get(shutdownTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("tcPing operation timed out after '" + shutdownTimeout + " ms.");
            if (this.createdCacheManager) {
                // Was at least able to create a CacheManager; might have timed out because we couldn't perform ops fast enough
                returnCode = 2;
            }
            else {
                // Couldn't create CacheManager; Unclear if TC is completely down or is just missing 1 or more stripes
                returnCode = 3;
            }
        } catch (Exception e) {
            log.error("An error occurred while running tcPing.", e);
            if (e.getCause() instanceof NonStopToolkitInstantiationException) {
                // Could not load config from TC; most likely completely shut down
                returnCode = 4;
            }
            else {
                // Received an arbitrary exception; can't draw any conclusions of cause
                returnCode = 1;
            }
        }
        executor.shutdownNow();
        log.info("Exiting with return code: " + returnCode);
        return returnCode;
    }

    public void doRun() throws Exception {
        log.info("Attempting to create a CacheManager instance...");
        CacheManager cacheManager = CacheFactory.getInstance().getCacheManager();
        if (cacheManager == null) {
            throw new Exception("Unable to create cache manager. Check if ehcache.xml is in path.");
        }
        this.createdCacheManager = true;

        log.info("Attempting to get cache '" + cacheName + "'");
        Ehcache pingCache = cacheManager.addCacheIfAbsent(cacheName);
        if (pingCache == null) {
            throw new Exception("Unable to create cache " + cacheName + "'. Check if ehcache.xml has this cache defined.");
        }

        if (step == 1) {
            log.info("Starting Step 1 of tcPing..");
            step1(pingCache, uid, count);
            log.info("Step 1 of tcPing done..");
        } else {
            log.info("Starting Step 2 of tcPing..");
            step2(pingCache, uid, count);
            log.info("Step 2 of tcPing done..");
        }
    }

    private void step1(Ehcache pingCache, String uid, int count) throws Exception {
        //Clear the cache to remove stale data
        log.info("Now clearing all objects in cache '" + pingCache + "'...");
        pingCache.removeAll();
        if (pingCache.getSize() > 0) {
            throw new Exception("Unable to clear cache before Starting ping.");
        }

        log.info("Cache '" + pingCache.getName() + "' cleared. Starting to put elements...");
        SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
        Date date = new Date();
        for (int i = 0; i < count; i++) {
            String key = uid + "_" + new Integer(i).toString();
            pingCache.put(new Element(key, sdf.format(date)));
        }

        if (count != pingCache.getSize())
            throw new Exception("Unexpected amount of objects in cache (added amount[" + count + "] != cache size[" + pingCache.getSize() + "])");

        log.info("Loaded cache " + pingCache.getName() + ". Size =  " + pingCache.getSize());
    }

    private void step2(Ehcache pingCache, String uid, int count) throws Exception {
        log.info("Found cache " + pingCache.getName() + " with Size " + pingCache.getSize());
        log.info("Now verifying validity of all objects in cache");

        int cacheCount = 0;
        int missCount = 0;
        for (cacheCount = 0; cacheCount < count; cacheCount++) {
            String key = uid + "_" + new Integer(cacheCount).toString();
            Element val = pingCache.get(key);
            if (val == null) {
                if (missCount == 0) log.info("First cache miss. Key = " + key);
                missCount++;
            }
        }

        if (cacheCount != pingCache.getSize())
            throw new Exception("Unexpected amount of objects in cache (fetched amount[" + cacheCount + "] != cache size[" + pingCache.getSize() + "])");

        if (missCount > 0) {
            throw new Exception("Encountered at least one cache miss (" + missCount + ")");
        }

        log.info("Found " + cacheCount + " valid objects in cache");

        log.info("Now clearing all objects in cache");
        pingCache.removeAll();

        if (pingCache.getSize() > 0) {
            throw new Exception("Unable to clear cache.");
        }

        log.info("Cleared " + pingCache.getName() + ". Final Size " + pingCache.getSize());


    }

    private void deleteCache(String cacheName) {
        log.info("Deleting cache '" + cacheName + "'...");
        CacheFactory.getInstance().getCacheManager().removeCache(cacheName);
    }
}
