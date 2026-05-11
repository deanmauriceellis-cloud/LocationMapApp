#!/usr/bin/env python3
"""
Build the V1 Launch Review intake .odt document.

Generated once-off for S189 from the post-S188 total-review synthesis.
The .odt is structured so the operator can open it in LibreOffice,
fill in "Operator Response" blocks after each actionable item, and
hand it back as the intake for next-steps planning.
"""

from odf.opendocument import OpenDocumentText
from odf.style import (
    Style, TextProperties, ParagraphProperties, TableColumnProperties,
    TableProperties, TableCellProperties, PageLayout, PageLayoutProperties,
    MasterPage,
)
from odf.text import H, P, Span, List, ListItem, ListStyle, ListLevelStyleBullet
from odf.table import Table, TableColumn, TableRow, TableCell

doc = OpenDocumentText()


# ---------- styles ----------

def _normalize(props):
    """odfpy element attributes use no underscores; convert dict keys."""
    if not props:
        return {}
    return {k.replace("_", ""): v for k, v in props.items()}


def add_text_style(name, parent=None, text_props=None):
    s = Style(name=name, family="text")
    if parent:
        s.setAttribute("parentstylename", parent)
    s.addElement(TextProperties(**_normalize(text_props)))
    doc.styles.addElement(s)
    return s


def add_para_style(name, parent=None, text_props=None, para_props=None):
    s = Style(name=name, family="paragraph")
    if parent:
        s.setAttribute("parentstylename", parent)
    if text_props:
        s.addElement(TextProperties(**_normalize(text_props)))
    if para_props:
        s.addElement(ParagraphProperties(**_normalize(para_props)))
    doc.styles.addElement(s)
    return s


# Headings
add_para_style("H1", text_props={"fontsize": "22pt", "fontweight": "bold", "color": "#1a1a2e"},
               para_props={"marginbottom": "0.15in", "margintop": "0.30in", "breakbefore": "page"})
add_para_style("H1First", text_props={"fontsize": "22pt", "fontweight": "bold", "color": "#1a1a2e"},
               para_props={"marginbottom": "0.15in", "margintop": "0.05in"})
add_para_style("H2", text_props={"fontsize": "16pt", "fontweight": "bold", "color": "#16213e"},
               para_props={"marginbottom": "0.10in", "margintop": "0.25in"})
add_para_style("H3", text_props={"fontsize": "13pt", "fontweight": "bold", "color": "#0f3460"},
               para_props={"marginbottom": "0.06in", "margintop": "0.18in"})
add_para_style("H4", text_props={"fontsize": "11pt", "fontweight": "bold", "color": "#222"},
               para_props={"marginbottom": "0.04in", "margintop": "0.12in"})

# Body
add_para_style("Body", text_props={"fontsize": "11pt", "color": "#1a1a1a"},
               para_props={"marginbottom": "0.08in", "lineheight": "120%"})
add_para_style("Bullet", text_props={"fontsize": "11pt"},
               para_props={"marginbottom": "0.04in", "marginleft": "0.25in", "textindent": "-0.18in"})
add_para_style("SubBullet", text_props={"fontsize": "11pt"},
               para_props={"marginbottom": "0.03in", "marginleft": "0.50in", "textindent": "-0.18in"})

# Severity / status banners
add_para_style("Critical", text_props={"fontsize": "11pt", "fontweight": "bold", "color": "#a02020"},
               para_props={"marginbottom": "0.04in", "marginleft": "0.0in", "padding": "0.04in",
                           "backgroundcolor": "#ffe3e3"})
add_para_style("Warning", text_props={"fontsize": "11pt", "fontweight": "bold", "color": "#9c5a00"},
               para_props={"marginbottom": "0.04in", "padding": "0.04in", "backgroundcolor": "#fff4e0"})
add_para_style("Good", text_props={"fontsize": "11pt", "fontweight": "bold", "color": "#1f6f3a"},
               para_props={"marginbottom": "0.04in", "padding": "0.04in", "backgroundcolor": "#e3f4e7"})

# Operator-response block
add_para_style("ResponseLabel", text_props={"fontsize": "10pt", "fontweight": "bold",
                                              "color": "#5a4a8a", "fontstyle": "italic"},
               para_props={"margintop": "0.10in", "marginbottom": "0.03in"})
