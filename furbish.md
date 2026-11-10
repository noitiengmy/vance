# Furbish — inherited Refurbished Display design decisions

Furbish is Vance's first module. It carries forward the former Refurbished Display workflow: add launcher display styles and a Change Display chooser to a target APK without cloning the app or replacing its original code.

The former Refurbished Display README's technical decisions are retained here as the module-specific specification. Where historical design notes differ from the current implementation, this file describes intended constraints and explicitly flags compatibility limits; it is not a claim that every target APK has been tested.

## 1. Core behavior

- Prepare one or more alternate launcher name + icon pairs in Vance.
- Inject a Change Display static shortcut into the target APK.
- Long-press the target app's launcher icon → Change Display → choose Original or an alternate style → Save.
- The target APK keeps its original package name, original application logic and original DEX files.
- Furbish adds its own DEX and only the manifest/resources required for aliases, icons, labels, shortcut metadata and the chooser.
- Re-refurbishing an already refurbished APK should update the existing Furbish injection in place, not append duplicate classes, aliases or shortcuts.

## 2. Non-negotiable patching principles

1. Never replace, rewrite, decompile/recompile or otherwise modify original target DEX files.
2. Never change the target package name or clone the target application.
3. Add the injected DEX at the next available `classesN.dex` index.
4. Preserve unrelated manifest declarations, resources, launcher entries and original static shortcuts.
5. Use ARSCLib/APKEditor-style binary resource manipulation rather than rebuilding the entire target through an apktool/smali pipeline.
6. Collision-check resource names, resource IDs, ZIP paths and component names. Use Furbish-specific namespacing.
7. If the launcher identity is ambiguous, warn and do not silently choose a secondary launcher.
8. Do not bypass app integrity/signature protections. Report compatibility limits accurately.
9. A runtime compatibility issue may still allow a structurally valid signed output; a failed patch, invalid archive or failed signature verification must never be reported as success.

## 3. Launcher and alias model

- Identify the actual primary launcher from manifest intent filters, not by assuming a class named `MainActivity`.
- The primary launcher may itself be an `activity-alias`; preserve its real target activity and launch semantics.
- Preserve secondary launcher entries and unrelated intent filters.
- Alias 0 represents the original name/icon; Alias 1…N represent the supplied styles.
- Exactly one Furbish launcher alias should be enabled at a time. New installations start with Alias 0 enabled.
- Each alias explicitly declares exported state, MAIN/LAUNCHER intent filter and shortcut metadata as required.
- Furbish component namespace: `ipa.maico.furbish`. Resource names use a collision-resistant Furbish-specific prefix.
- User images are converted/normalized for launcher use; the original icon and label remain available as Alias 0.

## 4. Change Display shortcut

- Change Display is a static shortcut available without first opening the target app.
- It is inserted at the top of the static shortcut list where Android/launcher ordering permits; existing static shortcuts are copied and preserved.
- Dynamic shortcuts remain managed by the target app.
- A dedicated shortcut icon is used; it must not borrow a user style icon or the original app icon.
- Shortcut XML and manifest metadata must consistently reference the same resource and explicit Furbish activity.
- Android launchers control how many shortcuts are visible and may cache shortcut presentation. XML order does not guarantee absolute visual order across static/dynamic shortcuts.
- A target app's existing shortcuts that explicitly target the old launcher component may behave differently after alias insertion. Do not patch target DEX merely to repair such app-specific assumptions.

## 5. Furbish runtime and alias switching

- Change Display opens a small chooser Activity in the dedicated `:vance` process.
- It does not require launching the target app's normal UI first.
- The chooser discovers the aliases present in the host package rather than relying on a hard-coded style count or host package name.
- On Save, enable the selected alias and disable the other Furbish aliases, leaving exactly one launcher entry enabled.
- Avoid intentionally killing the target app while switching aliases. Launcher icon/name refresh may be delayed or cached by the launcher.
- The injected module shares the target app UID and data sandbox. The process boundary isolates lifecycle/heap only; it is not a separate security identity.

## 6. Style editor and presets

- The editor starts with three optional rows; users may add more rows.
- A row is either completely filled (name + image) or completely empty. A partial pair is rejected with a row-specific error.
- Completed rows are compacted in visual order for generated aliases.
- Presets are saved by target package name and restore ordered names/image references where the underlying files/permissions remain available.
- Missing or revoked image references require the user to choose the image again.
- Do not keep imported target APKs as a package-history/archive feature unless separately designed.

## 7. Source packages and split handling

Supported input families include standalone APK and common APK-family containers (APKS, XAPK, APKM), plus installed applications where Android permits access to base and split APK paths.

