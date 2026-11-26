package kampus.model;

import java.time.LocalDateTime;

public record AuditLog(long id, long mahasiswaId, String aksi, String keterangan,
                       LocalDateTime dibuatPada) {
}
