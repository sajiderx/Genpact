# TFS Safety Case Processing – Data Flow Diagrams

**Version:** 1.0  
**Owner:** Sajid (Technical Lead)  
**Last Updated:** November 4, 2024

---

## Document Purpose

This document provides visual representations of data flows within the TFS AE pipeline enhancement. Diagrams are organized by abstraction level: system context, component interactions, and detailed process flows.

**Diagram Format:** Mermaid (renders in GitHub, Azure DevOps, and most markdown viewers)

**Related Documents:**
- [Master Architecture](00-MASTER-Architecture.md) - System overview and design principles
- [Component Designs](02-Component-Designs/) - Detailed component specifications

---

## 1. System Context (Level 0)

### 1.1 External System Integration

```mermaid
graph TB
    subgraph "External Systems"
        Client[Client Systems<br/>FDA, EMA Portals]
        Storage[Azure Blob Storage<br/>PDF/TIFF Input]
        Monitor[Azure Monitor<br/>App Insights]
    end
    
    subgraph "TFS AE Pipeline (MVP)"
        Pipeline[Document Intelligence<br/>Enhancement Layer]
    end
    
    subgraph "Downstream Systems"
        EMDM[EMDM System<br/>Master Data]
        Databricks[Databricks Delta<br/>Training Data]
        Reporting[Power BI<br/>Accuracy Dashboards]
    end
    
    Storage -->|Documents| Pipeline
    Client -->|Form Submissions| Storage
    Pipeline -->|Validated JSON| EMDM
    Pipeline -->|HITL Corrections| Databricks
    Pipeline -->|Metrics| Monitor
    Monitor -->|Dashboards| Reporting
    
    style Pipeline fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Storage fill:#E8F4F8,stroke:#7BB3D0
    style EMDM fill:#E8F4F8,stroke:#7BB3D0
```

### 1.2 Data Flow Summary

| Source | Destination | Data Type | Volume | Frequency |
|--------|-------------|-----------|--------|-----------|
| Client Systems → Blob Storage | PDF/TIFF documents | ~2-50 pages | 1000/day | Continuous |
| Blob Storage → Pipeline | Binary blobs | 1-20 MB | 1000/day | Event-driven |
| Pipeline → EMDM | Validated JSON | 10-100 KB | 850/day | Real-time |
| Pipeline → HITL Queue | Low-confidence extractions | 10-100 KB | 150/day | Real-time |
| HITL → Databricks | Corrected labels | 10-50 KB | 150/day | Batch (hourly) |

---

## 2. Component Interaction (Level 1)

### 2.1 End-to-End Processing Flow

```mermaid
sequenceDiagram
    participant Blob as Azure Blob
    participant Textract as Textract
    participant Finger as Fingerprint
    participant Registry as Layout Registry
    participant Extract as Extractors
    participant Arb as Arbitration
    participant Val as Validation
    participant Pub as Publisher
    participant HITL as HITL Queue
    participant OTel as OpenTelemetry
    
    Note over Blob,OTel: Trace ID: abc-123-xyz
    
    Blob->>Textract: Event: blob created (PDF)
    activate Textract
    Textract->>Textract: OCR + structure analysis
    Textract-->>Finger: Raw text + bboxes
    deactivate Textract
    
    activate Finger
    Finger->>Finger: Generate embedding (768-dim)
    Finger->>OTel: Span: fingerprint.generate (45ms)
    Finger-->>Registry: KNN search request
    deactivate Finger
    
    activate Registry
    Registry->>Registry: IVFFlat search (top-5)
    Registry->>OTel: Span: registry.lookup (62ms)
    
    alt Match found (similarity ≥ 0.75)
        Registry-->>Extract: policy_id + layout_id
        Note right of Registry: Known layout<br/>Use specific policy
    else No match
        Registry-->>Extract: generic_policy
        Registry->>Registry: Mark as candidate_new
        Note right of Registry: New layout<br/>Learn for future
    end
    deactivate Registry
    
    activate Extract
    par Parallel Extraction
        Extract->>Extract: Textract (existing)
        Extract->>Extract: AOAI GPT-4 (if enabled)
    end
    Extract->>OTel: Span: extractors.parallel (1.8s)
    Extract-->>Arb: candidates[]
    deactivate Extract
    
    activate Arb
    loop For each field
        Arb->>Arb: Score candidates
        Arb->>Arb: Apply policy weights
        Arb->>Arb: Select winner
    end
    Arb->>OTel: Span: arbitration.execute (0.3s)
    Arb-->>Val: winner_json + metadata
    deactivate Arb
    
    activate Val
    Val->>Val: JSON schema validation
    Val->>Val: RxNorm/MedDRA lookup
    Val->>Val: Cross-field rules
    Val->>OTel: Span: validation.run (0.5s)
    
    alt Valid + High confidence (≥0.85)
        Val-->>Pub: Publish to EMDM
        activate Pub
        Pub->>Pub: Write JSON + audit trail
        Pub->>OTel: Span: publish.write (0.2s)
        deactivate Pub
    else Invalid OR Low confidence
        Val-->>HITL: Queue for review
        activate HITL
        HITL->>HITL: Human correction
        HITL-->>Pub: Corrected JSON
        HITL->>Databricks: Feedback for training
        deactivate HITL
    end
    deactivate Val
    
    OTel->>OTel: Aggregate trace (total: 3.2s)
```

