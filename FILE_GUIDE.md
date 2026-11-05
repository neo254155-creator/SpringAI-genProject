# File guide

One short explanation per tracked source/configuration/test/documentation file.
Generated build output, local credentials, IDE settings and screenshots are excluded.

Local-only files: `.idea/workspace.xml` holds IntelliJ run settings;
`.local/database.properties` holds ignored database settings; the existing local
`RESUME_INTERVIEWS.md` explains the résumé journey. Keep credential values private.

## Root files and guides

| File | Purpose |
| --- | --- |
| `.dockerignore` | Excludes credentials and unrelated files from container builds. |
| `.gitattributes` | Keeps Maven launcher line endings portable. |
| `.gitignore` | Keeps local secrets, build output and screenshots out of Git. |
| `.mvn/wrapper/maven-wrapper.properties` | Pins the Maven wrapper download. |
| `ADAPTIVE_INTERVIEWS.md` | Explains how feedback guides weakness practice. |
| `DOCUMENTS.md` | Explains optional note upload, embeddings and cited answers. |
| `Dockerfile.vercel` | Builds and runs the Java app as a non-root container. |
| `FILE_GUIDE.md` | One-line guide to project files. |
| `LIVE_VERIFICATION.md` | Explains opt-in live checks and recorded results. |
| `PERSISTENCE.md` | Explains database history, accounts, sessions and retries. |
| `PROJECT_REVIEW.md` | Earlier source review; use the implementation log for latest status. |
| `README.md` | Explains setup, running and API use. |
| `VERCEL.md` | Explains deployment preparation and release checks. |
| `compose.yaml` | Starts a local PostgreSQL development database. |
| `implementation.md` | Brief progress, current scope and next work. |
| `intervieAssistant.md` | Detailed feature catalogue and historical roadmap. |
| `mvnw` | Runs Maven on Linux/macOS. |
| `mvnw.cmd` | Runs Maven on Windows. |
| `pom.xml` | Declares Java dependencies, builds and test profiles. |

## Frontend checks and guides

| File | Purpose |
| --- | --- |
| `frontend/README.md` | Explains frontend modules and browser checks. |
| `frontend/VERIFICATION.md` | Records browser/test evidence and limits. |
| `frontend/auth-client.test.mjs` | Checks auth client behavior without live providers. |
| `frontend/browser-check.mjs` | Checks dashboard/navigation layouts using API fixtures. |
| `frontend/core-flow-check.mjs` | Tests the packaged app in a real browser, including restart. |
| `frontend/dashboard.test.mjs` | Checks dashboard behavior without live providers. |
| `frontend/documents-browser-check.mjs` | Checks note UI, quotas, recovery and mobile layouts. |
| `frontend/documents-client.test.mjs` | Checks documents client behavior without live providers. |
| `frontend/history-client.test.mjs` | Checks history client behavior without live providers. |
| `frontend/interview-client.test.mjs` | Checks interview client behavior without live providers. |
| `frontend/resume-browser-check.mjs` | Checks the main journey and account UI with HTTP fixtures. |
| `frontend/resume-client.test.mjs` | Checks resume client behavior without live providers. |
| `frontend/retry-keys.test.mjs` | Checks retry keys behavior without live providers. |

## Setup and connection checks

| File | Purpose |
| --- | --- |
| `scripts/LocalDatabaseSetup.java` | Migrates and checks the local InterviewData database. |
| `scripts/VerifyLiveConnections.java` | Checks PostgreSQL; calls providers only with --include-ai. |
| `scripts/check-local-database.sql` | Lists local database/schema information. |
| `scripts/setup-local-postgres.ps1` | Prepares local PostgreSQL and ignored connection settings. |
| `scripts/verify-live-connections.py` | Builds the classpath and launches connection checks. |

## Backend

