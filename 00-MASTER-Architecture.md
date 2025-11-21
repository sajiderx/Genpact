Here’s a clean **v2** of both docs with the new Azure Table + Blob design baked in and registry content de-duplicated.

I’ll give you:

1. **Master Architecture – v2 (full text)**
2. **Registry Design – v2 (full text, condensed, with table defs you can paste under your ERD images)**

---

## 1. TFS Safety Case Processing – Master Architecture (MVP, v2)

> Supersedes the earlier v1.0 master architecture and updates the Layout Registry to use Azure Table + Blob for MVP.

### Document Purpose

This master architecture document provides the executive overview and design principles for Thermo Fisher Scientific's Adverse Event (AE) case processing enhancement. It serves as the navigation hub for detailed component designs and implementation guides.

**Target Audience**
Executive stakeholders, technical leads, and cross-functional team members.

For implementation details, see component-specific design documents referenced in Section 8 (Document Index).

---

### 1. Executive Summary

We are strengthening the existing Azure Textract-centric AE pipeline by adding a **thin, modular “intelligence layer”** that:

* Uses a **Layout Registry** to recognize known document layouts.
* Applies **embedding-based K-NN similarity search** to match new documents to known layouts.
* Drives **policy-based arbitration** across multiple extractors.
* Provides **end-to-end auditability and observability**.

For MVP we deliberately **keep storage light and Azure-native**:

* Layout registry metadata lives in **Azure Table Storage**.
* Large artifacts (vector embeddings, diagnostics payloads) live in **Azure Blob Storage**.
* Vector math (K-NN search) runs in **Helios’s Python service**, not in the database.
* The logical model is **storage-agnostic**, so we can move to Cosmos DB for PostgreSQL + pgvector or Azure AI Search later without changing the semantics.

These changes directly answer leadership guidance: optimize **accuracy, auditability, and repeatability**, not platform purity.

Preprocessing and full XML publishing remain Phase-2 accelerators once MVP is stable.

**Success Criteria (MVP)**

* **Accuracy:** ≥90% on premium client layouts (vs. ~82% baseline).
* **Latency:** P95 < 60s for 10-page documents.
* **Layout match:** ≥80% of incoming docs match an existing layout.
* **Auditability:** 100% of decisions are traceable (layout, policies, scores, reasons).

---

### 2. System Overview

#### 2.1 High-Level Architecture

```text
┌─────────────┐
│   Ingest    │  PDF/TIFF arrives
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│              TEXTRACT (Existing)                        │
│  Structure + Text Extraction (Forms, Tables, Key-Value) │
└──────┬──────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│   NEW INTELLIGENCE LAYER (MVP)       │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  1. Fingerprint & Embed        │  │ ← Layout identification
│  │     (page structure → vector)  │  │
│  └────────────┬───────────────────┘  │
│               │                      │
│  ┌────────────▼───────────────────┐  │
│  │  2. Layout Registry            │  │ ← Match to known layouts
│  │     Azure Table + Blob + KNN   │  │   (KNN in Python service)
│  └────────────┬───────────────────┘  │
│               │                      │
│  ┌────────────▼───────────────────┐  │
│  │  3. Multi-Extractor Candidates │  │ ← Parallel extraction
│  │     Textract + AOAI (optional) │  │
│  └────────────┬───────────────────┘  │
│               │                      │
│  ┌────────────▼───────────────────┐  │
│  │  4. Arbitration Engine         │  │ ← Winner selection
│  │     Weighted voting + policy   │  │
│  └────────────┬───────────────────┘  │
│               │                      │
│  ┌────────────▼───────────────────┐  │
│  │  5. Schema Validation          │  │ ← Quality gates
│  │     JSON schema + semantic     │  │
│  └────────────┬───────────────────┘  │
│               │                      │
│  ┌────────────▼───────────────────┐  │
│  │  6. Client Fine-Tuning (opt)   │  │ ← Premium accuracy
│  │     Custom AOAI models         │  │
│  └────────────┬───────────────────┘  │
└───────────────┼──────────────────────┘
                │
                ▼
         ┌─────────────┐
         │   HITL?     │  Low confidence → human review
         └──────┬──────┘
                │
                ▼
         ┌─────────────┐
         │   Publish   │  Validated JSON + audit trail
         └─────────────┘
                │
                ▼
         ┌─────────────┐
         │Observability│  OpenTelemetry spans + metrics
         └─────────────┘
```

