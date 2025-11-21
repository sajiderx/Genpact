Following up on our discussions about the Layout Registry, I’d like to get your help setting up the infrastructure pieces on the TFS side so we can start wiring the registry into the pipeline.

I’ve attached the Registry ERD / layout showing the four tables:

layouts

layout_embeddings

layout_policies

registry_audit_log

and how they relate.

What I’m asking your team to create

1) Azure Tables

Using the attached ERD and schemas, can your team please create:

layouts

layout_embeddings

layout_policies

registry_audit_log

with the specified PartitionKey / RowKey patterns and columns?
(I’ve included a small schema summary in the attachment for each table.)

2) Blob Storage

Two logical areas/containers:

embeddings – for layout embedding vectors (JSON/npz/parquet)

diagnostics – for optional diagnostics artifacts tied to layouts/policies

We will handle how the files are structured within those containers.

How we’ll use it on our side

Our Python service will:

Generate fingerprints and embeddings.

Store the vectors in Blob.

Read/write registry metadata in the Azure Tables.

Perform KNN similarity search in memory (no vector DB required).

Write to registry_audit_log for every decision.

So from your side, it’s mainly provisioning the tables + containers. We’ll own the KNN logic, lookup behavior, and audit writing.

Once these are available, we can start integrating the registry into the rest of the flow and validate end-to-end behavior.
