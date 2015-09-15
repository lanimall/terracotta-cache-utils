package com.terracotta.tools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.InvalidOptionSpecificationException;
import com.lexicalscope.jewel.cli.Option;
import com.terracotta.tools.utils.BaseAppParams;
import com.terracotta.tools.utils.CacheFactory;
import net.sf.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class cacheMgrConnect {
    private static Logger log = LoggerFactory.getLogger(cacheMgrConnect.class);

    private static final int SLEEP_DEFAULT = 30000;
    private final AppParams runParams;

    public cacheMgrConnect(final AppParams params) {
        this.runParams = params;
    }

    public static void main(String[] args) {
        AppParams params = null;
        try {
            params = CliFactory.parseArgumentsUsingInstance(new AppParams(), args);

            try {
                cacheMgrConnect launcher = new cacheMgrConnect(params);

                launcher.run();

                System.out.println("Shutting down now...");

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
        CacheManager cm = CacheFactory.getInstance().getCacheManager();
        System.out.println(String.format("Now, waiting %d milliseconds...", runParams.getSleep()));
        Thread.sleep(runParams.getSleep());
    }

    public static class AppParams extends BaseAppParams {
        int sleep;

        public AppParams() {
        }

        public AppParams(int sleep) {
            this.sleep = sleep;
        }

        public int getSleep() {
            return sleep;
        }

        @Option(defaultValue = "" + SLEEP_DEFAULT, longName = "sleep", minimum = 0, description = "Amount of time (ms) to stay connected before shutdown. Default=" + SLEEP_DEFAULT + "ms")
        public void setSleep(int sleep) {
            this.sleep = sleep;
        }
    }
}