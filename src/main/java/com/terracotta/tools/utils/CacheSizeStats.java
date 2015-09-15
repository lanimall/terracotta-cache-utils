package com.terracotta.tools.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.HashSet;

/**
 * @author Fabien Sanglier
 */
public class CacheSizeStats implements Cloneable {
    private static final NumberFormat nf = NumberFormat.getInstance();
    private static final Logger log = LoggerFactory.getLogger(CacheSizeStats.class);

    private final HashSet<String> objectTypes = new HashSet<String>();
    private long objCount;
    private long totalSize;
    private long minSize, maxSize;

    public CacheSizeStats() {
        reset();
    }

    public CacheSizeStats(CacheSizeStats stat) {
        if (stat != null) {
            init(stat.getObjCount(), stat.getTotalSize(), stat.getMinSize(), stat.getMaxSize());
        } else {
            throw new IllegalArgumentException("Stat may not be null");
        }
    }

    /**
     * resets the stats
     */
    public void reset() {
        init(0, 0, Long.MAX_VALUE, Long.MIN_VALUE);
    }

    private void init(long objCount, long totalSize, long minSize, long maxSize) {
        this.objCount = objCount;
        this.totalSize = totalSize;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    /**
     * Add {@link com.terracotta.tools.utils.CacheSizeStats} to the current.
     *
     * @param stat
     * @return {@link this}
     */
    public CacheSizeStats add(CacheSizeStats stat) {
        if (null != stat) {
            this.objCount += stat.objCount;
            this.totalSize += stat.totalSize;

            if (stat.minSize < this.minSize)
                this.minSize = stat.minSize;

            if (stat.maxSize > this.maxSize)
                this.maxSize = stat.maxSize;

            if (null != stat.objectTypes)
                objectTypes.addAll(stat.objectTypes);
        }
        return this;
    }

    /**
     * Add transaction length
     *
     * @param size transaction length
     */
    public void add(long size, String objectType) {
        objCount += 1;
        totalSize += size;

        if (size < minSize) {
            minSize = size;
        }
        if (size > maxSize) {
            maxSize = size;
        }

        if (null != objectType)
            objectTypes.add(objectType);
    }

    /**
     * @return average size
     */
    public double getAvgSize() {
        if (objCount > 0)
            return (double) totalSize / objCount;
        return 0;
    }

    /**
     * @return total size
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * @return max object size
     */
    public long getMaxSize() {
        return (maxSize == Long.MIN_VALUE) ? 0 : maxSize;
    }

    /**
     * @return min object size
     */
    public long getMinSize() {
        return (minSize == Long.MAX_VALUE) ? 0 : minSize;
    }

    /**
     * @return total count
     */
    public long getObjCount() {
        return objCount;
    }

    public String getCacheItemTypes() {
        StringBuilder sb = new StringBuilder();
        for (String type : objectTypes) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(type);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String
                .format("Object Types: {%s}, Size:[Sampled Count: %d, Avg Size: %s KB, Min Size: %s KB, Max Size: %s KB]",
                        getCacheItemTypes(),
                        getObjCount(),
                        nf.format(getAvgSize() / 1024),
                        nf.format(getMinSize() / 1024),
                        nf.format(getMaxSize() / 1024));
    }
}