| File | Purpose |
| --- | --- |
| `src/main/java/com/first/genProject/Controller/AIController.java` | Exposes general coding-chat endpoints. |
| `src/main/java/com/first/genProject/Controller/ApiExceptionHandler.java` | Returns safe, consistent API error responses. |
| `src/main/java/com/first/genProject/Controller/DocumentAnswerController.java` | Exposes answers backed by note passages. |
| `src/main/java/com/first/genProject/Controller/DocumentSearchController.java` | Exposes note indexing and semantic search. |
| `src/main/java/com/first/genProject/Controller/FrontendConfigController.java` | Returns the public API address without secrets. |
| `src/main/java/com/first/genProject/Controller/InterviewController.java` | Exposes manual interview options and generated questions. |
| `src/main/java/com/first/genProject/Controller/InterviewFeedbackController.java` | Exposes stateless answer evaluation. |
| `src/main/java/com/first/genProject/Controller/InterviewSessionController.java` | Exposes saved questions, answers, history and progress. |
| `src/main/java/com/first/genProject/Controller/ResumeController.java` | Exposes résumé upload/paste, skills and interview start. |
| `src/main/java/com/first/genProject/Controller/StudyDocumentController.java` | Exposes note uploads, metadata and passages. |
| `src/main/java/com/first/genProject/GenProjectApplication.java` | Starts the Spring Boot application. |
| `src/main/java/com/first/genProject/auth/AccountController.java` | Exposes registration, login, CSRF and current-account APIs. |
| `src/main/java/com/first/genProject/auth/AccountSecurity.java` | Configures authentication, CSRF, logout and login throttling. |
| `src/main/java/com/first/genProject/auth/Accounts.java` | Hashes passwords, loads users and assigns legacy ownership once. |
| `src/main/java/com/first/genProject/auth/CurrentAccount.java` | Gets the authenticated account ID for ownership checks. |
| `src/main/java/com/first/genProject/config/ChatOptionsConfig.java` | Configures the shared chat client and model options. |
| `src/main/java/com/first/genProject/config/DeploymentSessions.java` | Shares production login/CSRF sessions through JDBC. |
| `src/main/java/com/first/genProject/config/DocumentEmbeddingConfig.java` | Enables the configured Gemini adapter or disables embeddings. |
| `src/main/java/com/first/genProject/config/RetryRequestFilter.java` | Stops duplicate keyed requests from repeating saved/AI work. |
| `src/main/java/com/first/genProject/dto/AnswerSubmission.java` | Carries a submitted answer for a saved question. |
| `src/main/java/com/first/genProject/dto/ChatRequest.java` | Carries a chat message. |
| `src/main/java/com/first/genProject/dto/ChatResponse.java` | Carries generated chat text. |
| `src/main/java/com/first/genProject/dto/CriterionScore.java` | Carries one rubric score and reason. |
| `src/main/java/com/first/genProject/dto/DocumentAnswerResponse.java` | Carries a note answer and its cited sources. |
| `src/main/java/com/first/genProject/dto/DocumentIndexResponse.java` | Carries saved embedding-index metadata. |
| `src/main/java/com/first/genProject/dto/DocumentSearchRequest.java` | Carries a search query, limit and similarity threshold. |
| `src/main/java/com/first/genProject/dto/DocumentSearchResponse.java` | Carries ranked passages and similarities. |
| `src/main/java/com/first/genProject/dto/FeedbackEvaluation.java` | Represents feedback parsed from model JSON. |
| `src/main/java/com/first/genProject/dto/InterviewAnswerRequest.java` | Carries and validates a manual answer-evaluation request. |
| `src/main/java/com/first/genProject/dto/InterviewFeedback.java` | Carries checked feedback and the calculated total. |
| `src/main/java/com/first/genProject/dto/InterviewQuestion.java` | Carries a generated manual interview question. |
| `src/main/java/com/first/genProject/dto/InterviewSelection.java` | Validates supported manual topics and difficulties. |
| `src/main/java/com/first/genProject/dto/PageResponse.java` | Carries a page of records and pagination metadata. |
| `src/main/java/com/first/genProject/dto/QuestionFocus.java` | Carries the weakness criterion/reason for practice. |
| `src/main/java/com/first/genProject/dto/RubricScores.java` | Groups correctness, completeness and clarity scores. |
| `src/main/java/com/first/genProject/dto/SessionResponse.java` | Carries saved interview status and context. |
| `src/main/java/com/first/genProject/dto/SessionResumeResponse.java` | Carries saved state for reopening an interview. |
| `src/main/java/com/first/genProject/dto/StoredAnswerResponse.java` | Carries a saved answer, timestamp and feedback. |
| `src/main/java/com/first/genProject/dto/StoredQuestionResponse.java` | Carries a saved question and practice focus. |
| `src/main/java/com/first/genProject/dto/StudyDocumentResponse.java` | Carries saved note metadata. |
| `src/main/java/com/first/genProject/dto/StudyPassageResponse.java` | Carries a numbered source passage. |
| `src/main/java/com/first/genProject/dto/TopicProgress.java` | Carries aggregate scores by topic and difficulty. |
| `src/main/java/com/first/genProject/embedding/EmbeddingVectors.java` | Validates and normalizes embedding vectors. |
| `src/main/java/com/first/genProject/embedding/HostedPassageEmbeddingClient.java` | Calls the configured hosted embeddings API. |
| `src/main/java/com/first/genProject/embedding/PassageEmbeddingClient.java` | Defines passage/query embedding operations. |
| `src/main/java/com/first/genProject/exception/AiProviderException.java` | Represents chat-provider failures. |
| `src/main/java/com/first/genProject/exception/DocumentSearchException.java` | Represents embedding/search failures. |
| `src/main/java/com/first/genProject/exception/InterviewNotFoundException.java` | Represents missing or inaccessible records. |
| `src/main/java/com/first/genProject/exception/InterviewStateException.java` | Represents invalid completed-session operations. |
| `src/main/java/com/first/genProject/exception/InvalidInputException.java` | Represents invalid API input. |
| `src/main/java/com/first/genProject/persistence/DocumentSearchIndexEntity.java` | Maps document embedding indexes to database rows. |
| `src/main/java/com/first/genProject/persistence/DocumentSearchIndexRepository.java` | Queries and saves document embedding indexes. |
| `src/main/java/com/first/genProject/persistence/InterviewAnswerEntity.java` | Maps interview answers to database rows. |
| `src/main/java/com/first/genProject/persistence/InterviewAnswerRepository.java` | Queries and saves interview answers. |
| `src/main/java/com/first/genProject/persistence/InterviewFeedbackEntity.java` | Maps rubric feedback to database rows. |
| `src/main/java/com/first/genProject/persistence/InterviewFeedbackRepository.java` | Queries and saves rubric feedback. |
| `src/main/java/com/first/genProject/persistence/InterviewQuestionEntity.java` | Maps interview questions to database rows. |
| `src/main/java/com/first/genProject/persistence/InterviewQuestionRepository.java` | Queries and saves interview questions. |
| `src/main/java/com/first/genProject/persistence/InterviewSessionEntity.java` | Maps interview sessions to database rows. |
| `src/main/java/com/first/genProject/persistence/InterviewSessionRepository.java` | Queries and saves interview sessions. |
| `src/main/java/com/first/genProject/persistence/StudyDocumentEntity.java` | Maps uploaded notes to database rows. |
| `src/main/java/com/first/genProject/persistence/StudyDocumentRepository.java` | Queries and saves uploaded notes. |
| `src/main/java/com/first/genProject/persistence/StudyPassageEntity.java` | Maps numbered note passages to database rows. |
| `src/main/java/com/first/genProject/persistence/StudyPassageRepository.java` | Queries and saves numbered note passages. |
| `src/main/java/com/first/genProject/resume/ResumeEntity.java` | Maps résumé profiles to database rows. |
| `src/main/java/com/first/genProject/resume/ResumeInterviewContext.java` | Carries the confirmed skill snapshot for an interview. |
| `src/main/java/com/first/genProject/resume/ResumeProfile.java` | Carries a saved résumé profile and extracted skills. |
| `src/main/java/com/first/genProject/resume/ResumeRepository.java` | Queries and saves résumé profiles. |
| `src/main/java/com/first/genProject/resume/ResumeService.java` | Extracts grounded skills and starts confirmed-skill interviews. |
| `src/main/java/com/first/genProject/resume/ResumeSessionRequest.java` | Carries selected skills and difficulty for interview start. |
| `src/main/java/com/first/genProject/resume/ResumeSkill.java` | Carries one extracted skill and its source excerpt. |
| `src/main/java/com/first/genProject/resume/ResumeStore.java` | Saves and retrieves the current account’s résumé profiles. |
| `src/main/java/com/first/genProject/resume/ResumeTextReader.java` | Safely reads bounded DOCX/TXT/MD résumé text. |
| `src/main/java/com/first/genProject/service/AIServiceImpl.java` | Validates chat input, renders prompts and requests a response. |
| `src/main/java/com/first/genProject/service/AdaptiveQuestionPolicy.java` | Chooses a weakness from recent rubric feedback. |
| `src/main/java/com/first/genProject/service/AiService.java` | Defines the general-chat service contract. |
| `src/main/java/com/first/genProject/service/DocumentAnswerService.java` | Generates note answers and checks their source IDs. |
| `src/main/java/com/first/genProject/service/DocumentIndexStore.java` | Atomically saves and loads owned passage vectors. |
| `src/main/java/com/first/genProject/service/DocumentSearchService.java` | Embeds queries and ranks relevant note passages. |
| `src/main/java/com/first/genProject/service/InterviewFeedbackService.java` | Validates model feedback and calculates rubric scores. |
| `src/main/java/com/first/genProject/service/InterviewHistoryStore.java` | Saves and reads owned interview records and progress. |
| `src/main/java/com/first/genProject/service/InterviewService.java` | Generates manual/résumé questions and follow-ups. |
| `src/main/java/com/first/genProject/service/InterviewSessionService.java` | Coordinates saved interview questions, scoring and completion. |
| `src/main/java/com/first/genProject/service/RequestLedger.java` | Stores durable retry claims and response copies. |
| `src/main/java/com/first/genProject/service/StudyDocumentService.java` | Validates notes and saves their numbered passages. |

