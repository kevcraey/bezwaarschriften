# Technical Decision Records (TDR)

| ID | Titel | Datum | Korte samenvatting |
|----|-------|-------|--------------------|
| TDR-001 | Java 21 + Spring Boot 3.4 | 2025-12 | Java 21 voor virtual threads en records; Spring Boot 3.4 voor Spring AI compatibiliteit. |
| TDR-002 | Spring AI met profile-based provider switching | 2026-01 | OpenAI en Ollama via `@Profile` gescheiden; geen code-wijzigingen nodig bij wisselen. |
| TDR-003 | pgvector voor vector similarity search | 2026-01 | PostgreSQL + pgvector extensie voor embedding-opslag en cosine similarity queries. |
| TDR-004 | bge-m3 embedding model | 2026-01 | Meertalig embedding model (bge-m3) voor Nederlandse bezwaarteksten. |
| TDR-005 | PDFBox + Tesseract OCR | 2026-01 | Apache PDFBox voor PDF-extractie, Tesseract als fallback voor gescande documenten. |
| TDR-006 | Liquibase voor database migraties | 2025-12 | Liquibase changelogs voor reproduceerbare schema-evolutie. |
| TDR-007 | Testcontainers voor integratietests | 2026-01 | Testcontainers met PostgreSQL + pgvector voor realistische integratietests zonder externe infra. |
| TDR-008 | UMAP voor dimensiereductie | 2026-02 | UMAP reduceert high-dimensional embeddings naar 2D voor HDBSCAN clustering input. |
| TDR-009 | JPA voor data-access | 2025-12 | Spring Data JPA met Hibernate voor ORM; native queries enkel voor vector-operaties. |
| TDR-010 | Minimaal mocken in tests | 2026-01 | Voorkeur voor integratietests boven unit tests met mocks om meer bugs te vangen. |
| TDR-011 | Cypress voor frontend tests | 2026-02 | Cypress component tests voor Lit web components in shadow DOM. |
| TDR-012 | Tekst-extractie als aparte stap | 2026-03 | Tekst-extractie losgekoppeld van bezwaar-extractie voor herbruikbaarheid en preview. |
