# ADR 0002: Multi-Tenant SQLite Database Isolation and Backup Strategy for Institutional Deployments

## Status
Accepted

## Context
We are scaling the College Executive Function (CEF) application from a single-user local tool to a multi-tenant university deployment capable of supporting campus-wide populations of up to 40,000+ active students.

To deliver this at institutional scale, the architecture must satisfy the following constraints:
1. **Absolute Data Privacy (FERPA):** In the United States, student academic records are governed by the Family Educational Rights and Privacy Act (FERPA). We must physically protect student records from leakage or accidental cross-talk.
2. **Infrastructure Agnosticism:** While some large universities operate on Amazon Web Services (AWS), many smaller colleges use Microsoft Azure, Google Cloud Platform (GCP), or run physical virtualized server closets (on-premise VMware/Proxmox clusters) inside their own campus network firewalls.
3. **Low Hardware Footprint:** The application should be highly efficient, avoiding the cost and operational overhead of maintaining massive shared PostgreSQL/MySQL clusters.
4. **Resiliency to Corruption:** A failure in one student's data space must have zero blast radius on the rest of the campus population.

## Scope & Architectural Partitioning
We explicitly separate database storage, backups, and recoverability strategies into two distinct architectural domains:
1. **Institutional Server Deployments (The Ktor JVM Web Server):** Governed by this ADR. This is a multi-tenant network environment where university IT manages the data of thousands of students. It requires server-level replication (Litestream, cloud blobs, on-premise NAS mounts).
2. **Client Applications (Android, iOS, JVM Desktop Client):** Governed by native OS guidelines. Each device represents a single-user sandbox environment. Backups are handled via native platform tools (Android Google Play Auto Backup, iOS iCloud Backup, Desktop user folder snapshots) rather than central server infrastructure.

## Decision
For **Institutional Server Deployments**, we adopt a **Database-per-Student SQLite Architecture** combined with a **Cloud-Agnostic Continuous Replication and Backup Strategy**:

### 1. Database-per-Student Physical Isolation
* Instead of storing all student data in a single database with a `student_id` column, the server maintains **40,000+ separate SQLite database files**.
* Database files will be sharded on disk using student directory hashes to prevent single-folder listing bottlenecks (e.g., `/data/db/st/ud/student123.db`).
* This guarantees physical isolation, making it impossible for SQL query bugs to leak student data across sessions.

### 2. Least Recently Used (LRU) Connection Cache
* To prevent the server from running out of memory or OS file descriptors, the backend will manage database handles via an **LRU Connection Cache** (e.g., capped at 1,000 concurrent open connections).
* Database connections are opened dynamically when a student makes a request and are safely closed and evicted if they remain idle for a configured duration.

### 3. Write-Ahead Logging (WAL) Mode
* SQLite databases will have WAL mode enabled by default. This writes transaction deltas to a separate `-wal` file, enabling concurrent reads and writes without blocking, and allowing safe online backups while the database is actively being written to.

### 4. Continuous Replication via Litestream
* We adopt **Litestream** (or equivalent low-overhead WAL replication utility) to monitor active database logs.
* When transactions complete, the utility replicates the binary diffs to an object storage target.
* Litestream config files will be parameterized to support the university's preferred storage backend:
  * **Microsoft Azure:** Replicating natively to Azure Blob Storage containers.
  * **AWS / GCP:** Replicating to S3 or Google Cloud Storage buckets.
  * **On-Premise Private Network:** Replicating to a local Network Attached Storage (NAS/NFS/SMB) share or a locally hosted **MinIO** container.

### 5. Nightly Compaction & Snapshot Backups
* For daily static backups, the server will execute SQLite's native `VACUUM INTO` command on active student databases:
  ```sql
  VACUUM INTO '/var/backups/staging/student123.db';
  ```
* This creates a compacted, consistent, and non-corrupt snapshot file on disk. The snapshot files are archived, compressed, and transferred to the storage target.

### 6. Recoverability via "Disposable Local Cache" Architecture (Mobile & Web)
* On all client platforms (Android, iOS) as well as the multi-tenant Web server, the local SQLite database is designed as a **disposable, fully recoverable cache**.
* **Source of Truth:** The student's primary academic records and deliverable files reside externally in Google Calendar (for event schedules) and Google Drive (for ingested documents like syllabi and notes).
* **Corruption Auto-Recovery:** If a client or server-side SQLite instance encounters file corruption (throwing a database corrupt exception), the app executes the following auto-recovery flow:
  1. Catch the initialization/connection exception.
  2. Delete the corrupted database file (`cef.db` and its WAL logs).
  3. Initialize a fresh, empty SQLite file.
  4. Automatically trigger a full synchronization query from Google Drive and Google Calendar.
* This ensures that physical local hardware failures, sudden app kills, or filesystem corruption result in zero permanent data loss, operating seamlessly on both mobile devices and web servers.

### 7. Tenant Token and Preference Isolation
* **The Client Pattern:** On individual client devices (Android, iOS, JVM Desktop), settings and OAuth access/refresh tokens are stored in the platform's local settings repository (such as Android SharedPreferences or iOS NSUserDefaults) via a global `Settings` instance.
* **The Server Pattern:** In a multi-tenant web server environment, the backend must keep tokens and API keys isolated for thousands of concurrent users.
* **Decision:** To prevent token leaks or session collision, the Ktor server **must not** use a shared system settings store (like JVM's global Java Preferences API). Instead, the `Settings` interface and token storage repositories (`GoogleTokenRepository`) must be backed directly by the student's **individual, isolated SQLite database file** (e.g. stored inside dedicated tables in `student123.db`). This ensures credentials and settings are physically isolated and managed within the same tenant boundary as their calendar data.

## Consequences

### Positive
* **Absolute FERPA Isolation:** Physical database separation eliminates the risk of cross-talk security bugs.
* **Low Cost & Overhead:** SQLite runs in-process, meaning the server has zero network database latency. High performance can be achieved on very modest VPS hardware.
* **Blast Radius Control:** If a database file is corrupted or requires restoration, the process is isolated to that specific student. The other 39,999 students experience zero downtime.
* **Cloud-Agnostic Support:** The deployment easily accommodates AWS, Azure, GCP, or purely local, firewall-protected university networks.
* **High Recoverability:** Because database instances are designed as recoverable caches, local storage corruption on any platform (Android, iOS, Web) results in zero data loss.
* **Secured Credentials:** Storing tokens inside the tenant's individual SQLite file ensures that user credentials and API configurations are physically isolated and deleted automatically when their account database is deleted, adhering to strict data hygiene regulations.

### Negative
* **Multi-DB Schema Migrations:** Database migrations must be run across all 40,000 files. This requires automated migration runner scripts on startup to iterate through all active database paths.
* **Aggregated Campus Analytics:** If the university requires aggregated reporting (e.g., total active tasks completed campus-wide), the backend cannot run a single simple `GROUP BY` query. Data must be periodically exported to a central, anonymous reporting data warehouse.

