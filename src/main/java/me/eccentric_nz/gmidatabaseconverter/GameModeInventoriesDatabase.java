package me.eccentric_nz.gmidatabaseconverter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class GameModeInventoriesDatabase {

    private static final GameModeInventoriesDatabase instance = new GameModeInventoriesDatabase();
    public Connection connection = null;
    public Statement statement;

    public static synchronized GameModeInventoriesDatabase getInstance() {
        return instance;
    }

    public void setConnection(String path) throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Clone is not allowed.");
    }
}