add_para_style("ResponseBox", text_props={"fontsize": "11pt", "color": "#000080", "fontstyle": "italic"},
               para_props={"marginbottom": "0.18in", "marginleft": "0.10in",
                           "padding": "0.08in", "backgroundcolor": "#f0eef9",
                           "borderleft": "0.05in solid #5a4a8a"})

# Inline
add_text_style("Bold", text_props={"fontweight": "bold"})
add_text_style("Italic", text_props={"fontstyle": "italic"})
add_text_style("Code", text_props={"fontname": "DejaVu Sans Mono", "fontsize": "10pt",
                                    "backgroundcolor": "#f0f0f0"})
add_text_style("Red", text_props={"color": "#a02020", "fontweight": "bold"})
add_text_style("Green", text_props={"color": "#1f6f3a", "fontweight": "bold"})
add_text_style("Amber", text_props={"color": "#9c5a00", "fontweight": "bold"})

# Page
pl = PageLayout(name="StdLayout")
pl.addElement(PageLayoutProperties(pagewidth="8.5in", pageheight="11in",
                                     margintop="0.7in", marginbottom="0.7in",
                                     marginleft="0.8in", marginright="0.8in"))
doc.automaticstyles.addElement(pl)
mp = MasterPage(name="Standard", pagelayoutname="StdLayout")
doc.masterstyles.addElement(mp)


# ---------- helpers ----------

def heading(text, level, first=False):
    style = "H1First" if (level == 1 and first) else f"H{level}"
    h = H(outlinelevel=level, stylename=style, text=text)
    doc.text.addElement(h)


def para(text, style="Body"):
    p = P(stylename=style)
    p.addText(text)
    doc.text.addElement(p)


def rich_para(parts, style="Body"):
    """parts is a list of (text, span_style_name|None) tuples."""
    p = P(stylename=style)
    for text, span_style in parts:
        if span_style:
            sp = Span(stylename=span_style, text=text)
            p.addElement(sp)
        else:
            p.addText(text)
    doc.text.addElement(p)


def bullet(text, sub=False):
    style = "SubBullet" if sub else "Bullet"
    p = P(stylename=style)
    p.addText("• " + text)
    doc.text.addElement(p)


def rich_bullet(parts, sub=False):
    style = "SubBullet" if sub else "Bullet"
    p = P(stylename=style)
    p.addText("• ")
    for text, span_style in parts:
        if span_style:
            p.addElement(Span(stylename=span_style, text=text))
        else:
            p.addText(text)
    doc.text.addElement(p)


def banner(text, kind="Critical"):
    p = P(stylename=kind)
    p.addText(text)
    doc.text.addElement(p)


def response(prompt="Operator Response:"):
    """Insert a labelled response block."""
    lbl = P(stylename="ResponseLabel")
    lbl.addText(prompt + "  (replace this block with your notes)")
    doc.text.addElement(lbl)
    box = P(stylename="ResponseBox")
    box.addText("[ your comments here ]")
    doc.text.addElement(box)


def spacer():
    doc.text.addElement(P(stylename="Body"))


# =====================================================================
# DOCUMENT
# =====================================================================

heading("WickedSalemWitchCityTour — V1 Launch Review", 1, first=True)
rich_para([
    ("Prepared: ", None),
    ("2026-04-27 (S189 open)", "Bold"),
    ("  ·  Source synthesis: post-S188 total-review (CLAUDE.md, STATE.md, ", None),
    ("docs/session-logs/", "Code"),
    (" 184–188, GOVERNANCE.md, IP.md, COMMERCIALIZATION.md, PRIVACY policies, OMEN notes & directives).", None),
])
rich_para([
    ("This is an ", None),
    ("intake document", "Bold"),
    (". Each section has an ", None),
    ("Operator Response", "Italic"),
    (" block. Replace the placeholder text with your decisions, deferrals, or follow-up questions. The annotated copy becomes the input to S190+ planning.", None),
])
spacer()

# ----- Timeline anchors -----
heading("Timeline Anchors (from today, 2026-04-27)", 2)
rich_bullet([("Form TX copyright deadline (2026-05-20): ", None),
             ("23 days", "Red")])
rich_bullet([("Sept 1, 2026 ship target: ", None),
             ("127 days", "Amber")])
