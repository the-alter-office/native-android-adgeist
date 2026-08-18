# Storing the ad response instead of keeping the WebView alive

**Words used a lot below**

- **Rotation / config change** — the user rotates the phone, switches to dark mode, changes
  font size, enters split screen. Android throws the screen away and builds a new one.
- **Routing** — the user navigates to another screen and comes back.
- **App killed** — Android shuts down the whole app in the background to free memory.
- **Parking** — what the SDK does today: keep the live WebView in memory when the view is
  destroyed, and move it into the new screen.

---

## 1. Idea, in short

Today the SDK keeps the **whole live WebView** in memory and moves it into the new screen. Idea is to stop doing that:

- When an ad loads, store the ad response (creative + adspace info) and whether the
  impression and click were sent.
- When the screen comes back after a rotation or a navigation, read that back, **build a new
  WebView and show the ad again**.
- Check the sent-flags and send only the analytics that weren't sent yet.
- When the screen really leaves the back stack, drop it.

Expect three wins:

1. A publisher can't use the same adspace twice on one screen.
2. Analytics never get counted twice.
3. Rotation and navigation don't cost a new ad request.

### The scope

- **Ads only need to survive rotation and routing.** If Android kills the app, the ad is gone
  and the next launch fetches a fresh one. That's a product decision, not a limitation.
- **The one thing on disk is the analytics retry queue** — impression and click payloads that
  failed to send because the internet was off, replayed when it comes back.
