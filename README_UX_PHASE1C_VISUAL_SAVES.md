# RPG Creators — UX Phase 1C (v11)

Changes from v10:

- LAN navigation menus (including Notes, Journal, Skills) scroll horizontally on narrow portrait phones. No manual rotation needed to reach them.
- The LAN map uses landscape orientation only while its fullscreen map view is displayed. Notes, character views and skill tree request portrait; leaving the LAN flow explicitly restores portrait.
- Notes retain editor fields when the device changes orientation and the empty draft is saved early enough to survive Activity recreation. Notes remain private on the player's device.
- Tree rendering is clipped to its own viewport; the prior pan bounding calculation remains active.
- Per-character, per-map position writes are serialized to avoid lost updates during rapid map transitions. The existing fog persistence remains authoritative per map and is not reset in this update.
- Players still join rooms directly: **no approval mechanism** was added.

## Test checklist on real devices

1. On a phone in portrait, open LAN and verify that all navigation chips are reachable by horizontal swiping.
2. Enter fullscreen map: landscape. Open Notes or Skill Tree: portrait. Return to map: landscape. Exit the network screen: portrait.
3. Create a note, type without saving, rotate, confirm text stays. Save and restart app.
4. Drag/zoom the talent tree to all four edges: outside nodes must not draw beyond the tree viewport.
5. In a room, move to the edge of map A, travel to map B, move again, then return to A and reconnect with the same character. Verify both positions.
6. Erase some fog on A, use fog on B, switch and reconnect. Verify masks and revealed areas on both maps.
7. Put a map into preparation, disconnect and reconnect a player, publish again. Verify the player is returned to the saved position.

Full Gradle/Android build was not run in this environment. This ZIP is source code for Android Studio testing, not a verified APK.
