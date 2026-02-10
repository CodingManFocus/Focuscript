
# Focuscript (Paper 1.21+ / JDK 21)

Language: **English** | **한국어**  
(Scroll down, or use the links below)

- [English](#english)
- [한국어](#korean)

---

## English

### What is Focuscript?

**Focuscript** is a Paper plugin that compiles `.fs` scripts into Kotlin bytecode **at runtime** and loads them as **isolated modules**.  
It’s designed for quickly writing “small plugins” (commands, event logic, scheduled tasks, etc.) without rebuilding a full plugin JAR every time.

### Key ideas

- **Workspace = a folder** that contains `script.yml` + `.fs` source files.
- **Module = compiled result** of a workspace (identified by `script.yml:id`).
- Focuscript provides a **stable wrapper API (`focuscript-api`)** so scripts don’t directly depend on Paper classes.

### Features (current implementation)

- Runtime compile: `.fs` → Kotlin sources → module JAR
- Workspace-based module loading + dependency order (`depends`)
- Per-workspace config (`script.yml`) + simple YAML storage (`data.yml`)
- Module-scoped logging (`options.debug`)
- Event hooks (join/quit/chat/command/block events/death/damage)
- Scheduler helpers (`after`, `every`)
- Command dispatching via Focuscript commands (`/fs cmd`, `/fscmd`)
- Best-effort module isolation via a dedicated ClassLoader (not a perfect sandbox)
- **Web IDE assets are bundled** in the plugin JAR (experimental; see “Web IDE”)

---

## Requirements

- Paper / Minecraft **1.21+**
- **JDK 21+** (Focuscript checks this at startup)
- Runtime dependency: `kotlin-compiler-embeddable 2.2.20`  
  (auto-downloaded by Paper via `plugin.yml` `libraries`)

---

## Build

```bash
./gradlew :focuscript-plugin:jar
```

The plugin JAR will be generated at:

```
focuscript-plugin/build/libs/focuscript-plugin-0.1.0-SNAPSHOT.jar
```

---

## Install & first run

1) Put the plugin JAR into `plugins/`  
2) Start the Paper server

On the first run, Focuscript creates:

```
plugins/Focuscript/
  scripts/     # script workspaces live here
  _runtime/    # extracted focuscript-api.jar for compilation classpath
  _build/      # compiled module jars and cache
```

If `scripts/` is empty, Focuscript creates an example workspace at:

```
plugins/Focuscript/scripts/hello/
```

---

## Your first workspace

Create a folder:

```
plugins/Focuscript/scripts/<workspaceFolder>/
```

Recommended: make `<workspaceFolder>` the same as `script.yml:id`.

### `script.yml` (example)

```yaml
id: hello
name: Hello Module
version: 1.0.0
api: 1
entry: src/main.fs
load: enable

# Allow-lists for module command registration (optional)
commands:
  - hello
permissions:
  - focuscript.example.hello

# Your custom config values (anything you want)
settings:
  welcome: "Welcome to the server!"

options:
  debug: true
```

### `src/main.fs` (example)

```kotlin
val welcome = config.getString("settings.welcome", "Welcome!")

events.onJoin { e ->
  e.player.sendText(text(welcome).color("green").bold())
}

commands.register("hello", "focuscript.example.hello") { ctx ->
  ctx.sender.sendText(text("Hello from Focuscript!"))
}

scheduler.every(3.minutes) {
  server.broadcast(text("3 minutes passed!").color("yellow"))
}
```

### Running the module command

Module commands are dispatched through Focuscript:

- `/fscmd <moduleId> <command> [args...]`
- `/fs cmd <moduleId> <command> [args...]`

So for the example above:

```
/fscmd hello hello
```

---

## `script.yml` reference

Required:

- `id` (string)

Optional (defaults in parentheses):

- `name` (defaults to `id`)
- `version` (`1.0.0`)
- `api` (`1`)
- `entry` (`src/main.fs`)
- `load` (`enable`) — any other value skips loading
- `options.debug` (`false`) — enables module-scoped debug logging
- `depends` (list) — module IDs to load first
- `permissions` (list) — **allowed permission strings** for module command registration
- `commands` (list) — **allowed command names** for module command registration

> `permissions` and `commands` are enforced **only when the lists are non-empty**.  
> If empty, any permission/command name can be registered.

---

## `.fs` rules (important)

- Write the `entry` file as the module body.  
  Focuscript wraps it with `module { ... }` during compilation.
- Entry files that start with `module { ... }` are rejected.
- `.fs` files must **not** declare `package` or `import`

### Split into multiple `.fs` files (`#include`)

In `entry` (`main.fs`), you can inline other `.fs` files with compile-time includes:

```kotlin
#include "parts/events.fs"
#include "parts/commands.fs"

log.info("main body")
```

