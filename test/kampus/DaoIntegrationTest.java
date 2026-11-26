package kampus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;
import kampus.dao.MahasiswaDao;
import kampus.db.ConnectionFactory;
import kampus.db.Database;
import kampus.model.Mahasiswa;

/** Tes integrasi nyata dengan JDBC H2, bukan mock database. Tidak menyentuh data aplikasi. */
public final class DaoIntegrationTest {
    private static int passed;

    private DaoIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        Database db = new Database("jdbc:h2:mem:dao_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        db.initialize();
        db.initialize();
        MahasiswaDao dao = new MahasiswaDao(db);
        check(dao.findAll("").isEmpty(), "Inisialisasi idempotent dan tabel awal kosong");

        Mahasiswa input = mahasiswa("F1D024001", "  Aulia Putri  ");
        check(input.nama().equals("Aulia Putri"), "Input di-trim");
        expect(IllegalArgumentException.class, () -> mahasiswa("", "Aulia"), "NIM kosong ditolak");
        expect(IllegalArgumentException.class, () -> mahasiswa("abc'--", "Aulia"), "NIM tidak valid ditolak");
        expect(IllegalArgumentException.class,
                () -> new Mahasiswa(0, "ABC123", "Aulia", "Informatika", "bukan-email"), "Email tidak valid ditolak");
        expect(IllegalArgumentException.class, () -> mahasiswa("ABC123", "x".repeat(101)), "Panjang field divalidasi");
        expect(IllegalArgumentException.class, () -> mahasiswa("ABC123", "  "), "Nama kosong ditolak");

        Mahasiswa first = dao.create(input);
        check(first.id() > 0, "CREATE menghasilkan generated key");
        check(dao.findAll("").size() == 1 && dao.findAuditLogs().size() == 1, "COMMIT menyimpan data dan audit");
        check(dao.findAll("aULia").size() == 1, "READ pencarian tidak peka huruf besar/kecil");
        check(dao.findAll("' OR 1=1 --").isEmpty(), "PreparedStatement menolak SQL injection pada pencarian");
        check(dao.findAll("%").isEmpty() && dao.findAll("_").isEmpty(), "Wildcard pencarian diperlakukan literal");

        expect(SQLException.class, () -> dao.create(input), "NIM duplikat ditolak database");
        check(dao.findAll("").size() == 1 && dao.findAuditLogs().size() == 1, "Gagal insert tidak menambah data/audit");

        Mahasiswa edited = new Mahasiswa(first.id(), first.nim(), "Aulia Putri Revisi", "Sistem Informasi", "aulia@kampus.ac.id");
        dao.update(edited);
        check(dao.findAll("Revisi").get(0).prodi().equals("Sistem Informasi"), "UPDATE menyimpan perubahan");
        check(dao.findAuditLogs().get(0).aksi().equals("UPDATE"), "UPDATE tercatat di audit");
        dao.update(edited);
        check(dao.findAll("").size() == 1, "UPDATE dengan nilai sama tetap berhasil");
        Mahasiswa second = dao.create(mahasiswa("F1D024002", "Bima Saputra"));
        int beforeDuplicate = dao.findAuditLogs().size();
        expect(SQLException.class, () -> dao.update(new Mahasiswa(second.id(), first.nim(),
                second.nama(), second.prodi(), second.email())), "UPDATE ke NIM duplikat ditolak");
        check(dao.findAll(second.nim()).size() == 1 && dao.findAuditLogs().size() == beforeDuplicate,
                "UPDATE gagal menjaga data dan audit sebelumnya");

        // Gagal di statement kedua (audit) setelah mutasi pertama benar-benar berjalan.
        // Proxy hanya menyuntikkan kegagalan; rollback diuji pada database H2 nyata.
        MahasiswaDao brokenAudit = new MahasiswaDao(failOnAudit(db));
        int before = dao.findAuditLogs().size();
        expect(SQLException.class, () -> brokenAudit.create(mahasiswa("FAIL001", "Gagal Insert")), "Simulasi audit CREATE gagal");
        check(dao.findAll("FAIL001").isEmpty(), "ROLLBACK membatalkan INSERT jika audit gagal");
        expect(SQLException.class, () -> brokenAudit.update(new Mahasiswa(first.id(), first.nim(),
                "Nama Tidak Boleh Tersimpan", first.prodi(), first.email())), "Simulasi audit UPDATE gagal");
        check(dao.findAll("Revisi").size() == 1, "ROLLBACK mengembalikan nilai sebelum UPDATE");
        expect(SQLException.class, () -> brokenAudit.delete(first.id()), "Simulasi audit DELETE gagal");
        check(dao.findAll(first.nim()).size() == 1, "ROLLBACK mengembalikan baris setelah DELETE");
        check(dao.findAuditLogs().size() == before, "Semua rollback tidak meninggalkan audit parsial");

        dao.delete(first.id());
        check(dao.findAll(first.nim()).isEmpty(), "DELETE menghapus mahasiswa");
        check(dao.findAuditLogs().get(0).aksi().equals("DELETE"), "Audit DELETE tetap tersimpan");
        expect(SQLException.class, () -> dao.delete(first.id()), "DELETE ID yang sudah tidak ada ditolak");
        expect(SQLException.class, () -> dao.update(edited), "UPDATE ID yang sudah tidak ada ditolak");

        int initialRows = dao.findAll("").size();
        int initialLogs = dao.findAuditLogs().size();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Mahasiswa>> jobs = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                final int number = index;
                jobs.add(pool.submit(() -> dao.create(mahasiswa("THREAD" + number, "Mahasiswa " + number))));
            }
            for (Future<Mahasiswa> job : jobs) {
                check(job.get().id() > 0, "Worker paralel mendapatkan ID unik");
            }
        } finally {
            pool.shutdown();
        }
        check(dao.findAll("").size() == initialRows + 24, "24 operasi paralel tidak kehilangan data");
        check(dao.findAuditLogs().size() == initialLogs + 24, "24 operasi paralel memiliki audit lengkap");
        SwingUtilities.invokeAndWait(() -> {
            try {
                expect(IllegalStateException.class, db::open, "Koneksi database di EDT ditolak");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        testFilePersistence();
        System.out.println("\nPASS: " + passed + " pemeriksaan DAO/validasi/thread (H2 JDBC nyata).");
    }

    private static void testFilePersistence() throws Exception {
        Path folder = Files.createTempDirectory("kampus-persistence-test-");
        Path file = folder.resolve("kampus");
        String url = "jdbc:h2:" + file.toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try {
            Database firstInstance = new Database(url, "sa", "");
            firstInstance.initialize();
            new MahasiswaDao(firstInstance).create(mahasiswa("FILE001", "Data Persisten"));
            check(Files.exists(folder.resolve("kampus.mv.db")), "Database file benar-benar dibuat di disk");
            Database secondInstance = new Database(url, "sa", "");
            secondInstance.initialize();
            MahasiswaDao reopened = new MahasiswaDao(secondInstance);
            check(reopened.findAll("FILE001").size() == 1 && reopened.findAuditLogs().size() == 1,
                    "Data dan audit tetap ada setelah seluruh koneksi ditutup lalu database dibuka ulang");
        } finally {
            // Hanya berkas khusus yang dibuat oleh tes ini; tidak menyentuh folder data aplikasi.
            Files.deleteIfExists(folder.resolve("kampus.mv.db"));
            Files.deleteIfExists(folder.resolve("kampus.trace.db"));
            Files.deleteIfExists(folder);
        }
    }

    private static ConnectionFactory failOnAudit(Database db) {
        return () -> {
            Connection real = db.open();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args != null
                                && args[0] instanceof String sql && sql.startsWith("INSERT INTO audit_log")) {
                            throw new SQLException("Kegagalan audit yang disengaja untuk menguji rollback.");
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        };
    }

    private static Mahasiswa mahasiswa(String nim, String nama) {
        return new Mahasiswa(0, nim, nama, "Teknik Informatika", "mahasiswa@kampus.ac.id");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("FAIL: " + description);
        }
        passed++;
        System.out.println("PASS " + passed + ": " + description);
    }

    private static void expect(Class<? extends Exception> type, CheckedWork work, String description) throws Exception {
        try {
            work.run();
        } catch (Exception exception) {
            if (type.isInstance(exception)) {
                check(true, description);
                return;
            }
            throw exception;
        }
        throw new AssertionError("FAIL: " + description + " (exception tidak terjadi)");
    }

    @FunctionalInterface
    private interface CheckedWork {
        void run() throws Exception;
    }
}