rich_bullet([("Salem 400+ peak (Oct 2026): ", None),
             ("~6 months", "Amber")])
rich_bullet([("Counsel meeting 2026-04-20: ", None),
             ("7 days ago — outcome unconfirmed", "Red")])
rich_bullet([("OMEN-004 first Kotlin unit test deadline: ", None),
             ("2026-08-30 (~4 months)", None)])

response("Operator note on timeline (anything to revise?):")

# =====================================================================
# 1. EXECUTIVE SNAPSHOT
# =====================================================================
heading("1. Executive Snapshot", 1)

para("You have a shippable product. A signed 78 MB AAB sits at app-salem/build/outputs/bundle/release/app-salem-release.aab and an 87 MB APK runs on the Lenovo at 60 fps with no AndroidRuntime errors. Current asset bundle is healthy: 88 MB total (30 MB tiles, 9 MB content DB, 3.4 MB icons) — NOT the 544 MB still cited in CLAUDE.md and STATE.md, which is stale doc-text from before S167.")

para("The launch is gated by three things, none of them code:")
bullet("Operator-side legal & business plumbing (Form TX, Play Console account, privacy policy hosting)")
bullet("Data cleanup the lint tab now surfaces but no one has actually executed (365 dedup losers, 500+ missing years, 30 silent civic POIs, 119 missing images, 75 outlier coords)")
bullet("Eyes-on smoke verification of the V1 feature gates that R8 supposedly stripped — claimed working in S180 but the carry-forward list still says these are owed: POI detail Visit Website handoff, Find Reviews/Comments hidden, webcam View-Live hidden, toolbar gating")

para("The recent session arc (S185 → S188) is admin-tool polish, not user-facing app work. S187 and S188 shipped zero Android changes. Defensible — you're building tooling to fix the data — but it means the binary on the Lenovo is still the S186 build, and every day in admin-land is a day not spent on smoke test, content backfill, or store submission prep.")

response("Do you agree with this framing? Anything to push back on?")

# =====================================================================
# 2. STRENGTHS
# =====================================================================
heading("2. What's Genuinely Strong", 1)

strengths = [
    ("Module structure",
     "core / app / app-salem / salem-content / routing-jvm / cache-proxy / web. Clean, no circular deps. Lets V2 features land without rewrites."),
    ("V1 offline posture is real, not aspirational",
     "Manifest stripped of INTERNET / ACCESS_NETWORK_STATE. R8 + const dead-code-eliminates gated features. App is network-incapable at the OS level. Eliminates a whole class of Play Store data-safety risk."),
    ("Walking router unification (S179)",
     "47,704-edge bundled walking graph; single Router.route() path for both tour polylines and Find→Directions; bake-time edge-splitting brings POI snap distances ~95 m → ~30 m. Operator-confirmed working on Lenovo."),
    ("Lint tab + Geocodes modal (S187/S188)",
     "15 instant data-quality checks + on-demand Tiger geocode validation, smart conflict analyzer, in-map preview with accept/ignore/blacklist persistence. Most paid apps don't have this much QA infrastructure."),
    ("Asset publish chain is recoverable, not just functional",
     "S185 exposed two latent landmines (DEFAULT clauses, stale user_version) that would have wiped the asset on every install in production. align-asset-schema-to-room.js is now the canonical bridge."),
    ("Witchy-only basemap + server-side auto-overzoom (S188)",
     "sharp-backed crop/resize from the bundled tile pyramid. Never need a network fallback for missing zooms — defensible offline claim."),
    ("Tour Mode narration gate (S186)",
     "Pre-S186 the app silently narrated everything during tours. is_tour_poi baseline + Layers checkbox overrides + 1-hour dedup persistence across walk-sim restarts is the right shape. Operator-confirmed working: 'good enough.'"),
    ("First signed AAB exists on a real device",
     "versionCode 10000, signing config wired from ~/.gradle/gradle.properties, upload keystore registered in OMEN credential audit. You've actually built for the Play Store, not guessed at it."),
]
for title, body in strengths:
    rich_para([(title + ".", "Bold"), (" " + body, None)], style="Bullet")

response("Anything you'd add or remove from the strengths list?")

# =====================================================================
# 3. CRITICAL CONCERNS
# =====================================================================
heading("3. Critical Concerns (Launch-Blocking, Sorted by Severity)", 1)

