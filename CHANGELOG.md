# CHANGELOG

## 1.0.18 (2026-02-26)
- Add resilience to large-document page calculation with size-based safe fallback from exact layout to estimate.
- Keep page range continuity coverage by always validating generated ranges through normalizer.
- Improve reader stability during file open/resume by preventing page layout exceptions from crashing UI.
- Bump app version to `1.0.18` / `versionCode 18`.

## 1.0.17 (2026-02-26)
- Add regression coverage for page navigation consistency when page ranges are unordered/gappy.
- Enforce strict page-index based movement under repeated page transitions.
- Keep page range continuity behavior stabilized from previous fix set.
- Bump app version to `1.0.17` / `versionCode 17`.

## 1.0.16 (2026-02-26)
- Fix page range continuity to prevent lost or duplicated lines during navigation.
- Refactor page navigation to advance by page index, preventing multi-page jumps after repeated moves.
- Improve initial page calculation responsiveness with cached paging parameters keyed by content hash and viewport bucket size.
- Add Compose UI regression test for theme toggle stability after repeated page-state changes.
- Bump app version to `1.0.16` / `versionCode 16`.

## 1.0.15 (2026-02-25)
- Normalize page ranges before applying navigation offsets.
- Keep page navigation aligned to normalized page index boundaries.
- Update release artifacts and version metadata.

## 1.0.13 (2026-02-25)
- Recover resume/restore behavior for large documents.
- Update paging/metadata handling for oversized files.
- Improve early validation and fail-safe reads.
