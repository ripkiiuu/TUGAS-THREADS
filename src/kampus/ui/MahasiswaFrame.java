package kampus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import kampus.dao.MahasiswaDao;
import kampus.db.Database;
import kampus.model.AuditLog;
import kampus.model.Mahasiswa;

// JFrame mewarisi Serializable, tetapi frame aplikasi ini tidak disimpan lewat serialisasi.
@SuppressWarnings("serial")
public final class MahasiswaFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color INK = new Color(28, 45, 66);
    private static final Color TEAL = new Color(0, 111, 114);

    // Sesuai materi: satu worker untuk mengurutkan pekerjaan DB, bukan EDT.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "database-worker"));
    private MahasiswaDao dao;
    private boolean busy;
    private boolean ready;
    private List<Mahasiswa> rows = List.of();
    private long selectedId;

    private final JTextField nim = named(new JTextField(18), "nim");
    private final JTextField nama = named(new JTextField(18), "nama");
    private final JTextField prodi = named(new JTextField(18), "prodi");
    private final JTextField email = named(new JTextField(18), "email");
    private final JTextField search = named(new JTextField(20), "search");
    private final JButton create = named(new JButton("Tambah"), "create");
    private final JButton update = named(new JButton("Simpan perubahan"), "update");
    private final JButton delete = named(new JButton("Hapus"), "delete");
    private final JButton clear = named(new JButton("Form baru"), "clear");
    private final JButton refresh = named(new JButton("Muat ulang"), "refresh");
    private final JButton find = named(new JButton("Cari"), "find");
    private final JButton history = named(new JButton("Riwayat transaksi"), "history");
    private final JCheckBox slow = named(new JCheckBox("Simulasi DB lambat (1,5 detik)"), "slow");
    private final JLabel status = named(new JLabel("Menyiapkan aplikasi..."), "status");
    private final JLabel count = named(new JLabel("0 mahasiswa"), "count");
    private final JLabel database = new JLabel("DATABASE  /  menghubungkan...");
    private final JLabel clock = named(new JLabel(), "clock");
    private final JLabel formTitle = new JLabel("Mahasiswa baru");
    private final JProgressBar progress = new JProgressBar();
    private final DefaultTableModel tableModel = readOnlyModel("ID", "NIM", "Nama", "Program studi", "Email");
    private final JTable table = named(new JTable(tableModel), "mahasiswaTable");
    private final Timer clockTimer;

    public MahasiswaFrame() {
        super("Kampus | CRUD Mahasiswa - Thread & Database");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1180, 740);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 20));
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
        root.setBackground(new Color(244, 247, 249));
        setContentPane(root);
        root.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildForm(), buildData());
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerLocation(300);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        root.add(split, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        create.addActionListener(event -> save(false));
        update.addActionListener(event -> save(true));
        delete.addActionListener(event -> deleteSelected());
        clear.addActionListener(event -> clearForm());
        refresh.addActionListener(event -> {
            if (ready) {
                reload("Data berhasil dimuat ulang.");
            } else {
                initializeDatabase();
            }
        });
        find.addActionListener(event -> reload("Pencarian selesai."));
        search.addActionListener(event -> reload("Pencarian selesai."));
        history.addActionListener(event -> runDatabaseTask("Memuat riwayat...", dao::findAuditLogs,
                logs -> {
                    status.setText("Riwayat transaksi berhasil dimuat.");
                    showHistory(logs);
                }));
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !busy) {
                populateSelected();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (busy) {
                    JOptionPane.showMessageDialog(MahasiswaFrame.this,
                            "Tunggu operasi database selesai sebelum menutup aplikasi.",
                            "Operasi sedang berjalan", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    dispose();
                }
            }
        });
        clockTimer = new Timer(100, event -> clock.setText("UI AKTIF  /  "
                + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.S"))));
        clockTimer.start();
        updateControls();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setOpaque(false);
        JPanel titles = new JPanel(new java.awt.GridLayout(3, 1, 0, 6));
        titles.setOpaque(false);
        JLabel eyebrow = new JLabel("PBO  /  JAVA CODING PRACTICE  /  2026");
        eyebrow.setForeground(TEAL);
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 12));
        JLabel title = new JLabel("Data Mahasiswa");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(INK);
        titles.add(eyebrow);
        titles.add(title);
        titles.add(new JLabel("CRUD dengan JDBC, transaksi atomik, dan antarmuka yang tetap responsif."));
        header.add(titles, BorderLayout.CENTER);
        JPanel meta = new JPanel(new java.awt.GridLayout(2, 1, 0, 8));
        meta.setOpaque(false);
        database.setForeground(TEAL);
        clock.setForeground(INK);
        meta.add(database);
        meta.add(clock);
        header.add(meta, BorderLayout.EAST);
        return header;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 227, 233)),
                BorderFactory.createEmptyBorder(20, 18, 20, 18)));
        formTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 15, 0);
        form.add(formTitle, c);
        addField(form, c, "NIM *", nim);
        addField(form, c, "Nama lengkap *", nama);
        addField(form, c, "Program studi *", prodi);
        addField(form, c, "Email *", email);
        create.setBackground(TEAL);
        create.setForeground(Color.WHITE);
        for (JButton button : new JButton[] {create, update, delete, clear}) {
            c.gridy++;
            c.insets = new Insets(4, 0, 4, 0);
            form.add(button, c);
        }
        c.gridy++;
        c.weighty = 1;
        form.add(new JPanel(), c);
        return form;
    }

    private static void addField(JPanel panel, GridBagConstraints c, String label, JTextField field) {
        c.gridy++;
        c.insets = new Insets(8, 0, 4, 0);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setLabelFor(field);
        panel.add(fieldLabel, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 5, 0);
        field.setPreferredSize(new Dimension(200, 32));
        panel.add(field, c);
    }

    private JPanel buildData() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        panel.setOpaque(false);
        JPanel tools = new JPanel(new BorderLayout(8, 0));
        tools.setOpaque(false);
        search.setToolTipText("Cari NIM, nama, atau program studi; Enter untuk mencari.");
        search.getAccessibleContext().setAccessibleName("Cari mahasiswa");
        tools.add(search, BorderLayout.CENTER);
        tools.add(find, BorderLayout.EAST);
        panel.add(tools, BorderLayout.NORTH);
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(105);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(180);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        count.setForeground(TEAL);
        actions.add(count);
        actions.add(refresh);
        actions.add(history);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel options = new JPanel(new BorderLayout());
        options.setOpaque(false);
        slow.setOpaque(false);
        options.add(slow, BorderLayout.WEST);
        JLabel pipeline = new JLabel("EDT  >  database-worker  >  invokeLater  >  GUI");
        pipeline.setForeground(TEAL);
        options.add(pipeline, BorderLayout.EAST);
        footer.add(options, BorderLayout.NORTH);
        JPanel state = new JPanel(new BorderLayout(12, 0));
        state.setOpaque(false);
        progress.setPreferredSize(new Dimension(130, 18));
        progress.setVisible(false);
        status.setForeground(INK);
        state.add(status, BorderLayout.CENTER);
        state.add(progress, BorderLayout.EAST);
        footer.add(state, BorderLayout.SOUTH);
        return footer;
    }

    public void initializeDatabase() {
        runDatabaseTask("Menghubungkan database...", () -> {
            Database db = Database.fromConfig();
            db.initialize();
            return new Initialized(new MahasiswaDao(db), db.engine());
        }, initialized -> {
            dao = initialized.dao();
            database.setText("DATABASE  /  " + initialized.engine());
            ready = true;
            reload("Siap. Isi form untuk menambah, atau pilih baris untuk mengubah data.");
        });
    }

    private void save(boolean editing) {
        if (busy || !ready || (editing && selectedId == 0)) {
            return;
        }
        final Mahasiswa input;
        try {
            // Baca komponen GUI hanya di EDT; worker menerima model immutable.
            input = new Mahasiswa(editing ? selectedId : 0, nim.getText(), nama.getText(),
                    prodi.getText(), email.getText());
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Validasi input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runDatabaseTask("Saving...", () -> {
            if (editing) {
                dao.update(input);
            } else {
                dao.create(input);
            }
            return null;
        }, ignored -> {
            clearForm();
            reload(editing ? "Perubahan berhasil disimpan (COMMIT)." : "Mahasiswa berhasil ditambahkan (COMMIT).");
        });
    }

    private void deleteSelected() {
        if (busy || !ready || selectedId == 0) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Hapus mahasiswa " + nim.getText() + " - " + nama.getText() + "?",
                "Konfirmasi hapus", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        long id = selectedId;
        runDatabaseTask("Menghapus...", () -> {
            dao.delete(id);
            return null;
        }, ignored -> {
            clearForm();
            reload("Mahasiswa berhasil dihapus (COMMIT). Riwayat tetap tersimpan.");
        });
    }

    private void reload(String successMessage) {
        if (!ready || busy) {
            return;
        }
        String keyword = search.getText();
        // Jika mutasi sudah commit tetapi refresh gagal, tampilkan fakta commit-nya.
        String loading = successMessage.contains("COMMIT")
                ? successMessage + " Memuat ulang tabel..." : "Memuat data...";
        runDatabaseTask(loading, () -> dao.findAll(keyword), loaded -> {
            rows = loaded;
            table.clearSelection();
            selectedId = 0;
            tableModel.setRowCount(0);
            for (Mahasiswa mahasiswa : rows) {
                tableModel.addRow(new Object[] {mahasiswa.id(), mahasiswa.nim(), mahasiswa.nama(),
                        mahasiswa.prodi(), mahasiswa.email()});
            }
            clearForm();
            count.setText(rows.size() + " mahasiswa" + (keyword.isBlank() ? "" : " ditemukan"));
            status.setText(successMessage);
        });
    }

    /** Pola tugas: click handler -> disable -> worker DB -> invokeLater -> GUI. */
    private <T> void runDatabaseTask(String message, Callable<T> work, Consumer<T> onSuccess) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Handler GUI harus berjalan di EDT.");
        }
        if (busy) {
            return;
        }
        boolean simulateSlowDatabase = slow.isSelected();
        setBusy(true);
        status.setText(message);
        dbExecutor.submit(() -> {
            try {
                System.out.println("[" + Thread.currentThread().getName() + "] " + message);
                if (simulateSlowDatabase) {
                    Thread.sleep(1500); // Hanya demonstrasi; TIDAK pernah sleep di EDT.
                }
                T result = work.call();
                SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    onSuccess.accept(result);
                });
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                exception.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    String detail = errorMessage(exception);
                    status.setText(message.contains("COMMIT")
                            ? "Data sudah COMMIT, tetapi tabel gagal dimuat ulang. Klik Muat ulang."
                            : "Operasi gagal. " + detail);
                    JOptionPane.showMessageDialog(this, detail, "Operasi database gagal", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private static String errorMessage(Exception exception) {
        if (exception instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state != null && state.startsWith("23")) {
                return "NIM sudah terdaftar atau data melanggar batasan database. Gunakan NIM yang unik.";
            }
            return "Database tidak dapat memproses permintaan (SQLState: " + state
                    + "). Periksa koneksi, konfigurasi, dan log terminal.";
        }
        if (exception instanceof InterruptedException) {
            return "Pekerjaan dihentikan sebelum operasi database dijalankan.";
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private void populateSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        // Konversi indeks penting ketika pengguna mengurutkan tabel.
        Mahasiswa selected = rows.get(table.convertRowIndexToModel(viewRow));
        selectedId = selected.id();
        nim.setText(selected.nim());
        nama.setText(selected.nama());
        prodi.setText(selected.prodi());
        email.setText(selected.email());
        formTitle.setText("Edit mahasiswa #" + selectedId);
        updateControls();
    }

    private void clearForm() {
        table.clearSelection();
        selectedId = 0;
        for (JTextField field : new JTextField[] {nim, nama, prodi, email}) {
            field.setText("");
        }
        formTitle.setText("Mahasiswa baru");
        updateControls();
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisible(value);
        progress.setIndeterminate(value);
        setCursor(Cursor.getPredefinedCursor(value ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        updateControls();
    }

    private void updateControls() {
        boolean enabled = ready && !busy;
        for (Component component : new Component[] {nim, nama, prodi, email, search,
                clear, refresh, find, history, table, slow}) {
            component.setEnabled(enabled);
        }
        // Tetap bisa mencoba koneksi lagi setelah inisialisasi gagal.
        refresh.setEnabled(!busy);
        create.setEnabled(enabled && selectedId == 0);
        update.setEnabled(enabled && selectedId != 0);
        delete.setEnabled(enabled && selectedId != 0);
    }

    private void showHistory(List<AuditLog> logs) {
        DefaultTableModel model = readOnlyModel("ID", "ID Mahasiswa", "Aksi", "Keterangan", "Waktu");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        for (AuditLog log : logs) {
            model.addRow(new Object[] {log.id(), log.mahasiswaId(), log.aksi(),
                    log.keterangan(), log.dibuatPada().format(format)});
        }
        JTable historyTable = new JTable(model);
        historyTable.setRowHeight(28);
        int[] widths = {45, 95, 75, 455, 190};
        for (int index = 0; index < widths.length; index++) {
            historyTable.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
        JScrollPane pane = new JScrollPane(historyTable);
        pane.setPreferredSize(new Dimension(860, 300));
        JOptionPane.showMessageDialog(this, pane, "100 transaksi terakhir", JOptionPane.PLAIN_MESSAGE);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Long.class : String.class;
            }
        };
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    @Override
    public void dispose() {
        clockTimer.stop();
        // UI menolak close saat busy, sehingga transaksi tidak dipotong di tengah jalan.
        dbExecutor.shutdown();
        super.dispose();
    }

    private record Initialized(MahasiswaDao dao, String engine) {
    }
}
