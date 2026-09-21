# WebCraft 과제 API 명세 (v2.1.0)

원본: `https://f-api.github.io/game-spring-api-docs/expert/openapi.yaml` (HTML: `.../expert/api-docs.html`, JS 렌더링이라 openapi.yaml로 읽는다)

Base URL: `http://localhost:8080`

## 목록

| 메서드 | 경로 | 성공 | 설명 |
| --- | --- | ---: | --- |
| POST | `/players` | 201 | 플레이어 등록 |
| GET | `/worlds` | 200 | 월드 목록 조회 |
| POST | `/worlds` | 201 | 월드 생성 |
| GET | `/worlds/{worldId}/chats` | 200 | 최근 채팅 조회 |
| GET | `/worlds/{worldId}/chats/history` | 200 | 과거 채팅 커서 조회 (Lv 17) |
| POST | `/practice/worlds/{worldId}/chats/rollback` | 204 | 저장 롤백 확인 (Lv 18, `assignment-checks` 프로필 전용) |
| WS | `/ws/worlds/{worldId}?nickname=` | 101 | WebSocket 연결 |

`worldId`가 숫자가 아니면 `400 VALIDATION_FAILED`.

## 오류 응답

본문: `{"error": "<CODE>"}`

| error | 상태 | 언제 |
| --- | ---: | --- |
| `VALIDATION_FAILED` | 400 | 필수 값 누락, 길이·패턴 위반, 잘못된 숫자 파라미터 |
| `INVALID_REQUEST_BODY` | 400 | 잘못된 JSON, 알 수 없는 필드 또는 난이도 |
| `WORLD_NOT_FOUND` | 404 | 존재하지 않는 월드 |
| `PLAYER_NOT_FOUND` | 404 | 등록되지 않은 닉네임으로 월드 생성 |
| `DUPLICATE_NICKNAME` | 409 | 이미 등록된 닉네임 |
| `WORLD_LIMIT_REACHED` | 409 | 월드가 이미 3개인 상태에서 생성 |
| `INTERNAL_ERROR` | 500 | 예기치 못한 서버 오류 |
| `WORLD_BASELINE_INITIALIZING` | 503 | 서버 기동 직후 월드 준비 전 |

- REST 요청의 알 수 없는 필드는 `400 INVALID_REQUEST_BODY`.
- 시각은 서버 시간대 기준 ISO 8601, 시간대 표기 없음 (`2026-07-16T12:34:56`).

## REST

### POST /players — 플레이어 등록
- 요청: `{"nickname": "steve"}` — 2~12자, `^[a-zA-Z0-9_]+$` (한글 불가)
- 같은 닉네임 동시 요청이어도 하나만 저장, 나머지는 409.
- 응답: `201` 본문 없음 / `400 VALIDATION_FAILED` / `409 DUPLICATE_NICKNAME`

### GET /worlds — 월드 목록
- 월드는 최대 3개. `onlineCount`는 DB가 아닌 **Redis presence** 기준. 없으면 빈 배열.
- 응답 `200`: `[{id:int64, name, seed:int32, onlineCount:int, difficulty: easy|normal|hard}]`
- `503 WORLD_BASELINE_INITIALIZING`

### POST /worlds — 월드 생성
- 요청: `{name, difficulty?, nickname?}`
  - `name`: 1~30자, 공백만 불가
  - `difficulty`: `easy|normal|hard`, 생략 시 `normal`
  - `nickname`: 2~12자, 등록된 플레이어여야 하며 소유자가 됨. 생략 시 소유자 없음
- 시드는 서버가 signed int32 범위에서 정함. 동시 요청에도 3개 상한 유지.
- 응답 `201`: `{id, name, seed, difficulty, ownerNickname(nullable)}`
- 오류: `400 VALIDATION_FAILED`(이름 규칙) / `400 INVALID_REQUEST_BODY`(난이도·알 수 없는 필드) / `404 PLAYER_NOT_FOUND` / `409 WORLD_LIMIT_REACHED` / `503 WORLD_BASELINE_INITIALIZING`

### GET /worlds/{worldId}/chats — 최근 채팅
- 쿼리 `limit` (int, 기본 50). 1 미만/100 초과는 **오류가 아니라 1~100으로 보정**. 숫자가 아니면 `400`.
- 정렬: createdAt 내림차순, 같으면 id 내림차순으로 최신 `limit`건 선택 → **오래된 순(옛날→최신)** 으로 반환.
- 응답 `200`: `[{sender, content, createdAt}]`
- 오류: `400 VALIDATION_FAILED` / `404 WORLD_NOT_FOUND`

