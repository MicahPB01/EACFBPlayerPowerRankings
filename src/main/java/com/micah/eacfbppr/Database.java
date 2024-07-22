package com.micah.eacfbppr;

import Utilities.AppLogger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

public class Database {
    private static final Logger LOGGER = AppLogger.getLogger();

    private static Connection connection;

    public static Connection getConnection() {
        try {
            // Check if the connection is null or closed
            LOGGER.fine("Connecting to database");
            if (connection == null || connection.isClosed()) {
                //String url = "jdbc:mysql://localhost:3306/flyingfluffypanthers"; // production computer
                String url = "jdbc:mysql://localhost:3306/eacfbppr"; //  testing desktop server
                String user = "EACFBPPR";
                String password = "GKCO";



                connection = DriverManager.getConnection(url, user, password);

                LOGGER.info("Connected to database");
            }
        } catch (SQLException e) {
            LOGGER.severe("Could not reach database");
            e.printStackTrace();
            return null;
        }
        return connection;
    }
}

