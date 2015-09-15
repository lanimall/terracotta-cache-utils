package com.terracotta.tools.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Fabien Sanglier
 */
public class CacheStatsDefinition implements Cloneable {
    private static final Logger log = LoggerFactory.getLogger(CacheStatsDefinition.class);

    private final String cacheName;
    private final StatCacheObjectType cacheStatObjectType;
    private final StatSizingMethod cacheObjectSizingType;

    public enum StatCacheObjectType {KEY, VALUE}

    public enum StatSizingMethod {SERIALIZED, UNSERIALIZED}

    public CacheStatsDefinition(String cacheName, StatCacheObjectType cacheStatObjectType, StatSizingMethod cacheObjectSizingType) {
        this.cacheName = cacheName;
        this.cacheStatObjectType = cacheStatObjectType;
        this.cacheObjectSizingType = cacheObjectSizingType;
    }

    public static CacheStatsDefinition key_serialized(String cacheName) {
        return new CacheStatsDefinition(cacheName, StatCacheObjectType.KEY, StatSizingMethod.SERIALIZED);
    }

    public static CacheStatsDefinition key_unserialized(String cacheName) {
        return new CacheStatsDefinition(cacheName, StatCacheObjectType.KEY, StatSizingMethod.UNSERIALIZED);
    }

    public static CacheStatsDefinition value_serialized(String cacheName) {
        return new CacheStatsDefinition(cacheName, StatCacheObjectType.VALUE, StatSizingMethod.SERIALIZED);
    }

    public static CacheStatsDefinition value_unserialized(String cacheName) {
        return new CacheStatsDefinition(cacheName, StatCacheObjectType.VALUE, StatSizingMethod.UNSERIALIZED);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheStatsDefinition that = (CacheStatsDefinition) o;

        if (cacheName != null ? !cacheName.equals(that.cacheName) : that.cacheName != null) return false;
        if (cacheObjectSizingType != that.cacheObjectSizingType) return false;
        if (cacheStatObjectType != that.cacheStatObjectType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = cacheName != null ? cacheName.hashCode() : 0;
        result = 31 * result + cacheStatObjectType.hashCode();
        result = 31 * result + cacheObjectSizingType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String
                .format("Cache: %s, Target: %s, Size Type: %s",
                        cacheName,
                        cacheStatObjectType.toString(),
                        cacheObjectSizingType.toString());
    }
}
