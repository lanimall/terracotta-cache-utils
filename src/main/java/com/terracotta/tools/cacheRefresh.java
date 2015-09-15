package com.terracotta.tools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.InvalidOptionSpecificationException;
import com.lexicalscope.jewel.cli.Option;
import com.terracotta.tools.utils.AppConstants;
import com.terracotta.tools.utils.BaseAppParams;
import com.terracotta.tools.utils.CacheFactory;
import com.terracotta.tools.utils.NamedThreadFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.eventual.StronglyConsistentCacheAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

public class cacheRefresh {
    private static Logger log = LoggerFactory.getLogger(cacheRefresh.class);
    private static final boolean isDebug = log.isDebugEnabled();

    private static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private final ExecutorService cacheFetchService;
    private final AppParams runParams;

    public cacheRefresh(final AppParams params) {
        this.runParams = params;

        if (null == runParams.getCacheNames() || runParams.getCacheNames().length == 0) {
            throw new IllegalArgumentException("No cache defined...verify that ehcache.xml is specified.");
        }

        this.cacheFetchService = Executors.newCachedThreadPool(new NamedThreadFactory("Concurrent Cache Operations"));
    }

    public String[] getCacheNames() {
        String[] cname;
        if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheNamesCSV())) {
            System.out.println("Requested to get size for all caches...");
            cname = CacheFactory.getInstance().getCacheManager().getCacheNames();
        } else {
            cname = runParams.getCacheNames();
        }
        return cname;
    }

    public void run() {
        String[] cacheNames = getCacheNames();

        System.out.println("-----------------------------------------------------------------");
        System.out.println(String.format("Start reset of Cache Elements - %s", dateTimeFormatter.format(new Date())));

        if (runParams.isUseThreading()) {
            resetElementsInCache(cacheNames);
        } else {
            resetElementsInCacheSerial(cacheNames);
        }

        System.out.println(String.format("End reset of Cache Elements - %s", dateTimeFormatter.format(new Date())));
    }

    public void postRun() {
        CacheFactory.getInstance().getCacheManager().shutdown();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        shutdownAndAwaitTermination(cacheFetchService);
    }

    private void resetElementsInCache(String[] cacheNames) {
        if (null != cacheNames) {
            Future futs[] = new Future[cacheNames.length];
            int cacheCount = 0;
            for (String cacheName : cacheNames) {
                System.out.println(String.format("Working on cache %s", cacheName));
                Cache myCache = CacheFactory.getInstance().getCache(cacheName);
                futs[cacheCount++] = cacheFetchService.submit(
                        new CacheOp(myCache)
                );
            }

            for (int i = 0; i < cacheCount; i++) {
                try {
                    while (!futs[i].isDone()) {
                        System.out.print(".");
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    log.error("", e);
                }
            }
        } else {
            throw new IllegalArgumentException("No cache name defined. Doing nothing.");
        }
    }

    private void resetElementsInCacheSerial(String[] cacheNames) {
        if (null != cacheNames) {
            for (String cacheName : cacheNames) {
                System.out.println(String.format("Working on cache %s", cacheName));
                Cache myCache = CacheFactory.getInstance().getCache(cacheName);
                if (null != myCache) {
                    new CacheOp(myCache).run();
                }
            }
        } else {
            throw new IllegalArgumentException("No cache name defined. Doing nothing.");
        }
    }

    public class CacheOp implements Runnable {
        private final Ehcache myCache;
        private final ExecutorService cacheGetsPool;
        private final CompletionService<Integer> cacheGetCompletionService;

        public CacheOp(Cache myCache) {
            this.myCache = new StronglyConsistentCacheAccessor(myCache);
            this.cacheGetsPool = Executors.newFixedThreadPool(runParams.cacheThreadCounts, new NamedThreadFactory(String.format("Cache %s Gets Pool", myCache.getName())));
            this.cacheGetCompletionService = new ExecutorCompletionService<Integer>(cacheGetsPool);
        }

        @Override
        public void run() {
            int opsCount = 0;
            if (null != myCache) {
                System.out.println(String.format("Cache %s - Starting Size = %s", myCache.getName(), myCache.getSize()));

                List<Object> keys = myCache.getKeys();
                int keySize = keys.size();
                try {
                    for (final Object key : keys) {
                        cacheGetCompletionService.submit(new CacheFetchAndPutOp(key));
                    }

                    for (int i = 0; i < keySize; i++) {
                        Future<Integer> fut = cacheGetCompletionService.poll(5L, TimeUnit.SECONDS);
                        if (null != fut)
                            opsCount += fut.get();
                    }
                } catch (InterruptedException e) {
                    log.error("", e);
                } catch (ExecutionException e) {
                    log.error("", e);
                } finally {
                    shutdownAndAwaitTermination(cacheGetsPool);
                    System.out.println("");
                    System.out.println(String.format("Cache %s - %s entries updated", myCache.getName(), opsCount));
                    System.out.println(String.format("Cache %s - Final Size = %s", myCache.getName(), myCache.getSize()));
                }
            } else {
                throw new IllegalArgumentException("cache is null...not able to perform any operation.");
            }
        }

        public class CacheFetchAndPutOp implements Callable<Integer> {
            private final Object key;

            public CacheFetchAndPutOp(Object key) {
                this.key = key;
            }

            @Override
            public Integer call() throws Exception {
                int count = 0;
                Element e = myCache.getQuiet(key);
                if (e != null) {
                    myCache.replace(e, new Element(e.getObjectKey(), e.getObjectValue()));
                    count++;
                }
                return count;
            }
        }
    }

    /*
     * thread executor shutdown
     */
    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted

        try {
            // Wait until existing tasks to terminate
            while (!pool.awaitTermination(5, TimeUnit.SECONDS)) ;

            pool.shutdownNow(); // Cancel currently executing tasks

            // Wait a while for tasks to respond to being canceled
            if (!pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS))
                log.error("Pool did not terminate");
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();

            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheRefresh launcher = new cacheRefresh(params);

                launcher.run();

                launcher.postRun();

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
        private boolean useThreading;
        private int cacheThreadCounts;

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

        public boolean isUseThreading() {
            return useThreading;
        }

        @Option(defaultValue = "true", longName = "parallel", description = "specify whether the operations should be run using parallel threads.")
        public void setUseThreading(boolean useThreading) {
            this.useThreading = useThreading;
        }

        public int getCacheThreadCounts() {
            return cacheThreadCounts;
        }

        @Option(defaultValue = "4", longName = "cacheThreads", description = "if parallel construct enabled, specify how many threads to use for each cache operations")
        public void setCacheThreadCounts(int cacheThreadCounts) {
            this.cacheThreadCounts = cacheThreadCounts;
        }
    }
}