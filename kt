Slide 1 – Current State

Slide Text Update
Background

* Case reports arrive via email → picked up by an automated listener service → attachments sent via internal API for processing
* Mix of structured PDFs, faxes, and handwritten forms
* Some form types (MedWatch 3500) appear in multiple sponsor-specific layouts
* Databricks prototype extracts and transforms using ML models
* Azure Blob used as repository; lacks normalization and review UI

Core Problem & Opportunity

* Variable formats → need context-aware extraction (Azure Document Intelligence)
* Manual verification → human-in-loop review
* No normalization → add schema and validation layers
* Siloed processing → unify Azure orchestration (ADF, Databricks, DI)
* Limited learning → enable continuous model retraining

Speaker Notes

> The current process is semi-automated. Emails are received through Outlook, and a listener service forwards attachments to an internal API that handles extraction. SharePoint is no longer central—it’s used occasionally for manual uploads. Databricks prototypes the ML-based extraction and saves results in Blob. However, the flow lacks normalization, validation, and a unified review experience, which makes QA and audit difficult.

---

Slide 2 – Future State: Azure-Native Intelligent Pipeline

Slide Text Update
Future-State Vision: Azure-Native Intelligent Pipeline

* Ingestion: Outlook → Graph API → Azure Function → Blob (no SharePoint dependency)
* Extraction: Azure Document Intelligence (custom model) using Layout & Read APIs
* Validation: Schema-based normalization, data quality checks, human-in-loop review for <70 % confidence
* Publishing: JSON output with audit trail; XML transformation handled downstream
* Security: Azure Key Vault + Managed Identity for credential rotation

Modernization Approach

* Enhance & Integrate (Preferred): Retain Databricks ingestion, integrate Document Intelligence + review UI
* Targeted Rewrite (Fallback): If >20 % refactor required, rebuild brittle logic
* MVP Success Target: 85–90 % field-level extraction accuracy by January (Phase 1 validation goal)

Speaker Notes

> The future state replaces manual and SharePoint-dependent ingestion with a fully automated Azure Function triggered by Graph API. Each email attachment is securely written to Blob and processed by Azure Document Intelligence. A custom model handles both structured and unstructured forms, scoring confidence for each extracted field. Data below the confidence threshold is routed to a human-in-loop UI. The validated JSON is schema-checked and passed downstream, where another team manages XML conversion and regulatory submission. Security and key management are centralized in Azure Key Vault.

Slide 3 – Migration Phases & Timeline

Slide Text Update
Phase 1 (Now – Dec): MVP Build & Validation

* Implement Graph API-based ingestion
* Replace Textract with Azure Document Intelligence
* Enable schema validation + confidence routing

Phase 2 (Jan – Feb): Scale & Optimize

* Add multi-region reliability and monitoring
* Refine custom model and review UI

Phase 3 (Mar +): Production & Continuous Learning

* Implement model retraining cadence
* Enable automated drift detection

MVP live by Dec 2025 | Production-ready by Mar 2026

Speaker Notes

> Phase 1 establishes automated email ingestion through Graph API, Azure Functions, and the initial custom Document Intelligence model. Phase 2 focuses on reliability and scale—adding multi-region support and enhanced monitoring. From March onward, the focus shifts to continuous improvement and retraining for long-term resilience.


Slide 4 – Risk & Mitigation Plan

Slide Text Update
Model Accuracy

* Incremental training, fallback extraction

Azure Throttling / Failures

* Multi-region deployment, retries, idempotent logic

Schema or Downstream Drift

* Early alignment with downstream XML team, define interface contracts

File Size / Performance Variance

* Async processing and queue-based scaling for large PDFs

Team Enablement

* FastTrack training (Azure Functions, DI)

Timeline Pressure

* MVP early, validate fast, scale iteratively

Speaker Notes

> Major risks include model accuracy drift, schema misalignment with the downstream XML generator, and performance variance for large multi-page PDFs. We mitigate these through incremental model training, schema contracts, retry logic, and queue-based asynchronous processing. FastTrack support ensures the team is ready, and our MVP-first strategy keeps delivery momentum.


 Slide 5 – Open Questions / Dependencies

Slide Text Update

* End-user configurability for new-form onboarding
* Thresholds for handwriting and low-quality scan handling
* Regulatory submission integration (direct vs indirect)
* Ownership for retraining cadence and drift monitoring
* API polling frequency and error-handling for email ingestion
* JSON ↔ XML schema ownership and integration handoff

Speaker Notes

> We’ll clarify several open points with the client: how configurable onboarding should be, thresholds for handwriting confidence, and whether submissions go direct or through intermediaries. Additionally, we need to finalize who owns the JSON-to-XML schema mapping, and confirm the polling and retry logic for the Graph API listener.



Slide 6 – Next Steps

Slide Text Update

* Finalize scope and environment approvals
* Kick off MVP development after access granted
* Confirm UI, validation, and audit requirements
* Finalize email ingestion API design (listener → Azure Function)
* Schedule FastTrack enablement (Azure DI, Functions)
* Publish operational playbooks / runbooks

Speaker Notes

> Next, we’ll finalize scope and environments, then build the MVP foundation—especially the automated email ingestion service and validation UI. We’ll leverage Microsoft FastTrack to accelerate enablement and ensure transparent operations through runbooks and playbooks.


 Slide 7 – Summary

Slide Text Update

> To summarize, we’re enhancing the existing Databricks-based pipeline with Azure-native components—Graph API ingestion, Document Intelligence extraction, schema validation, and human-in-loop review—to create a smarter, faster, and auditable AE/SAE processing system. MVP by year-end, production-ready by March.

Speaker Notes

> The modernization eliminates manual SharePoint uploads and replaces them with an event-driven Graph API ingestion service. It integrates Azure Document Intelligence for context-aware extraction, validates outputs through schema checks, and escalates uncertain results for human review. Together, these enhancements create a secure, scalable, and compliant pipeline that meets our MVP and production targets.


