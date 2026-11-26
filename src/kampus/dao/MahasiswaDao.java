package kampus.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kampus.db.ConnectionFactory;
import kampus.model.AuditLog;
import kampus.model.Mahasiswa;

/** DAO: seluruh SQL terpisah dari GUI. Tidak menyimpan koneksi bersama. */
public final class MahasiswaDao {
    private final ConnectionFactory connections;

    public MahasiswaDao(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<Mahasiswa> findAll(String keyword) throws SQLException {
        String sql = "SELECT id, nim, nama, prodi, email FROM mahasiswa "
                + "WHERE LOWER(nim) LIKE ? ESCAPE '!' OR LOWER(nama) LIKE ? ESCAPE '!' "
                + "OR LOWER(prodi) LIKE ? ESCAPE '!' ORDER BY id DESC";
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 3; index++) {
                statement.setString(index, "%" + escaped + "%");
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Mahasiswa> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new Mahasiswa(rows.getLong("id"), rows.getString("nim"),
                            rows.getString("nama"), rows.getString("prodi"), rows.getString("email")));
                }
                return List.copyOf(result);
            }
        }
    }

    public Mahasiswa create(Mahasiswa mahasiswa) throws SQLException {
        return inTransaction(connection -> {
            String sql = "INSERT INTO mahasiswa (nim, nama, prodi, email) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, mahasiswa);
                requireOne(statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Database tidak mengembalikan ID baru.");
                    }
                    Mahasiswa created = mahasiswa.withId(keys.getLong(1));
                    addAudit(connection, created.id(), "CREATE", created.nim() + " - " + created.nama());
                    return created;
                }
            }
        });
    }

    public void update(Mahasiswa mahasiswa) throws SQLException {
        inTransaction(connection -> {
            String sql = "UPDATE mahasiswa SET nim = ?, nama = ?, prodi = ?, email = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, mahasiswa);
                statement.setLong(5, mahasiswa.id());
                requireOne(statement.executeUpdate());
                addAudit(connection, mahasiswa.id(), "UPDATE", mahasiswa.nim() + " - " + mahasiswa.nama());
                return null;
            }
        });
    }

    public void delete(long id) throws SQLException {
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mahasiswa WHERE id = ?")) {
                statement.setLong(1, id);
                requireOne(statement.executeUpdate());
                // Tidak memakai FK: riwayat tetap tersimpan setelah mahasiswa dihapus.
                addAudit(connection, id, "DELETE", "Menghapus mahasiswa dengan ID " + id);
                return null;
            }
        });
    }

    public List<AuditLog> findAuditLogs() throws SQLException {
        String sql = "SELECT id, mahasiswa_id, aksi, keterangan, dibuat_pada FROM audit_log ORDER BY id DESC";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(100);
            try (ResultSet rows = statement.executeQuery()) {
                List<AuditLog> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new AuditLog(rows.getLong("id"), rows.getLong("mahasiswa_id"),
                            rows.getString("aksi"), rows.getString("keterangan"),
                            rows.getTimestamp("dibuat_pada").toLocalDateTime()));
                }
                return List.copyOf(result);
            }
        }
    }

    private static void bind(PreparedStatement statement, Mahasiswa mahasiswa) throws SQLException {
        statement.setString(1, mahasiswa.nim());
        statement.setString(2, mahasiswa.nama());
        statement.setString(3, mahasiswa.prodi());
        statement.setString(4, mahasiswa.email());
    }

    private static void requireOne(int affected) throws SQLException {
        if (affected != 1) {
            throw new SQLException("Data tidak ditemukan atau sudah dihapus. Muat ulang tabel.");
        }
    }

    private static void addAudit(Connection connection, long id, String action, String description)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_log (mahasiswa_id, aksi, keterangan) VALUES (?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, action);
            statement.setString(3, description);
            requireOne(statement.executeUpdate());
        }
    }

    /** Perubahan data dan audit harus berhasil bersama; kegagalan salah satunya di-rollback. */
    private <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    exception.addSuppressed(rollbackError);
                }
                throw exception;
            }
            // Koneksi milik operasi ini langsung ditutup oleh try-with-resources.
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