### 2.2 Component Dependencies

```mermaid
graph LR
    subgraph "Data Sources"
        Blob[Azure Blob<br/>Storage]
        RxNorm[RxNorm API]
        MedDRA[MedDRA Lookup]
    end
    
    subgraph "Core Pipeline"
        Textract[Textract<br/>Existing]
        Fingerprint[Fingerprint<br/>Generator]
        Registry[Layout Registry<br/>Postgres+pgvector]
        Extractors[Multi-Extractor<br/>Textract+AOAI]
        Arbitration[Arbitration<br/>Engine]
        Validation[Schema+Semantic<br/>Validator]
    end
    
    subgraph "Supporting Services"
        Policy[Policy Store<br/>Blob/Config]
        AOAI[Azure OpenAI<br/>GPT-4]
        Cluster[Clustering<br/>ADAH]
    end
    
    subgraph "Outputs"
        Publish[JSON Publisher]
        HITL[HITL Queue]
        Metrics[Metrics Store]
    end
    
    Blob --> Textract
    Textract --> Fingerprint
    Fingerprint --> Registry
    Registry --> Policy
    Registry --> Extractors
    Policy --> Arbitration
    Policy --> Validation
    
    Extractors --> Textract
    Extractors -.->|if enabled| AOAI
    Extractors --> Arbitration
    
    Arbitration --> Validation
    Validation --> RxNorm
    Validation --> MedDRA
    
    Validation --> Publish
    Validation --> HITL
    
    Fingerprint -.->|batch| Cluster
    Cluster -.->|updates| Registry
    
    Arbitration --> Metrics
    Validation --> Metrics
    Registry --> Metrics
    
    style Registry fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Arbitration fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Cluster fill:#F5A623,stroke:#D68910,color:#fff
```

---

## 3. Detailed Process Flows (Level 2)

### 3.1 Registry Lookup Flow

```mermaid
flowchart TD
    Start([Document Arrives]) --> Extract[Textract Extraction]
    Extract --> Generate[Generate Fingerprint<br/>- Page layout<br/>- Text density<br/>- Field positions]
    Generate --> Embed[Create Embedding<br/>text-embedding-3-small<br/>768 dimensions]
    
    Embed --> KNN{KNN Search<br/>Postgres pgvector<br/>top-k=5, τ=0.75}
    
    KNN -->|Match found<br/>similarity ≥ 0.75| LoadPolicy[Load Policy<br/>from policy_id]
    KNN -->|No match| Generic[Use Generic Policy]
    
    LoadPolicy --> Cache{Policy in<br/>Redis cache?}
    Cache -->|Yes| UsePolicy[Route with Policy]
    Cache -->|No| FetchPolicy[Fetch from Blob]
    FetchPolicy --> CacheIt[Cache for 1 hour]
    CacheIt --> UsePolicy
    
    Generic --> MarkNew[Mark as candidate_new<br/>for clustering]
    MarkNew --> UsePolicy
    
    UsePolicy --> Extractors([Pass to Extractors])
    
    style KNN fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style LoadPolicy fill:#50E3C2,stroke:#2EAA8C,color:#000
    style Generic fill:#F5A623,stroke:#D68910,color:#fff
```

**Key Decision Points:**

| Decision | Criteria | Outcome |
|----------|----------|---------|
| **Similarity threshold met?** | cosine_similarity ≥ 0.75 | Use matched policy vs. generic |
| **Policy cached?** | Redis lookup (key: policy_id) | Fast path vs. blob fetch |
| **Mark as new layout?** | No match OR similarity < 0.50 | Queue for clustering analysis |