### GET /worlds/{worldId}/chats/history — 과거 채팅 커서 조회 (Lv 17)
- 쿼리: `beforeCreatedAt`(string, ISO 8601), `beforeId`(int64), `limit`(1~100, 기본 20)
- createdAt 내림차순, 같으면 id 내림차순 (**최신부터**). 첫 요청은 커서 생략.
- 다음 요청에 응답의 `nextCreatedAt`/`nextId`를 `beforeCreatedAt`/`beforeId`로 **함께** 전달.
- Lv 18 캐시 대상 아님.
- 응답 `200`: `{items:[{id, sender, content, createdAt}], hasNext:boolean, nextCreatedAt(nullable), nextId(nullable)}`
- 오류: `400 VALIDATION_FAILED`(커서 쌍 누락, 잘못된 시각, limit 범위 위반) / `404 WORLD_NOT_FOUND`

### POST /practice/worlds/{worldId}/chats/rollback — 저장 롤백 확인 (Lv 18)
- `assignment-checks` 프로필에서만 등록. 기본 실행에서는 경로 없음.
- 채팅 저장 서비스 호출 후 트랜잭션을 의도적으로 롤백. `204`여도 DB·수신 화면에 채팅이 남지 않아야 하고 캐시 무효화도 실행되면 안 됨.
- 쿼리(필수): `nickname`(2~12자, `^[A-Za-z0-9_]+$`), `content`(1~200자). 실제 등록된 닉네임·월드 ID 사용.
- 응답: `204` / `404 WORLD_NOT_FOUND`

## WebSocket

### 연결
```
ws://localhost:8080/ws/worlds/{worldId}?nickname=steve
```
- 성공 시 `101`. 허용되지 않은 Origin은 핸드셰이크 `403`, 월드 준비 전은 `503`.
- 종료 코드

| 코드 | 상황 |
| ---: | --- |
| 4000 | 닉네임 누락 또는 미등록 닉네임 |
| 4001 | 접속할 수 없는 월드 ID |
| 4002 | 같은 월드에 같은 닉네임이 이미 접속 중 (기존 연결 유지) |
| 1000 | 정상 종료 |

### 메시지 형식
텍스트 프레임, 문자열 `type` 필드를 가진 JSON 객체. `data`로 감싸지 않는다.
잘못된 JSON/필드/처리 불가 `type`은 `error` 응답으로 확인. 게임 초기화는 제공 코드가 처리.

### 클라이언트 → 서버

| type | 본문 | 설명 |
| --- | --- | --- |
| `ping` | `{"type":"ping"}` | 같은 연결로 `pong` 응답. heartbeat 과제에서 주기 전송 |
| `chat` | `{"type":"chat","content":"..."}` | `content` 1~200자(UTF-16 code unit), 공백만이면 거절. 보낸 사람·월드는 연결 정보에서 취득. 기본 제공 코드는 플레이어별 첫 허용 요청부터 10초간 최대 5건 (비원자 구현의 동시성 문제는 Lv 19에서 개선) |
| `move` | `{"type":"move","x","y","z","yaw","pitch","crouching","gliding"}` | x/y/z double, yaw/pitch float(모두 유한값), crouching/gliding는 생략 불가 boolean. 별도 성공 응답 없음(엔진 상태 전송으로 확인). 내부 검증용 선택 필드는 제공 코드가 처리, 구현 대상 제외 |
| `onlineUsers` | `{"type":"onlineUsers"}` | 현재 연결의 월드에서 **이 서버**에 등록된 열린 연결만 대상, 요청자 포함. 다른 월드 지정·다중 서버 합산 없음 |

### 서버 → 클라이언트

| type | 본문 | 설명 |
| --- | --- | --- |
| `onlineUsers` | `{"type":"onlineUsers","users":["Alice","Bob"],"count":2}` | 요청한 연결에만 반환. 닉네임 대소문자 보존, Java 문자열 자연 순서 정렬. `count` = `users.length`. Redis 인원수와 합산하지 않음 |
| `pong` | `{"type":"pong"}` | `ping` 보낸 연결에만 |
| `chat` | `{"type":"chat","sender","content","timestamp"}` | 저장한 채팅을 같은 월드 모든 연결에 전달(보낸 사람 포함). REST는 `createdAt`, WS는 `timestamp` |
| `error` | `{"type":"error","code":"..."}` | 요청한 연결에만. 이 오류만으로 연결을 끊지 않음 |

`error.code`

| code | 의미 |
| --- | --- |
| `INVALID_JSON` | 텍스트를 JSON으로 읽을 수 없음 |
| `INVALID_MESSAGE` | JSON 객체가 아니거나 필수 필드·값이 잘못됨 |
| `UNKNOWN_TYPE` | type이 없거나 처리할 핸들러가 없음 |
| `INTERNAL_ERROR` | 처리 중 예기치 못한 서버 오류 |
| `CHAT_COOLDOWN` | 10초 구간 허용 횟수 초과. 저장·전송하지 않음 |
