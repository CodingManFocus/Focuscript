# Focuscript (Paper 1.21+ / JDK 21)

**English**

Focuscript is a Paper plugin that compiles `.fs` scripts into Kotlin bytecode at runtime and loads them as isolated modules. The README below is based on the current implementation.

## Requirements

- Paper / Minecraft **1.21+**
- **JDK 21+** (Focuscript checks this at startup)
- Runtime dependency: `kotlin-compiler-embeddable 2.2.20` (auto-downloaded by Paper via `plugin.yml` `libraries`)

## Build

```bash
./gradlew :focuscript-plugin:jar
```

The plugin jar will be generated at:
`focuscript-plugin/build/libs/focuscript-plugin-0.1.0-SNAPSHOT.jar`

## Install & First Run

1. Drop the plugin jar into `plugins/`.
2. Start the Paper server.

On the first run, Focuscript creates the following folders:

```
plugins/Focuscript/
  scripts/     # script workspaces live here
  _runtime/    # extracted focuscript-api.jar for compilation
  _build/      # compiled module jars and cache
```

If `scripts/` is empty, Focuscript creates an example workspace at `scripts/hello/`.

## Workspace Structure

Each workspace is a folder under `plugins/Focuscript/scripts/` with a `script.yml` and `.fs` sources inside `src/`.

```
plugins/Focuscript/scripts/<workspaceId>/
  script.yml
  src/
    main.fs
    ...
  data.yml     # created on demand by FsStorage
```

### `script.yml` fields

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
- `permissions` (list) — allowed permission strings for module commands
- `commands` (list) — allowed command names for module commands

> `permissions` and `commands` are enforced only when the lists are **non-empty**. If empty, any permission/command name can be registered.

### `.fs` rules

- `entry` file must start with `module { ... }` (comments/whitespace are allowed before it)
- `.fs` files **must not** declare `package` or `import`

## Runtime Model

- `.fs` files are converted to Kotlin sources and compiled to a module jar.
- Compiled output is cached in `_build/<module>/cache` based on script content and versions.
- Each module is loaded with a **dedicated classloader** that blocks direct access to Paper/Adventure classes and Focuscript internals (best-effort isolation).

## Available Module APIs

Inside `module { ... }` you can use:

- `server`: online players, broadcast, dispatch commands
- `events`: join/quit/chat/command/block break/block place/death/damage handlers
- `scheduler`: `after(Duration)` and `every(Duration)`
- `log`: module-scoped logger (debug depends on `options.debug`)
- `config`: read access to `script.yml`
- `commands`: register module commands
- `storage`: simple YAML storage per workspace (`data.yml`)

### Prelude helpers (auto-included)

- `module { ... }`
- `text("...")` → `FsText`
- `location("world", x, y, z, yaw, pitch)`
- `3.ticks`, `3.seconds`, `3.minutes`, `3.hours` (converted to `java.time.Duration`)

`FsText` supports color and decorations:

```kotlin
text("Hello").color("green").bold().italic()
text("#ffcc00 hex").color("#ffcc00")
```

## Commands

- `/fs reload` — disables all modules, then reloads and recompiles them
- `/fs cmd <moduleId> <command> [args...]` — dispatches a module command
- `/fscmd <moduleId> <command> [args...]` — shortcut for dispatching module commands

Permissions:
- `focuscript.admin` (default: op) for `/fs` and `/fscmd`

## Example

### `script.yml`

```yaml
id: hello
name: Hello Module
version: 1.0.0
api: 1
entry: src/main.fs
load: enable
commands:
  - hello
permissions:
  - focuscript.example.hello
settings:
  welcome: "Welcome to the server!"
options:
  debug: true
```

### `src/main.fs`

```kotlin
module {
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
}
```

---

**한국어**

Focuscript는 `.fs` 스크립트를 런타임에 Kotlin 바이트코드로 컴파일해 모듈로 로드하는 Paper 플러그인입니다. 아래 문서는 현재 코드 기준으로 정리했습니다.

## 요구 사항

- Paper / Minecraft **1.21+**
- **JDK 21+** (플러그인 시작 시 체크)
- 런타임 의존성: `kotlin-compiler-embeddable 2.2.20` (Paper의 `plugin.yml` `libraries`로 자동 다운로드)