- `#include` paths are relative to the current file
- Included files must be inside the same workspace and end with `.fs`
- Included files are treated as entry body fragments (they are not compiled as standalone `.fs` sources)

This keeps scripts in a controlled “template” so Focuscript can manage compilation and isolation consistently.

---

## Runtime model

- `.fs` files are converted into Kotlin sources and compiled into a module JAR.
- Compiled output is cached under:

  ```
  plugins/Focuscript/_build/<moduleId>/cache/
  ```

- Each module is loaded with a dedicated ClassLoader that **blocks direct access** to:
  - Paper/Adventure classes
  - Focuscript internals  
  (best-effort isolation; still treat scripts as trusted code)

---

## APIs available in entry `.fs`

Inside the entry script body (auto-wrapped as a module), you typically use these **context objects**:

- `server` — online players, broadcast, dispatch commands
- `events` — subscribe to server/player events
- `scheduler` — run tasks later / repeatedly
- `log` — module logger (debug depends on `options.debug`)
- `config` — read values from `script.yml`
- `commands` — register module commands (dispatched via `/fscmd` or `/fs cmd`)
- `storage` — workspace-local YAML storage (`data.yml`)

### Wrapper types included in the API

Focuscript ships a wrapper API (`focuscript-api`) that includes types like:

- `FsPlayer`, `FsWorld`, `FsLocation`, `FsBlock`
- `FsInventory`, `FsItemStack`, `FsItemMeta` (+ simple builders: `FsSimpleItemStack`, `FsSimpleItemMeta`)
- `FsText` (text building + color/decorations)

These exist so scripts can do gameplay work without depending directly on Paper classes.

---

## Prelude helpers (auto-included)

- `text("...")` → `FsText`
- `location("world", x, y, z, yaw, pitch)`
- `3.ticks`, `3.seconds`, `3.minutes`, `3.hours` → `java.time.Duration`

`FsText` supports color and decorations:

```kotlin
text("Hello").color("green").bold().italic()
text("#ffcc00 hex").color("#ffcc00")
```

---

## Commands & permissions

Commands:

- `/fs reload` — disables all modules, then reloads and recompiles them
- `/fs cmd <moduleId> <command> [args...]` — dispatches a module command
- `/fscmd <moduleId> <command> [args...]` — shortcut for dispatching module commands

Permission:

- `focuscript.admin` (default: op) for `/fs` and `/fscmd`

---

## Web IDE (experimental)

The plugin JAR contains a bundled web UI (`resources/webide/`) and a `WebIdeManager` implementation.

Because Web IDE behavior may evolve quickly, this README does not hard-code a port/URL.  
Instead:

- Check server startup logs for a “Web IDE” URL/port message (if enabled in your build).
- **Do not expose** a script editor to the public internet. Use localhost or a trusted LAN.

If you can’t find any Web IDE logs yet, treat it as a work-in-progress feature.

---

## Troubleshooting

- **“JDK 21 required”**  
  Run your Paper server with Java 21 (or newer).

- **Workspace not loaded**  
  Ensure `script.yml` has `load: enable`.

- **Command/permission registration blocked**  
  If you use `commands:` / `permissions:` allow-lists, make sure the command name / permission string is listed.

- **Stuck cache / weird behavior after edits**  
  Try `/fs reload`. If needed, delete:
  ```
  plugins/Focuscript/_build/<moduleId>/
  ```

- **Compilation errors**  
  Check that your entry file is not empty, is not wrapped with `module { ... }`, and that you didn’t add `import`/`package`.

---

## Repository layout (for contributors)

- `focuscript-api/` — the wrapper API exposed to scripts
- `focuscript-plugin/` — the Paper plugin (compiler, loader, runtime bridge)
- `example-workspaces/` — example `.fs` workspaces you can copy into your server

Build plugin JAR:

```bash
./gradlew :focuscript-plugin:jar
```

---

## License

GPL-3.0

---

## Korean

### Focuscript란?

**Focuscript**는 `.fs` 스크립트를 **서버 실행 중에 Kotlin 바이트코드로 컴파일**하고, 이를 **모듈 형태로 로드**해 주는 Paper 플러그인입니다.  
전체 플러그인 JAR을 매번 다시 빌드하지 않고도, **작은 기능(명령어/이벤트/스케줄러 등)** 을 빠르게 만들 수 있도록 설계되었습니다.

### 핵심 개념

- **워크스페이스(Workspace)**: `script.yml` + `.fs` 소스들이 들어있는 폴더
- **모듈(Module)**: 워크스페이스가 컴파일되어 로드된 결과 (`script.yml:id`가 모듈 ID)
- 스크립트는 Paper 클래스를 직접 쓰지 않고, **Focuscript API(`focuscript-api`) 래퍼**를 통해 동작합니다.

