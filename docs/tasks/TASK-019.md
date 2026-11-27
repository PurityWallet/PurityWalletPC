# TASK-019: Keep the PWC backend free of waits

## Scope

- [x] Add REQ-017, amend REQ-011 and REQ-014, and synchronize the specification, architecture, API contract and task index.
- [ ] Write failing regression tests for the backend without waits, the replay clearance, device signatures in the prompt flow, broadcast timeouts and error mapping.
- [ ] Publish an immutable wallet and connection view from the JavaFX thread and read only it in the backend.
- [ ] Move device signing into the PSBT prompt flow; bind kept approvals to their request and expire them.
- [ ] Record replay clearances in the signing prompt and require them for PWC broadcasts on mainnet and signet.
- [ ] Bound the broadcast, map its failures and wrap every backend method.
- [ ] Upgrade the SDK to 0.2.1.
- [ ] Run the regtest end-to-end flow with the pwc-spec Python client.

## Behavior

See REQ-017 and the "PWC backend without waits" section of the architecture.

## Validation

Pending.
