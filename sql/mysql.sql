-- Jalankan sebagai administrator MySQL 8+ melalui MySQL CLI/phpMyAdmin.
-- Tidak menghapus atau menimpa data yang sudah ada.
CREATE DATABASE IF NOT EXISTS thread_database
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE thread_database;

CREATE TABLE IF NOT EXISTS mahasiswa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL UNIQUE,
    nama VARCHAR(100) NOT NULL,
    prodi VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mahasiswa_id BIGINT NOT NULL,
    aksi VARCHAR(10) NOT NULL,
    keterangan VARCHAR(255) NOT NULL,
    dibuat_pada TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Opsional: buat user aplikasi (ganti password, jalankan sebagai admin).
-- CREATE USER 'thread_app'@'localhost' IDENTIFIED BY 'PASSWORD_YANG_KUAT';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON thread_database.*
--     TO 'thread_app'@'localhost';
-- CREATE diperlukan karena aplikasi memanggil CREATE TABLE IF NOT EXISTS.
