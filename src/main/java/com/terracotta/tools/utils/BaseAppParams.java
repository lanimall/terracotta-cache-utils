package com.terracotta.tools.utils;

import com.lexicalscope.jewel.cli.Option;

/**
 * Created by FabienSanglier on 10/29/14.
 */
public class BaseAppParams {
    boolean help;

    public boolean isHelp() {
        return help;
    }

    @Option(helpRequest = true, description = "this help message")
    public void setHelp(boolean help) {
        this.help = help;
    }
}