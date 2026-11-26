package kampus.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Satu koneksi baru per operasi; koneksi tidak dibagikan antar-thread. */
@FunctionalInterface
public interface ConnectionFactory {
    Connection open() throws SQLException;
}