- **That queue is for analytics events only, never ad requests.** An ad request is only worth
  something while the slot is actually on screen; replaying one later would fetch an ad nobody
  sees, into a screen the user has already left. The code already works this way: only
  `CreativeAnalytics` feeds the queue
  ([line 45](adgeistkit/src/main/java/com/adgeistkit/data/network/CreativeAnalytics.kt#L45)),
  and a failed ad request reports the error and stops
  ([FetchCreative.kt:103-106](adgeistkit/src/main/java/com/adgeistkit/data/network/FetchCreative.kt#L103-L106)).
  Keep it that way.

### First conclusion: the ad store belongs in memory, not SQLite

"for store we can use sqlite". With this scope, it shouldn't be.

Surviving the app being killed is the **only** thing a database gives you over a plain
in-memory map. The ad state doesn't need that. So SQLite for the ad response is all cost and
no benefit: a disk read on the exact path where the ad has to appear (or rewriting `loadAd` to
be asynchronous), a cleanup pass and row limits to stop rows piling up, schema upgrades and
corrupt-database handling — and rows you'd have to delete at startup anyway, because if they
*were* used you'd be restoring an ad after an app kill, which you don't want.

| What | Where | Why |
|---|---|---|
| Ad response + sent-flags | **Memory** | Only has to survive rotation and routing, which memory already does |
| Analytics retry queue | **Disk / SQLite** | Must survive the app being killed — that's its whole purpose |
| Failed ad requests | **Nowhere — not retried** | Only worth something while the slot is on screen |

The rest of this document assumes that.

### What the code does today

| Thing | Today | Where |
|---|---|---|
| What gets kept | The **live WebView** + its JS bridge | [AdSession](adgeistkit/src/main/java/com/adgeistkit/ads/session/AdSessionStore.kt#L21-L35) |
| On rotation / coming back | The same WebView is **moved** into the new screen | [adoptSession](adgeistkit/src/main/java/com/adgeistkit/ads/BaseAdView.kt#L285-L324) |
| When the screen is really finished | `onCleared()` destroys everything for that screen | [AdSlotToken](adgeistkit/src/main/java/com/adgeistkit/ads/identity/AdSlotToken.kt#L22-L25) |
| Double-count guard | Flags on one long-lived `AdActivity` object in memory | [AdActivity](adgeistkit/src/main/java/com/adgeistkit/ads/tracking/AdActivity.kt#L45-L48) |
| Failed analytics sends | **In memory only**, max 200, lost if the app dies | [AnalyticsRetryQueue](adgeistkit/src/main/java/com/adgeistkit/data/network/AnalyticsRetryQueue.kt#L33) |
| Same adspace twice on a screen | Blocked already, before any network call | [claimSlot](adgeistkit/src/main/java/com/adgeistkit/ads/session/AdSessionStore.kt#L94-L101) |
| Off-screen ad limit | Oldest thrown away past 3 | [enforceParkedCap](adgeistkit/src/main/java/com/adgeistkit/ads/session/AdSessionStore.kt#L166-L179) |

---

## 2. What it really fixes

### 2.1 Real win: the analytics retry queue must be on disk

This is a live bug, and the most valuable part of idea.

`AnalyticsRetryQueue` keeps failed sends **in memory**, max 200
([lines 29-33](adgeistkit/src/main/java/com/adgeistkit/data/network/AnalyticsRetryQueue.kt#L29-L33)).
Every impression and click waiting there is **silently lost if the app dies** — publisher money
and advertiser data gone, with no trace. Someone on a train with patchy signal loses their
events the moment Android reclaims the app.

Work for this already exists: `stash@{0}` ("sqlite retry mechanism") plus commit `6a6f9bb`,
which add `AnalyticsRetryQueueStore.kt` and a `queued_requests` table using plain
`SQLiteOpenHelper`. **So the SDK's established way of doing SQLite is plain `SQLiteOpenHelper`,
not Room.** Land it.

### 2.2 Real win: memory

Today up to 3 off-screen WebViews stay fully alive. Each one is a real browser page with its
own memory, images and video decoder — realistically 5 to 20 MB. On a cheap phone or a deep
back stack that's the biggest cost of the current design. And the limit is crude: past 3, the
oldest is thrown away, so a user can lose the ad on their 4th screen for no good reason.

An ad response in memory is a few kilobytes. **This is the strongest point in idea, and
you didn't list it.**

### 2.3 Real win: no risk of holding on to a dead screen

The current code is careful only because it holds live objects. It swaps the WebView's
`Context` to the app context when parking
([line 127](adgeistkit/src/main/java/com/adgeistkit/ads/session/AdSessionStore.kt#L127)) and
back to the new screen when restoring
([lines 291-292](adgeistkit/src/main/java/com/adgeistkit/ads/BaseAdView.kt#L291-L292)), keeps
slot claims weak, and clears `hostView` / `hostActivity` at exactly the right moments.
`adgeist-sdk-code-review.md` already flags this as leak-shaped risk. Plain data can't hold on
to a dead screen.

### 2.4 Not fixed: "a publisher can't use the same adspace twice on one screen"

1. **It already works, and storage isn't why.** `claimSlot` plus
   [performLoad](adgeistkit/src/main/java/com/adgeistkit/ads/BaseAdView.kt#L165-L174) already
   fail the second slot through `onAdFailedToLoad`, before any network call.
2. **A store can't answer this question.** It's about what's alive *right now* — "is another
   AdView for this unit on screen at this moment?" The code answers that with a weak reference
   plus `isAttachedToWindow`. A stored record has no idea what's alive.
3. **The across-screens version was removed on purpose.** Commit `1b90d46`;
   `AD_LIFECYCLE.md` lines 114-128 explain that a check inside the SDK the publisher ships
   can't stop anyone determined, so it's handled by contract now.

### 2.5 Not fixed: "no new ad on rotation and navigation"

**This already works today, and rebuilding is worse at it.** See point 3.1 — this is the main
objection.

### 2.6 Already solved, in the right place: the sent-flags

`AdActivity` already holds this state in memory: `hasViewEvent` for the impression,
`lastClickTime` for clicks
([lines 45-48](adgeistkit/src/main/java/com/adgeistkit/ads/tracking/AdActivity.kt#L45-L48)).
Because the same object is carried across rotation and routing, an ad that comes back never
fires a second impression. That is your "analytics status" — already built, already in memory,
already surviving exactly the cases you care about. Point 3.2 covers why replacing it with two
booleans would be a step backwards.

---

## 3. What's still a problem

### 3.1 Rebuilding the WebView looks worse than moving it — the main objection

Today restoring an ad costs one `removeView` and one `addView`. Rebuilding means, on every
rotation: read the stored response, create a WebView, load the HTML, wait for the page and its
JavaScript. That gives you:

- **An empty box, then a flash.** Creating a WebView and loading the page takes tens to
  hundreds of milliseconds. The ad slot is visibly blank during a rotation instead of just
  reappearing.
- **The creative is downloaded again.** Teardown calls **`clearCache(true)`**
  ([AdWebViewTeardown.kt:32](adgeistkit/src/main/java/com/adgeistkit/ads/render/AdWebViewTeardown.kt#L32)),
  which wipes the WebView cache for the whole app. So a rebuild won't find the image or video
  in cache — it downloads it every time. **You save one small ad request and pay for a full
  media download.**
- **Video restarts from zero**, and anything the user was doing inside the ad is lost.
- **Teardown isn't free either.** Destroying a WebView happens in stages with a 600 ms wait
  before the final destroy, and `performLoad` already waits 400 ms after a cleanup before
  loading again
  ([lines 200-208](adgeistkit/src/main/java/com/adgeistkit/ads/BaseAdView.kt#L200-L208)).
  Rotating back and forth would leave a pile of half-destroyed WebViews.

This cost is the same wherever you store the response. It's the price of rebuilding instead of
moving.

### 3.2 Two things the disk queue needs

- **A unique id per event.** Once failed sends survive an app kill, some will reach the server
  twice — a send can succeed on the server and still fail on the way back, so the client
  retries something that already landed. `metaData + type` can't tell that apart from a real
  second click. Give each queued event its own id and have the **server ignore repeats of the
  same id**. Retrying is only safe if the server is ready for it.
- **Delete the queue when consent is withdrawn.** Once events sit on disk between app runs,
  that becomes a real obligation. (The code review already flags "consent is stored but never
  enforced" as Critical.)

---

## 4. Side by side

Comparing the two ways of surviving a rotation, with both keeping their data in memory:

| | Today: move the live WebView | Idea: rebuild from the stored response | Better |
|---|---|---|---|
| Ad request on rotation | None | None | Tie |
| Media downloaded on rotation | **None** | Downloaded again (cache is wiped on teardown) | **Today** |
| What the user sees | Ad just reappears | Empty box, then a flash; video restarts | **Today** |
| Memory for off-screen ads | 5-20 MB each, max 3 | A few KB, no limit needed | **Idea** |
| Risk of holding a dead screen | Real, managed by careful code | Impossible | **Idea** |
| Double-count guard | Works, on `AdActivity` | Works, if kept per event and not flattened | Tie |
| More than one click per ad | Works | Broken by a `click_sent` flag | **Today** |
| Same adspace twice on a screen | Works | No better | Tie |
| Cost on the main thread | Two map reads | Two map reads | Tie |
| Work to build | Already done | Rewrites the render path | **Today** |

**Reading the table:** Idea wins on **memory** and **leak safety**. It loses on **how the
ad looks and behaves, and on bandwidth**. The two things it was meant to win — blocking
duplicate adspaces and avoiding a new ad request on rotation — it doesn't win, because both
already work.

---

## 5. What to do

### Level 1 — Put the analytics retry queue on disk (Idea, and it's half built)

Land `stash@{0}` / `6a6f9bb`. Then give each queued event a unique id, an explicit state
(waiting / sending / sent / failed), an attempt count and a next-attempt time. Write the row
**before** the network call and mark it sent on success, so nothing is lost if the app dies
mid-send. Replay when the internet comes back and on `AdgeistCore.initialize`. Ask the backend
to ignore repeats of the same id. Delete the table when consent is withdrawn.

**Impressions and clicks only.** Leave `FetchCreative` exactly as it is — a failed ad request
reports the error and stops. Don't let ad requests into this queue, now or later.

This is the highest-value change on this page.

### Level 2 — Keep moving the live WebView for rotation and routing

Don't replace this. It's instant, costs no bandwidth, and keeps video playing. Rebuilding
would be a visible downgrade on the most common path (point 3.1).

### Level 3 — Keep the ad response in memory as a fallback

This is the idea, used where it actually helps: **when there is no WebView to move.**

- The 4th screen, whose parked WebView gets thrown away today by the limit of 3.
- When the system reports memory pressure (`onTrimMemory`) and dropping WebViews is right.
- Screens with no ViewModel, which today fall back to a 60-second timer.

In those cases the SDK currently fetches a **whole new ad**. With the response in memory it can
rebuild instead — no ad request, and the double-count guard still applies because `AdActivity`'s
state is in memory too. So you get your memory win as a safety net, and rotation stays instant.

Read `expiresAt` / `campaignValidity` / `frontendCacheDurationSeconds` when doing this. They
already exist in the response and nothing reads them today, and an app can sit in the
background for hours before the user comes back.

### Level 4 — Make the rebuild look better, only if level 3 ships

Keep a spare blank WebView warm so creating one is fast, and stop `clearCache(true)` from
wiping the app-wide WebView cache on teardown so the media is actually cached. **The second one
is worth doing regardless** — right now it punishes every WebView reload in the publisher's
app, not just ads.

### Don't do

- **Don't put the ad response in SQLite.** With app-kill survival out of scope, disk buys
  nothing and costs a lot (section 1).
- **Don't persist the sent-flags.** Same reason, and they'd let a restored ad post an impression
  for a render the user never saw.
- **Don't flatten analytics state into two booleans** (point 3.2).
- **Don't try to block duplicate adspaces with a store** (point 2.4).
- **Don't put ad requests in the retry queue.** Impressions and clicks only.

---

## Which files would change

**Level 1 (the analytics retry queue):** `data/network/AnalyticsRetryQueue.kt`,
`data/network/CreativeAnalytics.kt`, `request/AnalyticsRequest.kt` (add the event id), new
`data/local/*` files from `6a6f9bb`, `AdgeistCore.kt` (replay on init, delete on consent
withdrawal).

**Level 3 (response kept in memory):** `ads/session/AdSessionStore.kt` (hold a response-only
record alongside live sessions), `ads/BaseAdView.kt` (`performLoad` tries a live session, then
a stored response, then a fetch), plus reading the expiry fields from
`data/models/CreativeDataModel.kt`.

**Level 4:** `ads/render/AdWebViewTeardown.kt` (stop wiping the app-wide cache),
`ads/render/AdWebViewFactory.kt` (a warm spare WebView).

See `AD_PERSISTENCE_OPTIONS.md` for the other ways of doing level 2 and level 3.
