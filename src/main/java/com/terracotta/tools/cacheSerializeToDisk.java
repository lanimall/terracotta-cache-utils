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
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class cacheSerializeToDisk {
    private static Logger log = LoggerFactory.getLogger(cacheSerializeToDisk.class);
    private static final boolean isDebug = log.isDebugEnabled();
    private static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
    private final AppParams runParams;

    public cacheSerializeToDisk(final AppParams params) {
        this.runParams = params;
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheSerializeToDisk launcher = new cacheSerializeToDisk(params);

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
        if (runParams.getCacheName() == null || "".equals(runParams.getCacheName())) {
            throw new Exception("No cache name defined. Doing nothing.");
        } else {
            Cache cache = CacheFactory.getInstance().getCacheManager().getCache(runParams.getCacheName());
            if (cache == null) {
                throw new Exception("Cache " + runParams.getCacheName() + " not found.");
            }

            List cacheKeyList = null;
            if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheKeysCSV())) {
                cacheKeyList = (AppConstants.useKeyWithExpiryCheck) ? cache.getKeysWithExpiryCheck() : cache.getKeys();
                System.out.println(String.format("Requesting all (=%d) Keys (type=%s) in cache %s", cacheKeyList.size(), runParams.getCacheKeysType().getTypeString(), cache.getName()));
            } else {
                cacheKeyList = Arrays.asList(runParams.getCacheKeys());
                System.out.println(String.format("Requesting %d Keys (type=%s) in cache %s", cacheKeyList.size(), runParams.getCacheKeysType().getTypeString(), cache.getName()));
            }

            int savedElements = serializeAndSaveElements(cache, cacheKeyList);

            System.out.println(String.format("Saved cache elements = %d", savedElements));
            System.out.println(String.format("End Cache Save-to-Disk Operation - %s", dateTimeFormatter.format(new Date())));
        }
    }

    private int serializeAndSaveElements(final Cache cache, final List cacheKeysList) throws Exception {
        int savedElements = 0;
        if (null != cache) {

            File file = new File(runParams.getFilePath());
            try {
                //use buffering
                OutputStream fileStream = new FileOutputStream(file);
                OutputStream buffer = new BufferedOutputStream(fileStream);
                ObjectOutput output = new ObjectOutputStream(buffer);

                try {
                    Iterator iterator = cacheKeysList.iterator();
                    while (iterator.hasNext()) {
                        Pair<byte[], byte[]> keyValuePair = serializeElement(cache, iterator.next());
                        if (null != keyValuePair) {
                            output.writeObject(keyValuePair);
                            savedElements++;
                        }
                    }
                } finally {
                    output.close();
                }
            } catch (IOException ex) {
                log.error("Cannot perform output.", ex);
            }


        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
        return savedElements;
    }

    private Pair<byte[], byte[]> serializeElement(final Cache cache, final Object key) throws Exception {
        Pair<byte[], byte[]> serializedElement = null;
        if (null != cache) {
            Element e = cache.getQuiet(key);
            if (null != e) {
                serializedElement = new ImmutablePair<byte[], byte[]>(
                        SerializationUtils.serialize(e.getKey()),
                        SerializationUtils.serialize(e.getValue()));
            }
        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
        return serializedElement;
    }

    public static class AppParams extends BaseAppParams {
        private String cacheName;
        private String cacheKeysCSV;
        private String cacheKeysType;
        private String filePath;

        public AppParams() {
        }

        public String getCacheName() {
            return cacheName;
        }

        @Option(longName = "cache", description = "cache name")
        public void setCacheName(String cacheName) {
            this.cacheName = cacheName;
        }

        public String getCacheKeysCSV() {
            return cacheKeysCSV;
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

        @Option(longName = "keys", description = "comma-separated cache keys, or keyword \"all\" to include all keys")
        public void setCacheKeys(String cacheKeys) {
            this.cacheKeysCSV = cacheKeys;
        }

        public AppConstants.KeyPrimitiveType getCacheKeysType() {
            return AppConstants.KeyPrimitiveType.getPrimitiveType(cacheKeysType);
        }

        @Option(defaultValue = "String", longName = "keysType", description = "Specifies the object type to cast the provided keys to")
        public void setCacheKeysType(String cacheKeysType) {
            this.cacheKeysType = cacheKeysType;
        }

        public String getFilePath() {
            return filePath;
        }

        @Option(longName = "filePath", description = "path of file to save cache state to")
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }
}