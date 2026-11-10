# Note.md — Sổ tay kỹ thuật và bảo trì Refurbished

> **Phạm vi:** mô tả **mã nguồn đang có trên nhánh `main` tại commit `20f9923`** (07/11/2026), không phải đặc tả các tính năng dự kiến. File này dành cho người tiếp quản dự án, muốn sửa lỗi, mở rộng hoặc tái tổ chức code. Khi code đổi, hãy cập nhật tài liệu tương ứng. README là tài liệu giới thiệu; **Note.md là bản đồ bảo trì**.

## 1. Bản chất hệ thống — hiểu trong một phút

Refurbished là ứng dụng Android dùng để **tân trang tên/icon launcher** của APK, không đổi package name và không viết lại chức năng nghiệp vụ của app đích.

Có **ba module Gradle, ba trách nhiệm độc lập**:

| Module | Vai trò | Chạy ở đâu? | Chính sách sửa |
|---|---|---|---|
| `app/` | Ứng dụng người dùng: chọn nguồn, nhập styles, chọn khóa ký, xuất/cài gói, quản lý giao diện | Trong **Refurbished** | Thay đổi tự do khi thêm tính năng UI, import, xuất, cài, ký |
| `patcher/` | Thư viện biến đổi APK: đọc manifest/ARSC, tạo/update launcher aliases và shortcuts, thêm/thay DEX, ký và kiểm tra kết quả | Trong **Refurbished** lúc patch | Chỉ sửa theo quy tắc tương thích đầu ra; kiểm thử đặc biệt kỹ |
| `inject/` | **Module aliaswi dùng chung**: giao diện Change Display và receiver sau cập nhật | **Bên trong mỗi APK được tân trang**, sau khi cài | **Ưu tiên giữ cố định**; không sửa chỉ vì thay đổi UI/chức năng app chính |

Luồng tổng quát:

```text
Người dùng chọn APK / app đã cài / APKS / XAPK / APKM
    ↓
app: xác định package, tập APK và launcher, nạp cấu hình styles
    ↓
app: chọn 1..N cặp tên+icon, chọn signer và kiểu đầu ra
    ↓
PackageSets: chuẩn bị base/splits, hoặc RealSplitMerger khi cần
    ↓
patcher: ARSCLib + manifest + icon resources + XML static shortcuts
    ↓
patcher: thêm module DEX một lần, hoặc thay DEX aliaswi đã có
    ↓
SigningKey: ký APK, PatchedApkVerifier: kiểm tra sau ký
    ↓
app: xuất APK / APKS / XAPK / APKM qua Android document picker
    ↓
Ứng dụng đã cài: Change Display chạy từ module DEX, không gọi về app Refurbished
```

**Nguyên tắc nền tảng:** `inject` không biết tên package app gốc, không biết số styles từ trước, không tự đọc/ghi ARSC và không chứa dữ liệu icon của từng style. **Patcher tạo tài nguyên và manifest; hệ điều hành cung cấp chúng cho DEX qua `PackageManager`.**

## 2. Cây thư mục / nơi tìm chức năng

```text
Refurbished/
├── Note.md                          ← tài liệu bảo trì này
├── README.md                        ← mô tả tổng quan và lịch sử ghi chú
├── settings.gradle                   ← :app, :patcher, :inject
├── build.gradle                      ← Android Gradle Plugin 8.7.3
├── gradle.properties
├── .github/workflows/build.yml       ← workflow build debug thủ công
├── app/
│   ├── build.gradle                  ← cấu hình app và tác vụ generateAliasDex
│   └── src/main/
│       ├── AndroidManifest.xml       ← manifest của app Refurbished, KHÔNG phải APK đích
│       ├── assets/default.p12        ← danh tính ký mẫu; xem cảnh báo ở mục 10
│       ├── res/
│       │   ├── drawable-nodpi/brand_icon.png    ← ảnh thương hiệu chuẩn
│       │   ├── drawable/ic_flat_*.xml            ← icon thao tác UI
│       │   ├── drawable/flat_button_*.xml        ← nền nút Light/Dark
│       │   └── values[/-vi]/                     ← chuỗi giao diện EN/VI
│       └── java/com/maico/refurbished/
│           ├── MainActivity.java
│           ├── PackageSets.java
│           ├── RealSplitMerger.java
│           ├── StyleStore.java
│           ├── SigningPanel.java
│           ├── SignerManager.java
│           ├── PortableKeyManager.java
│           ├── PortableKeyDialog.java
│           ├── DefaultKey.java
│           ├── UiSections.java
│           ├── UiSettings.java
│           ├── FlatUi.java
│           ├── InstalledAppPicker.java
│           ├── WebDownloadActivity.java
│           ├── PackageInstall.java
│           └── InstallReceiver.java
├── patcher/
│   └── src/main/java/com/maico/refurbished/patcher/
│       ├── ApkInspector.java
│       ├── ApkPatcher.java
│       ├── PatchRequest.java
│       ├── PatchedApkVerifier.java
│       ├── IconNormalizer.java
│       ├── ShortcutIcon.java
│       └── SigningKey.java
└── inject/
    └── src/main/java/ipa/maico/aliaswi/
        ├── ChangeDisplayActivity.java
        └── AliasRefreshReceiver.java
```

