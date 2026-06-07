# ADR 0003: Multi-Tenant Server Provisioning Architecture for Institutional Deployments

## Status
Accepted

## Context
We are deploying the College Executive Function (CEF) app as a multi-tenant web application in university private networks, scaling up to 40,000+ students. 

Because CEF is an academic planning utility (rather than a high-risk financial, transactional, or e-commerce platform), the runtime characteristics are unique:
1. **Low Transaction Risk:** There are no payment gateways, financial ledgers, or global states that require distributed lock managers or real-time consistency.
2. **Strict Workload Partitioning:** Every student’s calendar, preferences, and documents are 100% independent. There are zero transactions that span multiple student spaces.
3. **Bursty, Read-Heavy Operations:** Writes are highly clustered (e.g., uploading syllabi during the first week of the semester), while daily usage is read-heavy (checking agenda, chat queries).
4. **Constrained IT Budgets:** Universities (especially smaller regional colleges) prefer low-maintenance, cost-effective deployments that do not require dedicated Kubernetes or container orchestration expertise.

## Decision
For institutional deployments, we adopt the **Multi-Tenant Monolithic JVM Server** architecture backed by an **Asynchronous Ingest Worker Pool**:

### 1. Single-Process Monolithic Deployment
* The Ktor JVM server will be deployed as a single-process application (running on a VM or a single container instance) behind a basic reverse proxy (Nginx or Caddy) to handle SSL/TLS termination.
* High availability (HA) clusters are rejected as the primary deployment target due to unnecessary complexity, though simple Active-Passive VM replication can be used for hardware disaster recovery.

### 2. High-Speed Local SSD Storage
* Isolated student SQLite files are stored directly on high-speed, local block storage (SSD/NVMe) attached to the virtual machine.
* Network-mounted file systems (like NFS or SMB) are avoided to eliminate filesystem locking lag and latency overhead on SQLite read/write operations.

### 3. Asynchronous Ingest Worker Pool
* To absorb the CPU/memory spikes caused by concurrent document parses (students uploading syllabi at the start of a semester), Ktor will offload all PDF/DOCX parses and vector indexing tasks to an asynchronous background worker pool (`kotlinx.coroutines.channels`).
* The main HTTP thread pool remains dedicated to serving instant API responses and live SSE chat stream packets.

### 4. Off-Hours Maintenance and Migration Windows
* Database migrations (running the schema upgrades across all 40,000 SQLite database files) will be scheduled during low-traffic windows (e.g., 2:00 AM – 4:00 AM). 
* Short, temporary maintenance windows are acceptable in the context of an academic calendar planner.

## Consequences

### Positive
* **Operational Simplicity:** Requires no complex container orchestration (Kubernetes, Nomad, Knative), making it easy for campus IT staff to provision and maintain.
* **Low Hardware Cost:** Can run comfortably on a single cost-effective virtual machine, keeping the university’s hosting footprint very small.
* **Maximum SQLite Performance:** Storing files on local SSD ensures SQLite executes I/O operations in microseconds with zero network overhead.

### Negative
* **Vertical Scaling Limits:** Scaling is vertical (adding CPU/RAM to the server) rather than horizontal. However, given SQLite's lightweight nature, vertical scaling is more than sufficient to support 40,000 students.
* **Single Point of Failure:** If the host VM crashes, all students at the institution temporarily lose access until the VM restarts. This is mitigated by standard cloud hypervisor auto-restarts and is an acceptable trade-off for a non-financial planning utility.
