# Wintertodt+

Wintertodt+ is a RuneLite Wintertodt HUD with a live strategy coach.

## Features
- Movable Warmth and Wintertodt Energy meters.
- Live coach with **Max FM XP** and **Max Points** goals.
- Points, inventory potential, and round-state tracking.
- Intentional, priority-aware screen alerts and RuneLite notifications.
- Standard-width RuneLite sidebar with compact nested accordion controls.
- Optional live diagnostics in the Debug accordion.

## Sidebar
Wintertodt+ uses RuneLite's normal sidebar width and native LookAndFeel. All major and nested sections start collapsed.

## Moving HUD elements
Hold **Alt** and drag movable Wintertodt+ HUD pieces.

## Privacy and network access
Wintertodt+ does not communicate with third-party servers. It does not read credentials or write custom audit files. Structured development diagnostics use RuneLite's normal debug logger.

## Plugin Hub build
`runelite-plugin.properties` uses `build=standard`. The plugin has no additional runtime dependencies or custom Plugin Hub build steps.

## Development
Java 11 is required. The local Gradle build follows RuneLite's current example-plugin layout and targets `latest.release`.


## Diagnostics
Detailed planner tracing is OFF by default. Normal development launch does not enable RuneLite debug logging. Use the separate debug launcher and enable **Planner trace** only when collecting a trace.