### 주요 기능 (현재 구현 기준)

- 런타임 컴파일: `.fs` → Kotlin 소스 → 모듈 JAR
- 워크스페이스 단위 로드 + 의존성 로드 순서(`depends`)
- 워크스페이스 설정(`script.yml`) + 간단한 YAML 저장소(`data.yml`)
- 모듈 전용 로그 + 디버그 옵션(`options.debug`)
- 이벤트 훅 (입장/퇴장/채팅/커맨드/블록 이벤트/죽음/데미지)
- 스케줄러 헬퍼 (`after`, `every`)
- Focuscript 명령어로 모듈 명령 실행 (`/fs cmd`, `/fscmd`)
- 전용 ClassLoader 기반 “최대한의” 모듈 격리(완전한 샌드박스는 아님)
- **Web IDE 정적 파일이 플러그인에 포함**(실험적 기능)

---

## 요구 사항

- Paper / Minecraft **1.21+**
- **JDK 21+** (플러그인 시작 시 체크)
- 런타임 의존성: `kotlin-compiler-embeddable 2.2.20`  
  (Paper의 `plugin.yml` `libraries`로 자동 다운로드)

---

## 빌드

```bash
./gradlew :focuscript-plugin:jar
```

플러그인 JAR:

```
focuscript-plugin/build/libs/focuscript-plugin-0.1.0-SNAPSHOT.jar
```

---

## 설치 및 첫 실행

1) 위 JAR을 `plugins/`에 넣습니다.  
2) Paper 서버를 실행합니다.

첫 실행 시 다음 폴더가 생성됩니다:

```
plugins/Focuscript/
  scripts/     # 스크립트 워크스페이스
  _runtime/    # 컴파일 클래스패스용 focuscript-api.jar 추출본
  _build/      # 컴파일 결과 및 캐시
```

`scripts/`가 비어 있으면 예제 워크스페이스를 자동 생성합니다:

```
plugins/Focuscript/scripts/hello/
```

---

## 첫 워크스페이스 만들기

폴더를 하나 만듭니다:

```
plugins/Focuscript/scripts/<workspaceFolder>/
```

추천: `<workspaceFolder>` 이름을 `script.yml:id`와 동일하게 맞추기.

### `script.yml` 예시

```yaml
id: hello
name: Hello Module
version: 1.0.0
api: 1
entry: src/main.fs
load: enable

# 모듈 명령 등록을 위한 allow-list (선택)
commands:
  - hello
permissions:
  - focuscript.example.hello

# 커스텀 설정(원하는 값 자유롭게)
settings:
  welcome: "Welcome to the server!"

options:
  debug: true
```

### `src/main.fs` 예시

```kotlin
val welcome = config.getString("settings.welcome", "Welcome!")

events.onJoin { e ->
  e.player.sendText(text(welcome).color("green").bold())
}

commands.register("hello", "focuscript.example.hello") { ctx ->
  ctx.sender.sendText(text("Hello from Focuscript!"))
}

scheduler.every(3.minutes) {
  server.broadcast(text("3 minutes passed!").color("yellow"))
}
```

### 모듈 명령 실행

모듈 명령은 Focuscript 명령어로 실행합니다:

- `/fscmd <moduleId> <command> [args...]`
- `/fs cmd <moduleId> <command> [args...]`

예시:

```
/fscmd hello hello
```

---

## `script.yml` 필드 정리

필수:

- `id` (문자열)

선택(기본값):

- `name` (`id`)
- `version` (`1.0.0`)
- `api` (`1`)
- `entry` (`src/main.fs`)
- `load` (`enable`) — 그 외 값은 로드하지 않음
- `options.debug` (`false`) — 모듈 로그 debug 활성화
- `depends` (리스트) — 먼저 로드되어야 하는 모듈 ID
- `permissions` (리스트) — 모듈 명령 등록 시 허용할 **권한 문자열 allow-list**
- `commands` (리스트) — 모듈 명령 등록 시 허용할 **이름 allow-list**

> `permissions`/`commands` 목록이 **비어 있지 않을 때만** 제한합니다.  
> 비어 있으면 어떤 이름/권한도 등록 가능합니다.

---

## `.fs` 규칙 (중요)

- `entry` 파일은 모듈 본문만 작성하면 됩니다.  
  컴파일 시 Focuscript가 자동으로 `module { ... }`로 감싸줍니다.
- `module { ... }`로 시작하는 entry 파일은 허용되지 않습니다.
- `.fs`에는 `package`/`import` 선언을 넣을 수 없습니다

### 여러 `.fs` 파일 분리 (`#include`)

`entry` (`main.fs`)에서 컴파일 타임 include로 다른 `.fs`를 불러올 수 있습니다:

```kotlin
#include "parts/events.fs"
#include "parts/commands.fs"

log.info("main body")
```