- Read base APK and relevant split APKs as a package set.
- Preserve the source family/appropriate output container where supported.
- Sign every APK in a split set with the same selected signing identity.
- Default output is the refurbished APK or split package; standalone output from splits is optional and only when a valid merge path exists.
- Prefer an existing universal/standalone APK when supplied in a bundle.
- Real split merging may use ARSCLib to merge resources, manifest, DEX and native files; it is not equivalent to concatenating ZIPs and is not guaranteed for every bundle.
- APKM may contain package metadata and nested APKs; its outer archive signature may become invalid after modification. The inner APKs must still be signed and validated consistently.
- XAPK may contain OBB expansion files. APK installation sessions do not install OBB content; preserve it in archive output or warn when an install path cannot deploy it.
- Reject unsupported multi-package layouts, missing base APKs and malformed package sets with explicit errors rather than creating a partial fake package.

## 8. Signing identities

- Default signing identity: the project's fixed GiaoKhoa test keystore. It is suitable for development/testing, not a production release identity.
- Private signing: device-bound Android Keystore identity; loss of app/keystore state may make it unrecoverable.
- Custom signing: user-selected JKS/PKCS12/PFX keystore with alias and passwords supplied at use time. Do not persist passwords.
- Portable PKCS12 mode: user-managed backup/restore, with password protected outside app preferences.
- All split APKs in one package use the same signing key.
- Updating an installed app requires the same or Android-compatible signing lineage. Different certificates generally require uninstall first, which removes app-private data.
- Never log private keys or passwords. Keep debug/test signing separate from any future production signing process.

## 9. Verification and diagnostics

After signing, reopen and verify the produced APK rather than trusting the ZIP write or signer return alone.

Check:
- APK signature schemes and signer details.
- Target package identity.
- Presence of the injected Furbish DEX.
- Expected Alias0…AliasN declarations, launcher filters and exactly one initially enabled alias.
- Change Display Activity and receiver declarations.
- Static shortcut XML, intent target, metadata references and dedicated shortcut icon.
- Resource IDs and file paths resolve to the expected Furbish assets.

Do not suppress verification errors. If V1 compatibility is required for old Android API levels, choose a V1 digest/signature algorithm compatible with those levels while retaining V2/V3 verification as appropriate. A signature failure is a hard failure, not a warning.

## 10. Install and update behavior

- Install through Android PackageInstaller; user confirmation remains in Android's system UI.
- If the package is not installed or its signer is compatible, submit the APK set as an install/update session.
- If an installed package has a different signing certificate, stage the complete APK set privately, request system-confirmed uninstall, then submit the new APK set only after uninstall success.
- Warn that uninstall removes the target app's private data. If the user cancels, do not install.
- The install/uninstall callback flow must be resilient to process recreation and clean staged files after success/failure.
- UI notifications/Toast must be dispatched on the main thread, even when package work runs on a worker thread.

## 11. Compatibility limits and test matrix

Furbish does not guarantee compatibility with every Android application. Known risk areas include:

- Multiple or ambiguous launcher activities and aliases.
- Static/dynamic shortcuts tied directly to the original launcher component.
- Apps with signature permissions, integrity checks or self-verification.
- Split APKs with dynamic features, uncommon module dependencies or ABI-specific native code.
- OBB expansion files and external data requirements.
- Launcher-specific shortcut caching and delayed icon refresh.
- Re-refurbishing, reducing style count, stale aliases/resources and DEX duplication.
- Different signer lineages, signing-scheme/API compatibility and installer confirmation lifecycle.

Minimum regression matrix before expanding the module:
1. A simple standalone APK with one launcher Activity.
2. An APK whose primary launcher is an activity-alias.
3. An app with multiple launcher entries.
4. A multi-DEX APK.
5. A split package with base + configuration splits.
6. A package already refurbished once (re-refurbish/idempotence).
7. One, two, three and more than three style rows, including sparse rows.
8. Alias switching through all styles and back to Original.
9. Same-certificate update and different-certificate uninstall → install.
10. User cancels uninstall; verify the new APK is not installed.
11. Install failure after uninstall; report clearly and preserve recoverable staged files where appropriate.
12. Android launcher cache refresh and static shortcut behavior on the target device.

## 12. Current Furbish status

The user has confirmed that the Vance app runs, refurbishment and saving a resulting APK work, and the different-signature install flow reaches Android uninstall confirmation and then Android install confirmation. This is a basic end-to-end milestone only. It does not prove broad compatibility across APK types or target apps.

The next work is a dedicated Furbish regression/debugging pass. Do not start implementing Monitor/Explorer/other modules until Furbish's core paths have been systematically exercised and the user has reviewed the results.
