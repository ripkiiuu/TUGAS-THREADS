package kampus.db;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import javax.swing.SwingUtilities;

public final class Database implements ConnectionFactory {
    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Dipanggil dari worker, bukan EDT, karena membaca file konfigurasi. */
    public static Database fromConfig() throws IOException {
        Properties config = new Properties();
        Path file = Path.of(System.getProperty("kampus.config", "db.properties"));
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config.load(reader);
            }
        }
        String url = config.getProperty("db.url",
                "jdbc:h2:./data/kampus;MODE=MySQL;DATABASE_TO_LOWER=TRUE").trim();
        if (!url.startsWith("jdbc:h2:") && !url.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("db.url harus berupa URL JDBC H2 atau MySQL.");
        }
        if (url.startsWith("jdbc:h2:./data/")) {
            Files.createDirectories(Path.of("data"));
        }
        return new Database(url, config.getProperty("db.user", "sa"),
                config.getProperty("db.password", ""));
    }

    @Override
    public Connection open() throws SQLException {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Operasi database tidak boleh dijalankan di EDT.");
        }
        return DriverManager.getConnection(url, user, password);
    }

    public String engine() {
        return url.startsWith("jdbc:mysql:") ? "MySQL" : "H2 / file lokal";
    }

    public void initialize() throws SQLException {
        // DDL statis: tidak ada input pengguna yang digabungkan ke SQL.
        // MySQL melakukan implicit commit untuk DDL; inisialisasi bukan transaksi CRUD.
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahasiswa ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "nim VARCHAR(20) NOT NULL UNIQUE, nama VARCHAR(100) NOT NULL, "
                    + "prodi VARCHAR(100) NOT NULL, email VARCHAR(120) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS audit_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, mahasiswa_id BIGINT NOT NULL, "
                    + "aksi VARCHAR(10) NOT NULL, keterangan VARCHAR(255) NOT NULL, "
                    + "dibuat_pada TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }
}