#### 2.2 What Changes vs. Current State

| Aspect               | Current (Baseline)      | MVP Enhancement                         | Impact                     |
| -------------------- | ----------------------- | --------------------------------------- | -------------------------- |
| Extraction           | Textract only           | Textract + optional AOAI                | +5–8pp accuracy            |
| Layout Handling      | Manual mapping per form | Layout Registry + embeddings            | 10× faster onboarding      |
| Decision Logic       | “Last extractor wins”   | Arbitration with scoring & policies     | Explainable, auditable     |
| Accuracy Monitoring  | Manual spot checks      | Real-time field-level metrics           | Proactive quality mgmt     |
| Client Customization | Not available           | Fine-tuned models for premium clients   | ≥90% accuracy where needed |
| Observability        | Basic logs              | Full OpenTelemetry tracing & dashboards | Root cause in <5 minutes   |

---

### 3. Component Map

The system consists of 5 core components and 3 cross-cutting concerns. Each core component has its own detailed design doc.

#### 3.1 Core Components

**Layout Registry**
*Detailed Design → Layout Registry – Detailed Design (Azure Table + Blob)*

* **Purpose:** Fast layout lookup + policy routing using embeddings.
* **Technology (MVP):** Azure Table Storage (metadata), Azure Blob Storage (vectors & diagnostics), Python K-NN implementation using OpenAI / Azure OpenAI embeddings.
* **Key Capability:** Match incoming document layout and return policies in <100 ms for registry sizes in the low thousands.
* **Owner:** Sajid
* **MVP Status:** Critical path.

Why it matters: avoids re-solving the same forms, improves onboarding speed, and provides a single source of truth for layouts, policies, and audit history.

---

**Arbitration Engine**
*Detailed Design → Arbitration Engine Design*

* **Purpose:** Multi-extractor fusion and winner selection per field.
* **Technology:** Weighted voting algorithm + policy-driven rules stored in `layout_policies`.
* **Key Capability:** Choose best extraction result with explainable scoring and thresholds.
* **Owner:** Sajid (implementation) + Yash (policy definitions).
* **MVP Status:** Critical path.

---

**Client Fine-Tuning**

* **Purpose:** Achieve ≥90% accuracy for selected premium clients.
* **Technology:** Azure OpenAI fine-tuning + prompt engineering.
* **Status:** Limited MVP rollout (1–2 clients), extended in Phase-2.

---

**Observability Stack**

* **Purpose:** End-to-end tracing, accuracy metrics, and drift detection.
* **Technology:** OpenTelemetry + Azure Application Insights + time-series store.
* **Status:** Critical for demonstrating MVP success.

---

**(Optional / Phase-2) Clustering Engine (ADAH)**

* **Purpose:** Auto-generalization and merging of highly similar layouts to keep the registry tidy.
* **Technology:** HDBSCAN / similar clustering over stored layout embeddings (run offline or on a schedule).
* **Status:** **Phase-2** feature. MVP design keeps the data model clustering-friendly but does not require ADAH to be live on day one.

---

#### 3.2 Cross-Cutting Concerns

**Data Models & APIs**

* Logical schemas for:

  * `layouts`
  * `layout_embeddings`
  * `layout_policies`
  * `registry_audit_log`
* Azure Table / Blob implementation details (MVP) plus storage-agnostic contracts for future migrations.
* External and internal API contracts (JSON payloads, error codes).

**Error Handling & Resilience**

* Retry policies per component.
* Circuit breaker patterns around AOAI and storage.
* Fallback logic (e.g., generic policy if registry unavailable).

**Performance & Scalability**