**Lưu ý:** `ShortcutIcon.java` là **fallback tạo icon shortcut đơn sắc**, **không phải icon thương hiệu chuẩn**. Trong đường UI hiện tại, `MainActivity` đọc `R.drawable.brand_icon` và gửi byte PNG cho `PatchRequest.shortcutIcon`; do đó icon app chính và shortcut dùng cùng ảnh màu. Đường gọi patcher độc lập không truyền `shortcutIcon` sẽ dùng fallback đơn sắc.

## 3. Chức năng từng lớp — module `app`

### 3.1. Điều phối chính

**`MainActivity.java`** — điểm điều phối lớn nhất; hiện hơn 1.000 dòng, đồng thời:
- Tạo các màn Sources / Styles / Signing / Output, theo dõi widgets và trạng thái app.
- Nhận kết quả từ Android file pickers (`onActivityResult`): APK, ảnh, keystore, nơi lưu, bản tải từ web.
- `inspectSource(...)`: tạo một `PackageSets.Stage`, đọc package/version/launchers, nạp `StyleStore`, hiển thị thông tin.
- `addStyleRow`, `refreshStyleNumbers`, `clearEditors`: dựng danh sách style động. Mặc định **3 hàng**, có Add more style; hàng thêm có thể xóa.
- `saveCurrentStyles` / `displayIcon`: lưu preset và tạo preview trên luồng phụ. `previewSerial` tránh preview cũ ghi đè hàng mới.
- `startPatch(single, obbAcknowledged)`: lọc **cặp tên+icon hoàn chỉnh**, bỏ hàng trống, nén lại thành Alias1..AliasN; tạo key và module DEX tạm; đọc PNG thương hiệu; gọi `Stage.patch` hoặc `patchSingle`.
- `selectionSerial` cùng `chosenPackage` giúp tránh kết quả của lần chọn APK cũ ghi đè phiên đang chọn.
- `requestExport` / `exportSignedApk`: gợi ý tên theo `signedOutputPackage` của **kết quả patch**, xuất qua `ACTION_CREATE_DOCUMENT`.
- Điều phối kiểm tra chứng chỉ, cài APK, xuất APK gốc và nhập/xuất P12.

**Không nên thêm xử lý ZIP, ARSC hay thuật toán ký vào MainActivity**. UI được phép thay đổi, nhưng dữ liệu chuyển sang patcher phải giữ đúng các trường của `PatchRequest`. Đây là ứng viên **ưu tiên số 1 để tách lớp** (mục 9).

### 3.2. Nhập và đóng gói

**`PackageSets.java`** — xử lý các tập APK và ZIP:
- `Stage.prepare(input, installedApks, cacheDir)` tạo staging directory riêng và nhận APK độc lập, tập APK đã cài hoặc các archive.
- `readArchive` nhận `.apks`, `.xapk`, `.apkm`; phát hiện split/base, ứng viên universal/standalone và OBB; kiểm soát đường dẫn ZIP không an toàn.
- `validate` dùng `ApkInspector` xác nhận package/version, chính xác một base APK và các split liên quan.
- `Stage.patch`: patch base, ký lại các split khác bằng cùng signer, xuất APK đơn hoặc đóng lại tập.
- `Stage.patchSingle`: dùng universal có sẵn hoặc gọi `RealSplitMerger` trước khi patch.
- `writeXapk`: giữ các mục archive khác, thay APK đã ký; loại chữ ký archive APKM cũ không còn hợp lệ.
- `exportOriginal`, `close`: xuất bản gốc hoặc dọn thư mục staging.

Giới hạn **hiện có trong code**: tối đa **100 module APK**, mỗi APK tối đa **1 GiB**, tổng payload APK tối đa **2 GiB**. `canBuildSingle()` không chứng minh mọi split sẽ gộp thành công: đây chỉ là điều kiện ban đầu để thử.

**`RealSplitMerger.java`** — gọi ARSCLib `ApkBundle.mergeModules(false)`, làm mới bảng tài nguyên/manifest, viết APK gộp rồi kiểm tra `package`, `versionCode`, launcher và cờ split. Đây là bước *merge kỹ thuật*; không đảm bảo mọi ứng dụng phức tạp đều chạy như một APK độc lập.