# 3.1
heading("3.1  Form TX copyright filing — 23 days, status unconfirmed", 2)
banner("BLOCKER — hard external deadline 2026-05-20")
para("IP.md flags this as 'Immediate Actions (This Week)' with a hard deadline. No session log indicates it has been filed. Cost is $65 and ~10 minutes online. Missing it loses statutory damages eligibility for any infringement during the launch window — meaningfully weaker IP posture for a $19.99 paid product.")
rich_para([("Action: ", "Bold"),
           ("File this week. Does not require the entity to exist; file as Dean Maurice Ellis, individual author, and assign to the entity later.", None)])
response("Status: filed / scheduled / blocked? If blocked, on what?")

# 3.2
heading("3.2  Privacy policy placeholders + 2026-04-20 lawyer meeting outcome unknown", 2)
banner("BLOCKER — gates Play Console submission")
para("Two policies live in docs/: PRIVACY-POLICY-V1.md (offline, V1-shippable in shape) and PRIVACY-POLICY.md (full OMEN-008 draft for the future Salem-data-collection feature). The V1 one is well-written and accurately reflects the network-stripped manifest, but has four [TBD] fields:")
bullet("[OPERATING ENTITY TBD] — C-corp vs. sole prop vs. LLC", sub=True)
bullet("[CONTACT EMAIL TBD]", sub=True)
bullet("[MAILING ADDRESS TBD]", sub=True)
bullet("[JURISDICTION TBD]", sub=True)
para("The 2026-04-20 counsel meeting was supposed to resolve these. It has been 7 days. No session log mentions the meeting outcome. Cannot submit to Play Console without a hosted privacy-policy URL whose entity matches the merchant profile.")
response("Did the 2026-04-20 meeting happen? Outcome? Next steps owed?")

# 3.3
heading("3.3  Pre-AAB hard-delete of 365 dedup losers", 2)
banner("BLOCKER — must happen before next AAB rebuild")
para("S185 estimated 110. S187 lint surfaced 365 actual rows where data_source matches dedup-loser pattern. These are soft-deleted POIs polluting the asset and contributing to (a) confused geofence registration, (b) bigger bundled DB than necessary, (c) the dedup-cluster confusion that S188's modal is now polishing UX around. Lint tab gives you one-click navigate-to-each, or run bulk SQL after verifying zero FK refs.")
rich_para([("Don't ship 365 ghost rows to paying customers.", "Bold")])
response("Plan to drain — bulk SQL or one-click-per-row through the lint tab?")

# 3.4
heading("3.4  Play Console developer account + identity verification", 2)
banner("BLOCKER — multi-week lead time, must start now")
para("$25, government-issued ID required. D-U-N-S/identity verification has historically been 2–4 weeks. If you start this 2 weeks before submission, you will miss your window. Independent of legal entity setup — start it this week in parallel with copyright filing.")
response("Has this been started? If not, what's blocking?")

# 3.5
heading("3.5  IARC content rating + data-safety form", 2)
banner("BLOCKER — required for Play Console listing", "Warning")
para("Both Play Console questionnaires. Data-safety form is straightforward (V1 collects nothing off-device — privacy policy already has the answers). IARC asks about violence/horror/etc. — Salem witch trial content is borderline Teen-rated for 'horror themes' depending on narration phrasing. Get this right or you'll be re-classified to Mature post-launch (a brand hit for a tourism product).")
response("Any concerns about content surfacing as Mature? Want a Bark/sox redaction pass on dark content?")

# 3.6
heading("3.6  Eyes-on smoke test of V1 feature gating on the current AAB", 2)
banner("BLOCKER — gates everything else; one focused 30-min device session", "Warning")
para("S180 R8-stripped 10 V1-disabled UI sites. The carry-forward says 'smoke test interrupted before items 2–N: POI detail Visit Website ACTION_VIEW handoff, Find dialog Reviews/Comments hidden, Find Directions on-device router, toolbar gating, webcam dialog View Live hidden.' Until these are eyes-on confirmed on the Lenovo, you do NOT know your V1 binary actually meets V1 scope. R8 should make it true; verify it.")
rich_para([("Action: ", "Bold"),
           ("Run the smoke test BEFORE the next code change.", None)])
response("Schedule a Lenovo session for this? When?")

