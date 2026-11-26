# JDBC driver

Dua dependency berikut disertakan sebagai binary resmi tanpa modifikasi. Script `scripts/project.ps1` dapat mengunduh ulang dari Maven Central jika belum ada dan memverifikasi SHA-256 setiap kali build.

| File | Fungsi | Sumber resmi |
| --- | --- | --- |
| `h2-2.3.232.jar` | Driver JDBC sekaligus engine database SQL berbasis file/memori | [Maven Central](https://repo.maven.apache.org/maven2/com/h2database/h2/2.3.232/h2-2.3.232.jar) |
| `mysql-connector-j-9.2.0.jar` | Driver JDBC klasik untuk server MySQL | [Maven Central](https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar) |

SHA-256:

```text
h2-2.3.232.jar
8dae62d22db8982c3dcb3826edb9c727c5d302063a67eef7d63d82de401f07d3

mysql-connector-j-9.2.0.jar
7e9941bbdcca244d878ea95bfff788fd9ba6a65af757f24be6c632930d61c7ed
```

## Lisensi pihak ketiga

- **H2**: dual license **Mozilla Public License 2.0** atau **Eclipse Public License 1.0**; lihat [lisensi H2](https://h2database.com/html/license.html) dan [source versi 2.3.232](https://github.com/h2database/h2database/tree/version-2.3.232).
- **MySQL Connector/J**: **GNU GPL v2** dengan ketentuan/pengecualian yang tercantum pada berkas `LICENSE` di dalam JAR resminya. Lihat juga [source versi 9.2.0](https://github.com/mysql/mysql-connector-j/tree/9.2.0) dan [informasi lisensi Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-licensing.html).

Hak cipta dan lisensi dependency tetap milik penerbitnya. Driver yang dipakai adalah JDBC klasik; fitur X DevAPI dan dependency opsionalnya tidak digunakan.
