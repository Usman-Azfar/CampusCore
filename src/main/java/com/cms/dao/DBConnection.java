package com.cms.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database connections, from a small connection pool (HikariCP): opening a connection, especially
 * an encrypted one to a remote database, costs several network round trips, so open connections
 * are kept and reused. Callers use getConnection() and close() as before; close() returns the
 * connection to the pool.
 *
 * Settings come from environment variables (or JVM system properties with the same name), so no
 * real credentials live in the source code:
 *
 *   CMS_DB_URL        default jdbc:mysql://localhost:3306/cms_ead?useSSL=false&connectionTimeZone=LOCAL
 *   CMS_DB_USER       default root
 *   CMS_DB_PASSWORD   default root
 *   CMS_DB_POOL_SIZE  default 5 (most connections kept open at once)
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

    private static volatile HikariDataSource pool;

    // A JVM system property (-DCMS_DB_USER=...) wins over the environment variable.
    // A variable set to an empty value is used as is (e.g. a MySQL user with no password).
    private static String setting(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null)
            value = System.getenv(name);
        return value == null ? fallback : value;
    }

    static int poolSize() {
        try {
            int n = Integer.parseInt(setting("CMS_DB_POOL_SIZE", "5").trim());
            return n < 1 ? 1 : Math.min(n, 50);
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    // Created on first use, so the application still starts when the database is briefly unreachable
    private static HikariDataSource pool() {
        HikariDataSource p = pool;
        if (p == null) {
            synchronized (DBConnection.class) {
                p = pool;
                if (p == null) {
                    HikariConfig c = new HikariConfig();
                    c.setPoolName("campuscore");
                    c.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    c.setJdbcUrl(URL);
                    c.setUsername(USER);
                    c.setPassword(PASSWORD);
                    c.setMaximumPoolSize(poolSize());
                    c.setMinimumIdle(1);
                    c.setConnectionTimeout(30_000);     // wait at most 30 s for a free connection
                    c.setIdleTimeout(600_000);          // close extra idle connections after 10 min
                    c.setKeepaliveTime(120_000);        // ping idle connections every 2 min so networks do not drop them
                    c.setMaxLifetime(1_800_000);        // replace each connection after 30 min
                    c.setInitializationFailTimeout(-1); // do not fail start-up if the database is down
                    // Prepared statements are re-used per connection by the MySQL driver
                    c.addDataSourceProperty("cachePrepStmts", "true");
                    c.addDataSourceProperty("prepStmtCacheSize", "250");
                    c.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    pool = p = new HikariDataSource(c);
                }
            }
        }
        return p;
    }

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    /** Closes all pooled connections (called when the application stops or is redeployed). */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }
}
