package com.cms.listeners;

import com.cms.dao.UserDAO;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Hashes any plain-text passwords when the application starts, so a database created
 * before password hashing was added is converted without manual steps. Users keep
 * logging in with the same passwords. Does nothing once every password is hashed.
 */
public class PasswordMigrationListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        int converted = new UserDAO().hashPlainTextPasswords();
        if (converted > 0)
            event.getServletContext().log("CampusCore: hashed " + converted + " plain-text password(s).");
    }
}
