# TextViewer Agent Rule

- 릴리즈 업데이트(버전 증가, `app-release.apk` 재빌드, GitHub Release 갱신) 작업이 완료되면 항상 Discord 알림을 전송한다.
- 알림은 `release-discord` 스킬로 처리한다.
- 실행 규칙:
  - 버전 태그: `v<version>`
  - 커밋 해시 포함
  - 변경 요약 1~2줄 이하
  - 릴리스 링크 포함
  - `app-release.apk` 갱신 상태 표기
- 알림 전송 시 환경 변수 경로는 `/Users/jihun/StudioProjects/dash/.env`의 `DISCORD_WEBHOOK_URL` 또는 `OMC_DISCORD_WEBHOOK_URL`을 사용한다.