**`InstalledAppPicker.java`** — duyệt ứng dụng đã cài, lựa chọn `sourceDir` và `splitSourceDirs`, có lọc/tìm kiếm. **`WebDownloadActivity.java`** — WebView + DownloadManager, chỉ nhận các định dạng gói phù hợp, trả file về MainActivity.

**`PackageInstall.java` / `InstallReceiver.java`** — tạo một `PackageInstaller.Session` cho các APK liên quan, nhận kết quả, chuyển tới xác nhận của hệ điều hành. **Chưa cài OBB tự động**; `PackageInstall.install` cố ý từ chối tập có OBB. Cho phép *xuất một APK từ XAPK chứa OBB* không có nghĩa APK đó chứa luôn OBB.

### 3.3. Cấu hình và giao diện

- **`StyleStore.java`**: SharedPreferences `asw_styles`, khóa theo package; lưu các mảng JSON `names`, `icons`, `backgrounds`. Lưu **URI ảnh được người dùng chọn**, không copy ảnh; sau này URI không đọc được sẽ báo missing. Khi restore, giữ ít nhất 3 dòng mặc định.
- **`UiSections.java`**: bốn panel/tab và chỉ báo busy; quản lý hiển thị chứ không xử lý APK.
- **`UiSettings.java`**: ngôn ngữ EN/VI và Light/Dark, lưu ở prefs riêng; tạo context/theme tương ứng.
- **`FlatUi.java`**, `ic_flat_*.xml`, `flat_button_*.xml`: style phẳng, vector đen–trắng của các nút (khác với brand icon màu).

### 3.4. Ký và khóa

- **`SigningPanel.java`**: widget/biểu mẫu chọn kiểu ký, đối tượng `Choice` chụp lại trạng thái để luồng nền dùng.
- **`SignerManager.java`**: lựa chọn default/private/custom/portable, lưu tùy chọn không lưu mật khẩu; private signer dùng AndroidKeyStore.
- **`PortableKeyManager.java`**: tạo, nhập, chuẩn hóa, xuất/xóa khóa **P12 có thể sao lưu**. Được bảo vệ bằng mật khẩu do người dùng chọn; lưu trong vùng dữ liệu app.
- **`PortableKeyDialog.java`**: hộp thoại xác nhận tác vụ liên quan đến khóa.
- **`DefaultKey.java`**: thông tin mặc định trong code; cùng `assets/default.p12` là đường ký mẫu.
- **`patcher/SigningKey.java`**: thực sự tải khóa/chứng chỉ, lấy fingerprint SHA-256, ký qua apksig, kiểm tra signer.

**Quy tắc:** cùng package đã cài mà muốn **cài đè** thì chứng chỉ ký phải tương thích; chỉ đổi style không làm thay đổi quy tắc Android này. Tuyệt đối không log password/private key hoặc đưa khóa dùng cho production vào repository.

## 4. Chức năng từng lớp — module `patcher`

**`ApkInspector.java`**: chỉ đọc, trích `packageName`, `versionName/code`, cờ split, các launcher `MAIN/LAUNCHER`. Với APK đã tân trang, nhận diện Change Display và coi nhóm aliaswi là **một launcher logic** (Alias0), tránh hiện tất cả styles như launcher độc lập.

**`PatchRequest.java`**: hợp đồng đầu vào patch:
- File base APK, DEX module, unsigned/signed output; tên `names[]`, `pngs[][]`, `backgrounds[]`.
- `SigningKey`, launcher chọn, và **`shortcutIcon` (PNG thương hiệu)**.
- Ba mảng style phải có cùng chiều dài; **ít nhất một style hoàn chỉnh**; không giới hạn cứng 3. Lớp này validate cơ bản, chưa tự parse cấu trúc APK.

**`IconNormalizer.java`**: chỉ nhận PNG/JPEG, giới hạn 24 MiB một ảnh; đọc EXIF JPEG, thu nhỏ ảnh lớn, giữ tỉ lệ, vẽ vào foreground PNG **432×432** và giữ artwork trong vùng trung tâm an toàn. Lỗi ảnh/previews nên truy ngược từ lớp này.

**`ShortcutIcon.java`**: fallback riêng của patcher khi client không cung cấp ảnh; không cần sửa nếu chỉ đổi brand icon ở app chính.