# 3.7
heading("3.7  No second-medium copy of upload signing keystore", 2)
banner("BLOCKER — single-medium backups fail")
rich_para([
    ("One USB copy on ", None),
    ("/media/witchdoctor/writable/wickedsalem-upload-key-backup-2026-04-26/", "Code"),
    (". CLAUDE.md flags it as critical. If that USB dies before second-medium AND you've uploaded to Play Console, you cannot ever push an update to that listing. Google's 'lost upload key' recovery is multi-week and not guaranteed.", None),
])
rich_para([("Action: ", "Bold"),
           ("Encrypted cloud (Tarsnap, Backblaze B2 + PGP, encrypted Drive) AND a second USB stored physically elsewhere. Today.", None)])
response("Where will the second copy live?")

# =====================================================================
# 4. SOFT CONCERNS
# =====================================================================
heading("4. Soft Concerns (Worth Flagging, Not Ship-Blocking)", 1)

soft_concerns = [
    ("4.1  Documentation drift on poi-icons size",
     "Both CLAUDE.md and STATE.md still cite 'poi-icons/ at 544 MB is the pre-Play-Store audit target.' Reality: 3.4 MB (S167 fixed it). APK is 87 MB. Not broken; just stale text contradicting actual artifact. Fix during next STATE.md update so future Claude sessions don't open with the wrong premise.",
     "Worth a one-line edit?"),

    ("4.2  Zero Kotlin unit tests; narration gate is the highest-risk untested logic",
     "OMEN-004 Phase 1 (≥1 real Kotlin test file) extended to 2026-08-30 — conflicts with ship target. 103 shell tests cover integration but not narration-gate logic. S186 surfaced TWO correction passes mid-session on the gate's force-visible/force-audible layering. One targeted unit test on NarrationGeofenceManager.setTourMode would have caught what only operator field-testing caught.",
     "Worth burning 1 hour pre-launch to write that one test?"),

    ("4.3  osmdroid → WickedMap migration: 338 call sites still on osmdroid",
     "Custom WickedMap engine shipped in S171 as a parallel system. None of SalemMainActivity migrated. Correctly deferred to V2. But S171–S172 dominant theme work currently sits unused in the AAB. Low-pressure post-launch is the right place to invest.",
     "V2 timing — Q4 2026? Q1 2027?"),

    ("4.4  Carry-forward list is growing, not shrinking",
     "~16 items still owed across S150–S188 (water animation tuning, GPS-OBS heartbeat, Witchy bbox extension to Beverly, walk-confirm walker dwells, S168 launchMode singleInstance fix, Find type-search smoke test, etc.). Some have aged 30+ sessions. Pre-launch you should explicitly triage each to one of: (a) ship-blocker, (b) V1.0.1 patch, (c) deferred V2. Right now they all live in the same 'owed' list, which lets things drift indefinitely. Witchy bbox extension is a personal-stake item (your Beverly home is north of bake bbox max lat) — has quietly slipped past 4 sessions.",
     "Want a dedicated session to triage the full carry-forward list?"),

    ("4.5  Sessions 187 and 188 had zero Android impact",
     "Both polished the admin tool. Reasonable in isolation: the admin tool is how you fix the data. But two consecutive zero-Android sessions when you're 4 months from ship and have unverified V1 gating, an unfiled copyright, and an open lawyer-meeting placeholder is worth reflecting on. Are admin polish iterations the highest-leverage thing each day, or is it that they're easier to ship than the harder-to-finish operator-side items?",
     "How do you want to balance admin work vs. operator-side launch prep?"),

    ("4.6  Runtime narration is Android TTS, not the Bark/sox pipeline",
     "Per feedback_runtime_narration_is_android_tts.md, the Bark/sox/AudioCraft voice-clip pipeline is PLANNED, not live. Shipping app uses on-device Android TTS for all 2,354 narrated POIs. Has anyone evaluated whether default Android TTS is $19.99-paid-app quality? TTS varies by device — Pixel sounds OK, mid-range tablets sound robotic. If reviews say 'sounds like a robot,' you'll wish you'd pre-rendered hero POIs with Bark — even just for top 50 stops.",
     "Schedule a 30-min Lenovo listen-test before submission?"),

    ("4.7  Two competing privacy policies in repo",
     "PRIVACY-POLICY-V1.md ships; PRIVACY-POLICY.md is the future-Salem-data-collection version. Both draft. Risk: at submission a future session accidentally references the wrong file. Recommend renaming the V2 one to PRIVACY-POLICY-V2-DRAFT.md so V1 is unambiguously live.",
     "OK to rename now?"),

    ("4.8  No marketing / ASO plan visible",
     "COMMERCIALIZATION.md is 100KB and details a freemium-with-ads model that contradicts the V1 flat-paid decision. For a $19.99 paid app trying to capture 1M+ Salem 400+ visitors: Play Store listing copy + screenshots + feature graphic don't appear to exist; no ASO keyword research; no Destination Salem / Essex Heritage outreach; no press kit; no pre-launch beta. This is the weakest leg of the launch tripod.",
     "Who's owning marketing? Is it on you, or is there help?"),

    ("4.9  No iOS plan",
     "V1 is Android-only. October 2026 Salem 400+ visitors will be ~50% iPhone users. Every one of them is a person you can't sell to. Acceptable for V1 (scope discipline), but the addressable market is half what the timeline implies.",
     "Is iOS V1.5? V2? Never?"),
]
for title, body, q in soft_concerns:
    heading(title, 3)
    para(body)
    response(q)