**Performance Characteristics:**

```
KNN Search:        42ms @ P50, 87ms @ P95 (50k vectors)
Policy Load (hit): 2ms (Redis)
Policy Load (miss): 45ms (Blob fetch + parse)
Total Latency:     ~50-130ms
```

---

### 3.2 Arbitration Decision Tree

```mermaid
flowchart TD
    Start([Candidates Array<br/>Textract + AOAI]) --> Loop{For each field}
    
    Loop --> Score[Calculate Scores<br/>- Source reliability<br/>- Field difficulty<br/>- Historical accuracy]
    
    Score --> Weight[Apply Policy Weights<br/>Textract: 0.4<br/>AOAI: 0.6]
    
    Weight --> Compute[Weighted Score<br/>= reliability × weight × confidence]
    
    Compute --> Compare{Compare Scores}
    
    Compare -->|Clear winner<br/>margin > 0.15| SelectWinner[Select Winner]
    Compare -->|Tie<br/>margin ≤ 0.15| TieBreaker{Tie-Breaker<br/>Strategy}
    
    TieBreaker -->|Policy: prefer_aoai| SelectAOAI[Choose AOAI]
    TieBreaker -->|Policy: highest_conf| SelectHighConf[Choose Higher<br/>Confidence]
    TieBreaker -->|Policy: committee| Average[Average Values]
    
    SelectWinner --> Log[Log Decision<br/>- Winner<br/>- Scores<br/>- Reason]
    SelectAOAI --> Log
    SelectHighConf --> Log
    Average --> Log
    
    Log --> More{More fields?}
    More -->|Yes| Loop
    More -->|No| Aggregate[Aggregate Results<br/>Calculate overall confidence]
    
    Aggregate --> ConfCheck{Overall<br/>confidence ≥ 0.85?}
    ConfCheck -->|Yes| Proceed([Proceed to Validation])
    ConfCheck -->|No| FlagHITL[Flag for HITL<br/>+ proceed to validation]
    FlagHITL --> Proceed
    
    style Score fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style SelectWinner fill:#50E3C2,stroke:#2EAA8C,color:#000
    style FlagHITL fill:#F5A623,stroke:#D68910,color:#fff
```

**Scoring Formula:**

```python
weighted_score = (
    source_reliability[source]  # 0.85 (Textract) vs 0.92 (AOAI)
    × policy_weight[field][source]  # e.g., 0.4 vs 0.6
    × candidate_confidence  # 0.0-1.0 from extractor
    × (1 - field_difficulty[field])  # 0.3 (easy) vs 0.9 (hard)
)

# Example calculation:
# Field: patient_name, Textract candidate
# = 0.85 × 0.4 × 0.95 × (1 - 0.3)
# = 0.85 × 0.4 × 0.95 × 0.7
# = 0.226

# Field: patient_name, AOAI candidate
# = 0.92 × 0.6 × 0.88 × 0.7
# = 0.342  ← Winner
```

**Tie-Breaker Strategies (configurable per field):**

| Strategy | When to Use | Example |
|----------|-------------|---------|
| `prefer_aoai` | Unstructured text (narratives) | Event descriptions |
| `prefer_textract` | Structured fields (dates, IDs) | Form numbers, dates |
| `highest_confidence` | No strong preference | Phone numbers |
| `committee_average` | Numeric values | Dosage amounts |

---

### 3.3 Validation Pipeline