**`ApkPatcher.java`**: lớp biến đổi nhạy cảm nhất:
1. Mở base APK với ARSCLib; tìm hoặc tạo `resources.arsc` phù hợp; đọc manifest.
2. Nhận diện APK sạch vs APK đã có `ChangeDisplayActivity`/Alias0; kiểm tra patch cũ hợp lệ.
3. Với app sạch: tìm launcher được chọn, giữ icon/label gốc, chuyển bộ lọc MAIN/LAUNCHER khỏi launcher cũ sang các alias. Với app đã patch: dùng **Alias0** làm tham chiếu nguồn, giữ các launcher không thuộc aliaswi.
4. Lấy **resource prefix không trùng** trên APK sạch, hoặc dùng lại prefix aliaswi trên APK đã tân trang. Thêm/cập nhật string, PNG foreground, adaptive icon XML cho các styles.
5. Tạo/cập nhật **một** icon Change Display riêng, một XML static shortcuts; bảo toàn shortcuts khác và thay shortcut Change Display cũ.
6. Tạo **Alias0 + Alias1..AliasN**; mỗi alias có MAIN/LAUNCHER, label/icon và **metadata `android.app.shortcuts` trỏ tới cùng XML**. Alias0 enabled mặc định, các alias khác disabled.
7. Khai báo ChangeDisplayActivity và AliasRefreshReceiver.
8. APK sạch: thêm `classesX.dex` mới; APK từng tân trang: tìm descriptor `Lipa/maico/aliaswi/ChangeDisplayActivity;` trong các classes*.dex, **thay DEX tại đúng filename**, không thêm một định nghĩa class trùng.
9. Xóa chữ ký APK cũ không còn hợp lệ, viết unsigned, ký, gọi verifier.

**`PatchedApkVerifier.java`**: dùng `apksig` kiểm tra chữ ký; mở APK đã ký để đối chiếu package, DEX được chèn, số alias thực tế, chỉ một alias enabled mặc định, MAIN/LAUNCHER, cùng shortcut XML, chỉ một Change Display activity, một receiver, đúng shortcut intent và PNG icon riêng.

**Điểm cần nhớ:** các phương thức `putFile/putString`, `readShortcuts`, `serializeBinaryXml`, `findInjectedDex`, `uniquePrefix` hiện tập trung trong `ApkPatcher`. Không được sửa hoặc chuyển phương thức này mà quên giữ thứ tự cập nhật ARSC → file ZIP → manifest → ký → verifier.

## 5. Module DEX `inject`: hợp đồng cố định

Đây là **ranh giới ổn định quan trọng nhất** của dự án, cần hạn chế thay đổi khi app chính phát triển.

### 5.1. `ChangeDisplayActivity.java`

- Package cố định `ipa.maico.aliaswi`; class đầy đủ `ipa.maico.aliaswi.ChangeDisplayActivity`.
- Dùng `getPackageName()`, `PackageManager.getPackageInfo(...GET_ACTIVITIES | MATCH_DISABLED_COMPONENTS)` để đọc **các alias tồn tại trong chính APK chủ** (kể cả bị tắt).
- Chỉ chấp nhận class tên chính xác `ipa.maico.aliaswi.AliasN` với `N` là số nguyên không âm; sắp xếp số bằng `TreeMap`.
- Lấy label/icon qua `ActivityInfo.loadLabel/loadIcon`, tức Android tự resolve resource; **không có tên ARSC cứng, không hardcode tên package gốc, không giới hạn Alias3**.
- Hiển thị danh sách cuộn có radio; chọn style mới: **enable alias đích trước**, sau đó disable các alias còn lại qua `setComponentEnabledSetting(...DONT_KILL_APP)`.
- Một module giống nhau làm việc cho APK có 1 style, 3, 10 hoặc nhiều styles trong giới hạn thực tế của Android.

### 5.2. `AliasRefreshReceiver.java`

- Được patcher khai báo trong APK đích, lắng nghe `android.intent.action.MY_PACKAGE_REPLACED`.
- Sau cập nhật app, đọc lại alias thực tế; nếu alias đang bật vẫn có thì giữ, nếu không chọn Alias0; đưa hệ thống về trạng thái một alias enabled.
- Mục tiêu: tránh mất icon launcher khi giảm số styles ở lần tân trang mới rồi cài đè.

### 5.3. **Không sửa** những gì khi chỉ đổi tính năng app chính

Không cần sửa `inject/`, build DEX khác, hay thay package/class aliaswi khi:
- Đổi UI app, thêm nút, tab, ngôn ngữ, dark mode.
- Thêm nguồn APK, cách tải/import, hỗ trợ archive/OBB.
- Thay các tùy chọn khóa ký, xuất/cài đặt, báo tiến độ.
- Thay ảnh thương hiệu `brand_icon.png` hoặc icon style người dùng.
- Tăng/giảm số styles, thay layout nhập danh sách, sửa resource prefix (nếu manifest **vẫn giữ** AliasN cùng hợp đồng).

**Chỉ cần đổi `inject`** khi thay chức năng thực sự của Change Display (ví dụ cách lựa chọn, UI dialog, hành vi đổi alias) hoặc xử lý vòng đời sau cài đè; mọi thay đổi đó phải được kiểm thử trên app đích.

### 5.4. Những chuỗi/tên là **giao diện nhị phân** giữa `patcher` và `inject`

