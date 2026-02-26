# CHANGELOG

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