## Configuration, prompts and migrations

| File | Purpose |
| --- | --- |
| `src/main/resources/Prompt/DocumentAnswerSystem` | Defines grounded note-answer and citation rules. |
| `src/main/resources/Prompt/DocumentAnswerUser` | Supplies the question and retrieved source passages. |
| `src/main/resources/Prompt/InterviewFeedbackSystem` | Defines the scoring rubric and output rules. |
| `src/main/resources/Prompt/InterviewFeedbackUser` | Inserts the question, answer and selection for evaluation. |
| `src/main/resources/Prompt/InterviewFollowUp` | Supplies bounded weakness evidence and recent questions. |
| `src/main/resources/Prompt/InterviewQuestion` | Inserts a manual topic and difficulty. |
| `src/main/resources/Prompt/InterviewSystem` | Defines interview-question generation rules. |
| `src/main/resources/Prompt/ResumeInterviewQuestion` | Inserts confirmed résumé skills for a question. |
| `src/main/resources/Prompt/ResumeSkillsSystem` | Defines grounded skill-extraction rules. |
| `src/main/resources/Prompt/ResumeSkillsUser` | Supplies résumé text as untrusted source data. |
| `src/main/resources/Prompt/SystemMessage` | Defines general coding-chat instructions. |
| `src/main/resources/Prompt/UserMessage` | Inserts the user’s coding-chat input. |
| `src/main/resources/application-production.properties` | Requires hosted DB settings and enables Secure production cookies. |
| `src/main/resources/application.properties` | Binds runtime settings to environment variables. |
| `src/main/resources/db/migration/V1__create_interview_history.sql` | Creates interview history and rubric tables. |
| `src/main/resources/db/migration/V2__create_study_documents.sql` | Creates note metadata and source passages. |
| `src/main/resources/db/migration/V3__create_document_search_indexes.sql` | Creates saved embedding indexes. |
| `src/main/resources/db/migration/V4__add_question_practice_focus.sql` | Adds weakness-focus fields to questions. |
| `src/main/resources/db/migration/V5__resume_interview_flow.sql` | Adds résumé profiles and interview skill snapshots. |
| `src/main/resources/db/migration/V6__request_replay_protection.sql` | Adds durable request replay protection. |
| `src/main/resources/db/migration/V7__accounts_and_ownership.sql` | Adds accounts, ownership and first-account bootstrap state. |
| `src/main/resources/db/migration/V8__deployment_sessions.sql` | Adds shared production session tables. |

