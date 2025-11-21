Gotcha—let’s do **both** 👍
I’ll give you:

1. A clean **ADR** you can drop into your architecture repo.
2. A short **email-style one-pager** you can send to Yash / Tevfik.

You can copy-paste as-is.

---

## 1️⃣ ADR: “Use Azure Table + Blob for Layout Registry (MVP)”

**Title:** ADR-XX – Use Azure Table Storage + Blob for Layout Registry (MVP)
**Status:** Accepted
**Date:** <today’s date>
**Owner:** Sajid

### 1. Context

The Layout Registry is a small but critical component of the TFS AE processing pipeline. It must:

* Store **layout metadata** (IDs, names, types, hashes).
* Store **embeddings** (vectors for K-NN similarity).
* Store **policies** (routing/arbitration configuration per layout).
* Store **audit logs** (every classification + routing decision).

The initial design assumed a **Postgres + pgvector** implementation for K-NN search. While powerful, that approach introduces:

* A new database platform to provision, secure, and operate.
* More operational overhead (backups, upgrades, index maintenance).
* Higher friction for an MVP with relatively **small scale** (tens of layouts, thousands of embeddings).

We need a **low-friction, Azure-native** implementation that:

* Fits current scale.
* Keeps us inside already-approved Azure services.
* Still keeps the data model **storage-agnostic** for future migration.

### 2. Decision

For MVP, the Layout Registry will use:

* **Azure Table Storage** for **metadata**:

  * `layouts`
  * `layout_embeddings`
  * `layout_policies`
  * `registry_audit_log`
* **Azure Blob Storage** for **large artifacts**:

  * Embedding vectors
  * Diagnostics payloads

K-NN similarity search will be implemented **in the application layer** (Helios Python service), not in the database.

The logical entities (`layouts`, `layout_embeddings`, `layout_policies`, `registry_audit_log`) remain **storage-agnostic** and can be migrated to Postgres/Cosmos/AI Search later without changing identifiers or external contracts.

### 3. Rationale

**Why Azure Table + Blob is a good fit for MVP**

* **Scale is small** in MVP (≤50 layouts, ≤a few thousand embeddings).
* Access patterns are simple:

  * Point lookups by `layout_id`, `policy_id`, `tenant_id`.
  * Small scans per tenant/month for reporting and auditing.
* **Azure Table** gives:

  * Fully managed key–value store.
  * No servers or databases to run.
  * Low cost and low operational overhead.
* **Azure Blob** is the right place for:

  * Large vectors (896-dim).
  * Future model artifacts and diagnostics.
* All **vector math** (K-NN search) is:

  * Implemented in Python (NumPy/FAISS or similar).
  * Decoupled from storage engine.
* The design keeps identifiers stable (`layout_id`, `policy_id`, `audit_id`), making it straightforward to:

  * Move embeddings to **pgvector** later.
  * Or push them into **Azure AI Search / vector DB**.

This approach lets us **ship MVP faster** and prove value before committing to a heavier persistence layer.

### 4. Alternatives Considered

1. **Postgres + pgvector (original design)**

   * **Pros:**

     * Rich SQL + relational modeling.
     * DB-native K-NN search.
     * Mature ecosystem.
   * **Cons:**

     * New DB platform for this project.
     * More operational overhead, especially for small scale.
     * Overkill for tens of layouts.

2. **Dedicated Vector DB (Pinecone, Weaviate, etc.)**

   * **Pros:**

     * Purpose-built for vector search at scale.
   * **Cons:**

     * Additional managed service.
     * Extra cost, complexity, and governance for MVP.
     * Not strictly necessary for current volumes.

3. **Azure AI Search with Vector Support**

   * **Pros:**

     * Integrated with search and filters.
   * **Cons:**

     * Higher complexity to configure and manage early.
     * Still a new component to justify at MVP stage.

### 5. Consequences

**Positive:**

* Faster time-to-MVP delivery.
* Lower infra cost and less ops burden.
* Registry is easy to reason about (4 Azure Tables + Blob).
* Clear separation between:

  * **Metadata** (Table)
  * **Vectors + diagnostics** (Blob)
  * **Similarity logic** (Python)

