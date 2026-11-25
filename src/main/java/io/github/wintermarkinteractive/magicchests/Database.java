package io.github.wintermarkinteractive.magicchests;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** SQLite storage (magicchests.db). All queries run in order on one background thread. */
public final class Database {

    private final File file;
    private final Logger log;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WintermarkMagicChests-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public Database(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void open() throws Exception {
        executor.submit(() -> {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("CREATE TABLE IF NOT EXISTS chests ("
                        + "world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                        + "owner TEXT NOT NULL, placed INTEGER NOT NULL, "
                        + "PRIMARY KEY (world, x, y, z))");
                st.execute("CREATE INDEX IF NOT EXISTS chests_owner ON chests(owner)");
                st.execute("CREATE TABLE IF NOT EXISTS furnace_queue ("
                        + "world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                        + "owner TEXT NOT NULL, input TEXT NOT NULL, remaining INTEGER NOT NULL, "
                        + "total INTEGER NOT NULL, output INTEGER NOT NULL, "
                        + "PRIMARY KEY (world, x, y, z))");
                st.execute("CREATE TABLE IF NOT EXISTS trust ("
                        + "owner TEXT NOT NULL, trusted TEXT NOT NULL, "
                        + "PRIMARY KEY (owner, trusted))");
            }
            return null;
        }).get();
    }

    public List<MagicChest> loadChests() throws Exception {
        return executor.submit(() -> {
            List<MagicChest> list = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT world, x, y, z, owner, placed FROM chests")) {
                while (rs.next()) {
                    try {
                        list.add(new MagicChest(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3),
                                rs.getInt(4), UUID.fromString(rs.getString(5)), rs.getLong(6)));
                    } catch (IllegalArgumentException e) {
                        log.warning("Skipping a broken row in magicchests.db");
                    }
                }
            }
            return list;
        }).get();
    }

    public Map<UUID, Set<UUID>> loadTrust() throws Exception {
        return executor.submit(() -> {
            Map<UUID, Set<UUID>> map = new HashMap<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT owner, trusted FROM trust")) {
                while (rs.next()) {
                    try {
                        map.computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new HashSet<>())
                                .add(UUID.fromString(rs.getString(2)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return map;
        }).get();
    }

    public List<FurnaceQueue> loadQueues() throws Exception {
        return executor.submit(() -> {
            List<FurnaceQueue> list = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT world, x, y, z, owner, input, remaining, total, output FROM furnace_queue")) {
                while (rs.next()) {
                    try {
                        org.bukkit.Material m = org.bukkit.Material.matchMaterial(rs.getString(6));
                        if (m == null) continue;
                        list.add(new FurnaceQueue(new MagicChest.Key(UUID.fromString(rs.getString(1)), rs.getInt(2),
                                rs.getInt(3), rs.getInt(4)), UUID.fromString(rs.getString(5)), m, rs.getInt(7),
                                rs.getInt(8), rs.getInt(9) != 0));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return list;
        }).get();
    }

    public void saveQueue(FurnaceQueue q) {
        MagicChest.Key k = q.key();
        String owner = q.owner().toString(), input = q.input().name();
        int remaining = q.remaining(), total = q.total(), output = q.output() ? 1 : 0;
        run("INSERT OR REPLACE INTO furnace_queue (world, x, y, z, owner, input, remaining, total, output) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, k.world().toString());
            ps.setInt(2, k.x());
            ps.setInt(3, k.y());
            ps.setInt(4, k.z());
            ps.setString(5, owner);
            ps.setString(6, input);
            ps.setInt(7, remaining);
            ps.setInt(8, total);
            ps.setInt(9, output);
        });
    }

    public void deleteQueue(MagicChest.Key k) {
        run("DELETE FROM furnace_queue WHERE world = ? AND x = ? AND y = ? AND z = ?", ps -> {
            ps.setString(1, k.world().toString());
            ps.setInt(2, k.x());
            ps.setInt(3, k.y());
            ps.setInt(4, k.z());
        });
    }

    public void insertChest(MagicChest c) {
        run("INSERT OR REPLACE INTO chests (world, x, y, z, owner, placed) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, c.world().toString());
            ps.setInt(2, c.x());
            ps.setInt(3, c.y());
            ps.setInt(4, c.z());
            ps.setString(5, c.owner().toString());
            ps.setLong(6, c.placed());
        });
    }

    public void deleteChest(MagicChest.Key k) {
        run("DELETE FROM chests WHERE world = ? AND x = ? AND y = ? AND z = ?", ps -> {
            ps.setString(1, k.world().toString());
            ps.setInt(2, k.x());
            ps.setInt(3, k.y());
            ps.setInt(4, k.z());
        });
    }

    public void addTrust(UUID owner, UUID trusted) {
        run("INSERT OR IGNORE INTO trust (owner, trusted) VALUES (?, ?)", ps -> {
            ps.setString(1, owner.toString());
            ps.setString(2, trusted.toString());
        });
    }

    public void removeTrust(UUID owner, UUID trusted) {
        run("DELETE FROM trust WHERE owner = ? AND trusted = ?", ps -> {
            ps.setString(1, owner.toString());
            ps.setString(2, trusted.toString());
        });
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws Exception;
    }

    private void run(String sql, Binder binder) {
        executor.submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Database write failed: " + sql, e);
            }
        });
    }

    public void close() {
        executor.submit(() -> {
            try {
                if (connection != null) connection.close();
            } catch (Exception ignored) {
            }
        });
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