## Frontend source

| File | Purpose |
| --- | --- |
| `src/main/resources/static/analytics.html` | Hosts score/activity analytics. |
| `src/main/resources/static/coach.js` | Renders question, answer, feedback and practice controls. |
| `src/main/resources/static/dashboard.html` | Hosts the saved-progress overview. |
| `src/main/resources/static/documents.html` | Hosts the optional Study Notes workspace. |
| `src/main/resources/static/history-client.js` | Loads and validates saved history and progress. |
| `src/main/resources/static/history.html` | Hosts saved sessions, answers and scores. |
| `src/main/resources/static/history.js` | Renders saved sessions, answers and rubric feedback. |
| `src/main/resources/static/index.html` | Serves the public landing page. |
| `src/main/resources/static/interview-client.js` | Calls interview APIs with validation and retry protection. |
| `src/main/resources/static/login.html` | Serves registration and sign-in. |
| `src/main/resources/static/practice.html` | Hosts the résumé interview journey. |
| `src/main/resources/static/privacy.html` | Explains storage, accounts and provider requests. |
| `src/main/resources/static/questions.html` | Hosts the question-bank placeholder/history link. |
| `src/main/resources/static/recommendations.html` | Hosts score-based practice suggestions. |
| `src/main/resources/static/resume-client.js` | Calls résumé extraction and confirmed-session APIs. |
| `src/main/resources/static/resume-setup.js` | Renders upload, skill confirmation and difficulty selection. |
| `src/main/resources/static/settings.html` | Hosts motion settings and account status. |
| `src/main/resources/static/ui/analytics.js` | Calculates chart data, averages and topic priorities. |
| `src/main/resources/static/ui/api-config.js` | Loads the backend’s public API configuration. |
| `src/main/resources/static/ui/app.js` | Bootstraps signed-in workspace pages. |
| `src/main/resources/static/ui/auth-client.js` | Adds session cookies, CSRF and safe login redirects. |
| `src/main/resources/static/ui/auth.css` | Styles the account form. |
| `src/main/resources/static/ui/auth.js` | Handles the registration/sign-in form. |
| `src/main/resources/static/ui/components.js` | Provides shared navigation, icons and safe UI helpers. |
| `src/main/resources/static/ui/dashboard-client.js` | Loads saved data for dashboard and analytics. |
| `src/main/resources/static/ui/dashboard.js` | Renders overview, analytics and recommendations. |
| `src/main/resources/static/ui/design.css` | Styles the landing page and workspace shell. |
| `src/main/resources/static/ui/documents-client.js` | Calls note APIs and caches successful per-visit results. |
| `src/main/resources/static/ui/documents.css` | Styles optional Study Notes. |
| `src/main/resources/static/ui/documents.js` | Renders optional note upload, indexing and answers. |
| `src/main/resources/static/ui/favicon.svg` | Provides the browser-tab icon. |
| `src/main/resources/static/ui/landing.js` | Renders public landing-page interactions. |
| `src/main/resources/static/ui/motion.js` | Applies animation and reduced-motion preferences. |
| `src/main/resources/static/ui/resume.css` | Styles résumé upload and skill confirmation. |
| `src/main/resources/static/ui/retry-keys.js` | Keeps safe retry keys and input hashes per tab. |
| `src/main/resources/static/ui/tokens.css` | Defines shared colors, typography and spacing. |
| `src/main/resources/static/ui/workspace.css` | Styles interview/history custom elements. |

