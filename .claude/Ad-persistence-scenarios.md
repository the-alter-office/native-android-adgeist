# Scenarios That Must Not Destroy the Ad

| # | Scenario | Expected behavior |
|---|----------|-------------------|
| 1 | Navigate to next screen (this screen pushed onto the back stack, not popped) | Ad paused, session parked; adopted when the screen returns |
| 2 | Screen rotation | Session parked, not destroyed; the recreated screen adopts the live WebView |
| 3 | Warm start (app backgrounded, process alive, then foregrounded) | Ad paused on background, resumed on foreground; never destroyed |
| 4 | Split-screen / multi-window enter, exit, or resize | Parked and re-adopted, same as rotation |
| 5 | Other config changes: dark/light theme, font scale, density, keyboard attach, locale | Parked and re-adopted |
| 6 | Foldable fold/unfold, multi-window drag-resize | Parked and re-adopted |
| 7 | Picture-in-Picture enter/exit | Ad keeps running, unaffected |
| 8 | Transient interruptions: incoming call, permission dialog, notification shade | Ad paused, resumed on return |
| 9 | RecyclerView / ViewPager2 scroll — the ad view detaches and reattaches without its screen being destroyed | Ad paused off-screen, resumed when scrolled back |
| 10 | The same screen class on the back stack more than once (two product pages), or several tabs of one fragment | Each instance gets and keeps **its own** ad |
