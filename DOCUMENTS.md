# Document notes and semantic search

Upload UTF-8 `.txt` or `.md` notes and retrieve their saved passages. Files must
contain nonblank text and be at most 65,536 bytes (64 KiB). PDF and Word extraction
are not implemented. This is a backend API; the current frontend has no upload form.

```sh
curl -X POST http://localhost:8080/documents \
  -F 'title=Spring Boot notes' \
  -F 'file=@spring-notes.md'
```

The response is HTTP 201 with `id`, `title`, `filename`, `createdAt`, and a
`Location` header pointing to the document. Use its ID in these routes:

| Route | Result |
| --- | --- |
| `GET /documents?page=0&size=20` | Newest documents first, with pagination metadata; size 1–100 |
| `GET /documents/{id}` | Document metadata |
| `GET /documents/{id}/passages` | Ordered passages with stable `id`, `number`, and `text` |
| `POST /documents/{id}/index` | Generate and persist passage embeddings; reindexing replaces the saved index |
| `POST /documents/{id}/search` | Find similar passages within this document |

Passages preserve the uploaded text in consecutive sections of at most 1,200
Java UTF-16 code units, without splitting a Unicode surrogate pair. An initial
UTF-8 byte-order mark is removed. Joining the passage text reconstructs the
decoded notes. This initial splitter has no overlap and does not perform
token-based or semantic splitting. Uploaded filenames are display metadata;
files are never written to a path supplied by the uploader.

`V2__create_study_documents.sql` adds `study_documents` and `study_passages` using
Flyway on startup. The upload transaction saves metadata and passages together.
Missing documents return 404; invalid files, titles, or page parameters return
400. Uploads above the server's multipart request limit can return 413 before
reaching application validation.

Upload and read endpoints require database connectivity but make no AI calls. The app's
existing Groq configuration is still required for normal application startup.
Storage follows the existing single-learner model: documents are shared across
the application and have no per-user ownership or authentication.

## Configure hosted embeddings

`application.properties` now defines these settings. They are custom properties
read by `DocumentEmbeddingConfig`; they are separate from `spring.ai.openai.*`,
which this project uses for Groq chat.

| Property | Environment variable | Default |
| --- | --- | --- |
| `document-search.embedding.enabled` | `DOCUMENT_EMBEDDINGS_ENABLED` | `false` |
| `document-search.embedding.provider` | `DOCUMENT_EMBEDDINGS_PROVIDER` | `openai-compatible` |
| `document-search.embedding.base-url` | `DOCUMENT_EMBEDDINGS_URL` | Empty; supply your provider's HTTPS API base URL |
| `document-search.embedding.model` | `DOCUMENT_EMBEDDINGS_MODEL` | Empty; supply your provider's embedding model ID |
| `document-search.embedding.api-key` | `DOCUMENT_EMBEDDINGS_API_KEY` | Empty; supply your embedding provider's key |

The hosted adapter supports the OpenAI-compatible `POST /embeddings` contract
with bearer authentication, `input`, `model`, `encoding_format: float`, and
response `data` entries containing `index` and `embedding`. A provider using a
different format requires a different adapter. Response indexes are validated
and used to match vectors back to the correct passages.

For example, OpenAI's base URL is `https://api.openai.com/v1`; its embedding model
IDs include `text-embedding-3-small`. This is an example, not a configured or
tested provider selection. See the [official embeddings API contract](https://developers.openai.com/api/reference/resources/embeddings/methods/create).
Supply the base URL without `/embeddings`; the adapter appends that path.

Set the variables in the terminal or hosting environment that launches the app,
then restart it. Keep keys out of committed files. Properties reference your
environment; they do not create the environment variables. Disabled embeddings
need no credentials. Enabling with missing settings fails startup with a
configuration error. Groq still uses its separate `GROQ_API_KEY` and `GROQ_MODEL`.

## Optional local Ollama setup

The optional adapter uses Ollama's `/api/embed` endpoint, separate from the Groq
chat model. It is disabled by default. Install and start Ollama separately, then
download the embedding model and enable the feature in the app's environment:

```sh
ollama pull embeddinggemma
export DOCUMENT_EMBEDDINGS_ENABLED=true
export DOCUMENT_EMBEDDINGS_PROVIDER=ollama
export DOCUMENT_EMBEDDINGS_URL=http://localhost:11434
export DOCUMENT_EMBEDDINGS_MODEL=embeddinggemma
```

Restart the Spring Boot application with these variables. Ollama requires no
embedding API key for this local setup. Hosted providers use the other adapter.
These commands are setup instructions, not a claim that Ollama was installed or
the model was downloaded here. In a deployed container, localhost refers to that
container: use a reachable Ollama service for deployments. Do not point these
settings at Groq's chat endpoint.

After uploading a document, index it explicitly:

```sh
curl -X POST http://localhost:8080/documents/DOCUMENT_ID/index
curl -X POST http://localhost:8080/documents/DOCUMENT_ID/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"How are Spring beans managed?","limit":5,"minimumScore":0.3}'
```

Replace DOCUMENT_ID with the ID returned by upload. Search returns document ID,
title, query, and `matches`; each match has the actual saved `passageId`, `number`,
`text`, and cosine `similarity`. Results are ordered by descending similarity,
then passage number. `limit` defaults to 5 (range 1–20), `minimumScore` defaults
to 0 (range -1–1), and query length is at most 2,000 Java string characters.
An empty matches list means no passages met the requested threshold. Similarity
is a ranking signal, not a probability or proof that the passage answers the question.

V3 adds `document_search_indexes`. It stores normalized vectors as JSON text in
regular PostgreSQL and records model/endpoint identity and dimensions. Java
calculates cosine similarity over one document's bounded set of passages. There
is no pgvector extension, approximate index, or search across the entire library
in this milestone. This is intended for the existing 64 KiB document limit.

Indexing generates vectors before entering the save transaction, and replaces
the snapshot atomically under a document lock. Failed embedding calls preserve
the previous index. Searches also make no provider calls inside transactions.
Embeddings must have consistent dimensions, finite values, and nonzero length;
malformed outputs fail rather than being stored. Ollama requests disable silent
input truncation. Both adapters use a 5-second connection timeout and 60-second
read timeout. Hosted provider token limits still apply.

If the provider URL or model changes, reindex documents. The model key includes
an endpoint hash. If model weights change under the same name, reindex manually;
the app does not detect model-weight changes automatically. Upload alone does
not index a document. No embedding calls happen during startup or upload.

| Status | Meaning |
| --- | --- |
| 400 | Invalid query, limit, or similarity threshold |
| 404 | Document does not exist |
| 409 `DOCUMENT_INDEX_NOT_READY` | Missing index or incompatible model/dimensions; reindex |
| 502 `EMBEDDING_PROVIDER_ERROR` | Embedding request failed or returned invalid vectors |
| 503 `EMBEDDINGS_UNAVAILABLE` | Embeddings are disabled |

Remaining work: generate explanations from retrieved passages with verified
citations, evaluate retrieval quality against real notes, and optionally use
pgvector for larger collections. Search returns source passages but does not yet
generate an AI explanation or incorporate notes into interview feedback.

Adapter contract: [Ollama embedding API](https://github.com/ollama/ollama/blob/main/docs/api.md)
and [embedding examples](https://github.com/ollama/ollama/blob/main/docs/capabilities/embeddings.mdx).
