package com.terracotta.tools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.InvalidOptionSpecificationException;
import com.lexicalscope.jewel.cli.Option;
import com.terracotta.tools.utils.*;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;
import net.sf.ehcache.pool.sizeof.SizeOf;
import net.sf.ehcache.pool.sizeof.filter.PassThroughFilter;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class cacheInspect {
    private static Logger log = LoggerFactory.getLogger(cacheInspect.class);
    private static final boolean isDebug = log.isDebugEnabled();

    private final AppParams runParams;
    private final ExecutorService cacheFetchService;
    //private final ExecutorService cacheGetService;

    //agent (DEFAULT), reflection, unsafe
    private final SizeOf sizeOf;

    public enum SizeOfType {
        reflection {
            @Override
            public SizeOf getSizeOfEngine() {
                return new net.sf.ehcache.pool.sizeof.ReflectionSizeOf(new PassThroughFilter(), true);
            }
        }, agent {
            @Override
            public SizeOf getSizeOfEngine() {
                return new net.sf.ehcache.pool.sizeof.AgentSizeOf(new PassThroughFilter(), true);
            }
        }, unsafe {
            @Override
            public SizeOf getSizeOfEngine() {
                return new net.sf.ehcache.pool.sizeof.UnsafeSizeOf(new PassThroughFilter(), true);
            }
        };

        public abstract SizeOf getSizeOfEngine();
    }

    public cacheInspect(final AppParams params) {
        this.runParams = params;

        if (runParams.getCacheNamesCSV() == null || "".equals(runParams.getCacheNamesCSV())) {
            throw new IllegalArgumentException("No cache name defined. Doing nothing.");
        }

        if (runParams.getSamplingSize() <= 0)
            throw new IllegalArgumentException("Sampled size should be > 0");

        this.sizeOf = runParams.getSizeOfType().getSizeOfEngine();

        this.cacheFetchService = Executors.newFixedThreadPool(runParams.getCachePoolSize(), new NamedThreadFactory("Cache Sizing Pool"));
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        shutdownAndAwaitTermination(cacheFetchService);
    }

    public void run() {
        String[] cname;
        if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheNamesCSV())) {
            System.out.println("Requested to get size for all caches...");
            cname = CacheFactory.getInstance().getCacheManager().getCacheNames();
        } else {
            cname = runParams.getCacheNames();
        }

        if (!runParams.isNoThreading()) {
            findThreadingObjectSizesInCache(cname);
        } else {
            findSerialObjectSizesInCache(cname);
        }
    }

    public void postRun() {
        CacheFactory.getInstance().getCacheManager().shutdown();
    }

    private void findThreadingObjectSizesInCache(String[] cacheNames) {
        Future<Map<CacheStatsDefinition, CacheSizeStats>> futs[] = new Future[runParams.getCacheNames().length];
        int cacheCount = 0;

        for (String cacheName : cacheNames) {
            Cache myCache = CacheFactory.getInstance().getCache(cacheName);
            futs[cacheCount] = cacheFetchService.submit(new CacheFetchOp(myCache));
            cacheCount++;
        }

        for (int i = 0; i < cacheCount; i++) {
            try {
                while (!futs[i].isDone()) {
                    System.out.print(".");
                    Thread.sleep(5000);
                }

                Map<CacheStatsDefinition, CacheSizeStats> cacheStats = futs[i].get();
                if (null != cacheStats) {
                    for (CacheStatsDefinition statsDef : cacheStats.keySet()) {
                        CacheSizeStats stats = cacheStats.get(statsDef);

                        System.out.println(statsDef.toString() + " " + ((null != stats) ? stats.toString() : "null"));
                    }
                }
                System.out.println("");
            } catch (InterruptedException e) {
                log.error("", e);
            } catch (ExecutionException e) {
                log.error("", e);
            }
        }
    }

    private void findSerialObjectSizesInCache(String[] cacheNames) {

        Map<CacheStatsDefinition, CacheSizeStats> cacheStats = null;
        for (String cacheName : cacheNames) {
            Cache myCache = CacheFactory.getInstance().getCache(cacheName);
            cacheStats = new CacheFetchOp(myCache).call();

            if (null != cacheStats) {
                for (CacheStatsDefinition statsDef : cacheStats.keySet()) {
                    CacheSizeStats stats = cacheStats.get(statsDef);

                    System.out.println(statsDef.toString() + " " + ((null != stats) ? stats.toString() : "null"));
                }
            }
            System.out.println("");
        }
    }

    private class CacheFetchOp implements Callable<Map<CacheStatsDefinition, CacheSizeStats>> {
        private final Cache myCache;

        public CacheFetchOp(Cache myCache) {
            this.myCache = myCache;
        }

        @Override
        public Map<CacheStatsDefinition, CacheSizeStats> call() {
            Map<CacheStatsDefinition, CacheSizeStats> cacheStats = new HashMap<CacheStatsDefinition, CacheSizeStats>();
            try {
                List<Object> keys = myCache.getKeys();
                int iterationLimit = 0;
                for (Object key : keys) {
                    if (iterationLimit >= runParams.getSamplingSize()) break;

                    doGets(key, cacheStats);

                    iterationLimit++;
                }
            } catch (IllegalStateException e1) {
                log.error("", e1);
            } catch (CacheException e1) {
                log.error("", e1);
            }
            return cacheStats;
        }

        private void doGets(Object key, Map<CacheStatsDefinition, CacheSizeStats> cacheStats) {
            try {
                Element e = myCache.getQuiet(key);
                if (e != null) {
                    Object objKey = e.getObjectKey();
                    Object objValue = e.getObjectValue();

                    if (objKey != null) {
                        String objType = getObjectType(objKey);

                        //serialized case
                        if (runParams.isPrintSerializedSize()) {
                            byte[] serializedSize = SerializationUtils.serialize((Serializable) objKey);
                            add(CacheStatsDefinition.key_serialized(myCache.getName()), cacheStats, serializedSize.length, objType);

                            if (isDebug) {
                                log.debug(String.format("Cache=%s - Target: KEY - Serialized size=%d", myCache.getName(), serializedSize.length));
                            }
                        }

                        //unserialized case
                        if (runParams.isPrintUnSerializedSize()) {
                            long objectOnHeapSize = sizeOf.deepSizeOf(Integer.MAX_VALUE, true, objKey).getCalculated();
                            add(CacheStatsDefinition.key_unserialized(myCache.getName()), cacheStats, objectOnHeapSize, objType);

                            if (isDebug) {
                                log.debug(String.format("Cache=%s - Target: KEY - Unserialized Size=%d", myCache.getName(), objectOnHeapSize));
                            }
                        }
                    }

                    if (objValue != null) {
                        String objType = getObjectType(objValue);

                        //serialized case
                        if (runParams.isPrintSerializedSize()) {
                            byte[] serializedSize = SerializationUtils.serialize((Serializable) objValue);
                            add(CacheStatsDefinition.value_serialized(myCache.getName()), cacheStats, serializedSize.length, objType);

                            if (isDebug) {
                                log.debug(String.format("Cache=%s - Target: VALUE - Serialized size=%d", myCache.getName(), serializedSize.length));
                            }
                        }

                        //unserialized case
                        if (runParams.isPrintUnSerializedSize()) {
                            long objectOnHeapSize = sizeOf.deepSizeOf(Integer.MAX_VALUE, true, objValue).getCalculated();
                            add(CacheStatsDefinition.value_unserialized(myCache.getName()), cacheStats, objectOnHeapSize, objType);

                            if (isDebug) {
                                log.debug(String.format("Cache=%s - Target: VALUE - Serialized size=%d", myCache.getName(), objectOnHeapSize));
                            }
                        }
                    }
                }
            } catch (IllegalStateException e1) {
                log.error("", e1);
            } catch (CacheException e1) {
                log.error("", e1);
            }
        }

        private void add(CacheStatsDefinition cacheDef, Map<CacheStatsDefinition, CacheSizeStats> cacheStats, long value, String type) {
            CacheSizeStats stats = null;
            if (null == (stats = cacheStats.get(cacheDef))) {
                stats = new CacheSizeStats();
            }
            stats.add(value, type);
            cacheStats.put(cacheDef, stats);
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

    public String getObjectType(Object obj) {
        String objectType;
        if (obj instanceof Collection) {
            Collection objBag = (Collection) obj;

            objectType = objBag.getClass().getCanonicalName();
            for (Object innerObj : objBag) {
                objectType += "->" + innerObj.getClass().getCanonicalName();
                break;
            }
        } else {
            objectType = obj.getClass().getCanonicalName();
        }
        return objectType;
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheInspect launcher = new cacheInspect(params);

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
        private int samplingSize;
        private boolean printUnSerializedSize;
        private boolean printSerializedSize;
        private boolean noThreading;
        private int cachePoolSize;
        private SizeOfType sizeOfType;

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

        public int getSamplingSize() {
            return samplingSize;
        }

        @Option(longName = "samplingSize", defaultValue = "100", description = "amount of objects to sample for the size calculations", minimum = 1, maximum = 10000)
        public void setSamplingSize(int samplingSize) {
            this.samplingSize = samplingSize;
        }

        public boolean isPrintUnSerializedSize() {
            return printUnSerializedSize;
        }

        @Option(longName = "printUnSerializedSize", defaultValue = "true")
        public void setPrintUnSerializedSize(boolean printUnSerializedSize) {
            this.printUnSerializedSize = printUnSerializedSize;
        }

        public boolean isPrintSerializedSize() {
            return printSerializedSize;
        }

        @Option(longName = "printSerializedSize", defaultValue = "true")
        public void setPrintSerializedSize(boolean printSerializedSize) {
            this.printSerializedSize = printSerializedSize;
        }

        public boolean isNoThreading() {
            return noThreading;
        }

        @Option(longName = "noThreading")
        public void setNoThreading(boolean noThreading) {
            this.noThreading = noThreading;
        }

        public int getCachePoolSize() {
            return cachePoolSize;
        }

        @Option(longName = "cachePoolSize", defaultValue = "4", minimum = 1, maximum = 16)
        public void setCachePoolSize(int cachePoolSize) {
            this.cachePoolSize = cachePoolSize;
        }

        public SizeOfType getSizeOfType() {
            return sizeOfType;
        }

        @Option(longName = "sizeOfType", defaultValue = "agent")
        public void setSizeOfType(SizeOfType sizeOfType) {
            this.sizeOfType = sizeOfType;
        }
    }
}