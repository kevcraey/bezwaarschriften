# Architectural Decision Records (ADR)

| ID | Titel | Datum | Korte samenvatting |
|----|-------|-------|--------------------|
| ADR-001 | Hexagonale architectuur | 2025-12 | Ports & adapters patroon voor ontkoppeling van domeinlogica en infrastructuur. |
| ADR-002 | WebSocket voor real-time taakupdates | 2026-02 | WebSocket i.p.v. polling voor live statusupdates van extractie- en consolidatietaken. |
| ADR-003 | Persistente bestandsstatus in database | 2026-03 | Bestandsstatus opslaan in `bezwaar_bestand` tabel i.p.v. in-memory ConcurrentHashMap. |
| ADR-004 | Async workers voor langlopende taken | 2026-02 | Extractie- en consolidatietaken asynchroon verwerken via `@Async` workers met retry-logica. |
| ADR-005 | Lit + @domg-wc design system voor frontend | 2026-01 | Vlaams design system (@domg-wc/Flux) als enige UI-componentbibliotheek. |
| ADR-006 | HDBSCAN clustering van bezwaren | 2026-02 | Density-based clustering (HDBSCAN) voor het groeperen van gelijkaardige bezwaren. |
| ADR-007 | Centroid-matching voor kernbezwaren | 2026-02 | Kernbezwaar selectie via centroid-afstand in embedding-ruimte. |