**Negative / Trade-offs:**

* No DB-side K-NN; all similarity search is in-app.
* For larger scale (hundreds of thousands of embeddings), we will need:

  * In-memory indexes, or
  * Migration to pgvector/AI Search.
* More responsibility on the app to manage:

  * Caching of embeddings.
  * Efficient loading of vectors.

### 6. Future Migration Path

Because we preserved the same logical model and identifiers, we can later:

* Create equivalent schemas in **Cosmos DB for PostgreSQL** (with `VECTOR` columns).
* Bulk-load data from Azure Table/Blob into Postgres/pgvector.
* Swap K-NN logic from “Python + Blob” to “pgvector in DB.”
* Keep ALL external API contracts and IDs unchanged.

This ADR supports a **“start simple, evolve safely”** strategy.

---

## 2️⃣ One-Pager Email Summary (to Yash / Tevfik)

You can send this as an email body (maybe tweak greetings/closing as you like):

---

**Subject:** Layout Registry – MVP Storage Choice (Azure Table + Blob)

Hi Yash,
Hi Tevfik,

I wanted to close the loop on the Layout Registry design and how we’re implementing it for MVP.

**What we’re building**

The Layout Registry is the small but critical metadata store that powers:

* Layout recognition (one row per known document layout, e.g., “FDA 3500A v2”).
* Layout embeddings (vectors used for K-NN similarity search).
* Policies (routing/arbitration rules per layout/tenant).
* Audit log (every classification and routing decision).

These show up in four logical tables:

1. `layouts`
2. `layout_embeddings`
3. `layout_policies`
4. `registry_audit_log`

**MVP storage choice**

For MVP, we’re intentionally keeping the registry **lightweight and fully Azure-native**:

* **Azure Table Storage** for all metadata:

  * Layouts (IDs, fingerprints, types, names, status).
  * Embeddings metadata (model, version, blob pointer).
  * Policies (JSON, versions, default flags).
  * Audit entries (doc_id, action, tier, scores, reasons).
* **Azure Blob Storage** for:

  * The actual embedding vectors (e.g., 896-dim arrays).
  * Diagnostics artifacts we might want to store later.

All **K-NN similarity** is done in the **Python service**, not in the DB. The Python code loads the small set of relevant embeddings into memory (or a lightweight local index) and computes cosine similarity there.

**Why this path (vs Postgres/pgvector now)**

Given our current scale (tens of layouts, a few thousand embeddings), Postgres+pgvector or a full vector DB is overkill. Azure Table + Blob gives us:

* No new DB platform to provision/manage for MVP.
* Simple, cheap, and approved Azure primitives.
* A straightforward partition/RowKey design optimized for:

  * Fast point reads by tenant/layout.
  * Efficient per-tenant/per-month audit scans.
* A clean separation of concerns:

  * Table = metadata and keys.
  * Blob = vectors and large JSON.
  * App = K-NN and decision logic.

**Future-proofing**

The important part: the logical model is **storage-agnostic**:

* We still have clear entities: `layouts`, `layout_embeddings`, `layout_policies`, `registry_audit_log`.
* IDs (`layout_id`, `policy_id`, `audit_id`, `trace_id`) won’t change.
* If we need to scale to hundreds of thousands of embeddings, we can:

  * Move vectors into **Cosmos DB for PostgreSQL + pgvector**, or
  * Use **Azure AI Search** as a vector index,
  * Without redesigning the layout registry itself.

From the pipeline’s point of view, it’s the same:
“Given this embedding, find the best layout + policy and log the decision.”

**Net effect**

* We stay within the existing Azure footprint for MVP.
* We avoid up-front complexity while still keeping a clean migration story.
* We can prove value (higher accuracy, faster layout onboarding, better auditability) first, and then harden the storage layer if and when scale requires it.

If you’re aligned with this, I’ll finalize the detailed design with the new ERD and Azure Table/Blob schemas and proceed with implementation.

Thanks,
Sajid

---

If you want, next I can give you:

* A **tiny “cheat sheet”** with the 4 tables + PK/RK patterns on one slide, or
* A **Mermaid ERD snippet** you can paste into docs for quick diagrams.
