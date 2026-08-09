package com.ading.ai.hermes.config;

public enum ConfigurationSource {
    LOCAL_FILE("本地配置文件"),
    ENVIRONMENT("环境变量"),
    MIXED("本地配置文件和环境变量"),
    WEB_FORM("Web 页面"),
    INTERACTIVE("交互输入"),
    UNCONFIGURED("未配置");

    private final String displayName;

    ConfigurationSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