# =====================================================================
# 5. OPEN QUESTIONS
# =====================================================================
heading("5. Open Questions That Need Your Answers", 1)
para("These are the highest-leverage unknowns. Each is a one-or-two-sentence answer that unblocks downstream planning.")

questions = [
    "Did the 2026-04-20 lawyer meeting happen? What was the outcome?",
    "Has Form TX been filed? If not, when will it be?",
    "Has the Play Console developer account application been started?",
    "Have you ever listened to the Android TTS narration end-to-end on the Lenovo? Audio quality acceptable for $19.99?",
    "Where do you stand on Play Store store-listing assets (icon, feature graphic 1024×500, 8 screenshots, description, what's-new)?",
    "Is there anyone outside you who has used the app for ≥30 minutes? (Friendly-eyes beta is the fastest way to surface issues you've gone blind to.)",
    "Is Sept 1, 2026 a hard ship date or a soft target? If something has to slip, what's negotiable?",
    "What's the marketing budget — $0, $500, $5,000? Materially changes options.",
]
for i, q in enumerate(questions, 1):
    rich_para([(f"Q{i}.  ", "Bold"), (q, None)], style="Body")
    response()

# =====================================================================
# 6. FEATURE / POLISH IDEAS
# =====================================================================
heading("6. Feature & Polish Ideas Worth Considering for V1", 1)
para("Tagged H/M/L by leverage-vs-effort for the Sept 1 window. Triage each: KEEP / DEFER-V2 / KILL.")

features = [
    ("H", "Pre-launch beta program with ~10 Salem locals or historical society members",
     "Single best ROI move. 1 week to set up, 2 weeks to run. Surfaces real-device GPS issues, narration quality complaints, content errors."),
    ("H", "Onboarding-to-nearest-tour-point",
     "S185 carry-forward; ~3 hours. Massive UX uplift — first-time users land outside Salem and the app currently doesn't help them. Salem 400+ visitors arrive by train/car and need this."),
    ("H", "Pre-render Bark voice clips for top 30 POI narrations",
     "Even keeping Android TTS as bulk fallback, premium voice for hero content (Witch House, Hawthorne Hotel, Old Burying Point, Salem Common, etc.) materially differentiates."),
    ("H", "Halloween / October seasonal layer",
     "Salem in October is a different city. Ghost-tour POIs, costume-friendly photo spots, October-only events. Dynamic content hidden behind a date check, no network needed. Killer feature for peak season pricing justification."),
    ("M", "Photo capture from POIs (write-only, V1 scope)",
     "User saves a photo with POI metadata for their own trip log. No upload, no social — fully offline, local SQLite write. Future GeoInbox tie-in. Adds a 'trip diary' use case."),
    ("M", "Salem Heritage Trail / Salem Ferry / hotel concierge partnerships",
     "$19.99 retail, but $9.99 wholesale to hotels who hand it out as a guest amenity is real B2B revenue and bypasses ASO entirely. Approach 3 hotels pre-launch."),
    ("M", "Press kit + tourism-board outreach for Salem 400+",
     "Destination Salem, Essex Heritage, Salem Witch Museum will mention partner apps in visitor briefings if you give them assets to use."),
    ("M", "Trip planner mode",
     "Multi-day itinerary, '2 hours / a day / a weekend' presets. Differentiates against generic Salem walking tours."),
    ("L", "Witchy bbox extension to Beverly",
     "Personal-stake S159 carry-forward. Worth doing for your own use; your Beverly home is north of the bake bbox."),
    ("L", "Apple/iOS port",
     "V2. Worth scoping the cost now so you can decide if a Q4 2026 / Q1 2027 iOS launch is realistic."),
]
for tag, title, body in features:
    color = "Red" if tag == "H" else ("Amber" if tag == "M" else None)
    rich_para([(f"[{tag}] ", color),
               (title + ". ", "Bold"),
               (body, None)], style="Bullet")
    response("KEEP / DEFER-V2 / KILL — and any notes:")

