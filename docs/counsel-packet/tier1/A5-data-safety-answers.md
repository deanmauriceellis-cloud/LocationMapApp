<a name="a5-data-safety-answers"></a>

# A5. Google Play Data Safety — Pre-Filled Answers

**Purpose:** Every question on Google Play Console's Data Safety questionnaire, pre-answered for the V1 posture. Counsel should confirm the answers match the V1 Privacy Policy ([§A3](#a3-privacy-policy-v1)) exactly. If the app behavior diverges from these answers at submission time, the listing will be rejected.

**Source:** Google Play Console → Policy → App content → Data safety. Form current as of 2026 Q1.

---

<a name="a5-1-headline"></a>

## 1. Headline answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | **N/A** (no data collected) |
| Do you provide a way for users to request that their data be deleted? | **Yes — uninstall the app; all local data is removed by the OS.** |

---

<a name="a5-2-categories"></a>

## 2. Data category-by-category

Google's Data Safety form walks through each data category and asks (a) is it collected? (b) is it shared? (c) is collection required or optional? (d) why is it collected? (e) is it ephemeral? For V1, every category answer is **No — not collected, not shared** unless noted.

### 2.1 Personal info

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Name | No | No | |
| Email address | No | No | |
| User IDs | No | No | No user accounts. |
| Address | No | No | |
| Phone number | No | No | |
| Race and ethnicity | No | No | |
| Political or religious beliefs | No | No | |
| Sexual orientation | No | No | |
| Other info | No | No | |

### 2.2 Financial info

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| User payment info | No | No | Google Play handles payment end-to-end. |
| Purchase history | No | No | Google Play maintains this, not the app. |
| Credit score | No | No | |
| Other financial info | No | No | |

### 2.3 Health and fitness

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Health info | No | No | |
| Fitness info | No | No | Walking activity is not tracked or recorded. |

### 2.4 Messages

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Emails | No | No | |
| SMS or MMS | No | No | |
| Other in-app messages | No | No | |

### 2.5 Photos and videos

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Photos | No | No | |
| Videos | No | No | |

### 2.6 Audio files

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Voice or sound recordings | No | No | The app PLAYS audio (narration, TTS) but does not RECORD. |
| Music files | No | No | |
| Other audio files | No | No | |

### 2.7 Files and docs

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Files and docs | No | No | |

### 2.8 Calendar

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Calendar events | No | No | |

### 2.9 Contacts

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Contacts | No | No | The app has no contacts permission. |

### 2.10 Location

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Approximate location | No | No | Not used; the app requests fine location only. |
| Precise location | **ACCESSED BUT NOT COLLECTED** | No | Used to position the user on the tour map and trigger POI narrations. Not transmitted, not stored beyond ephemeral in-memory use. |

> **Important:** Google's form has a specific option for the precise-location answer when the data is used on-device but not transmitted. The correct answer is to check "Accessed but not collected" rather than declaring a collection purpose. This aligns with Privacy Policy §2.2 and §2.3.

### 2.11 Web browsing

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Web browsing history | No | No | |

### 2.12 App activity

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| App interactions | No | No | Tour progress is stored locally only. |
| In-app search history | No | No | |
| Installed apps | No | No | |
| Other user-generated content | No | No | |
| Other actions | No | No | |

### 2.13 Web and app activity

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Crash logs | No | No | No crash reporting SDK is integrated. |
| Diagnostics | No | No | |
| Performance data | No | No | |
| Other app-performance data | No | No | |

### 2.14 Device or other IDs

| Subcategory | Collected? | Shared? | Notes |
|---|---|---|---|
| Device or other IDs | No | No | No Android Advertising ID collection, no device fingerprinting. |

---

<a name="a5-3-security"></a>

## 3. Security practices

| Practice | Applicable to V1? | Answer |
|---|---|---|
| Data encrypted in transit | Not applicable (no transmission) | N/A |
| Users can request data deletion | Yes | Uninstall the app — all local data is removed by Android's standard uninstall. |
| Data collection follows Google Play Families Policy | Applicable IF rated for children | **Not applicable — V1 is rated Teen, not a Families app.** |
| App committed to Play's Families Policy | N/A | No. |
| Independent security review | No | Optional; V1 has not undergone third-party security review. |

---

<a name="a5-4-listing-preview"></a>

## 4. Store listing "Data Safety" block — what users will see

Once submitted, Google renders a summary block on the Play Store listing. The V1 block will read approximately:

> **Data safety**
>
> No data collected. *The developer says this app doesn't collect any data.*
>
> Data is not encrypted. *(This is a tag Google shows because there is no data in transit.)*
>
> Data can't be deleted. *(Google's standard phrasing for apps that collect nothing — there is nothing to delete.)*
>
> **Learn more about Data safety**

The "data can't be deleted" wording is Google's default framing when the app collects nothing, and is not a problem. The Privacy Policy §2.7 explains the uninstall mechanism for anyone who clicks through.

---

<a name="a5-5-counsel-review"></a>

## 5. Counsel review checklist

- [ ] Confirm the per-category answers match what the V1 app actually does. **This is the load-bearing check** — if the app ever calls a library that transmits anything (a silent analytics SDK, a tile-fetch-with-UA-header, a crash reporter), the Data Safety form is factually wrong and the listing can be pulled.
- [ ] Confirm the "Precise location — accessed but not collected" answer (§2.10) is the correct disposition. This is the single non-obvious answer on the form.
- [ ] Confirm the "uninstall is the deletion mechanism" framing is acceptable.
- [ ] Confirm Google Play Families Policy does not apply (V1 is Teen, not Designed for Families).
- [ ] Flag any state-specific regulatory exposure (CCPA, VCDPA, CPA, CTDPA, CTPA) — with zero data collected, these should be inapplicable, but counsel should sanity-check.

---

[Back to top](#cover) | [Play Store checklist →](#a6-play-store-checklist)
