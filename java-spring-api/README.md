# Java Spring Boot API

This module exposes the same HTTP endpoints as the repository's `api.py`:
`/health`, `/gen_voice`, `/gen_image`, `/gen_clip`, `/cutout`, `/capture_web`,
`/gen_music`, `/gen_sfx`, `/mix_music`, `/mix_sfx`, `/gen_chords`,
`/bakeoff_clip`, `/render/remotion`, and `/render/remotion/frames`.

## Run

From `D:\Open Source\claude-faceless-shorts-creator\java-spring-api`:

```text
mvn spring-boot:run
```

The default port is `8000`. `REPOSITORY_ROOT` defaults to `..` when launched from
this module. Override it when needed:

```text
set REPOSITORY_ROOT=D:\Open Source\claude-faceless-shorts-creator
mvn spring-boot:run
```

## Migration status

The deterministic audio, mixing, white-key cutout, image request, and video request
adapters are implemented in Java. The remaining integrations still require their
external runtimes: ElevenLabs, fal.ai, Cloudflare, Playwright/Chromium, FFmpeg, and
Remotion/Node.

The Python command is retained only for legacy process-backed paths and is not a
requirement for the Java implementations listed above. A Java-only deployment is
not yet supported for every endpoint.

### Java rembg model

The Java cutout service uses ONNX Runtime with a U2-Net ONNX model and follows
rembg's 320x320 ImageNet-normalized input and first-output mask normalization.
Provision the model outside Git, then set its path before starting the API:

```text
set REMBG_MODEL_PATH=D:\models\u2net.onnx
mvn spring-boot:run
```

Without `REMBG_MODEL_PATH`, Java checks `%USERPROFILE%\.u2net\u2net.onnx`.
`--method rembg` returns an actionable 503 if the model is absent; `--method auto`
uses the Java white-key fallback in that case. Exact artifact-level parity still
requires running both implementations against the same representative images and
model version.

The project path service rejects absolute and escaping paths and keeps tool outputs
under `projects/<project_id>/`.
## Spring AI agent

The five production skills are packaged under `src/main/resources/skills/`: `make-ai-short`, `make-short`, `make-vox`, `suggest-sfx`, and `vidtsx-2d-generator`. Graphify remains in the repository agent-skills directory and is intentionally not loaded by this API.

The agent uses Spring AI `ChatClient` and exposes the existing Java tool services through Spring AI tools. Configure `OPENAI_API_KEY` to enable chat:

```text
set OPENAI_API_KEY=...
set OPENAI_MODEL=gpt-4o-mini
set OPENAI_BASE_URL=https://api.openai.com
mvn spring-boot:run
```

`OPENAI_BASE_URL` supports OpenAI-compatible providers as well. For example, to
use an OpenAI-compatible local server:

```text
set OPENAI_BASE_URL=http://localhost:11434/v1
set OPENAI_API_KEY=ollama
set OPENAI_MODEL=llama3.2
mvn spring-boot:run
```

### Supabase PostgreSQL and JDBC sessions

Spring Session is configured to store sessions in PostgreSQL through JDBC. Set
the Supabase connection values before starting the API. Use the Supabase pooler
connection string for deployments that cannot connect directly to the database:

```text
set SUPABASE_DB_URL=jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres?sslmode=require
set SUPABASE_DB_USERNAME=postgres.<project-ref>
set SUPABASE_DB_PASSWORD=your-supabase-database-password
mvn spring-boot:run
```

The JDBC session schema is initialized automatically on startup. The same
settings are available in `src/main/resources/application.yml` as the standard
`spring.datasource.*` properties.

Endpoints:

* `GET /agent/skills` � list available skills.
* `GET /agent/tools` � list registered production tools.
* `POST /agent/chat` � body `{ "skill": "make-short", "message": "..." }`.

The selected skill is included as the system prompt. The agent is instructed to call only registered Spring tools; it must not reference the former Python `tools/*.py` commands. Mixing remains subject to the approval gates documented by the migrated skills.
