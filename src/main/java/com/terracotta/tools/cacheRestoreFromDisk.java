package com.terracotta.tools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.InvalidOptionSpecificationException;
import com.lexicalscope.jewel.cli.Option;
import com.terracotta.tools.utils.BaseAppParams;
import com.terracotta.tools.utils.CacheFactory;
import com.terracotta.tools.utils.NamedThreadFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;

public class cacheRestoreFromDisk {
    private static Logger log = LoggerFactory.getLogger(cacheRestoreFromDisk.class);
    private static final boolean isDebug = log.isDebugEnabled();
    private static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
    private final AppParams runParams;
    private final ExecutorService cacheOpsFetchService;
    private final ExecutorCompletionService<Boolean> cacheOpsCompletionService;

    public cacheRestoreFromDisk(final AppParams params) {
        this.runParams = params;
        this.cacheOpsFetchService = Executors.newFixedThreadPool(params.getThreadCounts(), new NamedThreadFactory("Concurrent Cache Operations"));
        this.cacheOpsCompletionService = new ExecutorCompletionService<Boolean>(cacheOpsFetchService);
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheRestoreFromDisk launcher = new cacheRestoreFromDisk(params);

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

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        shutdownAndAwaitTermination(cacheOpsFetchService);
    }

    public void run() throws Exception {
        if (runParams.getCacheName() == null || "".equals(runParams.getCacheName())) {
            throw new Exception("No cache name defined. Doing nothing.");
        } else {
            Cache cache = CacheFactory.getInstance().getCacheManager().getCache(runParams.getCacheName());
            if (cache == null) {
                throw new Exception("Cache " + runParams.getCacheName() + " not found.");
            }

            File file = new File(runParams.getFilePath());
            int restoredElements = restoreElementsFromFile(cache, file);

            System.out.println(String.format("Restored elements in cache = %d", restoredElements));
            System.out.println(String.format("End Cache Restore-From-Disk Operation - %s", dateTimeFormatter.format(new Date())));
        }
    }

    private int restoreElementsFromFile(final Cache targetCache, final File serializationFile) throws Exception {
        int restoredElements = 0;
        if (null != targetCache) {
            int opSubmittedCount = 0;
            try {
                //use buffering
                InputStream file = new FileInputStream(serializationFile);
                InputStream buffer = new BufferedInputStream(file);
                ObjectInput input = new ObjectInputStream(buffer);
                try {
                    Object obj = null;

                    try {
                        while ((obj = input.readObject()) != null) {
                            Pair<byte[], byte[]> serializedElement = (Pair<byte[], byte[]>) obj;
                            Object key = SerializationUtils.deserialize(serializedElement.getKey());
                            Object value = SerializationUtils.deserialize(serializedElement.getValue());

                            if (!runParams.noThreading) {
                                cacheOpsCompletionService.submit(new CacheOp(targetCache, key, value));
                            } else {
                                try {
                                    if (new CacheOp(targetCache, key, value).call()) {
                                        restoredElements++;
                                    }
                                } catch (Exception e) {
                                    log.error("ERROR", e);
                                }
                            }
                            opSubmittedCount++;
                        }
                    } catch (EOFException eof) {
                        //we're done...
                        System.out.println("End of file detected...we're done");
                    }
                } finally {
                    input.close();
                }
            } catch (IOException ex) {
                log.error("Cannot perform output.", ex);
            }

            //if we used threading, get the results now...
            if (!runParams.noThreading) {
                int opCompletedTasks = 0;
                while (opCompletedTasks < opSubmittedCount) {
                    try {
                        Future<Boolean> fut = cacheOpsCompletionService.poll(20, TimeUnit.SECONDS);
                        if (null != fut) {
                            opCompletedTasks++;
                            if (fut.get())
                                restoredElements++;
                        } else {
                            throw new Exception("Waited too long to get a future...should not happen");
                        }
                    } catch (InterruptedException e) {
                        log.error("Interrupted...", e);
                    } catch (ExecutionException e) {
                        log.error("Unexpected issue happened...", e);
                    }
                }
            }
        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
        return restoredElements;
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

    public class CacheOp implements Callable<Boolean> {
        private final Ehcache targetCache;
        private final Object key;
        private final Object value;

        public CacheOp(Ehcache targetCache, Object key, Object value) {
            this.targetCache = targetCache;
            this.key = key;
            this.value = value;
        }

        @Override
        public Boolean call() {
            if (null != key) {
                targetCache.put(new Element(key, value));
                return true;
            } else {
                log.error(String.format("Key is null...doing nothing"));
            }
            return false;
        }
    }

    public static class AppParams extends BaseAppParams {
        private String cacheName;
        private String filePath;
        private boolean noThreading;
        private int threadCounts;

        public AppParams() {
        }

        public String getCacheName() {
            return cacheName;
        }

        @Option(longName = "cache", description = "cache name")
        public void setCacheName(String cacheName) {
            this.cacheName = cacheName;
        }

        public String getFilePath() {
            return filePath;
        }

        @Option(longName = "filePath", description = "path of file to save cache state to")
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public boolean isNoThreading() {
            return noThreading;
        }

        @Option(longName = "disableThreading", description = "specify whether the operations should be run using parallel threads.")
        public void setNoThreading(boolean noThreading) {
            this.noThreading = noThreading;
        }

        public int getThreadCounts() {
            return threadCounts;
        }

        @Option(defaultValue = "4", longName = "threads", description = "if parallel construct enabled, specify how many threads to use for the operation")
        public void setThreadCounts(int cacheThreadCounts) {
            this.threadCounts = cacheThreadCounts;
        }
    }
}