```mermaid
flowchart TD
    Start([Arbitrated JSON]) --> SchemaVal{JSON Schema<br/>Validation}
    
    SchemaVal -->|Pass| Semantic[Semantic Checks]
    SchemaVal -->|Fail| Errors[Collect Errors<br/>- Missing fields<br/>- Type mismatches<br/>- Range violations]
    
    Semantic --> RxNorm{RxNorm Lookup<br/>Product names}
    RxNorm -->|Found| ScoreRx[Confidence boost<br/>+0.05]
    RxNorm -->|Not found| WarnRx[Warning<br/>Unknown product]
    
    ScoreRx --> MedDRA{MedDRA Lookup<br/>Event terms}
    WarnRx --> MedDRA
    
    MedDRA -->|Found| ScoreMed[Confidence boost<br/>+0.05]
    MedDRA -->|Not found| WarnMed[Warning<br/>Non-standard term]
    
    ScoreMed --> CrossField[Cross-Field Rules<br/>- AE present → Severity required<br/>- Date logic<br/>- Unit consistency]
    WarnMed --> CrossField
    
    CrossField -->|Pass| AdjustConf[Adjust Overall<br/>Confidence]
    CrossField -->|Fail| AddErrors[Add to error list]
    
    Errors --> Decision{Can auto-fix?}
    AddErrors --> Decision
    
    Decision -->|Yes| AutoFix[Apply Fixes<br/>- Date formatting<br/>- Unit conversion<br/>- Capitalization]
    Decision -->|No| FlagErrors[Flag for HITL]
    
    AutoFix --> AdjustConf
    AdjustConf --> FinalCheck{Overall<br/>confidence ≥ 0.85<br/>AND no critical errors?}
    
    FinalCheck -->|Yes| Approve([Approve for Publishing])
    FinalCheck -->|No| HITL([Route to HITL])
    FlagErrors --> HITL
    
    style SchemaVal fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Approve fill:#50E3C2,stroke:#2EAA8C,color:#000
    style HITL fill:#F5A623,stroke:#D68910,color:#fff
```

**Validation Rules Hierarchy:**

```
1. Schema Validation (blocking)
   └─ Required fields present
   └─ Data types correct (string, number, date)
   └─ Range constraints (e.g., age 0-120)

2. Semantic Validation (warning)
   └─ RxNorm product lookup
   └─ MedDRA event term lookup
   └─ Unit standardization (mg vs g)

3. Business Logic (blocking or warning)
   └─ Cross-field consistency
   └─ Temporal logic (report_date ≥ event_date)
   └─ Conditional requirements (if X then Y must exist)
```

**Auto-Fix Capabilities:**

| Issue | Auto-Fix Strategy | Example |
|-------|------------------|---------|
| Date format inconsistency | Parse + reformat to ISO 8601 | "10/15/2024" → "2024-10-15" |
| Unit mismatch | Convert to standard unit | "1000mg" → "1g" |
| Case sensitivity | Title case for names | "john doe" → "John Doe" |
| Whitespace | Trim and normalize | "  ABC  " → "ABC" |

---

### 3.4 HITL Feedback Loop

```mermaid
flowchart TD
    Start([Low Confidence<br/>OR Validation Error]) --> Queue[Add to HITL Queue<br/>Azure Service Bus]
    
    Queue --> Prioritize{Prioritization<br/>Strategy}
    Prioritize -->|High priority| Premium[Premium Client<br/>SLA: 4 hours]
    Prioritize -->|Standard| Standard[Standard Queue<br/>SLA: 24 hours]
    
    Premium --> Review[Human Reviewer<br/>Correction UI]
    Standard --> Review
    
    Review --> Correct[Reviewer Makes<br/>Corrections]
    Correct --> Verify{QA Verification<br/>by senior reviewer?}
    
    Verify -->|Complex case| QA[QA Review]
    Verify -->|Simple case| Accept[Accept Corrections]
    
    QA --> Accept
    Accept --> Store[Store Corrected Data<br/>Databricks Delta]
    
    Store --> Publish[Publish to EMDM<br/>as validated JSON]
    Store --> Training[Add to Training Set<br/>with gold labels]
    
    Training --> Batch{Enough samples<br/>for retraining?<br/>N ≥ 100}
    
    Batch -->|Yes| FineTune[Trigger Fine-Tuning<br/>AOAI job]
    Batch -->|No| Accumulate[Accumulate]
    
    FineTune --> Deploy[Deploy New Model<br/>Version]
    Deploy --> UpdatePolicy[Update Policy<br/>with new model_id]
    
    UpdatePolicy --> Monitor[Monitor Accuracy<br/>on holdout set]
    Monitor --> Feedback{Accuracy<br/>improved?}
    
    Feedback -->|Yes ≥2pp| Promote[Promote to Production]
    Feedback -->|No| Rollback[Rollback to Previous]
    
    Promote --> End([Continuous Improvement])
    Rollback --> End
    Accumulate --> End
    Publish --> End
    
    style Review fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style FineTune fill:#9013FE,stroke:#6A0DAD,color:#fff
    style Promote fill:#50E3C2,stroke:#2EAA8C,color:#000
```

**HITL Queue Prioritization:**

| Priority | Criteria | SLA | Routing |
|----------|----------|-----|---------|
| **Critical** | Premium client + SAE form | 2 hours | Senior reviewer |
| **High** | Premium client OR complex extraction | 4 hours | Any reviewer |
| **Standard** | Standard client + simple form | 24 hours | Any reviewer |
| **Low** | Training data collection only | 7 days | Batch processing |

