# Plugin Hub readiness

- `build=standard`
- `latest.release` RuneLite development target
- No additional runtime dependencies
- No Lombok / annotation-processor dependency
- No third-party network communication
- No reflection or native code
- No direct credential access
- No custom filesystem audit logging
- Standard RuneLite `PluginPanel` sidebar width
- BSD-2-Clause license

RuneLite still reviews every initial submission/update for security and game-rule compliance. Automated approval cannot be guaranteed; this repository is structured to remain compatible with the expedited standard-build path.

- Detailed planner tracing is opt-in and disabled by default.
- Config UI changes marshal client-state access onto RuneLite ClientThread.
