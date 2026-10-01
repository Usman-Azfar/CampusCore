package com.cms.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database connection settings come from environment variables (or JVM system properties
 * with the same name), so no real credentials live in the source code:
 *
 *   CMS_DB_URL       default jdbc:mysql://localhost:3306/cms_ead?useSSL=false&connectionTimeZone=LOCAL
 *   CMS_DB_USER      default root
 *   CMS_DB_PASSWORD  default root
 *
 * The defaults suit a local development MySQL only.
 */
public class DBConnection {
    // connectionTimeZone=LOCAL: MySQL and the JVM share this machine's time zone. The old
    // serverTimezone=UTC shifted every displayed TIMESTAMP by the UTC offset. (SERVER cannot
    // be used: MySQL on Windows reports a zone name like "Russia TZ 4 Standard Time" that the
    // driver does not recognise.) If MySQL runs elsewhere, set its zone id in CMS_DB_URL,
    // e.g. connectionTimeZone=Asia/Karachi.
    private static final String URL = setting("CMS_DB_URL",
            "jdbc:mysql://localhost:3306/cms_ead?useSSL=false&connectionTimeZone=LOCAL");
    private static final String USER = setting("CMS_DB_USER", "root");
    private static final String PASSWORD = setting("CMS_DB_PASSWORD", "root");

    static {
        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load MySQL Driver");
        }
    }

    // A JVM system property (-DCMS_DB_USER=...) wins over the environment variable.
    // A variable set to an empty value is used as is (e.g. a MySQL user with no password).
    private static String setting(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null)
            value = System.getenv(name);
        return value == null ? fallback : value;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