* Latency targets (P50/P95/P99) by step.
* Throughput requirements (docs/hour).
* Back-pressure and throttling strategies.

**Security & Compliance**

* PHI handling (encryption at rest and in transit).
* Least-privilege access using managed identities.
* Audit requirements (FDA 21 CFR Part 11) and data retention policies.

---

### 4. Design Principles

**Accuracy First**
Choose the best extractor output via arbitration; measure everything.

**Config > Code**
Policies, thresholds, and routing live in configuration (YAML/JSON in Blob), not code. The registry just points to the current policy for a layout/tenant.

**Reusable Intelligence**
Learn each layout once. The registry stores a durable fingerprint, embedding, and policy configuration.

* MVP: one row per layout + per-model embedding entries.
* Phase-2: optional ADAH clustering for auto-generalization, using the same embeddings and IDs (no schema change required).

**Human-in-the-Loop (HITL)**
Low-confidence extractions go to humans; corrections flow back into training datasets and policy refinements.

**Audit Everywhere**
Every classification and arbitration decision is explainable (layout match, similarity score, policy version, chosen extractor, reason).

---

### 5. Detailed Data Flow

(High-level flow is unchanged; only the implementation of “Registry” is now Azure Table + Blob.)

1. Textract produces structure + text.
2. Fingerprint service builds a **layout embedding** using the chosen embedding model.
3. Python service performs **K-NN search in memory** using embeddings stored in Blob and metadata in Azure Table.
4. The best match returns:

   * `layout_id`
   * `policy_id` / policy JSON
   * match similarity + tier (T1/T2/T3).
5. Arbitration runs multiple extractors, applies policy, and selects winners.
6. Validation checks schema and semantics.
7. Results are either:

   * Published as validated JSON + audit log entries in `registry_audit_log`, or
   * Sent to HITL for review and later re-ingestion.

(You can retain your existing Mermaid sequence diagram; just update the registry label to “Registry (Azure Table + Blob + KNN in app)”.)

---

### 6. Scope: MVP vs Phase-2

#### 6.1 MVP Scope (Phase-1: Months 1–3)

Goal: Prove the intelligence layer improves accuracy and auditability with minimal infrastructure.

| Capability                               | Included | Success Metric                             |
| ---------------------------------------- | -------: | ------------------------------------------ |
| Layout Registry (Azure Table + Blob)     |        ✅ | Match rate ≥ 80%, registry size manageable |
| App-level K-NN search (Python)           |        ✅ | P95 layout lookup < 100 ms                 |
| Policy-driven Arbitration + Validation   |        ✅ | +5pp accuracy improvement                  |
| Registry Audit Log                       |        ✅ | 100% of decisions logged                   |
| Premium Client Fine-Tuning (1–2 clients) |        ✅ | ≥90% accuracy for selected clients         |
| Observability & Metrics                  |        ✅ | Field-level dashboards live                |
| Publishing (JSON)                        |        ✅ | Audit trail queryable                      |

#### 6.2 Phase-2 Scope (Months 4–6)

Goal: Production hardening, clustering, and expanded fine-tuning.

| Capability                         | Description                                     | Benefit                            |
| ---------------------------------- | ----------------------------------------------- | ---------------------------------- |
| Embedding-Based Clustering (ADAH)  | Offline/periodic clustering of layouts          | Registry stays small + clean       |
| Layout Generalization / Auto-merge | Merge N variants into canonical layout policies | Less manual curation               |
| Expanded Fine-Tuning               | 5+ clients                                      | Premium tier revenue               |
| XML Publishing Path                | XML contract validation + delivery              | Integration with client systems    |
| Advanced Business Rules            | Cross-form, temporal, and consistency checks    | Catch logical AE inconsistencies   |
| SLO Enforcement & Auto-Scaling     | SLOs, anomaly detection, and capacity tuning    | 99.5%+ uptime, predictable latency |

---

### 7. Non-Functional Requirements (NFRs)

