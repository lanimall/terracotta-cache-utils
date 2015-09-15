package com.terracotta.tools;

import com.terracotta.tools.utils.CacheFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class tcPing {
    private static Logger log = LoggerFactory.getLogger(tcPing.class);

    private final int step;
    private final String uid;
    private final int count;

    public tcPing(int step, String uid, int count) {
        this.step = step;
        this.uid = uid;
        this.count = count;
    }

    private static void usage() {
        System.out
                .println("Usage tcPing  [1/2] [unique id] [number of elements]");
        System.out
                .println("tcPing needs to be executed in a 2 step process. The first argument 1 is to load and 2 is retrieve and avalidate");
    }

    public static void main(String args[]) {
        int step = 1;
        String uid = "";
        int count = 1000;
        try {
            // Parse Input Arguments
            if (args.length < 3) {
                usage();
                System.exit(1);
            } else {
                step = Integer.parseInt(args[0]);
                uid = args[1];
                count = Integer.parseInt(args[2]);
            }

            new tcPing(step, uid, count).run();

            System.exit(0);
        } catch (Exception ex) {
            log.error("", ex);
            System.exit(1);
        } finally {
            CacheFactory.getInstance().getCacheManager().shutdown();
        }
    }

    public void run() throws Exception {
        CacheManager cacheManager = CacheFactory.getInstance().getCacheManager();
        if (cacheManager == null) {
            throw new Exception("Unable to create cache manager. Check if ehcache.xml is in path.");
        }

        Cache pingCache = cacheManager.getCache("terracottaPing");
        if (pingCache == null) {
            throw new Exception("Unable to create cache 'terracottaPing'. Check if ehcache.xml has this cache defined.");
        }

        if (step == 1) {
            System.out.println("Starting Step 1 of tcPing..");
            step1(pingCache, uid, count);
            System.out.println("Step 1 of tcPing done..");
            Thread.sleep(1000);
        } else {
            System.out.println("Starting Step 2 of tcPing..");
            step2(pingCache, uid, count);
            System.out.println("Step 2 of tcPing done..");
        }
    }

    private void step1(Cache pingCache, String uid, int count) throws Exception {
        //Clear the cache to remove stale data
        System.out.println("Now clearing all objects in cache");
        pingCache.removeAll();
        if (pingCache.getSize() > 0) {
            throw new Exception("Unable to clear cache before Starting ping.");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
        Date date = new Date();
        for (int i = 0; i < count; i++) {
            String key = uid + "_" + new Integer(i).toString();
            pingCache.put(new Element(key, sdf.format(date)));
        }

        if (count != pingCache.getSize())
            throw new Exception("Unexpected amount of objects in cache (added amount[" + count + "] != cache size[" + pingCache.getSize() + "])");

        System.out.println("Loaded cache " + pingCache.getName() + ". Size =  " + pingCache.getSize());
    }

    private void step2(Cache pingCache, String uid, int count) throws Exception {
        System.out.println("Found cache " + pingCache.getName() + " with Size " + pingCache.getSize());
        System.out.println("Now verifying validity of all objects in cache");

        int cacheCount = 0;
        for (cacheCount = 0; cacheCount < count; cacheCount++) {
            String key = uid + "_" + new Integer(cacheCount).toString();
            Element val = pingCache.get(key);
            if (val == null) {
                throw new Exception("Cache elements not found. Failed to validate key=" + key);
            }
        }

        if (cacheCount != pingCache.getSize())
            throw new Exception("Unexpected amount of objects in cache (fetched amount[" + cacheCount + "] != cache size[" + pingCache.getSize() + "])");

        System.out.println("Found " + cacheCount + " valid objects in cache");

        System.out.println("Now clearing all objects in cache");
        pingCache.removeAll();

        if (pingCache.getSize() > 0) {
            throw new Exception("Unable to clear cache.");
        }

        System.out.println("Cleared " + pingCache.getName() + ". Final Size " + pingCache.getSize());
    }
}
