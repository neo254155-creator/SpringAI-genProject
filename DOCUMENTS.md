# Document notes and semantic search

Upload UTF-8 `.txt` or `.md` notes and retrieve their saved passages. Files must
contain nonblank text and be at most 65,536 bytes (64 KiB). PDF and Word extraction
are not implemented. Open `/documents.html` for the optional Study Notes workspace;
the main interview journey remains resume-based.

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
| `POST /documents/{id}/answer` | Generate an answer from retrieved passages and return checked source references |

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
| `document-search.embedding.base-url` | `DOCUMENT_EMBEDDINGS_URL` | `https://generativelanguage.googleapis.com/v1beta/openai` |
| `document-search.embedding.model` | `DOCUMENT_EMBEDDINGS_MODEL` | `gemini-embedding-001` |
| `document-search.embedding.api-key` | `DOCUMENT_EMBEDDINGS_API_KEY` | Empty; supply your embedding provider's key |

The hosted adapter supports the OpenAI-compatible `POST /embeddings` contract
with bearer authentication, `input`, `model`, `encoding_format: float`, and
response `data` entries containing `index` and `embedding`. A provider using a
different format requires a different adapter. Response indexes are validated
and used to match vectors back to the correct passages.

Google AI Studio (Gemini) is the selected embedding provider. Use its
OpenAI-compatible base URL without `/embeddings`; the adapter appends that path.
The key comes from Google AI Studio; the URL is fixed in Google's documentation:
https://ai.google.dev/gemini-api/docs/openai#embeddings.

Set the variables in the terminal or hosting environment that launches the app,
then restart it. Keep keys out of committed files. Properties reference your
environment; they do not create the environment variables. Disabled embeddings
need no credentials. Enabling with missing settings fails startup with a
configuration error. Groq still uses its separate `GROQ_API_KEY` and `GROQ_MODEL`.

## Google AI Studio setup

Set these variables in the IntelliJ Run Configuration that launches the app:

```text
DOCUMENT_EMBEDDINGS_ENABLED=true
DOCUMENT_EMBEDDINGS_PROVIDER=openai-compatible
DOCUMENT_EMBEDDINGS_URL=https://generativelanguage.googleapis.com/v1beta/openai
DOCUMENT_EMBEDDINGS_MODEL=gemini-embedding-001
DOCUMENT_EMBEDDINGS_API_KEY=<your Gemini API key>
```

Restart the application after setting them. Keep the real key out of source control.
Embeddings stay disabled by default until enabled. A direct Gemini request was
verified; application indexing/search and retrieval quality still need live checks.

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
malformed outputs fail rather than being stored. The hosted adapter uses a
5-second connection timeout and 60-second read timeout. Provider token limits
still apply.

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

## Answers with source citations

After upload and indexing, ask a question using the same query, limit, and
minimumScore fields as search:

```sh
curl -X POST http://localhost:8080/documents/DOCUMENT_ID/answer \
  -H 'Content-Type: application/json' \
  -d '{"query":"How are Spring beans managed?","limit":5,"minimumScore":0.3}'
```

The application retrieves passages with Gemini embeddings, then asks the existing
Groq chat model to answer using only those passages. No new API key is required.
The response contains `documentId`, `title`, `query`, `answer`,
`insufficientEvidence`, and `sources`. Each source has the actual saved
`passageId`, `number`, `text`, and `similarity`; source text is never taken from
model output. Only the sources cited by the model are returned.

Citation IDs must belong to the retrieved passages, be unique, and be nonempty
for an answer. Invalid model output returns the existing safe 502 error. If no
passages meet the search threshold, chat is skipped. If the model reports that
evidence is insufficient, the application returns a standard explanation and an
empty source list, discarding generated prose. Requests read existing data and
do not save generated answers, create indexes, or run migrations.

Checking citation IDs proves source identity, not that every generated claim is
entailed by its cited text. Prompt-injection resistance, real retrieval quality,
answer quality, and citation support still require live evaluation. The default
similarity cutoff is a retrieval filter, not a confidence estimate. The document
workspace is available at `/documents.html`. Incorporating notes into interview
feedback remains a future task.

This endpoint preserves the existing search/index errors (400, 404, 409, 502,
503). Validate API behavior with the focused DocumentAnswerServiceTest and
DocumentAnswerControllerTest tests; these use retrieval/model stubs, not live
providers. pgvector remains an optional upgrade for larger collections.

Adapter contract: https://ai.google.dev/gemini-api/docs/openai#embeddings.

## Study Notes screen

Save a document, choose it, click Index notes, then submit a question. Uploading,
browsing, selecting, and refreshing notes do not call either AI provider.
Indexing and answers only run on explicit clicks, with no automatic retries.
Successful indexing is reused during the visit; up to 20 successful answers are
cached by document/question in memory. Refreshing the page clears this cache.
Previously indexed documents can be queried directly without indexing again.

The screen shows insufficient-evidence responses and expandable saved source
excerpts as text, handles pagination/errors/cancellation, and preserves input on
failed answers. Cancelling stops waiting; the server can still consume quota.
Generated answers are not saved after a page refresh. Source identity checks do
not establish that all generated claims are supported.