* **Availability:** 99.5% for registry lookup & arbitration endpoints.
* **Latency:** Registry lookup P95 < 100 ms; end-to-end pipeline P95 < 60s.
* **Security:** All PHI encrypted at rest and in transit; registry restricted via managed identities and RBAC.
* **Compliance:** Full audit trail for all layout matching and arbitration decisions.

---

### 8. Document Index

* **Layout Registry – Detailed Design (Azure Table + Blob)** – this doc’s companion component spec.
* Arbitration Engine – Detailed Design.
* Observability & Telemetry – Detailed Design.
* Client Fine-Tuning – Detailed Design.
* Data Models & Schemas – Cross-cutting reference for JSON payloads and logical entities.

---

## 2. Layout Registry – Detailed Design (Azure Table + Blob, v2)

> Supersedes the earlier “Layout Registry – Detailed Design (Postgres + pgvector)” and aligns with the new Azure Table + Blob MVP design.

### 1. Component Overview

#### 1.1 Purpose & Context

The Layout Registry solves the “infinite layouts” problem in AE document processing:

* **One-time learning:** Capture each layout’s structure and metadata once.
* **Semantic matching:** Use embeddings to find “close enough” layouts even when scanners, margins, or minor versions change.
* **Policy routing:** Bind matched layouts to policies that drive arbitration and validation.
* **Auditability:** Record all classification and routing decisions for compliance and tuning.

The registry is intentionally **small, fast, and storage-agnostic**. MVP uses **Azure Table Storage** and **Azure Blob Storage**; future versions may move to Cosmos DB PostgreSQL or Azure AI Search without changing the logical entities.

#### 1.2 Success Criteria

| Metric                |   Target |
| --------------------- | -------: |
| Top-1 match precision |   ≥ 0.90 |
| Match recall          |   ≥ 0.85 |
| K-NN lookup P95       | < 100 ms |
| Registry size (MVP)   |  ≤ few k |
| Audit coverage        |     100% |

---

### 2. Storage & Data Model

#### 2.1 Logical Entities & Relationships

Logical entities (storage-agnostic):

* `layouts` – one row per known layout.
* `layout_embeddings` – embeddings per layout/model variant.
* `layout_policies` – configuration and routing rules per layout.
* `registry_audit_log` – append-only log of classification and routing events.

Relationships:

* `layout_embeddings.layout_id` → `layouts.layout_id` (1-to-many).
* `layout_policies.layout_id` → `layouts.layout_id` (1-to-many, usually small).
* `registry_audit_log.layout_id` → `layouts.layout_id` (optional FK at the logical level).

*(You can paste your updated ERD diagram image in this section.)*

#### 2.2 Azure Table Implementation

Each logical table is implemented as an **Azure Table** with chosen `PartitionKey` and `RowKey` patterns, plus regular columns.

##### 2.2.1 `layouts`

**Keys**

* `PartitionKey`: `tenant_id#client_id`
  Example: `Abbott-1234-prod#TFS-PV`
* `RowKey`: `layout_id` (GUID)
  Example: `81e62ad7-f8b3-4fa9-bb2e-81202d7a6294`

**Columns**

| Column             | Type     | Notes                                      |
| ------------------ | -------- | ------------------------------------------ |
| layout_id          | string   | GUID, also used as RowKey                  |
| client_id          | string   | Partition alternative / reporting key      |
| tenant_id          | string   | e.g. `"Abbott-1234-prod"`                  |
| layout_name        | string   | Human-friendly name, e.g. `"FDA 3500A v2"` |
| version            | int      | Schema/layout version                      |
| fingerprint_hash   | string   | SHA-256 hex of layout fingerprint          |
| layout_type        | string   | e.g. `"AE_FORM"`, `"LAB_REPORT"`           |
| status             | string   | `"active"` | `"deprecated"`                |
| embedding_blob_url | string   | Pointer to primary embedding in Blob       |
| created_at         | datetime | ISO-8601                                   |
| updated_at         | datetime | ISO-8601                                   |
| is_active          | bool     | Soft delete flag                           |
| row_version        | string   | UUID for optimistic concurrency            |

You can optionally add extra metadata (page count, etc.) as needed.

