# Third-party references

This project is BSD 2-Clause licensed. Its behavior and architecture are informed by public RuneLite / Plugin Hub implementations.

## RuneLite core Wintertodt plugin

Portions of Wintertodt activity/interruption behavior are adapted from RuneLite core. The applicable RuneLite copyright notices are retained in the relevant source file and RuneLite is BSD 2-Clause licensed.


## RuneLite core Status Bars / Regeneration Meter

The v0.2.17 hovered-consumable Warmth preview follows RuneLite core's public inventory-menu/item-composition patterns but intentionally does not depend on the separately-bound Item Stats service. The Warmth regeneration ring follows the 100-game-tick regeneration cadence and resynchronization approach used by RuneLite core Regeneration Meter. RuneLite is BSD 2-Clause licensed; no credentials, account data, or private RuneLite state are read.

## Wintertodt Solo Helper

The four-station widget approach, station world locations, and historical Wintertodt status sprite identifiers used as a **validated input map** by `WintertodtStationMonitor` are based on the Plugin Hub project **Wintertodt Solo Helper** at commit `c5761f0224c834b79d76c0f1b25b7ce4964a2211`.

Wintertodt Solo Helper is BSD 2-Clause licensed:

Copyright (c) 2023, AprilHT

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the copyright notice, conditions, and disclaimer are retained as required by the BSD 2-Clause license.

The v0.2.17 implementation uses the current live-cache component layout (pyros 7–10, braziers 11–14) and validates the live widget states every game tick. It suppresses numerical forecasts on unknown values. The alive-pyromancer null-sprite behavior and the 1397/1398/1399 brazier states are also checked against live observations/audit data rather than assumed blindly.

## Current interface research reference

The v0.2.17 component indices were cross-checked against the current `wint_status` interface dump in `Joshua-F/osrs-dumps` (pyros 7–10, braziers 11–14, Warmth 15–20, Energy 21–26). No source code from that dump is redistributed in this project.

## Other design references

- Jodelahithit `Wintertodt Notifications` — visual idle notification concepts
- Ryan `Wintertodt Idle Plugin` — full-screen idle overlay concept
- graceepants `Warmth Alert` — warmth threshold alert concept
- Custom Vital Bars — movable/snappable meter customization concepts


## Wintertodt Solo Helper BSD 2-Clause text

Copyright (c) 2023, AprilHT

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
