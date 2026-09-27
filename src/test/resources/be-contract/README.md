# caseflow-be contract fixtures

Request bodies exactly as `caseflow-be`'s `CaseflowAiClient` serializes them. `BeContractTest` proves
this service accepts every one of them (no 400 from bean validation).

These files are copies of `caseflow-be/src/test/resources/ai-contract/requests/`, where
`CaseflowAiClientContractTest` asserts that BE produces exactly this JSON. When either side changes a
DTO, update both copies in the same change — see `caseflow-central-brain/tasks/active/AI-001-*.md`.