| Định danh | Vai trò | Bên liên quan |
|---|---|---|
| `ipa.maico.aliaswi.AliasN` | Tên đầy đủ của mỗi activity-alias | Patcher tạo / DEX tìm |
| `ipa.maico.aliaswi.ChangeDisplayActivity` | Đích của static shortcut và class trong DEX | Manifest, shortcut XML, DEX, verifier |
| `ipa.maico.aliaswi.AliasRefreshReceiver` | BroadcastReceiver sau update | Manifest, DEX, verifier |
| `android.app.shortcuts` | Metadata **trên mỗi alias** | Manifest / Android launcher |
| `*_change_display` | Shortcut ID có namespace tránh trùng | XML cũ/mới, verifier |
| `res/xml/asw*_shortcuts.xml` | XML binary static shortcuts | ARSC, APK ZIP, verifier |
| `res/drawable-nodpi/asw*_shortcut_icon.png` | Icon thương hiệu được chèn riêng | ARSC, XML shortcuts, verifier |
| `classesX.dex` | DEX chứa Change Display; X tùy APK chủ | Patcher, verifier |

Đổi tên prefix/class hoặc cách mã hóa shortcut **là migration format**, không phải sửa UI. Phải cập nhật đồng bộ code phát hiện, thay thế DEX cũ, khôi phục Alias0, verifier và test trên các APK đã tân trang bằng bản cũ.

### 5.5. DEX được tạo như thế nào?

`app/build.gradle` có task **`generateAliasDex`**:
1. Phụ thuộc `:inject:compileDebugJavaWithJavac`.
2. Dùng Android SDK build-tools **35.0.0 / d8** đóng hai class vào **một** DEX.
3. Copy kết quả thành **`app/build/generated/alias-assets/asw_module.dex`**.
4. `mergeDebugAssets` phụ thuộc tác vụ trên.
5. Khi patch, MainActivity `copyAsset("asw_module.dex", dex)` rồi truyền file DEX tạm cho patcher.

Thư viện `inject` **không được cài như một app riêng**; APK chủ chỉ nhận nội dung DEX và các khai báo manifest do patcher thêm. **Không đánh đồng một DEX module với chỉ một DEX trong toàn bộ APK**: APK chủ có thể có nhiều classes*.dex nguyên bản; yêu cầu là chỉ **một bản aliaswi**, không duplicate class.

## 6. ARSC, aliases và static shortcut: quy trình đúng

Ví dụ ba cặp style hợp lệ:
```text
Launcher gốc: com.example.MainActivity
                    ↑ targetActivity
ipa.maico.aliaswi.Alias0 — icon/label gốc, enabled mặc định
ipa.maico.aliaswi.Alias1 — style mới 1, disabled
ipa.maico.aliaswi.Alias2 — style mới 2, disabled
ipa.maico.aliaswi.Alias3 — style mới 3, disabled
              ↘ cả bốn đều có metadata android.app.shortcuts
                trỏ tới một XML static shortcuts đã hợp nhất
                    ↘ Change Display intent → ChangeDisplayActivity
```

- **Alias0 là bộ tham chiếu gốc** trong những lần patch tiếp theo; đừng để mất tên/icon gốc.
- Bốn alias chỉ là ví dụ. `N` là số cặp đầy đủ người dùng nhập; hàng trống bị bỏ qua; hàng chỉ có tên/ảnh bị từ chối.
- Resource ID do ARSCLib quản lý; **không tự cộng số cuối của ARSC theo suy đoán**.
- Các icon style dùng adaptive-icon XML/foreground PNG; shortcut Change Display có resource PNG màu độc lập từ `brand_icon.png`.
- Đừng gắn `android.app.shortcuts` **chỉ** lên target Activity; mỗi alias phải có metadata riêng và cùng trỏ tới XML thích hợp.
- Khi re-refurbish: chỉ loại alias/module/shortcut **thuộc aliaswi**, giữ shortcut khác và các launcher độc lập; reuse resource prefix, thay DEX cũ tại filename của nó.
- Mục tiêu idempotent là **một tập khai báo aliaswi hiện hành, một shortcut Change Display logic, một DEX aliaswi**. Các resource style cũ không còn được tham chiếu khi giảm số styles **có thể vẫn nằm trong ARSC/ZIP**; hiện không có quy trình garbage-collection resource cũ tổng quát. Đừng tuyên bố rằng mỗi lần patch lại sẽ xóa hoàn toàn mọi byte thừa.

## 7. Quy trình build và vận hành