## 빌드

```bash
./gradlew :focuscript-plugin:jar
```

플러그인 JAR:
`focuscript-plugin/build/libs/focuscript-plugin-0.1.0-SNAPSHOT.jar`

## 설치 및 첫 실행

1. 위 JAR을 `plugins/`에 넣습니다.
2. 서버를 실행합니다.

첫 실행 시 다음 폴더가 생성됩니다:

```
plugins/Focuscript/
  scripts/     # 스크립트 워크스페이스
  _runtime/    # focuscript-api.jar 추출본 (컴파일 클래스패스용)
  _build/      # 컴파일 결과 및 캐시
```

`scripts/`가 비어 있으면 `scripts/hello/` 예제 워크스페이스를 생성합니다.

## 워크스페이스 구조

워크스페이스는 `plugins/Focuscript/scripts/` 아래에 위치하며 `script.yml`과 `src/` 폴더를 포함합니다.

```
plugins/Focuscript/scripts/<workspaceId>/
  script.yml
  src/
    main.fs
    ...
  data.yml     # FsStorage 사용 시 생성
```

### `script.yml` 필드

필수:
- `id` (문자열)

선택 (기본값):
- `name` (`id`)
- `version` (`1.0.0`)
- `api` (`1`)
- `entry` (`src/main.fs`)
- `load` (`enable`) — 그 외 값은 로드하지 않음
- `options.debug` (`false`) — 모듈 로그 debug 활성화
- `depends` (리스트) — 먼저 로드되어야 하는 모듈 ID
- `permissions` (리스트) — 모듈 명령에 허용할 권한 문자열
- `commands` (리스트) — 모듈 명령으로 허용할 이름

> `permissions`/`commands` 목록이 **비어 있지 않을 때만** 등록을 제한합니다. 비어 있으면 아무 이름/권한도 등록 가능합니다.

### `.fs` 규칙

- `entry` 파일은 `module { ... }`로 시작해야 합니다 (앞에 주석/공백 허용)
- `.fs`에는 `package`/`import` 선언을 넣을 수 없습니다

## 런타임 동작

- `.fs`를 Kotlin 소스로 변환하고 모듈 JAR로 컴파일합니다.
- `_build/<module>/cache`에 콘텐츠 기반 캐시를 저장합니다.
- 모듈은 **전용 ClassLoader**로 로드되며 Paper/Adventure 직접 접근과 Focuscript 내부 클래스 접근을 차단합니다(완전한 샌드박스는 아님).

## 모듈에서 사용할 수 있는 API

`module { ... }` 안에서 다음을 사용할 수 있습니다:

- `server`: 온라인 플레이어 조회, 브로드캐스트, 커맨드 디스패치
- `events`: join/quit/chat/command/block break/block place/death/damage
- `scheduler`: `after(Duration)`, `every(Duration)`
- `log`: 모듈 전용 로거 (`options.debug`에 따라 debug 출력)
- `config`: `script.yml` 읽기
- `commands`: 모듈 명령 등록
- `storage`: 워크스페이스 전용 `data.yml` 저장소

### 프렐류드 헬퍼 (자동 포함)

- `module { ... }`
- `text("...")` → `FsText`
- `location("world", x, y, z, yaw, pitch)`
- `3.ticks`, `3.seconds`, `3.minutes`, `3.hours` (`java.time.Duration`)

`FsText`는 색상과 꾸밈을 지원합니다:

```kotlin
text("Hello").color("green").bold().italic()
text("#ffcc00 hex").color("#ffcc00")
```

## 명령어

- `/fs reload` — 모든 모듈을 비활성화 후 다시 로드/컴파일
- `/fs cmd <moduleId> <command> [args...]` — 모듈 명령 실행
- `/fscmd <moduleId> <command> [args...]` — 모듈 명령 실행 단축

권한:
- `focuscript.admin` (기본: op) — `/fs`, `/fscmd` 사용

## 예시

### `script.yml`

```yaml
id: hello
name: Hello Module
version: 1.0.0
api: 1
entry: src/main.fs
load: enable
commands:
  - hello
permissions:
  - focuscript.example.hello
settings:
  welcome: "Welcome to the server!"
options:
  debug: true
```

### `src/main.fs`

```kotlin
module {
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
}
```
