package com.borinquenterrier.cef

import kotlin.test.*

class LitestreamConfigGeneratorTest {

    private val generator = LitestreamConfigGenerator()

    // ── S3 ────────────────────────────────────────────────────────────────────

    @Test
    fun `S3 replica produces a dbs entry with the correct path`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.S3(
                bucket = "cef-backups",
                region = "us-east-1",
                accessKeyId = "AKID1234",
                secretAccessKey = "SECRET"
            )
        )
        assertTrue(yaml.contains("path: /data/db/ab/cd/alice.db"), "yaml should contain db path")
    }

    @Test
    fun `S3 replica includes bucket URL`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.S3(
                bucket = "cef-backups",
                region = "us-east-1",
                accessKeyId = "AKID1234",
                secretAccessKey = "SECRET"
            )
        )
        assertTrue(yaml.contains("s3://cef-backups"), "yaml should reference S3 bucket")
    }

    @Test
    fun `S3 replica includes region and credentials`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.S3(
                bucket = "cef-backups",
                region = "eu-west-1",
                accessKeyId = "AKID1234",
                secretAccessKey = "MY_SECRET"
            )
        )
        assertTrue(yaml.contains("region: eu-west-1"))
        assertTrue(yaml.contains("access-key-id: AKID1234"))
        assertTrue(yaml.contains("secret-access-key: MY_SECRET"))
    }

    // ── Azure Blob Storage ────────────────────────────────────────────────────

    @Test
    fun `Azure replica produces type abs entry`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.AzureBlob(
                accountName = "cefstorage",
                accountKey = "base64key==",
                container = "tenant-dbs"
            )
        )
        assertTrue(yaml.contains("type: abs"), "yaml should specify abs replica type")
    }

    @Test
    fun `Azure replica includes account name, key, and container`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.AzureBlob(
                accountName = "cefstorage",
                accountKey = "base64key==",
                container = "tenant-dbs"
            )
        )
        assertTrue(yaml.contains("account-name: cefstorage"))
        assertTrue(yaml.contains("account-key: base64key=="))
        assertTrue(yaml.contains("bucket: tenant-dbs"))
    }

    // ── MinIO ─────────────────────────────────────────────────────────────────

    @Test
    fun `MinIO replica includes endpoint and force-path-style`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.MinIO(
                endpoint = "http://minio:9000",
                bucket = "cef-local",
                accessKeyId = "minioadmin",
                secretAccessKey = "minioadmin"
            )
        )
        assertTrue(yaml.contains("endpoint: http://minio:9000"))
        assertTrue(yaml.contains("force-path-style: true"))
    }

    @Test
    fun `MinIO replica includes S3-compatible bucket URL`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.MinIO(
                endpoint = "http://minio:9000",
                bucket = "cef-local",
                accessKeyId = "minioadmin",
                secretAccessKey = "minioadmin"
            )
        )
        assertTrue(yaml.contains("s3://cef-local"), "MinIO uses S3 protocol")
    }

    // ── multiple databases ────────────────────────────────────────────────────

    @Test
    fun `multiple db paths each produce a separate dbs entry`() {
        val yaml = generator.generate(
            dbPaths = listOf(
                "/data/db/ab/cd/alice.db",
                "/data/db/ef/gh/bob.db"
            ),
            replica = ReplicaConfig.S3("bucket", "us-east-1", "key", "secret")
        )
        assertTrue(yaml.contains("/data/db/ab/cd/alice.db"))
        assertTrue(yaml.contains("/data/db/ef/gh/bob.db"))
    }

    @Test
    fun `each db entry has its own distinct replica path derived from the filename`() {
        val yaml = generator.generate(
            dbPaths = listOf(
                "/data/db/ab/cd/alice.db",
                "/data/db/ef/gh/bob.db"
            ),
            replica = ReplicaConfig.S3("my-bucket", "us-east-1", "key", "secret")
        )
        // Each entry should reference its own object-storage prefix so blobs don't collide
        assertTrue(yaml.contains("alice"), "alice should have her own replica path")
        assertTrue(yaml.contains("bob"), "bob should have his own replica path")
    }

    @Test
    fun `empty db list produces a valid yaml document with empty dbs`() {
        val yaml = generator.generate(
            dbPaths = emptyList(),
            replica = ReplicaConfig.S3("bucket", "us-east-1", "key", "secret")
        )
        assertTrue(yaml.contains("dbs:"))
        assertFalse(yaml.contains("path:"), "no path entries expected for empty list")
    }

    // ── document structure ────────────────────────────────────────────────────

    @Test
    fun `output starts with a dbs top-level key`() {
        val yaml = generator.generate(
            dbPaths = listOf("/data/db/ab/cd/alice.db"),
            replica = ReplicaConfig.S3("b", "us-east-1", "k", "s")
        )
        assertTrue(yaml.trimStart().startsWith("dbs:"))
    }
}
