# Vance

Vance is a modular Android package workspace for inspecting, refurbishing, signing, exporting and installing APK-family packages. The installed builder app ID is `com.maico.vance`.

## Product and module architecture

Vance is the host workspace; individual capabilities are modules. The current dashboard and module registry are designed to let modules be added without turning every feature into a separate top-level application.

| Module | Injected identity | Status |
|---|---|---|
| Furbish (UI label: Refurbish) | `ipa.maico.furbish` | First integrated module; legacy Refurbished Display workflow |
| Monitor | `ipa.maico.monitor` | Reserved |
| Explorer | `ipa.maico.explorer` | Reserved |
| Inspect Layout | `ipa.maico.inspectlayout` | Reserved |
| Scan Agent | `ipa.maico.scanagent` | Reserved |
| MoreTime | `ipa.maico.moretime` | Reserved |
| SurfWeb | `ipa.maico.surfweb` | Reserved; planned embedded WebView |

Reserved identities are namespace commitments, not claims that these modules are implemented.

## Technical decisions

### Builder identity versus source namespace

- Installed application ID: `com.maico.vance`.
- Android/Java source namespace remains `com.maico.refurbished` for now. This is intentional: application ID controls installed package identity; migrating the resource namespace would require coordinated R-class/source changes and is not needed for the product rename.
- Main Activity remains `com.maico.refurbished.MainActivity`.
- Main display name is **Vance**. Debug builds use **Vance-debug** so they are distinguishable on-device.
- Keep the application ID stable from now on unless a deliberate migration is planned; changing it creates a separate installed app and does not migrate app data.

### Process and security boundaries

- Injected dispatcher: `ipa.maico.vance.VanceActivity`.
- Furbish Activity and Vance UI run in the dedicated `:vance` process inside the target APK.
- Android processes belonging to one package still share the same application UID and data sandbox. A separate process isolates heap/lifecycle, not security identity.
- Keep ordinary modules out of the target's base process. A minimal Scan Agent/hook may eventually need to run there if it must inspect live base-process objects.
- Communicate between a base-process agent and Vance UI through explicit IPC (Binder, Messenger or AIDL as justified); do not assume process separation itself grants access to another process's objects.

### DEX and module loading

- Keep a stable core DEX containing the Vance dispatcher, shared module contracts/registry, Furbish and tightly coupled helpers.
- The `:inject` module compiles these classes; `:app` invokes D8 through `generateVanceCoreDex` and packages the result as `vance_core.dex`.
- The patcher inserts it as the next available `classesN.dex` in the target APK. Existing target DEX files are not replaced.
- Do not create one DEX per dashboard button. A substantial or base-process-specific module may receive its own DEX later when size, dependency or process requirements justify it.
- Keep shared contracts and dispatcher stable so individual modules can evolve without repeatedly changing the injection boundary.

### Separation of responsibilities

- `:app`: Vance UI, source selection, signing-key management, export and install workflow.
- `:inject`: code injected into target APKs: dispatcher, module registry/contracts and module runtime.
- `:patcher`: APK/package inspection, split staging/merging, manifest and resource editing, DEX insertion, signing-output verification.
- Furbish-specific patch logic must remain isolated from general Vance navigation and shared services.
- Output is intended to become module-neutral. Its current execution path still invokes Furbish; a generic multi-module PatchPlan is future work.

### Target APK integrity and compatibility

- Preserve target package name, original application code, original DEX, unrelated resources and unrelated manifest declarations.
- Add injected components and resources with collision-checked namespaced identifiers.
- Do not replace the target's launcher Activity. Add Vance as a separate static shortcut.
- Keep Vance UI in `:vance`; do not move ordinary modules into the target base process.
- Verify signed output: APK signature, package identity, injected DEX, expected components, launcher aliases and shortcut resources.
- Never report success or export a purportedly valid APK when patching/signing/verification failed.
- Runtime compatibility cannot be guaranteed for every app. Dynamic features, app-specific integrity checks, native ABI constraints, unusual launchers, split layouts and external OBB data require separate tests. Warn rather than silently bypass app protections.

### Signing and installation

- All APKs in one split set must be signed consistently by the selected signing identity.
- Same package with a different signing certificate cannot normally be updated in place.
- In that case Vance stages the complete APK set in private storage, warns that uninstalling erases target-app data, asks Android to confirm uninstall, and only submits the new package after Android confirms removal. Android may then ask for install confirmation.
- If the user cancels uninstall, Vance must not install the new build. Never silently uninstall or clear target app data.
- Reusing a compatible signing key is the preferred update path when preserving target app data matters.
- Treat Android launcher/icon caches separately from package/signature conflicts; do not clear app data automatically.

### Future module rules

1. Keep module IDs stable and register modules through the Vance module registry.
2. Default module execution to the dedicated `:vance` process.
3. Reserve base-process execution for the minimal Scan Agent only when technically required.
4. Use IPC across process boundaries; do not assume shared process memory.
5. Keep one stable core DEX; split only substantial modules when justified.
6. Explorer must use PackageManager and paths/data accessible to Vance. Do not assume unrestricted access to `/data/app` or other apps' private data.
7. Monitor, Inspect Layout, Scan Agent, MoreTime and SurfWeb remain independent modules.
8. SurfWeb is planned as an embedded WebView module.
9. Stabilize and regression-test Furbish before generalizing Output into a multi-module PatchPlan.
10. Keep module-specific patching code out of shared navigation/UI wherever practical.

## Repository layout

- `:app` — installed Vance builder application.
- `:inject` — injected Vance runtime and modules.
- `:patcher` — shared APK transformation engine.
- `furbish.md` — detailed inherited Refurbished Display design decisions and Furbish-specific compatibility rules.
- `VANCE_ARCHITECTURE.md` — expanded architecture notes and module roadmap.

## Current status and verification

The Vance shell and Furbish integration are running on the user's device. The user has confirmed a successful refurbishment/export and a working uninstall-confirmation → install-confirmation sequence for packages signed with a different certificate. This is a basic workflow milestone, not proof of broad APK compatibility.

No build is run by the assistant unless the user explicitly asks. Every code change still needs the user's manual build and device testing. Next milestone: focused regression/debugging of Furbish inside Vance before implementing other modules.