- `#include` 경로는 현재 파일 기준 상대 경로입니다
- include 대상은 같은 워크스페이스 내부의 `.fs` 파일이어야 합니다
- include된 파일은 entry 본문 조각으로 처리되며, 별도 `.fs` 소스로는 컴파일하지 않습니다

---

## 런타임 동작

- `.fs` → Kotlin 소스 변환 → 모듈 JAR로 컴파일
- 컴파일 캐시는 다음 위치에 저장됩니다:

  ```
  plugins/Focuscript/_build/<moduleId>/cache/
  ```

- 모듈은 전용 ClassLoader로 로드되며 다음에 대한 직접 접근을 차단합니다:
  - Paper/Adventure 클래스
  - Focuscript 내부 클래스  
  (완전한 보안 샌드박스는 아니므로, 스크립트는 “신뢰 가능한 코드”로 취급하세요.)

---

## entry `.fs`에서 쓸 수 있는 API

entry 스크립트 본문(컴파일 시 module으로 자동 래핑됨)에서 대표적으로 아래 “컨텍스트 객체”들을 사용합니다:

- `server` — 온라인 플레이어 조회, 브로드캐스트, 커맨드 디스패치
- `events` — 이벤트 구독
- `scheduler` — 일정 시간 후 / 반복 작업
- `log` — 모듈 전용 로거 (`options.debug`에 따라 debug 출력)
- `config` — `script.yml` 읽기
- `commands` — 모듈 명령 등록(`/fscmd` 또는 `/fs cmd`로 실행)
- `storage` — 워크스페이스 전용 YAML 저장소(`data.yml`)

### API에 포함된 래퍼 타입들

`focuscript-api`에는 다음과 같은 래퍼 타입들이 포함되어 있습니다:

- `FsPlayer`, `FsWorld`, `FsLocation`, `FsBlock`
- `FsInventory`, `FsItemStack`, `FsItemMeta` (+ 간단 빌더: `FsSimpleItemStack`, `FsSimpleItemMeta`)
- `FsText` (텍스트 + 색상/꾸밈)

---

## 프렐류드 헬퍼 (자동 포함)

- `text("...")` → `FsText`
- `location("world", x, y, z, yaw, pitch)`
- `3.ticks`, `3.seconds`, `3.minutes`, `3.hours` → `java.time.Duration`

`FsText` 예시:

```kotlin
text("Hello").color("green").bold().italic()
text("#ffcc00 hex").color("#ffcc00")
```

---

## 명령어 & 권한

명령어:

- `/fs reload` — 모든 모듈을 비활성화 후 다시 로드/컴파일
- `/fs cmd <moduleId> <command> [args...]` — 모듈 명령 실행
- `/fscmd <moduleId> <command> [args...]` — 모듈 명령 실행 단축

권한:

- `focuscript.admin` (기본: op) — `/fs`, `/fscmd` 사용

---

## Web IDE (실험적)

플러그인 JAR에는 `resources/webide/` 정적 웹 UI와 `WebIdeManager` 구현이 포함되어 있습니다.

다만 Web IDE는 변경 가능성이 크기 때문에, 이 문서에서는 포트/URL을 고정해서 적지 않습니다.

- (활성화되어 있다면) 서버 시작 로그에서 Web IDE 주소/포트를 확인하세요.
- 외부 인터넷에 공개하지 마세요. 로컬/신뢰 가능한 LAN에서만 사용하세요.

로그가 아직 없다면, “작업 중인 기능”으로 생각해 주세요.

---

## 문제 해결

- **“JDK 21 필요”**  
  서버 실행 Java 버전을 21 이상으로 올려주세요.

- **모듈이 로드되지 않음**  
  `script.yml`에 `load: enable`인지 확인하세요.

- **명령/권한 등록이 막힘**  
  `commands:`/`permissions:` allow-list를 쓰는 경우, 등록하려는 값이 목록에 있어야 합니다.

- **수정 후 이상한 캐시 문제**  
  `/fs reload`를 먼저 시도하세요. 그래도 안 되면:
  ```
  plugins/Focuscript/_build/<moduleId>/
  ```
  를 삭제한 후 다시 로드해 보세요.

- **컴파일 에러**  
  entry 파일이 비어 있지 않은지, `module { ... }`로 감싸지 않았는지, `import`/`package`를 넣지 않았는지 확인하세요.

---

## 레포 구조 (개발자용)

- `focuscript-api/` — 스크립트에 노출되는 API
- `focuscript-plugin/` — Paper 플러그인(컴파일러/로더/런타임 브리지)
- `example-workspaces/` — 복사해서 바로 쓸 수 있는 예시 워크스페이스

플러그인 빌드:

```bash
./gradlew :focuscript-plugin:jar
```

---

## 라이선스

GPL-3.0
