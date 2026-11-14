# Changelog

## 0.1.0 — first version

- Added the network-level soft dynamics solver: overloaded networks decelerate to
  `ω_eq = C / K` instead of being hard-stopped by Create's overstress flag.
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
