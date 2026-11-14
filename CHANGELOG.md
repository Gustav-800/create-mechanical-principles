# Changelog

## 0.1.0 — first version

- Hot-plugging a component into a running network no longer forces the whole
  network back to nominal speed before re-evaluating stress. The solver keeps
  the current soft speed and only recalculates K/C.
- Clutch now has a real speed ramp after the engage delay. The output side is
  attached, captured, scaled down, and ramped back to full using
  `clutchAccelFraction`; the input side is not touched.
- Removed the sequenced gearshift zero-output startup gate. It prevented the
  output side from being reattached by Create, so the gearbox could not transmit
  power. Each step now reattaches at the new ratio and ramps the output members;
  Create's instruction timer remains authoritative.
- Fixed sleeping networks ignoring a freshly added load (the hot-plug
  transition appeared to have no dynamic acceleration).
- Overloaded networks now snap to exactly 0 RPM instead of sleeping at a tiny
  residual such as `1.19e-4 RPM`, so the stress meter reads zero and the
  original overstress condition is visually consistent. Recovery is preserved:
  the manager wakes on load/topology changes and ramps the source back up.
- Gearshift now performs a real stop-then-ramp reversal on the output side, in
  addition to the zero-output transition window.
- Clutch ramp default raised from `0.15` to `0.35` (about 3 ticks to full); the
  value is in the save's `serverconfig/create_mechanical_principles-server.toml`
  under `[components] clutchAccelFraction`.
- Version changed to 0.1.0.
- Added overload latching/hysteresis, stall entry/recovery, acceleration ceilings
  and a minimum acceleration floor.
- Preserved each member's nominal speed in `SoftDynamicsNominalSpeed` NBT while
  writing the throttled value to Create's normal `Speed` field.
- Added topology-versioned C/K caches, sleeping, a per-tick active-network budget
  and explicit client block-entity updates.
- Added hot-plug admission for placements against a running network, including
  conflicting-network detection and configurable RPM threshold.
- Added first-version safe component transitions:
  - clutch engage delay;
  - gearshift zero-output transition before reversing;
  - sequencer zero-output startup gate;
  - rotation speed controller output throttling under overload.
- Added `/cmp status`, `/cmp reload`, `/cmp preset` commands.
- Added NeoForge common/server config with REALISTIC/BALANCED/ARCADE presets.
- Added unit tests for the solver, planning math and settings clamping.
- Added README, MIT license, icon and GitHub Actions build/release workflow.
