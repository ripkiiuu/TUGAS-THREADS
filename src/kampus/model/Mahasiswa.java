package kampus.model;

/** Model immutable: aman dikirim dari EDT ke worker dan sebaliknya. */
public record Mahasiswa(long id, String nim, String nama, String prodi, String email) {
    public Mahasiswa {
        if (id < 0) {
            throw new IllegalArgumentException("ID tidak valid.");
        }
        nim = required(nim, "NIM", 20);
        nama = required(nama, "Nama", 100);
        prodi = required(prodi, "Program studi", 100);
        email = required(email, "Email", 120);
        if (!nim.matches("[A-Za-z0-9.-]{3,20}")) {
            throw new IllegalArgumentException("NIM harus 3-20 karakter: huruf, angka, titik, atau tanda hubung.");
        }
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Format email tidak valid, contoh: nama@kampus.ac.id.");
        }
    }

    private static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " wajib diisi.");
        }
        String cleaned = value.trim();
        if (cleaned.length() > max) {
            throw new IllegalArgumentException(label + " maksimal " + max + " karakter.");
        }
        return cleaned;
    }

    public Mahasiswa withId(long newId) {
        return new Mahasiswa(newId, nim, nama, prodi, email);
    }
}
