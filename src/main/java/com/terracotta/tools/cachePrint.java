package com.terracotta.tools;

import com.lexicalscope.jewel.cli.*;
import com.terracotta.tools.utils.AppConstants;
import com.terracotta.tools.utils.BaseAppParams;
import com.terracotta.tools.utils.CacheFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class cachePrint {
    private static Logger log = LoggerFactory.getLogger(cachePrint.class);
    private static final boolean isDebug = log.isDebugEnabled();

    private static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
    private static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private final AppParams runParams;

    public cachePrint(final String cacheNames, final String cacheKeys, final String cacheKeysType) {
        Cli<AppParams> cli = CliFactory.createCliUsingInstance(new AppParams());
        this.runParams = cli.parseArguments("--caches", cacheNames, "--keys", cacheKeys, "--keysType", cacheKeysType);
    }

    public cachePrint(final AppParams params) {
        this.runParams = params;
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cachePrint launcher = new cachePrint(params);

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
            if (runParams.getCacheKeysCSV() == null || "".equals(runParams.getCacheKeysCSV())) {
                throw new Exception("No cache key specified. Doing nothing.");
            } else {
                Cache cache = CacheFactory.getInstance().getCacheManager().getCache(runParams.getCacheNames());
                if (cache == null) {
                    throw new Exception("Cache " + runParams.getCacheNames() + " not found.");
                }

                System.out.println("-----------------------------------------------------------------");
                System.out.println(String.format("Start Cache Elements Print Operation - %s", dateTimeFormatter.format(new Date())));
                if (runParams.isDateTimeFilterEnabled()) {
                    System.out.println(String.format("Date Filtering enabled. Will Print only the elements matching the filter"));
                }

                List cacheKeyList = null;
                if (AppConstants.PARAMS_ALL.equalsIgnoreCase(runParams.getCacheKeysCSV())) {
                    cacheKeyList = (AppConstants.useKeyWithExpiryCheck) ? cache.getKeysWithExpiryCheck() : cache.getKeys();
                    System.out.println(String.format("Requesting all (=%d) Keys (type=%s) in cache %s", cacheKeyList.size(), runParams.getCacheKeysType().getTypeString(), cache.getName()));
                } else {
                    cacheKeyList = Arrays.asList(runParams.getCacheKeys());
                    System.out.println(String.format("Requesting %d Keys (type=%s) in cache %s", cacheKeyList.size(), runParams.getCacheKeysType().getTypeString(), cache.getName()));
                }

                int keyInCacheCount = printElements(cache, cacheKeyList);

                if (runParams.isDateTimeFilterEnabled()) {
                    System.out.println(String.format("Date Filtering enabled. Printed only the elements matching the filter = %d", keyInCacheCount));
                } else {
                    System.out.println(String.format("Keys found in cache = %d", keyInCacheCount));
                }

                System.out.println(String.format("End Cache Keys Print Operation - %s", dateTimeFormatter.format(new Date())));
            }
        }
    }

    private int printElements(final Cache cache, final List cacheKeysList) throws Exception {
        int keyInCacheCount = 0;
        if (null != cache) {
            System.out.println("---------- CSV Output -----------");

            StringWriter sw = new StringWriter();

            //CSV header
            sw.append("key").append(",").append("in-cache");
            if (runParams.isPrintKeyType())
                sw.append(",").append("type");
            if (runParams.isPrintSerializedSize())
                sw.append(",").append("serialized-size");
            if (runParams.isPrintCreationTime())
                sw.append(",").append("created");
            if (runParams.isPrintLastUpdatedTime())
                sw.append(",").append("updated");
            if (runParams.isPrintLastAccessedTime())
                sw.append(",").append("last accessed");
            if (runParams.isPrintExpirationTime())
                sw.append(",").append("expire");
            if (runParams.isPrintValue())
                sw.append(",").append("value");

            System.out.println(sw.toString());

            Iterator iterator = cacheKeysList.iterator();
            while (iterator.hasNext()) {
                sw = new StringWriter();

                Object key = iterator.next();
                keyInCacheCount += printSingleElement(cache, key, sw);

                if (sw.getBuffer().length() > 0)
                    System.out.println(sw.toString());
            }

            System.out.println("---------- End CSV Output -----------");
        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
        return keyInCacheCount;
    }

    private int printSingleElement(final Cache cache, final Object key, final StringWriter sw) throws Exception {
        int keyInCacheCount = 0;
        if (null != cache) {
            Element e = cache.getQuiet(key);
            if (!runParams.isDateTimeFilterEnabled() || (runParams.isDateTimeFilterEnabled() && matchElementDateFilter(e))) {
                if (e != null) {
                    sw.append((null != e.getObjectKey()) ? e.getObjectKey().toString() : "null");
                    sw.append(",").append(Boolean.toString(true));
                    if (runParams.isPrintKeyType())
                        sw.append(",").append((null != e.getObjectKey()) ? e.getObjectKey().getClass().getName() : "null");
                    if (runParams.isPrintSerializedSize())
                        sw.append(",").append(new Long(e.getSerializedSize()).toString());
                    if (runParams.isPrintCreationTime())
                        sw.append(",").append(dateTimeFormatter.format(new Date(e.getCreationTime())));
                    if (runParams.isPrintLastUpdatedTime())
                        sw.append(",").append(dateTimeFormatter.format(new Date(e.getLastUpdateTime())));
                    if (runParams.isPrintLastAccessedTime())
                        sw.append(",").append(dateTimeFormatter.format(new Date(e.getLastAccessTime())));
                    if (runParams.isPrintExpirationTime())
                        sw.append(",").append(dateTimeFormatter.format(new Date(e.getExpirationTime())));
                    if (runParams.isPrintValue())
                        sw.append(",").append(e.getObjectValue().toString());

                    keyInCacheCount++;
                } else {
                    sw.append((null != key) ? key.toString() : "null");
                    sw.append(",").append(Boolean.toString(false));

                    //append commas here just to be CSV compliant
                    if (runParams.isPrintKeyType())
                        sw.append(",");
                    if (runParams.isPrintSerializedSize())
                        sw.append(",");
                    if (runParams.isPrintCreationTime())
                        sw.append(",");
                    if (runParams.isPrintLastUpdatedTime())
                        sw.append(",");
                    if (runParams.isPrintLastAccessedTime())
                        sw.append(",");
                    if (runParams.isPrintExpirationTime())
                        sw.append(",");
                    if (runParams.isPrintValue())
                        sw.append(",");
                }
            }
        } else {
            throw new Exception("Cache is null...doing nothing.");
        }
        return keyInCacheCount;
    }

    private boolean matchElementDateFilter(Element e) {
        boolean matched = false;
        if (null != e && null != runParams.getDate()) {
            AppConstants.DateComparePrecision precision = runParams.getDateComparePrecision();

            long dateTimeToCompare = precision.transformTimeWithPrecision(runParams.getDate()).getTime();
            long elementDate = precision.transformTimeWithPrecision(new Date(runParams.getCacheElementDateType().getCacheElementDate(e))).getTime();

            matched = runParams.getDateCompareOperation().compare(elementDate, dateTimeToCompare);
        }
        return matched;
    }

    public static class AppParams extends BaseAppParams {
        private String cacheNames;
        private String cacheKeysCSV;
        private String cacheKeysType;
        private boolean printKeyType;
        private boolean printValue;
        private boolean printCreationTime;
        private boolean printLastUpdatedTime;
        private boolean printLastAccessedTime;
        private boolean printExpirationTime;
        private boolean printSerializedSize;
        private boolean dateTimeFilterEnabled;
        private AppConstants.CacheElementDateType cacheElementDateType;
        private AppConstants.DateCompareOperation dateCompareOperation;
        private AppConstants.DateComparePrecision dateComparePrecision;
        private Date date;

        public AppParams() {
        }

        public String getCacheNames() {
            return cacheNames;
        }

        @Option(longName = "caches", description = "comma-separated cache names, or keyword \"all\" to include all caches")
        public void setCacheNames(String cacheNames) {
            this.cacheNames = cacheNames;
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

        public boolean isPrintKeyType() {
            return printKeyType;
        }

        @Option(longName = "printKeyType")
        public void setPrintKeyType(boolean printKeyType) {
            this.printKeyType = printKeyType;
        }

        public boolean isPrintValue() {
            return printValue;
        }

        @Option(longName = "printValue")
        public void setPrintValue(boolean printValue) {
            this.printValue = printValue;
        }

        public boolean isPrintCreationTime() {
            return printCreationTime;
        }

        @Option(defaultValue = "true", longName = "printCreationTime")
        public void setPrintCreationTime(boolean printCreationTime) {
            this.printCreationTime = printCreationTime;
        }

        public boolean isPrintLastUpdatedTime() {
            return printLastUpdatedTime;
        }

        @Option(defaultValue = "true", longName = "printLastUpdatedTime")
        public void setPrintLastUpdatedTime(boolean printLastUpdatedTime) {
            this.printLastUpdatedTime = printLastUpdatedTime;
        }

        public boolean isPrintLastAccessedTime() {
            return printLastAccessedTime;
        }

        @Option(defaultValue = "true", longName = "printLastAccessedTime")
        public void setPrintLastAccessedTime(boolean printLastAccessedTime) {
            this.printLastAccessedTime = printLastAccessedTime;
        }

        public boolean isPrintExpirationTime() {
            return printExpirationTime;
        }

        @Option(defaultValue = "true", longName = "printExpirationTime")
        public void setPrintExpirationTime(boolean printExpirationTime) {
            this.printExpirationTime = printExpirationTime;
        }

        public boolean isPrintSerializedSize() {
            return printSerializedSize;
        }

        @Option(longName = "printSerializedSize")
        public void setPrintSerializedSize(boolean printSerializedSize) {
            this.printSerializedSize = printSerializedSize;
        }

        public boolean isDateTimeFilterEnabled() {
            return dateTimeFilterEnabled;
        }

        @Option(longName = "dateTimeFilterEnabled")
        public void setDateTimeFilterEnabled(boolean dateTimeFilterEnabled) {
            this.dateTimeFilterEnabled = dateTimeFilterEnabled;
        }

        public AppConstants.CacheElementDateType getCacheElementDateType() {
            return cacheElementDateType;
        }

        @Option(defaultToNull = true, longName = "cacheElementDateType", description = "specify which cache element datetime to pick for comparison (created, lastUpdated, lastAccessed, expireAt)")
        public void setCacheElementDateType(AppConstants.CacheElementDateType cacheElementDateType) {
            this.cacheElementDateType = cacheElementDateType;
        }

        public AppConstants.DateCompareOperation getDateCompareOperation() {
            return dateCompareOperation;
        }

        @Option(defaultToNull = true, longName = "dateCompareOperation", description = "specify the date operation (before, after, equal) to perform between --cacheElementDateType and --datetime")
        public void setDateCompareOperation(AppConstants.DateCompareOperation dateCompareOperation) {
            this.dateCompareOperation = dateCompareOperation;
        }

        public AppConstants.DateComparePrecision getDateComparePrecision() {
            return dateComparePrecision;
        }

        @Option(defaultValue = "second", longName = "datePrecision", description = "specify the date precision (second,minute,hour,day,month,year) to use for the comparison")
        public void setDateComparePrecision(AppConstants.DateComparePrecision dateComparePrecision) {
            this.dateComparePrecision = dateComparePrecision;
        }

        public Date getDate() {
            return date;
        }

        @Option(defaultToNull = true, longName = "datetime", pattern = "now|\\d{4}\\d{2}\\d{2}[-]\\d{2}\\d{2}\\d{2}|\\d{4}\\d{2}\\d{2}", description = "specify the date info to compare on. Allowed formats are \"yyyyMMdd\" or \"yyyyMMdd-HHmmss\"")
        public void setDate(String dateInString) {
            if (null != dateInString && !"".equals(dateInString)) {
                if ("now".equalsIgnoreCase(dateInString))
                    this.date = new Date();
                else {
                    try {
                        if (dateInString.length() == dateFormatter.toPattern().length())
                            this.date = dateFormatter.parse(dateInString);
                        else if (dateInString.length() == dateTimeFormatter.toPattern().length())
                            this.date = dateTimeFormatter.parse(dateInString);
                        else {
                            this.date = null;
                            log.error("Date string does not match valid date patterns.");
                        }
                    } catch (ParseException e) {
                        this.date = null;
                        log.error("Date string cannot be parsed using valid date patterns.", e);
                    }
                }
            } else {
                this.date = null;
            }
        }
    }
}