- AGP: **8.7.3**; workflow dùng **Gradle 8.9 + JDK 17**, Android SDK platform 35 / build-tools 35.0.0.
- `app`: `minSdk 26`, `targetSdk 28`, `compileSdk 35`, **`versionCode 3`** tại thời điểm viết. Thư viện `patcher` dùng ARSCLib **1.4.0**, apksig **8.7.3**; app dùng Bouncy Castle **1.78.1**.
- Workflow `.github/workflows/build.yml` chạy **thủ công** với `workflow_dispatch`; thực hiện `gradle --no-daemon :app:assembleDebug`; artifact `refurbished-debug`, file `app/build/outputs/apk/debug/app-debug.apk`.
- Repo hiện **không có Gradle wrapper** (`gradlew`); workflow cài Gradle qua `gradle/actions/setup-gradle`. Nếu thêm wrapper cần kiểm tra phiên bản và policy build.
- Debug APK ký bằng `giaokhoa`, dựa trên `app/src/main/assets/default.p12`. Đây là **danh tính thử nghiệm đã đưa vào repo**, không dùng cho sản phẩm cần giữ bí mật khóa. Bảo vệ/di chuyển cấu hình ký production ra ngoài Git.
- Ảnh thương hiệu chuẩn: `app/src/main/res/drawable-nodpi/brand_icon.png` (**PNG đã xác minh hợp lệ; đừng paste base64 bằng tay khi thay**). App manifest tham chiếu `@drawable/brand_icon`; cùng byte ảnh được gửi qua `PatchRequest.shortcutIcon` cho shortcut trong APK đích.
- Khi sửa icon: kiểm tra PNG đọc được, CRC/IEND và icon trên launcher *thực tế*; có thể gặp cache launcher sau cập nhật.

## 8. Tìm lỗi nhanh theo triệu chứng

| Triệu chứng | Kiểm tra trước tiên | Tập tin/điểm vào |
|---|---|---|
| Không chọn/đọc được APK hoặc package sai | URI quyền đọc, loại ZIP, `Stage.prepare`, manifest package | `MainActivity.inspectSource`, `PackageSets`, `ApkInspector` |
| Không hiện launcher để chọn | `MAIN/LAUNCHER`, logic gom Alias0 của APK đã patch | `ApkInspector`, `ApkPatcher` |
| Lỗi nhập style hoặc preview ảnh | URI ảnh, cặp tên/icon, PNG/JPEG/EXIF, kích cỡ | `MainActivity.startPatch/displayIcon`, `StyleStore`, `IconNormalizer` |
| Alias không đúng số lượng / thiếu Change Display | Vòng tạo Alias0..N, XML shortcut, metadata từng alias | `ApkPatcher`, `PatchedApkVerifier` |
| Dialog không có style nào / chọn không đổi | Có class AliasN trong manifest? `MATCH_DISABLED_COMPONENTS`? Component enabled? | `inject/ChangeDisplayActivity` |
| Tân trang lần hai thất bại | Có Change Display & Alias0 nguyên vẹn? tìm đúng DEX cũ? resource prefix? | `ApkPatcher.findInjectedDex`, `shortcutResourcePath` |
| Sau cài đè mất icon khi giảm style | Receiver có trong DEX + manifest? alias cũ không còn? launcher cache? | `AliasRefreshReceiver`, `ApkPatcher` |
| Shortcut hiện sai icon / không hiện icon | PNG thương hiệu thật trong assets, ARSC `*_shortcut_icon`, shortcut XML icon ref, launcher cache | `brand_icon.png`, `MainActivity`, `ApkPatcher`, `PatchedApkVerifier` |
| Ký xong không cài đè được | So sánh chứng chỉ APK đã cài và chữ ký mới | `SigningKey.fingerprint`, `SignerManager`, `MainActivity.installedSignatureMatch` |
| Split merge không chạy | Đủ config split? ABI/density? manifest, native libs, ARSC? | `PackageSets`, `RealSplitMerger` |
| XAPK có OBB xuất được nhưng app thiếu data | OBB vẫn bên ngoài, chưa có installer OBB | `PackageSets`, `PackageInstall` |
| Tên file lưu là package trước | `signedOutputPackage`, `selectionSerial`, tên được gắn vào kết quả patch | `MainActivity.requestExport` |
| Build thiếu `asw_module.dex` | Task Gradle có chạy? đường dẫn class inject? SDK D8? | `app/build.gradle`, `inject` |

**Chiến lược gỡ lỗi:** (1) ghi nhận loại nguồn, package, số styles, kiểu output, signer; (2) đọc ngoại lệ/cause; (3) kiểm tra APK đầu ra với ZIP/manifest/ARSC, **không chỉ dựa vào việc ký thành công**; (4) đối chiếu bản gốc và bản patch; (5) chỉ sửa đúng lớp chịu trách nhiệm. Kiểm tra lỗi installer/chữ ký trước khi sửa logic alias.

## 9. Đề xuất tái cấu trúc — nên tách lớp nào, ưu tiên ra sao?

Đây là **đề xuất**, chưa phải các class hiện có. Nên tái cấu trúc **không đổi hành vi** theo từng commit nhỏ, có test hồi quy trước/sau.