## Backend tests

| File | Purpose |
| --- | --- |
| `src/test/java/com/first/genProject/Controller/AIControllerTest.java` | Checks aicontroller behavior using offline fixtures. |
| `src/test/java/com/first/genProject/Controller/DocumentAnswerControllerTest.java` | Checks document answer controller behavior using offline fixtures. |
| `src/test/java/com/first/genProject/Controller/FrontendConfigControllerTest.java` | Checks frontend config controller behavior using offline fixtures. |
| `src/test/java/com/first/genProject/Controller/InterviewControllerTest.java` | Checks interview controller behavior using offline fixtures. |
| `src/test/java/com/first/genProject/Controller/InterviewFeedbackControllerTest.java` | Checks interview feedback controller behavior using offline fixtures. |
| `src/test/java/com/first/genProject/DocumentSearchTests.java` | Checks document search behavior using offline fixtures. |
| `src/test/java/com/first/genProject/GenProjectApplicationTests.java` | Checks gen project application behavior using offline fixtures. |
| `src/test/java/com/first/genProject/auth/AccountSecurityTest.java` | Checks authentication, ownership, CSRF and legacy transfer. |
| `src/test/java/com/first/genProject/config/ChatOptionsConfigTest.java` | Checks chat options config behavior using offline fixtures. |
| `src/test/java/com/first/genProject/config/DocumentEmbeddingConfigTest.java` | Checks document embedding config behavior using offline fixtures. |
| `src/test/java/com/first/genProject/embedding/HostedPassageEmbeddingClientTest.java` | Checks hosted passage embedding client behavior using offline fixtures. |
| `src/test/java/com/first/genProject/live/GroqLiveTest.java` | Makes an explicitly selected live Groq smoke request. |
| `src/test/java/com/first/genProject/persistence/InterviewPersistenceContract.java` | Checks saved interviews, answers, feedback and progress. |
| `src/test/java/com/first/genProject/persistence/InterviewPersistenceTest.java` | Runs the persistence contract on H2. |
| `src/test/java/com/first/genProject/persistence/PostgresPersistenceTest.java` | Runs that contract on disposable Docker PostgreSQL. |
| `src/test/java/com/first/genProject/persistence/RequestReplayTest.java` | Checks durable retries, concurrency and SDK attempt counts. |
| `src/test/java/com/first/genProject/resume/ResumeTextReaderTest.java` | Checks resume text reader behavior using offline fixtures. |
| `src/test/java/com/first/genProject/service/AIServiceImplTest.java` | Checks aiservice impl behavior using offline fixtures. |
| `src/test/java/com/first/genProject/service/AdaptiveQuestionPolicyTest.java` | Checks adaptive question policy behavior using offline fixtures. |
| `src/test/java/com/first/genProject/service/DocumentAnswerServiceTest.java` | Checks document answer service behavior using offline fixtures. |
| `src/test/java/com/first/genProject/service/InterviewFeedbackServiceTest.java` | Checks interview feedback service behavior using offline fixtures. |
| `src/test/java/com/first/genProject/service/InterviewServiceTest.java` | Checks interview service behavior using offline fixtures. |
| `src/test/java/com/first/genProject/support/AccountTestConfiguration.java` | Provides an offline fixture account for integration tests. |
| `src/test/java/com/first/genProject/support/FeedbackFixtures.java` | Provides reusable valid model-feedback JSON. |
| `src/test/java/com/first/genProject/support/StubChatModel.java` | Substitutes deterministic responses for live AI. |
| `src/test/resources/application-test.properties` | Uses H2 and disables live embeddings in tests. |