**Fine-Tuning Trigger Logic:**

```python
if training_set_size >= 100:
    if last_training_date is None or days_since_training >= 30:
        if new_samples_since_training >= 100:
            trigger_fine_tuning()
```

---

### 3.5 Clustering (ADAH) Batch Process

```mermaid
flowchart TD
    Start([Scheduled Trigger<br/>Weekly OR 1000 new layouts]) --> Fetch[Fetch Unassigned<br/>Layouts from Registry]
    
    Fetch --> Batch[Batch Embeddings<br/>N=1000]
    
    Batch --> Similarity[Calculate Pairwise<br/>Cosine Similarity]
    
    Similarity --> HDBSCAN[Run HDBSCAN<br/>min_cluster_size=5<br/>min_samples=3]
    
    HDBSCAN --> Validate{For each cluster}
    
    Validate --> Silhouette[Calculate<br/>Silhouette Score]
    
    Silhouette --> ScoreCheck{Score ≥ 0.4?}
    
    ScoreCheck -->|Yes| AcceptCluster[Accept Cluster]
    ScoreCheck -->|No| Noise[Mark as Noise<br/>Keep as individual]
    
    AcceptCluster --> Centroid[Calculate<br/>Cluster Centroid]
    Centroid --> Merge{Check for<br/>merge candidates<br/>with existing clusters}
    
    Merge -->|Similar cluster exists<br/>similarity > 0.85| MergeLogic{Merge<br/>Compatible?}
    Merge -->|No similar cluster| CreateNew[Create New Cluster]
    
    MergeLogic -->|Policy compatible| DoMerge[Merge Clusters<br/>Recalculate centroid]
    MergeLogic -->|Policy conflict| ManualReview[Queue for<br/>Manual Review]
    
    DoMerge --> UpdateRegistry[Update Registry<br/>- Assign cluster_ids<br/>- Update centroid]
    CreateNew --> UpdateRegistry
    Noise --> UpdateRegistry
    
    UpdateRegistry --> Metrics[Record Metrics<br/>- Cluster count<br/>- Merge ratio<br/>- Silhouette avg]
    
    Metrics --> More{More batches?}
    More -->|Yes| Fetch
    More -->|No| Report[Generate Report<br/>Email to team]
    
    ManualReview --> Report
    Report --> End([End])
    
    style HDBSCAN fill:#9013FE,stroke:#6A0DAD,color:#fff
    style DoMerge fill:#50E3C2,stroke:#2EAA8C,color:#000
    style ManualReview fill:#F5A623,stroke:#D68910,color:#fff
```

**Clustering Parameters (Tuned):**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `min_cluster_size` | 5 | At least 5 similar layouts to form cluster |
| `min_samples` | 3 | Core points need 3 neighbors |
| `metric` | cosine | Best for high-dimensional embeddings |
| `merge_threshold` | 0.85 | High similarity to prevent over-merging |

**Merge Compatibility Check:**

```python
def are_clusters_mergeable(c1: Cluster, c2: Cluster) -> bool:
    # 1. Centroid similarity
    if cosine_similarity(c1.centroid, c2.centroid) < 0.85:
        return False
    
    # 2. Policy compatibility
    if c1.policy_type != c2.policy_type:
        return False
    
    # 3. Silhouette score won't degrade
    merged_silhouette = calculate_silhouette(c1.members + c2.members)
    if merged_silhouette < min(c1.silhouette, c2.silhouette):
        return False
    
    # 4. Size constraint (prevent megaclusters)
    if len(c1.members) + len(c2.members) > 500:
        return False
    
    return True
```

---

## 4. Data State Transitions

### 4.1 Document Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> Ingested: Blob created event
    
    Ingested --> Extracting: Textract triggered
    Extracting --> Extracted: OCR complete
    
    Extracted --> Routing: Fingerprint generated
    Routing --> Extracting_Multi: Policy loaded
    
    Extracting_Multi --> Arbitrating: Candidates ready
    Arbitrating --> Validating: Winner selected
    
    Validating --> Publishing: Valid + high confidence
    Validating --> HITL: Invalid OR low confidence
    
    HITL --> Correcting: Assigned to reviewer
    Correcting --> QA: Corrections submitted
    QA --> Validated: QA approved
    Validated --> Publishing: Corrections stored
    Validated --> Training: Added to dataset
    
    Publishing --> Published: JSON written to EMDM
    Published --> [*]
    
    Extracting --> Failed: Textract error
    Extracting_Multi --> Failed: All extractors failed
    Failed --> Dead_Letter: Max retries exceeded
    Dead_Letter --> [*]
    
    note right of HITL
        Low confidence threshold: 0.85
        Validation errors trigger HITL
    end note
    
    note right of Training
        Batch accumulation
        Triggers fine-tuning at N≥100
    end note