# =====================================================================
# 7. V2 BACKLOG
# =====================================================================
heading("7. V2 / Post-Launch Backlog (Already Implicitly Deferred)", 1)
para("These are correctly out of V1; flagging only so they're explicit. Tick anything you want bumped back into V1 scope.")
v2_items = [
    "WickedMap engine migration (338 call sites)",
    "Sprite/overlay-creature system (7 characters built; walking deferred to Mixamo/Quaternius)",
    "Social features (chat, comments, ratings) — requires COPPA + content moderation",
    "Subscription / freemium tier (V2 commercial pivot per COMMERCIALIZATION.md)",
    "Salem data collection / RadioIntelligence hashed-observation contributor role — gated on full privacy policy",
    "Walking sprite animation (post-rigging)",
    "Patent provisional filings — ~$1,280 for 4 micro-entity provisionals (Adaptive Radius Cap-Retry, Probe-Calibrate-Spiral, JTS R-Tree Geofence, plus one more from the IP.md list of 14)",
    "iOS port",
]
for it in v2_items:
    bullet(it)
response("Anything to pull forward into V1?")

# =====================================================================
# 8. GO-TO-MARKET HONEST ASSESSMENT
# =====================================================================
heading("8. Go-to-Market Honest Assessment", 1)
gtm_grades = [
    ("Engineering", "A−",
     "Feature-complete, signed AAB on device, walking router unified, narration gate real, asset publish chain hardened. One verifiable smoke test from 'ready to submit.'"),
    ("Content", "B−",
     "2,354 narrated POIs is impressive; 365 dedup-loser pollution is not; 500+ missing year_established gates the Tour Mode hist-landmark whitelist; 30 silent civic POIs and 8 silent museum tour-flags are visible holes a reviewer or user will hit. Lint tab makes gaps visible — but visibility ≠ resolution. You need to actually run the backlog."),
    ("Legal/IP", "C+",
     "V1 manifest is genuinely network-incapable, which simplifies the legal posture enormously. But Form TX unfiled, lawyer meeting outcome unknown, privacy policy not hosted, no entity confirmed, no Play Console account started. None hard in isolation; together a 2–3 week wall of operator-side work that hasn't visibly begun."),
    ("Marketing/ASO", "F (or N/A)",
     "No store listing assets, keyword research, partnership outreach, or beta program visible. For a paid premium tourism app this is the leg the timeline is most exposed on."),
    ("Testing", "C",
     "Integration via 103 shell tests, zero unit tests, narration gate has been correction-passed twice in one session. Defensible for V1; technical debt for V2."),
    ("Project Management", "B",
     "Live conversation logs are excellent. STATE.md / SESSION-LOG / live-log layering is well-designed. Carry-forward list is growing without explicit triage — the one process gap. You're starting to drown in deferred items that haven't been formally re-classified."),
]
for cat, grade, body in gtm_grades:
    rich_para([(f"{cat}: ", "Bold"),
               (grade + ".  ", "Red" if grade.startswith("F") else ("Amber" if grade[0] in "CD" else "Green")),
               (body, None)], style="Body")
response("Disagree with any grade? Set your own:")

# =====================================================================
# 9. RECOMMENDED ACTION ORDER
# =====================================================================
heading("9. Recommended Action Order (Next 30 Days)", 1)

