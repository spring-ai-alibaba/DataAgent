# DataAgent Test Trust Guide

**Updated:** 2026-08-03
**Purpose:** Keep tests deterministic while proving that the intended production path actually ran.

## Test layers

| Layer | Naming / command | Real dependencies | Required proof |
| --- | --- | --- | --- |
| Unit | `*Test`, `make verify` | None; external boundaries may be mocked | Exact output, state transition, request, and error branch |
| Component | `*Test`, `make verify` | Multiple real DataAgent classes; only outer boundaries mocked | Data passes through the complete in-process chain |
| Infrastructure integration | `*IT`, `make integration-test` | Docker or an explicitly configured service | Real protocol, persistence, cleanup, and failure behavior |
| Provider live contract | `*LiveIT`, `make live-model-test` | Alibaba Cloud Model Studio | Parsed content, usage metadata, vector shape, and terminal completion |

Coverage reports show which lines ran. They do not prove that a test reached the intended branch or checked the right result.
`make test-trust-check` enforces deterministic bad-pattern rules, but it is only a guardrail; it cannot prove business correctness.

## Non-negotiable rules

1. Keep Mockito strict. Do not add class-wide `Strictness.LENIENT`.
2. Stub only behavior used by that test. Move branch-specific stubs out of `@BeforeEach`.
3. A reactive node has not executed merely because `apply` returned a `Flux`. Subscribe through terminal completion.
4. Do not use `assertNotNull(result)` or `containsKey(outputKey)` as the only oracle.
5. Check the exact state transition and at least one externally visible value. For boundary calls, capture and validate the request.
6. Negative tests must prove the concrete error, retry, fallback, or no-interaction behavior.
7. A test name must describe current behavior. Do not claim truncation, retry, persistence, or fallback without asserting it.
8. A missing live credential is a failure in the explicit live profile, never a skipped green test.

## Workflow-node pattern

Use the shared `GraphNodeTestSupport` helper. It validates the output type, consumes the stream, requires a done event, and returns the streamed text plus the final state.

```java
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;

NodeExecution execution = execute(node.apply(state), SQL_EXECUTE_NODE_OUTPUT);

assertThat(execution.finalResult())
    .containsEntry(SQL_REGENERATE_REASON, SqlRetryDto.empty())
    .containsEntry(PLAN_CURRENT_STEP, 2);
assertThat(execution.streamedText()).contains("SQL查询结果");

ArgumentCaptor<DbQueryParameter> query =
    ArgumentCaptor.forClass(DbQueryParameter.class);
verify(accessor).executeSqlAndReturnObject(any(DbConfigBO.class), query.capture());
assertThat(query.getValue().getSql()).isEqualTo("SELECT * FROM users");
```

This is insufficient because no subscription occurs and none of the SQL, vector, sandbox, or post-processing work is proven:

```java
Map<String, Object> result = node.apply(state);
assertNotNull(result);
assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
```

## Strict mocking

Mockito's default strictness is intentional. An `UnnecessaryStubbingException` usually means one of the following:

- the test never entered the intended branch;
- an asynchronous publisher was never consumed;
- shared setup contains behavior irrelevant to this case;
- the production implementation changed while the test kept stale expectations.

Fix the cause. Do not suppress the signal globally. A narrowly scoped `lenient().when(...)` is acceptable only when a framework invokes optional behavior outside the test's control, and the test must explain why in a comment.

Prefer real value objects and parsers when they are deterministic and cheap. Mock network, database, vector-store, clock, process, and sandbox boundaries. For a mocked boundary, verify the important request fields as well as the returned result.

## Reactive and streaming behavior

- Use `GraphNodeTestSupport.execute` for graph nodes.
- Use Reactor `StepVerifier` for service publishers.
- Require completion or the expected error; avoid fire-and-forget `subscribe()`.
- If the contract is specifically early emission, hold the downstream dependency with a sink or latch, assert the early event, release it, and then assert terminal completion.
- Assert structured payloads after decoding their stream markers. String presence alone is not enough when a DTO is available.

## Real provider tests

`DeepSeekModelLiveIT` uses the same `DynamicModelFactory` as production. It validates:

- a real streaming `deepseek-v4-flash` response with an exact protocol marker;
- terminal completion of the stream;
- non-zero token usage from the completed stream.

`DashScopeEmbeddingLiveIT` separately validates Alibaba Cloud Model Studio through
its OpenAI-compatible Embeddings endpoint. It requires two real results and checks
that both 1,024-dimensional vectors are finite, non-zero, distinct, and accompanied
by positive token usage. The test does not use the Anthropic-compatible
`/apps/anthropic` endpoint because that endpoint exposes Messages, not Embeddings.

Run locally in zsh without placing the key in source, command history, or Maven arguments:

```bash
read -s "DEEPSEEK_API_KEY?DeepSeek API Key: "
echo
export DEEPSEEK_API_KEY
read -s "DASHSCOPE_API_KEY?DashScope API Key: "
echo
export DASHSCOPE_API_KEY
mvn -pl data-agent-management -Plive-model \
  -Dit.test=DeepSeekModelLiveIT,DashScopeEmbeddingLiveIT verify
unset DEEPSEEK_API_KEY
unset DASHSCOPE_API_KEY
```

Optional overrides:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DATAAGENT_LIVE_DEEPSEEK_API_KEY` | falls back to `DEEPSEEK_API_KEY` | Dedicated live-test credential |
| `DATAAGENT_LIVE_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | Official OpenAI-compatible endpoint |
| `DATAAGENT_LIVE_DEEPSEEK_COMPLETIONS_PATH` | `/chat/completions` | Official Chat Completions path |
| `DATAAGENT_LIVE_DEEPSEEK_CHAT_MODEL` | `deepseek-v4-flash` | Current low-cost Chat model |
| `DATAAGENT_LIVE_DASHSCOPE_API_KEY` | falls back to `DASHSCOPE_API_KEY` | Dedicated Embedding credential |
| `DATAAGENT_LIVE_DASHSCOPE_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | Official OpenAI-compatible endpoint |
| `DATAAGENT_LIVE_DASHSCOPE_EMBEDDINGS_PATH` | `/embeddings` | Official Embeddings path |
| `DATAAGENT_LIVE_DASHSCOPE_EMBEDDING_MODEL` | `text-embedding-v4` | Existing 1,024-dimensional model space |

The GitHub workflow is manual-only and must exist on the default branch before GitHub allows administrators to dispatch it. An administrator enters an open PR number; the unprivileged resolver job validates the PR and resolves its fork repository plus immutable head SHA. The live job checks out that exact SHA and reads the `DEEPSEEK` and `DASHSCOPE_API_KEY` Actions secrets only for the provider-test step gated by the protected `live-model` Environment.

Configure required reviewers and prevent self-review for the `live-model` Environment. Before approval, inspect the PR and confirm that the resolved SHA is the revision intended for testing because checked-out PR code can access the credential during the final step. Never expose this secret to an automatic pull-request job and never use `pull_request_target` to execute pull-request code.

## Required evidence before merging

- `make verify` passes with no skipped tests introduced by the change.
- `make test-trust-check` passes with no disabled, lenient, constant, sleep-based, or broad-exception test patterns.
- Frontend unit tests run in CI and pass when frontend code or contracts change.
- `make integration-test` passes for affected real-infrastructure boundaries.
- `make live-model-test` passes when provider compatibility changes.
- Test reports, not log snippets, provide the final test/failure/error/skip counts.
- Every fixed fake-green case contains a meaningful assertion that would fail if the production behavior were removed.