```

**State Persistence:**

```sql
CREATE TABLE document_processing_state (
    document_id UUID PRIMARY KEY,
    current_state VARCHAR(50) NOT NULL,
    state_history JSONB[],
    last_updated TIMESTAMP DEFAULT NOW(),
    error_count INT DEFAULT 0,
    metadata JSONB,
    
    CONSTRAINT valid_state CHECK (
        current_state IN (
            'ingested', 'extracting', 'extracted', 'routing',
            'extracting_multi', 'arbitrating', 'validating',
            'publishing', 'hitl', 'correcting', 'qa',
            'validated', 'training', 'published', 'failed'
        )
    )
);
```

---

## 5. Error Handling Flows

### 5.1 Registry Failure Fallback

```mermaid
flowchart TD
    Start([KNN Search Request]) --> Try[Execute Query<br/>Postgres]
    
    Try -->|Success| Return[Return Matches]
    Try -->|Timeout| Retry{Retry Count<br/>< 3?}
    Try -->|Connection Error| Retry
    
    Retry -->|Yes| Backoff[Exponential Backoff<br/>1s → 2s → 4s]
    Retry -->|No| Circuit{Circuit Breaker<br/>Open?}
    
    Backoff --> Try
    
    Circuit -->|No| OpenCircuit[Open Circuit<br/>Fail fast for 60s]
    Circuit -->|Yes| Fallback[Use Fallback<br/>Generic Policy]
    
    OpenCircuit --> Fallback
    Fallback --> Cache{Policy in<br/>Local Cache?}
    
    Cache -->|Yes| UseCached[Use Cached Generic]
    Cache -->|No| Hardcoded[Use Hardcoded<br/>Fallback Policy]
    
    UseCached --> Alert[Alert: Registry Degraded]
    Hardcoded --> Alert
    
    Alert --> Proceed([Continue Processing<br/>with degraded accuracy])
    Return --> Proceed
    
    style Fallback fill:#F5A623,stroke:#D68910,color:#fff
    style Alert fill:#D0021B,stroke:#8B0000,color:#fff
```

**Circuit Breaker Configuration:**

```yaml
circuit_breaker:
  failure_threshold: 5  # Open after 5 consecutive failures
  success_threshold: 2  # Close after 2 consecutive successes
  timeout: 60s          # Stay open for 60 seconds
  half_open_max_calls: 3  # Test with 3 requests in half-open state
```

---

### 5.2 Arbitration Conflict Resolution

```mermaid
flowchart TD
    Start([Arbitration Start]) --> Check{All extractors<br/>available?}
    
    Check -->|Yes| Normal[Normal Arbitration]
    Check -->|No| Partial{At least one<br/>extractor succeeded?}
    
    Partial -->|Yes| Degrade[Degraded Mode<br/>Use available results]
    Partial -->|No| Fail[All Extractors Failed]
    
    Normal --> Scores[Calculate Scores]
    Degrade --> Scores
    
    Scores --> Winner{Clear winner?<br/>margin > 0.15}
    
    Winner -->|Yes| Select[Select Winner]
    Winner -->|No| Tie{Tie-break<br/>strategy defined?}
    
    Tie -->|Yes| Break[Apply Tie-Breaker]
    Tie -->|No| NoStrategy[No Strategy Defined]
    
    Break --> Select
    NoStrategy --> DefaultFallback[Use Highest<br/>Confidence]
    DefaultFallback --> Select
    
    Select --> Confidence{Overall<br/>confidence ≥ 0.85?}
    
    Confidence -->|Yes| Proceed([Proceed to Validation])
    Confidence -->|No| FlagLow[Flag for HITL]
    FlagLow --> Proceed
    
    Fail --> Poison[Route to<br/>Poison Queue]
    Poison --> ManualIntervention([Manual Investigation])
    
    style Degrade fill:#F5A623,stroke:#D68910,color:#fff
    style Poison fill:#D0021B,stroke:#8B0000,color:#fff
