# TASK-020: Limit PWC authorization to the running service

REQ-014 requires applications to pair and request access each time the PWC service starts. SDK 0.2.1 can acknowledge a client-initiated revocation before the host saves a snapshot. Keeping pairings and grants in memory prevents an older copy from restoring revoked access. The wallet keeps the owner-only ID salt so wallet and account IDs stay stable.

The LOCAL integration test checks that a restart rejects the old pairing credentials, starts without grants and requires a new grant after a fresh pairing. The service tests check that stopping the service writes no authorization snapshot and that a legacy snapshot is retired when the service starts.