| Ưu tiên | Vị trí hiện tại | Nên tách thành | Lợi ích / giới hạn |
|---|---|---|---|
| **P0** | `MainActivity` (UI + IO + patch + xuất + state) | `SourceController`, `StyleEditor`, `PatchCoordinator`, `ExportController` + state model | Tránh race giữa chọn gói/patch/lưu; UI không sở hữu logic file |
| **P0** | `ApkPatcher` (ARSC, manifest, shortcut, DEX, ký) | `AliasManifestEditor`, `ResourceInjector`, `ShortcutEditor`, `DexModuleManager`, `PatchPipeline` | Các phần ít phụ thuộc nhau hơn; test từng bước. **Không đổi prefix/class/ID contract** |
| **P1** | `PackageSets` (đọc ZIP, chọn split, ký, đóng gói) | `PackageArchiveReader`, `SplitSelector`, `PackageOutputWriter`, `StagingWorkspace` | Bớt logic lẫn lộn APK/APKS/XAPK/APKM; dễ thêm metadata/OBB |
| **P1** | `SigningPanel` / `SignerManager` / `PortableKeyManager` | `SigningIdentityRepository` và `SigningUseCase` với UI riêng | Giảm luồng mật khẩu qua UI; tách khóa riêng và các lựa chọn |
| **P1** | `PatchedApkVerifier` | `SignatureVerifier`, `AliasContractVerifier`, `ResourceVerifier` | Báo lỗi rõ từng lớp; test độc lập |
| **P2** | `StyleStore` / style rows trong `MainActivity` | `StylePresetRepository`, `StyleRowModel`, `StyleListView` | Bảo trì UI động, config nhiều styles |
| **P2** | `RealSplitMerger` | Service độc lập có dữ liệu `MergeReport` | Test các dạng split và thông báo tương thích |
| **P2** | `WebDownloadActivity` / `InstalledAppPicker` | Các nguồn kiểu `SourceProvider` | Thêm nguồn mà không sửa patcher |
| **Giữ ổn định** | **`inject/ChangeDisplayActivity`, `AliasRefreshReceiver`** | **Không cần tách** nếu không thêm chức năng module | Nhỏ, tự chứa, làm đúng một nhiệm vụ; tránh phá tương thích APK đã phát hành |

Cấu trúc tương lai có thể là `app/ui`, `app/application`, `app/data`, `patcher/manifest`, `patcher/resources`, `patcher/dex`, `patcher/verify`, **và một `inject/` độc lập**.

**Thứ tự refactor an toàn:** trước tiên tạo test từ trạng thái hiện tại; tách hàm thuần không đổi API; chạy cùng test; sau đó mới đổi điều phối UI. Không đổi kiến trúc và sửa lỗi APK trong một commit lớn.

## 10. Các giới hạn / rủi ro đang biết — tránh nhầm là đã hoàn thành

1. **DEX độc lập không có nghĩa module bất biến về binary giữa mọi phiên bản.** Với cùng `asw_module.dex`, APK nào cũng nhận nội dung đó. Khi thay code `inject`, build DEX mới và lần patch tiếp theo thay bản cũ; cần test tương thích ngược. Không cần sửa `inject` chỉ để tăng số alias.
2. **Tân trang lại APK:** hiện tập trung vào APK do chính aliaswi tạo và giữ cấu trúc nhận diện hợp lệ. Nếu file bị sửa ngoài hoặc các phiên bản cũ không đúng hợp đồng, patcher **có thể từ chối để tránh chèn trùng/làm hỏng app**. Không tự động “sửa mọi APK có alias bất kỳ”.
3. **Resource rác khi giảm styles:** alias thừa được loại khỏi manifest, nhưng các mục ARSC/file không còn tham chiếu **chưa được garbage collect toàn diện**. Có thể khảo sát cleanup có kiểm chứng sau, không xóa liều resource cũ.
4. **Shortcut:** launcher có thể cache icon/menu, và Android có giới hạn shortcut hiển thị; khai báo XML đúng không chứng minh shortcut cập nhật tức thời trên mọi hãng máy.
5. **Một nguồn APK phức tạp không tự động là lỗi resource:** ARSCLib nhận nhiệm vụ read/write; cần testcase cụ thể trước khi tạo hệ thống đặc biệt. Tuy nhiên các cấu hình split, resource ID, manifest đặc thù vẫn phải kiểm chứng khi gặp lỗi thật.
6. **XAPK/APKM metadata:** code đóng gói giữ nhiều mục archive gốc; **chưa có bước cập nhật mọi checksum/metadata của mọi định dạng bên thứ ba**. Không bảo đảm installer bên ngoài chấp nhận mọi archive xuất ra.
7. **OBB:** không có thao tác copy/cài OBB tự động. Xuất APK phần ứng dụng không đồng nghĩa APK độc lập hoàn toàn khỏi OBB.
8. **Tệp lớn và vòng đời:** còn nhiều file tạm, thao tác nền bằng `new Thread`; chưa có cancel/progress chính xác, checkpoint/khôi phục tác vụ qua recreation hoặc queue job chuyên biệt.
9. **Security:** `targetSdk 28` là hiện trạng, không phải khuyến nghị lâu dài. Quyền `QUERY_ALL_PACKAGES`, `INTERNET`, `REQUEST_INSTALL_PACKAGES` cần xem theo chính sách phân phối mục tiêu. Cấu hình ký debug có thông tin trong repo: **chỉ nên coi là key test**, không dùng cho sản phẩm bảo mật cao.
10. **Verifier không phải chứng minh 100% runtime:** verifier hiện kiểm tra cấu trúc/giải tham chiếu PNG header và chữ ký; không thay thế kiểm thử chạy app, launcher, DEX loading, install split thực tế. PNG thương hiệu đã từng hỏng do truyền base64: hãy kiểm tra toàn vẹn file trước khi push.
11. **“Không giới hạn styles” chỉ là không hardcode 3**: vẫn có giới hạn thực tế của Android, tài nguyên, bộ nhớ và kích thước APK. Không hứa hỗ trợ vô hạn theo nghĩa toán học.