---

##### 2.2.2 `layout_embeddings`

**Keys**

* `PartitionKey`: `layout_id`
* `RowKey`: `model_provider#model_name#model_version`
  Example: `azure-openai#text-embedding-3-small#2024-01-15`

**Columns**

| Column                    | Type     | Notes                                                      |
| ------------------------- | -------- | ---------------------------------------------------------- |
| embedding_id              | string   | GUID (convenience ID)                                      |
| layout_id                 | string   | FK to `layouts.layout_id`                                  |
| embedding_vector_blob_url | string   | Blob URL of serialized vector                              |
| model_name                | string   | e.g. `"text-embedding-3-small"`                            |
| model_version             | string   | Semantic version, e.g. `"2024-01-15"`                      |
| model_provider            | string   | `"openai"`, `"azure-openai"`, `"cohere"`, `"tfs-local"`, … |
| embedding_dim             | int      | e.g. `896`                                                 |
| created_at                | datetime | ISO-8601                                                   |
| row_version               | string   | UUID                                                       |

Each layout can have multiple embedding rows (e.g., different models or versions).

---

##### 2.2.3 `layout_policies`

**Keys**

* `PartitionKey`: `layout_id`
* `RowKey`: `policy_id` (GUID)
  Example: `3fa85f64-5717-4562-b3fc-2c963f66afa6`

**Columns**

| Column               | Type                | Notes                                                        |
| -------------------- | ------------------- | ------------------------------------------------------------ |
| policy_id            | string              | GUID, also RowKey                                            |
| layout_id            | string              | FK to `layouts.layout_id`                                    |
| policy_version       | int                 | Incrementing integer                                         |
| policy_json          | string              | Serialized JSON; ≤32 KB                                      |
| is_default           | bool                | `true` if default policy for this layout                     |
| effective_from       | datetime            | ISO-8601                                                     |
| effective_to         | datetime (nullable) | Optional end date                                            |
| diagnostics_blob_url | string              | Optional Blob URL for diagnostics (sampling, confusion data) |
| created_at           | datetime            | ISO-8601                                                     |
| updated_at           | datetime            | ISO-8601                                                     |
| row_version          | string              | UUID                                                         |

---

##### 2.2.4 `registry_audit_log`

**Keys**

* `PartitionKey`: `tenant_id#client_id#yyyyMM`
  Example: `Abbott-1234-prod#TFS-PV#202411`
* `RowKey`: `created_at_ISO8601#trace_id#audit_id`
  Example:
  `2024-11-15T10:23:45.123456Z#trace-12345#a1b2c3d4-5678-90ab-cdef-1234567890ab`

**Columns**

| Column           | Type          | Notes                                                 |
| ---------------- | ------------- | ----------------------------------------------------- |
| audit_id         | string (GUID) | Unique audit entry ID                                 |
| doc_id           | string        | Document trace identifier (ingest or pipeline id)     |
| layout_id        | string        | Matched layout_id, nullable when no match             |
| action           | string        | `"matched"`, `"defaulted"`, `"promoted"`, `"skipped"` |
| tier             | string        | `"T1"`, `"T2"`, `"T3"`                                |
| similarity_score | float         | Nullable; only for matched/defaulted                  |
| fingerprint_hash | string        | Layout fingerprint used during lookup                 |
| trace_id         | string        | OpenTelemetry trace / span correlation ID             |
| reason           | string        | Free-text / JSON reason; up to 32 KB                  |
| created_at       | datetime      | ISO-8601 (also part of RowKey)                        |
| created_by       | string        | Service or user id                                    |
| expires_at       | datetime      | ISO-8601; optional TTL / archival boundary            |

Audit log is **append-only**; no updates in normal operation.

---

#### 2.3 Key Patterns – Examples

You can keep these as examples in the doc or move them into an appendix.

* **Layouts**

  * PartitionKey: `Abbott-1234-prod#TFS-PV`
  * RowKey: `81e62ad7-f8b3-4fa9-bb2e-81202d7a6294`

