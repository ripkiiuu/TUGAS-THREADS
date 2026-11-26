package kampus;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import kampus.ui.MahasiswaFrame;

/** Menguji handler Swing asli dan mengambil screenshot jendela aplikasi yang berjalan. */
public final class GuiIntegrationTest {
    private static MahasiswaFrame frame;
    private static int passed;

    private GuiIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        // Konfigurasi uji terisolasi, bukan db.properties atau data milik pengguna.
        Path config = Files.createTempFile("kampus-gui-test-", ".properties");
        Files.writeString(config,
                "db.url=jdbc:h2:mem:gui_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1\n"
                + "db.user=sa\ndb.password=\n", StandardCharsets.UTF_8);
        System.setProperty("kampus.config", config.toString());
        try {
            onEdt(() -> {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                frame = new MahasiswaFrame();
                frame.setVisible(true);
                frame.initializeDatabase();
                return null;
            });
            awaitReady();
            check(rowCount() == 0, "Startup memuat database kosong tanpa memblokir EDT");
            add("F1D024001", "Aulia Putri", "Teknik Informatika", "aulia@kampus.ac.id");
            add("F1D024002", "Bima Saputra", "Sistem Informasi", "bima@kampus.ac.id");
            add("F1D024003", "Citra Lestari", "Teknik Informatika", "citra@kampus.ac.id");
            check(rowCount() == 3, "CREATE melalui tombol GUI menampilkan tiga mahasiswa");
            screenshot("01-data-mahasiswa.png");

            onEdt(() -> {
                component("slow", JCheckBox.class).setSelected(true);
                fill("F1D024004", "Dimas Pratama", "Teknik Elektro", "dimas@kampus.ac.id");
                button("create").doClick();
                return null;
            });
            check(onEdt(() -> label("status").getText().equals("Saving...")), "Klik Tambah menampilkan Saving...");
            check(onEdt(() -> !button("create").isEnabled() && !button("update").isEnabled()
                    && !button("delete").isEnabled() && !component("nim", JTextField.class).isEnabled()),
                    "Tombol dan input nonaktif selama worker berjalan");
            String before = onEdt(() -> label("clock").getText());
            await("Jam GUI tetap bergerak saat Saving...", () -> !uncheckedEdt(() -> label("clock").getText()).equals(before));
            check(onEdt(() -> label("status").getText().equals("Saving...")), "EDT responsif ketika pekerjaan DB belum selesai");
            screenshot("02-saving-thread.png");
            awaitReady();
            check(rowCount() == 4, "CREATE selesai dan tombol aktif kembali");
            onEdt(() -> { component("slow", JCheckBox.class).setSelected(false); return null; });

            // Urutkan tabel, lalu edit baris view pertama: uji konversi view/model index.
            onEdt(() -> {
                JTable table = component("mahasiswaTable", JTable.class);
                table.getRowSorter().toggleSortOrder(2);
                table.setRowSelectionInterval(0, 0);
                check(component("nim", JTextField.class).getText().equals("F1D024001"),
                        "Pemilihan baris setelah sorting mengisi model yang benar");
                component("nama", JTextField.class).setText("Aulia Putri Revisi");
                button("update").doClick();
                return null;
            });
            awaitReady();
            check(onEdt(() -> label("status").getText().contains("COMMIT")), "UPDATE GUI berhasil commit");
            search("Revisi");
            check(rowCount() == 1, "READ/pencarian GUI menampilkan hasil update");
            screenshot("03-hasil-update.png");

            onEdt(() -> {
                component("mahasiswaTable", JTable.class).setRowSelectionInterval(0, 0);
                // doClick dijadwalkan: dialog modal tetap dapat dijawab oleh loop uji.
                SwingUtilities.invokeLater(() -> button("delete").doClick());
                return null;
            });
            awaitDialog("Konfirmasi hapus");
            answerDialog("Konfirmasi hapus", JOptionPane.YES_OPTION);
            awaitReady();
            check(rowCount() == 0, "DELETE via konfirmasi GUI menghapus baris hasil pencarian");
            search("");
            check(rowCount() == 3, "READ setelah DELETE mempertahankan mahasiswa lain");

            onEdt(() -> { button("history").doClick(); return null; });
            awaitDialog("100 transaksi terakhir");
            check(onEdt(() -> {
                JDialog dialog = dialog("100 transaksi terakhir");
                JTable logs = findByType(dialog, JTable.class);
                return logs.getRowCount() == 6 && logs.getValueAt(0, 2).equals("DELETE");
            }), "Riwayat menunjukkan 4 CREATE, 1 UPDATE, dan 1 DELETE");
            screenshotDialog("100 transaksi terakhir", "04-riwayat-transaksi.png");
            answerDialog("100 transaksi terakhir", JOptionPane.OK_OPTION);
            awaitReady();

            // Error path: NIM duplikat, lalu pastikan UI pulih dan data tidak bertambah.
            onEdt(() -> {
                fill("F1D024002", "Duplikat", "Teknik Informatika", "duplikat@kampus.ac.id");
                button("create").doClick();
                return null;
            });
            awaitDialog("Operasi database gagal");
            check(onEdt(() -> label("status").getText().contains("NIM sudah terdaftar")),
                    "Error constraint ditampilkan sebagai pesan yang bisa dipahami");
            answerDialog("Operasi database gagal", JOptionPane.OK_OPTION);
            awaitReady();
            check(rowCount() == 3, "Error duplikat tidak menambah baris dan tombol aktif kembali");
            onEdt(() -> { button("clear").doClick(); return null; });
            System.out.println("\nPASS: " + passed + " pemeriksaan GUI; 4 screenshot tersimpan di docs/screenshots/.");
        } finally {
            if (frame != null) {
                onEdt(() -> {
                    for (Window window : frame.getOwnedWindows()) {
                        window.dispose();
                    }
                    frame.dispose();
                    return null;
                });
            }
            Files.deleteIfExists(config);
        }
    }

    private static void add(String nim, String nama, String prodi, String email) throws Exception {
        onEdt(() -> { fill(nim, nama, prodi, email); button("create").doClick(); return null; });
        awaitReady();
    }

    private static void fill(String nim, String nama, String prodi, String email) {
        component("nim", JTextField.class).setText(nim);
        component("nama", JTextField.class).setText(nama);
        component("prodi", JTextField.class).setText(prodi);
        component("email", JTextField.class).setText(email);
    }

    private static void search(String keyword) throws Exception {
        onEdt(() -> { component("search", JTextField.class).setText(keyword); button("find").doClick(); return null; });
        awaitReady();
    }

    private static int rowCount() throws Exception {
        return onEdt(() -> component("mahasiswaTable", JTable.class).getRowCount());
    }

    private static void awaitReady() throws Exception {
        await("GUI siap", () -> uncheckedEdt(() -> button("create").isEnabled()));
    }

    private static void awaitDialog(String title) throws Exception {
        await("Dialog " + title, () -> uncheckedEdt(() -> dialog(title) != null));
    }

    private static JDialog dialog(String title) {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog item && item.isShowing() && item.getTitle().equals(title)) {
                return item;
            }
        }
        return null;
    }

    private static void answerDialog(String title, int answer) throws Exception {
        onEdt(() -> {
            findByType(dialog(title), JOptionPane.class).setValue(answer);
            return null;
        });
    }

    private static void screenshot(String filename) throws Exception {
        capture(onEdt(() -> frame.getContentPane()), filename);
    }

    private static void screenshotDialog(String title, String filename) throws Exception {
        capture(onEdt(() -> dialog(title).getContentPane()), filename);
    }

    private static void capture(Component component, String filename) throws Exception {
        Path directory = Path.of("docs", "screenshots");
        Files.createDirectories(directory);
        // Capture hanya komponen aplikasi nyata, bukan desktop. Tidak menyertakan
        // jendela pribadi lain ketika GUI uji tertutup oleh aplikasi pengguna.
        BufferedImage image = onEdt(() -> {
            BufferedImage rendered = new BufferedImage(component.getWidth(), component.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rendered.createGraphics();
            try {
                component.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            return rendered;
        });
        ImageIO.write(image, "png", directory.resolve(filename).toFile());
    }

    private static JButton button(String name) {
        return component(name, JButton.class);
    }

    private static JLabel label(String name) {
        return component(name, JLabel.class);
    }

    private static <T extends Component> T component(String name, Class<T> type) {
        Component found = findByName(frame, name);
        if (found == null) {
            throw new AssertionError("Komponen tidak ditemukan: " + name);
        }
        return type.cast(found);
    }

    private static Component findByName(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findByName(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T findByType(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findByType(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void await(String description, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timeout: " + description);
            }
            Thread.sleep(25); // Polling kondisi hanya di thread test, tidak di EDT.
        }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static <T> T uncheckedEdt(Callable<T> action) {
        try {
            return onEdt(action);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("FAIL: " + description);
        }
        System.out.println("PASS " + (++passed) + ": " + description);
    }
}