heading("Week 1 — by 2026-05-04", 3)
w1 = [
    "File Form TX copyright (1 hour, $65, online)",
    "Start Play Console developer account application (multi-week lead time)",
    "Confirm 2026-04-20 lawyer meeting outcome — fill four privacy-policy placeholders or escalate",
    "Second-medium upload-keystore backup — encrypted cloud + second USB",
    "30-minute Lenovo eyes-on smoke test of S180 AAB against 5 V1-gating items",
    "Drop the 365 dedup losers (verify FK refs first, then bulk SQL or lint-tab one-click)",
]
for it in w1:
    bullet(it)
response("Confirm / re-prioritize Week 1:")

heading("Week 2 — by 2026-05-11", 3)
w2 = [
    "Backfill year_established on top ~50 most-visible historical buildings (rest can be V1.0.1)",
    "Author narration for 8 museum tour-flags + 5 most-prominent silent civic POIs",
    "Pre-render Bark voice clips for top 10 hero POIs — A/B vs TTS, decide if you ship hybrid",
    "Privacy policy hosted at public URL (DestructiveAIGurus.com or equivalent)",
    "Start drafting Play Store listing assets — copy, screenshots, feature graphic",
]
for it in w2:
    bullet(it)
response("Confirm / re-prioritize Week 2:")

heading("Weeks 3–4 — by 2026-05-25 (just past Form TX deadline)", 3)
w34 = [
    "Recruit 5–10 Salem-local beta testers — friends, historical society, hotel concierges",
    "Approach 2–3 Salem hotels about wholesale licensing",
    "Triage the 16-item carry-forward list — V1 / V1.0.1 / V2 explicitly",
    "Run the lint tab address-geocode deep scan and use map-preview workflow on worst 50 mismatches",
    "First ASO keyword pass + competitor research (search 'salem walking tour', 'salem ghost tour', 'salem witch trial app' in Play Store)",
]
for it in w34:
    bullet(it)
response("Confirm / re-prioritize Weeks 3–4:")

heading("Month 2+", 3)
para("Beta feedback iteration, store listing finalization, IARC + data-safety submission, AAB resubmission with backfilled content, soft launch (limited countries) for crash-rate validation, then full launch.")
response("Anything else to slot here?")

# =====================================================================
# 10. RISK REGISTER (top three)
# =====================================================================
heading("10. Top Three Risks (What Keeps Me Up)", 1)

heading("R1.  Operator-side bottleneck", 3)
para("Code is in good shape; legal/account/marketing prep has not visibly started. If those take 8 weeks instead of 4, Sept 1 slips into October — launching DURING Salem 400+ peak with no soak time. Fallback is 'ship in October during the rush' = crash-rate exposure during your highest-stakes traffic.")
response("Mitigation plan?")

heading("R2.  Android TTS quality unknown", 3)
para("Nobody on this project (visible to me) has formally evaluated whether the narration audio is paid-app quality. If reviews come in saying 'sounds like Siri reading a Wikipedia article' you'll wish you'd burned the GPU time on Bark renders for at least the top 50 POIs.")
response("Mitigation plan?")

heading("R3.  The 'we have a lint tab that surfaces 365 problems' trap", 3)
para("Visibility is not resolution. Risk: shipping with the lint tab still showing 300+ flags because each one takes operator time to actually fix. Lint tab needs to either be drained pre-ship or the app's content needs a 'data quality' disclaimer that won't read well in reviews.")
response("Mitigation plan?")

# =====================================================================
# CLOSING
# =====================================================================
heading("Closing Frame", 1)
rich_para([
    ("You're ", None),
    ("80% built, 30% ready-to-sell", "Bold"),
    (", and the gap is operator-side work that is more uncomfortable than coding. That's a totally normal indie-launch posture, and you have time. But the timer on the operator-side stuff is running, and the engineering work has been comfortable enough to keep doing instead of switching modes.", None),
])
rich_para([
    ("The most valuable thing you could do this week is ", None),
    ("not write code", "Red"),
    (".", None),
])
response("Final thoughts / direction for S190+:")

# =====================================================================
# SAVE
# =====================================================================
out_path = "/home/witchdoctor/Development/LocationMapApp_v1.5/docs/V1-LAUNCH-REVIEW-2026-04-27.odt"
doc.save(out_path)
print(f"Wrote: {out_path}")