* **Layout Embeddings**

  * PartitionKey: `81e62ad7-f8b3-4fa9-bb2e-81202d7a6294`
  * RowKey: `azure-openai#text-embedding-3-small#2024-01-15`

* **Layout Policies**

  * PartitionKey: `81e62ad7-f8b3-4fa9-bb2e-81202d7a6294`
  * RowKey: `3fa85f64-5717-4562-b3fc-2c963f66afa6`

* **Audit Log**

  * PartitionKey: `Abbott-1234-prod#TFS-PV#202411`
  * RowKey: `2024-11-15T10:23:45.123456Z#trace-12345#a1b2c3d4-5678-90ab-cdef-1234567890ab`

---

### 3. Core Flows

#### 3.1 Layout Lookup (Read Path)

1. **Input:**

   * `tenant_id`, `client_id`, `doc_id`
   * layout embedding vector from fingerprint service.

2. **Steps (Python service):**

   1. Load candidate embeddings for this tenant/client into memory (e.g., cached or from Blob).
   2. Run K-NN search (cosine similarity) to find top-k layout candidates.
   3. Apply similarity thresholds and tiering rules (T1/T2/T3).
   4. Resolve to:

      * `layout_id` (or `null` if no good match).
      * `policy_id` / `policy_json` from `layout_policies`.
   5. Write `registry_audit_log` entry with action = `"matched"`/`"defaulted"`/`"skipped"`.

3. **Output:**

   * `layout_id` (optional)
   * `policy_json` / policy reference
   * similarity + tier + audit id.

#### 3.2 Layout Registration / Update (Write Path)

1. Fingerprint service or admin UI proposes a new layout with metadata.
2. Service writes/updates a row in `layouts`:

   * Uses `tenant_id#client_id` partition.
   * Generates `layout_id` GUID.
3. For each embedding model:

   * Serialize vector to Blob.
   * Create row in `layout_embeddings` keyed by `layout_id` + `model_provider#model_name#model_version`.
4. Create or update `layout_policies` rows for this layout (policy JSON, versioning).
5. Write a `registry_audit_log` entry (operation `“promoted”`).

#### 3.3 Audit Querying

* Pipelines and dashboards can scan `registry_audit_log` by:

  * `PartitionKey` (tenant/client/month).
  * `RowKey` prefix (time range).
  * Additional filters on `action`, `layout_id`, `tier`, etc.
* For long-term compliance, logs can be periodically moved from Azure Table to archive storage or a data warehouse.

---

### 4. Operational Considerations

* **Performance:**

  * Keep registry small in MVP (tens of layouts, low thousands max).
  * Use in-memory caches of embeddings keyed by tenant/client.

* **Reliability:**

  * If Azure Table or Blob is unavailable, fall back to:

    * generic policy (no layout match), and
    * log degraded-mode audit entries.

* **Security:**

  * Use managed identities for access to Table and Blob.
  * Encrypt PHI and sensitive metadata in transit (HTTPS) and at rest.

* **Retention:**

  * Configure `expires_at` for audit logs and apply scheduled cleanup/archival jobs.

---

### 5. Future Evolution (Non-Breaking Options)

1. **Cosmos DB for PostgreSQL + pgvector**

   * Migrate `layout_embeddings` into a `VECTOR` column.
   * Keep the same logical entities; replace Python K-NN with DB-native K-NN.
   * Best if we need millions of embeddings and tighter SLAs.

2. **Azure AI Search or Vector DB**

   * Keep registry metadata in Azure Table/SQL.
   * Push embeddings and searchable fields into an Azure AI Search index.
   * Support hybrid search (text + vectors) and richer filtering.

The current design keeps all identifiers (`layout_id`, `policy_id`, `audit_id`, `trace_id`) stable so these migrations are behind the scenes and do **not** affect arbitration or client contracts.

---

If you like, next step I can do is:

* generate a **short ADR** (“Why Azure Table + Blob for MVP”) using this text, and
* a **one-pager** for the registry you can send to Yash / Tevfik as an email summary.
