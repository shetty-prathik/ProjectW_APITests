package com.projectw.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads and exposes values from src/test/resources/config.properties.
 * System properties override file values (useful for CI pipelines).
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream is = ConfigManager.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new RuntimeException("config.properties not found on classpath");
            }
            props.load(is);
            log.info("Loaded config.properties");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    private ConfigManager() {}

    /** Returns the property value; system property takes precedence. */
    public static String get(String key) {
        return System.getProperty(key, props.getProperty(key));
    }

    public static String getBaseUrl() {
        return get("base.url");
    }

    public static String getApiBasePath() {
        return get("api.base.path");
    }

    public static String getTestEid() {
        return get("test.eid");
    }

    public static String getAdminEmail() {
        return get("test.admin.email");
    }

    public static String getAdminPassword() {
        return get("test.admin.password");
    }

    public static String getAdminUserId() {
        return get("test.admin.user_id");
    }

    public static String getEmployeeEmail() {
        return get("test.employee.email");
    }

    public static String getEmployeePassword() {
        return get("test.employee.password");
    }

    public static int getConnectionTimeout() {
        return Integer.parseInt(get("connection.timeout"));
    }

    public static int getReadTimeout() {
        return Integer.parseInt(get("read.timeout"));
    }
}
