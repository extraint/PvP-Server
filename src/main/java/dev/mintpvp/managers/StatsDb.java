package dev.mintpvp.managers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** shared stats database */
final class StatsDb {

    record Row(UUID id, String name, String data, long updated) {}

    private final String url, user, pass;
    private final Object lock = new Object();
    private Connection conn;

    StatsDb(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    void connect() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
        }
        synchronized (lock) {
            try (Statement st = c().createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mint_stats (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32), data MEDIUMTEXT, updated BIGINT)");
            }
        }
    }

    private Connection c() throws SQLException {
        if (conn == null || conn.isClosed() || !conn.isValid(3)) {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) { }
            }
            conn = user == null || user.isEmpty() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, pass);
        }
        return conn;
    }

    List<Row> fetch(long since) throws SQLException {
        synchronized (lock) {
            try (PreparedStatement ps = c().prepareStatement("SELECT uuid, name, data, updated FROM mint_stats WHERE updated >= ?")) {
                ps.setLong(1, since);
                return read(ps.executeQuery());
            }
        }
    }

    Row fetchOne(UUID id) throws SQLException {
        synchronized (lock) {
            try (PreparedStatement ps = c().prepareStatement("SELECT uuid, name, data, updated FROM mint_stats WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                List<Row> rows = read(ps.executeQuery());
                return rows.isEmpty() ? null : rows.get(0);
            }
        }
    }

    private static List<Row> read(ResultSet rs) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (rs) {
            while (rs.next()) {
                try {
                    out.add(new Row(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getLong(4)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    void upsert(UUID id, String name, String data, long updated) throws SQLException {
        synchronized (lock) {
            Connection cn = c();
            try (PreparedStatement up = cn.prepareStatement("UPDATE mint_stats SET name = ?, data = ?, updated = ? WHERE uuid = ?")) {
                up.setString(1, name);
                up.setString(2, data);
                up.setLong(3, updated);
                up.setString(4, id.toString());
                if (up.executeUpdate() > 0) return;
            }
            try (PreparedStatement in = cn.prepareStatement("INSERT INTO mint_stats (uuid, name, data, updated) VALUES (?, ?, ?, ?)")) {
                in.setString(1, id.toString());
                in.setString(2, name);
                in.setString(3, data);
                in.setLong(4, updated);
                in.executeUpdate();
            }
        }
    }

    void close() {
        synchronized (lock) {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) { }
                conn = null;
            }
        }
    }
}