## 11. Ma trận test tối thiểu trước mỗi lần phát hành

**A. Styles/alias:** 1, 2, 3, 5, 10 styles; ô trống xen kẽ; ô thiếu một thành phần; thêm/xóa hàng; lưu preset và mở lại; icon white/transparent; thử nhiều ứng dụng khác nhau.

**B. Launcher/shortcut:** Alias0, Alias1, Alias cuối; chuyển ngược xuôi; nhấn giữ icon đang hoạt động và mở Change Display; xác minh label/icon từng style và **shortcut luôn là brand icon**, không mượn Style1 hoặc icon gốc.

**C. Re-refurbish:** patch app sạch → patch kết quả lần 1 với N lớn → lần 3 với N nhỏ → lần 4 N lớn; kiểm tra một DEX aliaswi duy nhất, một Change Display activity, một receiver, không duplicate shortcut; Alias0 vẫn giữ tên/icon gốc.

**D. Package:** APK độc lập; app đã cài một APK; app đã cài có splits; APKS/XAPK/APKM; universal; merge thật; nguồn chứa OBB (kiểm tra cảnh báo và giới hạn rõ). Test `PackageInstaller` với certificate tương thích và không tương thích.

**E. Export/state:** patch hai package liên tiếp; đổi nguồn giữa lúc patch; mở picker lưu/cancel/thử lại; tên file đề xuất luôn từ APK kết quả hiện tại; file tạm được dọn; không “rò” kết quả đã ký của package trước.

**F. Build:** task `generateAliasDex` sinh ra 1 `asw_module.dex` đúng; app manifest có brand icon; PNG hợp lệ; debug ký thành công; `versionCode` chỉ tăng khi chủ dự án yêu cầu.

## 12. Checklist cho người tiếp quản khi muốn bổ sung tính năng

1. Xác định tính năng nằm ở **Refurbished chính**, **patcher**, hay **DEX trong APK đích**.
2. Nếu **chỉ là nhập/xuất, UI, signing, OBB, progress, format**: sửa `app`/`patcher`, **giữ `inject` và contract aliaswi**.
3. Nếu đổi `Change Display` hoặc cách bật/tắt launcher: sửa `inject`, tạo DEX mới, kiểm tra `ApkPatcher` + `PatchedApkVerifier` + APK đã tân trang trước đó.
4. Nếu đổi cấu trúc resources/manifest: xác định ảnh hưởng tới **Alias0**, tất cả alias, XML static shortcuts, re-patch, receiver và khả năng cài đè.
5. Khi patch lại APK: **không nhân bản DEX aliaswi**, không làm mất launcher gốc, không xóa shortcut của app đích.
6. Không làm thay đổi signing identity/package name/version code ngoài phạm vi yêu cầu.
7. Chạy/đánh giá ma trận test mục 11 và ghi rõ những gì **chưa test**.
8. Cập nhật `Note.md` nếu thay đổi ranh giới module, class, tên resource, task build hoặc khả năng hỗ trợ nguồn.
9. Tạo commit nhỏ, có thể rollback; **không tự kích hoạt GitHub Actions nếu chưa được yêu cầu**.

---

**Tóm tắt nguyên tắc quan trọng nhất:** `app` tổ chức quy trình và giao diện; `patcher` tạo và kiểm tra kết quả APK; `inject` chỉ cung cấp Change Display + khôi phục alias sau update. **Giữ hợp đồng aliaswi ổn định để một module DEX sử dụng lâu dài cho nhiều APK, nhiều số styles và nhiều lần tân trang.**