```

---

## 6. Monitoring & Observability

### 6.1 OpenTelemetry Trace Hierarchy

```mermaid
graph TD
    Root[Root Span: document.process<br/>trace_id: abc-123]
    
    Root --> Ingest[ingest.receive<br/>+2ms]
    Root --> Extract[textract.extract<br/>+50ms → +1850ms]
    Root --> Finger[fingerprint.generate<br/>+1855ms → +1900ms]
    Root --> Registry[registry.lookup<br/>+1905ms → +1967ms]
    Root --> Multi[extractors.parallel<br/>+1970ms → +3770ms]
    Root --> Arb[arbitration.execute<br/>+3775ms → +4075ms]
    Root --> Val[validation.run<br/>+4080ms → +4580ms]
    Root --> Pub[publish.write<br/>+4585ms → +4785ms]
    
    Registry --> KNN[pgvector.knn_search<br/>+62ms]
    Registry --> Load[policy.load<br/>+18ms]
    
    Multi --> T[extractor.textract<br/>+1200ms]
    Multi --> A[extractor.aoai<br/>+1800ms]
    
    Arb --> S1[scoring.field_1<br/>+15ms]
    Arb --> S2[scoring.field_2<br/>+18ms]
    Arb --> SN[scoring.field_N<br/>+12ms]
    
    Val --> Schema[schema.validate<br/>+25ms]
    Val --> RxNorm[rxnorm.lookup<br/>+120ms]
    Val --> MedDRA[meddra.lookup<br/>+105ms]
    Val --> CrossField[crossfield.check<br/>+35ms]
    
    style Root fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Multi fill:#9013FE,stroke:#6A0DAD,color:#fff
```

**Trace Attributes (Key):**

```json
{
  "trace.id": "abc-123-xyz",
  "span.name": "document.process",
  "document.id": "550e8400-...",
  "document.type": "AE_FORM",
  "document.page_count": 4,
  "layout.matched": true,
  "layout.similarity": 0.92,
  "policy.id": "7c9e6679-...",
  "arbitration.winner_source": "aoai",
  "arbitration.confidence": 0.91,
  "validation.passed": true,
  "hitl.required": false,
  "duration_ms": 4785
}
```

---

### 6.2 Metrics Dashboard Structure

```mermaid
graph TB
    subgraph "Real-Time Metrics"
        Throughput[docs_processed_total<br/>counter]
        Latency[processing_duration_seconds<br/>histogram]
        Errors[errors_total{type}<br/>counter]
    end
    
    subgraph "Accuracy Metrics"
        FieldAcc[field_accuracy{field_name}<br/>gauge 0-1]
        LayoutMatch[layout_match_rate{doc_type}<br/>gauge 0-1]
        HITLRate[hitl_trigger_rate<br/>gauge 0-1]
    end
    
    subgraph "Component Health"
        RegLatency[registry_knn_latency_ms<br/>histogram]
        ArbAgreement[arbitration_agreement_rate<br/>gauge 0-1]
        ValFailure[validation_failure_rate{reason}<br/>gauge 0-1]
    end
    
    subgraph "ML Model Performance"
        ModelAcc[model_accuracy{model_version}<br/>gauge 0-1]
        FineTuneRuns[finetuning_runs_total<br/>counter]
        Drift[layout_drift_score<br/>gauge]
    end
    
    Throughput --> Alert1{> 1000/hour?}
    Latency --> Alert2{P95 > 60s?}
    FieldAcc --> Alert3{< 0.85?}
    RegLatency --> Alert4{P95 > 100ms?}
    
    Alert1 -->|No| Incident[Create Incident]
    Alert2 -->|Yes| Incident
    Alert3 -->|Yes| Incident
    Alert4 -->|Yes| Incident
    
    style Alert2 fill:#D0021B,stroke:#8B0000,color:#fff
    style Alert3 fill:#D0021B,stroke:#8B0000,color:#fff
```

---

## 7. Capacity Planning

### 7.1 Load Distribution by Time

```mermaid
gantt
    title Daily Processing Load Profile
    dateFormat HH:mm
    axisFormat %H:%M
    
    section Ingestion
    Batch Upload 1    :08:00, 2h
    Streaming (low)   :10:00, 4h
    Batch Upload 2    :14:00, 2h
    Streaming (low)   :16:00, 2h
    
    section Processing
    Heavy Load        :08:30, 3h
    Moderate Load     :11:30, 3h
    Heavy Load        :14:30, 3h
    Light Load        :17:30, 2h
    
    section HITL Review
    Shift 1 (peak)    :09:00, 4h
    Shift 2 (light)   :13:00, 4h
    Overflow          :17:00, 2h
    
    section Clustering
    Weekly Batch      :01:00, 3h
