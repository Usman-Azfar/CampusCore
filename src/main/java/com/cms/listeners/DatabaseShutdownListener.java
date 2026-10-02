package com.cms.listeners;

import com.cms.dao.DBConnection;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * When the application stops or is redeployed: closes the connection pool, unregisters the MySQL
 * driver loaded by this application and stops its clean-up thread, so Tomcat does not keep the old
 * application in memory (and does not warn about a leaked JDBC driver).
 */
public class DatabaseShutdownListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        DBConnection.shutdown();

        ClassLoader app = Thread.currentThread().getContextClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getClassLoader() == app) {
                try {
                    DriverManager.deregisterDriver(d);
                } catch (SQLException e) {
                    event.getServletContext().log("CampusCore: could not unregister JDBC driver " + d, e);
                }
            }
        }
        com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
    }
}
