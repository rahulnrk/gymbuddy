# Living technical specification

Engineering source of truth. Lives beside the implementation. Same-change updates.

- Product law: [`../../PRODUCT.md`](../../PRODUCT.md)
- Complete spec: [`TECHNICAL_SPEC.md`](TECHNICAL_SPEC.md)
- Agent rules: [`../../AGENTS.md`](../../AGENTS.md)
- PR 0 DoD: [`PR0_DEFINITION_OF_DONE.md`](PR0_DEFINITION_OF_DONE.md)
- Module boundaries: [`../MODULE_BOUNDARIES.md`](../MODULE_BOUNDARIES.md)

**MVP stack:** Android-first native Kotlin. `:domain` portable kernel. `:persist` Room EventLog (PR 3). Cloud STT/LLM behind ports. HUD pad when offline. No MVP sync server. Conversation is unrestricted; only committed `WorkoutEvent` is persisted.

## Layout

```
docs/living-spec/
  README.md
  TECHNICAL_SPEC.md
  CORE_LOOP.md
  PR0_DEFINITION_OF_DONE.md
  contracts/          28 feature contracts + _TEMPLATE.md
  events/             CATALOG.md, EXAMPLES.md
  schemas/v1/         frozen JSON Schema draft 2020-12
  state-machine/      CONTRACT.md
  voice/              PORTS.md
  safety/             DECISION_TABLE.md
  SAFETY.md           templates + implementation note
  testing/            STRATEGY.md, CASES.md
  conversation/       NATURAL_SPEECH.md
  context/            ASSEMBLY.md
  boundaries/         LLM_VS_APPLICATION.md, PR1_VS_PR2.md
```

If a split file and `TECHNICAL_SPEC.md` disagree, fix the split file unless `PRODUCT.md` changed. `PRODUCT.md` wins on product behavior.

## Kernel

See `CORE_LOOP.md`. Dashboard is secondary and **not implemented**. PR 0–9 are the headless companion kernel.