```

**Resource Allocation:**

| Time Window | Documents/hour | Required VMs | Auto-scale Trigger |
|-------------|----------------|--------------|-------------------|
| 08:00-11:00 | 2000 | 8 | >70% CPU sustained 5min |
| 11:00-14:00 | 800 | 4 | >70% CPU sustained 5min |
| 14:00-17:00 | 1500 | 6 | >70% CPU sustained 5min |
| 17:00-08:00 | 200 | 2 | >80% CPU sustained 10min |

---

## 8. Disaster Recovery

### 8.1 Failover Sequence

```mermaid
sequenceDiagram
    participant Primary as Primary Region<br/>(East US)
    participant Monitor as Health Monitor
    participant DNS as Azure Traffic Manager
    participant Secondary as Secondary Region<br/>(West US)
    participant Ops as Operations Team
    
    Primary->>Monitor: Health check (every 30s)
    Monitor->>Monitor: Check: /health endpoint
    
    Note over Primary,Monitor: Normal Operation
    
    Primary-xMonitor: Timeout (3 consecutive failures)
    Monitor->>Monitor: Mark Primary unhealthy
    Monitor->>Ops: Alert: Primary degraded
    Monitor->>DNS: Update routing
    
    DNS->>Secondary: Route traffic
    Secondary->>Secondary: Activate standby instances
    Secondary->>Secondary: Sync data from backup
    
    Note over Secondary: Processing continues
    
    Ops->>Primary: Investigate root cause
    Ops->>Primary: Apply fix
    Primary->>Monitor: Health restored
    
    Monitor->>Ops: Alert: Primary healthy
    Ops->>DNS: Manual failback decision
    DNS->>Primary: Route traffic back
    
    Note over Primary,Secondary: Primary restored
```

**RTO/RPO Targets:**

| Scenario | RTO | RPO | Cost |
|----------|-----|-----|------|
| **Registry failure** | 5 minutes | 0 (sync replica) | $$$ |
| **Region outage** | 1 hour | 15 minutes | $$$$ |
| **Data corruption** | 4 hours | 1 hour (point-in-time restore) | $$ |

---

## 9. Integration Patterns

### 9.1 Event-Driven Architecture

```mermaid
graph LR
    subgraph "Event Sources"
        Blob[Blob Storage<br/>EventGrid]
        HITL[HITL Queue<br/>Service Bus]
        Timer[Timer Trigger<br/>Clustering]
    end
    
    subgraph "Event Hub"
        Hub[Azure Event Grid<br/>Topic: ae-processing]
    end
    
    subgraph "Subscribers"
        Ingest[Ingestion<br/>Function]
        Metrics[Metrics<br/>Collector]
        Audit[Audit Log<br/>Writer]
        Notif[Notification<br/>Service]
    end
    
    Blob -->|blob.created| Hub
    HITL -->|hitl.completed| Hub
    Timer -->|clustering.triggered| Hub
    
    Hub -->|filter: blob.created| Ingest
    Hub -->|all events| Metrics
    Hub -->|filter: critical| Audit
    Hub -->|filter: hitl.*| Notif
    
    style Hub fill:#4A90E2,stroke:#2E5C8A,color:#fff
```

---

## Appendix: Diagram Legend

### Node Shapes

- **Rounded Rectangle**: Process/Operation
- **Diamond**: Decision Point
- **Parallelogram**: Input/Output
- **Circle**: Start/End
- **Hexagon**: Database/Storage

### Color Coding

- **Blue (#4A90E2)**: Core component/primary path
- **Green (#50E3C2)**: Success state
- **Orange (#F5A623)**: Warning/fallback
- **Red (#D0021B)**: Error/failure
- **Purple (#9013FE)**: ML/AI operations

---

## Document Maintenance

**Review Cadence:** Monthly or after significant architecture changes

**Owners:**
- Sajid (Technical Lead): Overall architecture, flows
- Helios (Data/ML): Clustering, fine-tuning flows
- DevOps: Monitoring, failover diagrams

**Feedback:** Submit pull requests or email sajid@thermofisher.com

---

**Next Steps:**
1. Review flows with team in architecture walkthrough
2. Use as reference for component deep-dive designs
3. Keep diagrams synchronized with implementation changes

**Last Updated:** November 4, 2024
