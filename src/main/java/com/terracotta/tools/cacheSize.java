package com.terracotta.tools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.InvalidOptionSpecificationException;
import com.lexicalscope.jewel.cli.Option;
import com.terracotta.tools.utils.AppConstants;
import com.terracotta.tools.utils.BaseAppParams;
import com.terracotta.tools.utils.CacheFactory;
import net.sf.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class cacheSize {
    private static Logger log = LoggerFactory.getLogger(cacheSize.class);

    private static final int SLEEP_DEFAULT = 1000;
    private static final int ITERATION_LIMIT_DEFAULT = 2;

    private final AppParams runParams;

    public cacheSize(final AppParams params) {
        this.runParams = params;
    }

    public cacheSize(String cacheNameCSV, int sleep, int iterationLimit) {
        this.runParams = new AppParams(cacheNameCSV, sleep, iterationLimit);
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheSize launcher = new cacheSize(params);

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

    public void run() throws Exception {
        if (runParams.getCacheNames() == null || "".equals(runParams.getCacheNames())) {
            throw new Exception("No cache name defined. Doing nothing.");
        } else {
            System.out.println("-----------------------------------------------------------------");
            System.out.println("Start Cache Sizes at " + new Date() + "\n");

            String[] cname;
            if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheNamesCSV())) {
                System.out.println("Requested to get size for all caches...");
                cname = CacheFactory.getInstance().getCacheManager().getCacheNames();
            } else {
                cname = runParams.getCacheNames();
            }

            //perform operation
            int it = 0;
            while (it < runParams.getIterationLimit()) {
                System.out.println(String.format("---------------- Iteration %d ----------------", it + 1));
                for (int i = 0; i < cname.length; i++) {
                    Cache cache = CacheFactory.getInstance().getCacheManager().getCache(cname[i]);
                    if (null != cache) {
                        printSize(cache);
                    } else {
                        System.out.println(String.format("Cache %s not found.", cname[i]));
                    }
                }
                Thread.sleep(runParams.getSleep());
                it++;
            }

            System.out.println("End Cache Sizes " + new Date());
        }
    }

    private void printSize(Cache cache) throws Exception {
        if (null != cache) {
            if (AppConstants.useKeyWithExpiryCheck) {
                System.out.println(String.format("%s %d", cache.getName(), cache.getKeysWithExpiryCheck().size()));
            } else {
                System.out.println(String.format("%s %d", cache.getName(), cache.getSize()));
            }
        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
    }

    public static class AppParams extends BaseAppParams {
        private String cacheNamesCSV;
        int sleep;
        int iterationLimit;

        public AppParams() {
        }

        public AppParams(String cacheNamesCSV, int sleep, int iterationLimit) {
            this.cacheNamesCSV = cacheNamesCSV;
            this.sleep = sleep;
            this.iterationLimit = iterationLimit;
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

        @Option(defaultValue = "", longName = "caches", description = "comma-separated cache names, or keyword \"all\" to include all caches")
        public void setCacheNames(String cacheNames) {
            this.cacheNamesCSV = cacheNames;
        }

        public int getSleep() {
            return sleep;
        }

        @Option(defaultValue = "" + SLEEP_DEFAULT, longName = "sleep", description = "amount of time (ms) to sleep between size iterations")
        public void setSleep(int sleep) {
            this.sleep = sleep;
        }

        public int getIterationLimit() {
            return iterationLimit;
        }

        @Option(defaultValue = "" + ITERATION_LIMIT_DEFAULT, longName = "iterations", description = "amount of times to perform the cache size operation")
        public void setIterationLimit(int iterationLimit) {
            this.iterationLimit = iterationLimit;
        }
    }
}