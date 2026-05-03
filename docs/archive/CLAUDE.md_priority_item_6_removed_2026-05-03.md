# Removed from CLAUDE.md priority block — 2026-05-03 (S223)

The following bullet was removed from the "S189 next steps" engineering list in `CLAUDE.md` at session S223 close. Reason: stale — S193 already implemented the dynamic schema-JSON read; this bullet was never updated to reflect that. Verified at `cache-proxy/scripts/publish-tour-legs.js:50–70` (`readLatestRoomIdentity()` discovers the latest schema file under `app-salem/schemas/.../<n>.json` and stamps that hash + version).

Original numbering: item #6 of the engineering carry-forward list.

---

> 6. **De-hardcode the Room identity_hash in `cache-proxy/scripts/publish-tour-legs.js`** (S186 carry-forward). Currently stamps `dad6c01b8e5f8fed0ae9ff6f8ef7432d` (v10). Read latest from `app-salem/schemas/<DB>/<v>.json` instead.

---

After removal, engineering items 7–13 in the S189 next-steps block were renumbered to 6–12. The duplicate operator-side numbering (9–13) was left as-is — it predates this change.
