package com.borinquenterrier.cef

import java.io.File

sealed class ReplicaConfig {
    data class S3(
        val bucket: String,
        val region: String,
        val accessKeyId: String,
        val secretAccessKey: String
    ) : ReplicaConfig()

    data class AzureBlob(
        val accountName: String,
        val accountKey: String,
        val container: String
    ) : ReplicaConfig()

    data class MinIO(
        val endpoint: String,
        val bucket: String,
        val accessKeyId: String,
        val secretAccessKey: String
    ) : ReplicaConfig()
}

class LitestreamConfigGenerator {

    fun generate(dbPaths: List<String>, replica: ReplicaConfig): String {
        val sb = StringBuilder("dbs:\n")
        for (path in dbPaths) {
            val name = File(path).nameWithoutExtension
            sb.append("  - path: $path\n")
            sb.append("    replicas:\n")
            sb.append(replicaYaml(name, replica))
        }
        return sb.toString()
    }

    private fun replicaYaml(name: String, replica: ReplicaConfig): String = when (replica) {
        is ReplicaConfig.S3 -> """
            |      - url: s3://${replica.bucket}/$name
            |        region: ${replica.region}
            |        access-key-id: ${replica.accessKeyId}
            |        secret-access-key: ${replica.secretAccessKey}
            |""".trimMargin()

        is ReplicaConfig.AzureBlob -> """
            |      - type: abs
            |        account-name: ${replica.accountName}
            |        account-key: ${replica.accountKey}
            |        bucket: ${replica.container}
            |        path: $name
            |""".trimMargin()

        is ReplicaConfig.MinIO -> """
            |      - url: s3://${replica.bucket}/$name
            |        endpoint: ${replica.endpoint}
            |        access-key-id: ${replica.accessKeyId}
            |        secret-access-key: ${replica.secretAccessKey}
            |        force-path-style: true
            |""".trimMargin()
    }
}
