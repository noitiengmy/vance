# UX_UI.md — Vùng sửa giao diện **chỉ cho ứng dụng Refurbished**

> **Mục tiêu:** người mới tiếp quản có thể thay đổi **thiết kế, layout, nút bấm, icon, typography, màu sắc, nhãn, strings, ngôn ngữ và trải nghiệm sử dụng của app chính** mà **không chạm vào bộ máy tân trang APK** hay module DEX `aliaswi` đã ổn định.
>
> **Mốc đối chiếu:** mã nguồn `main` tại commit `7f37329` (tài liệu `Note.md` vừa được bổ sung). Đây là bản đồ **file và phương thức đang tồn tại**, không phải hướng dẫn viết lại app. Dùng `Note.md` nếu cần kiến trúc đầy đủ.
>
> **Nguyên tắc:** **UI sửa ở `app/`**, **logic patch giữ nguyên trong `patcher/`**, **DEX nhúng vào app đích giữ nguyên ở `inject/`**. Thay đổi thuần giao diện không đòi hỏi sửa `AndroidManifest.xml`, `app/build.gradle`, D8 hay `asw_module.dex`.

## 1. Danh sách file được sửa cho tác vụ UX/UI

| Mức | File hiện tại | Được sửa những gì? | Không được tùy tiện thay |
|---|---|---|---|
| **A — Giao diện trực tiếp** | `app/src/main/java/com/maico/refurbished/FlatUi.java` | Phong cách nút: kích cỡ, padding, viền, text, căn icon, tints, dark/light | Chữ ký hàm `button`, `tab`, `icon` nếu chưa sửa mọi nơi gọi |
| **A** | `app/src/main/java/com/maico/refurbished/UiSections.java` | Thanh tab, lựa chọn tab, bố cục điều hướng, khoảng cách, chỉ báo busy | Quy ước `panels[0]=Sources`, `[1]=Styles`, `[2]=Signing`, `[3]=Output` và các lời gọi `show(index)` |
| **A** | `app/src/main/java/com/maico/refurbished/UiSettings.java` | Dialog cài đặt giao diện, cách trình bày lựa chọn ngôn ngữ/theme | Giá trị lưu `language` = `auto/en/vi` và `theme` = `auto/light/dark`; các API `wrap/dark/theme/show` |
| **A** | `app/src/main/res/drawable/ic_flat_*.xml` | Hình vector của từng icon nút, đường nét, kích thước | Tên resource đang được Java tham chiếu nếu không sửa tham chiếu |
| **A** | `app/src/main/res/drawable/flat_button_light.xml`, `flat_button_dark.xml` | Nền/viền/radius, pressed và disabled states | Khả năng hiển thị rõ ở cả hai theme |
| **A** | `app/src/main/res/values/strings.xml` và `values-vi/strings.xml` | Văn bản, tên nút/tab, lời nhắc, mô tả, Việt–Anh | **Tên resource**, placeholders `%1$d`, `%1$s` và escape XML; string dùng trong xử lý không chỉ là trình bày |
| **A** | `app/src/main/res/values/arrays.xml` và `values-vi/arrays.xml` | Nhãn hiển thị của danh sách ngôn ngữ/theme | Thứ tự các tùy chọn mà `UiSettings` ánh xạ bằng index |
| **B — UI pha lẫn nghiệp vụ** | `app/src/main/java/com/maico/refurbished/MainActivity.java` | **Chỉ** các đoạn dựng view, nhóm nút, layout, tiêu đề, spacing, style row, hiển thị status | Callbacks, state, request code, `startPatch`, signer, file IO, luồng nền, URI, kiểm soát lựa chọn APK |
| **B** | `app/src/main/java/com/maico/refurbished/SigningPanel.java` | Trình bày bảng Signing, icon, margin, layout controls | `snapshot()`, `Choice`, chế độ ký, mật khẩu, alias, URI và hành vi chọn khóa |
| **B** | `app/src/main/java/com/maico/refurbished/InstalledAppPicker.java` | Danh sách, ô tìm kiếm, font, icon/hàng, hộp thoại | Query PackageManager, path `sourceDir/splitSourceDirs`, dữ liệu trả cho callback |
| **B** | `app/src/main/java/com/maico/refurbished/WebDownloadActivity.java` | Thanh địa chỉ, nút, spacing, status, trình bày WebView | Quy tắc HTTPS, DownloadManager, file trả về, cookie/permission, lifecycle receiver |
| **B** | `app/src/main/java/com/maico/refurbished/PortableKeyDialog.java` | Cách trình bày hộp thoại nhập mật khẩu | Giá trị password, xóa char[], callback nhận mật khẩu, việc không lưu plaintext |
| **B** | `app/src/main/res/drawable-nodpi/brand_icon.png` | **Chỉ khi được yêu cầu đổi icon thương hiệu** | Đây là icon **dùng chung app chính + Change Display shortcut**, không phải chi tiết trang trí thuần UI |

**Thứ tự tìm kiếm ngắn nhất:** đổi màu/kiểu nút → `FlatUi` và XML drawable; đổi tab → `UiSections`; đổi lời thoại → `strings.xml`; đổi cách bố trí các màn chính → phần dựng View của `MainActivity.onCreate()`; đổi danh sách style → phần `addStyleRow()`.

## 2. Các phần **không đụng đến** khi chỉ sửa giao diện

```text
inject/                              # Không sửa DEX dùng chung cho APK đích
patcher/                             # Không sửa manifest/ARSC/DEX injection/signing/verifier
app/src/main/java/.../PackageSets.java
app/src/main/java/.../RealSplitMerger.java
app/src/main/java/.../PackageInstall.java
app/src/main/java/.../InstallReceiver.java
app/src/main/java/.../SignerManager.java
app/src/main/java/.../PortableKeyManager.java
app/src/main/java/.../StyleStore.java
app/src/main/java/.../DefaultKey.java
app/src/main/AndroidManifest.xml     # Không cần sửa cho layout/nút/strings
app/build.gradle                     # Không cần sửa cho layout/nút/strings
app/src/main/assets/default.p12      # Không đụng khóa ký
.github/workflows/build.yml         # Không sửa workflow cho yêu cầu thiết kế
```

**Ngoại lệ chủ động:** nếu yêu cầu thực sự là *đổi chức năng* (thêm workflow, thay dữ liệu persist, thêm screen/activity mới, đổi permission, thay kiến trúc ký/patch) thì đây **không còn là cosmetic UI-only**. Phải mở rộng phạm vi một cách rõ ràng thay vì lén sửa những file ngoài danh sách.

**Bất biến UI-only:** **giữ nguyên `versionCode` (đang là 3)**, applicationId, manifest, aliaswi prefix/class, `asw_module.dex`, APK signing identity và logic gói APK.

## 3. Bốn màn của app chính và nơi chỉnh

Màn hình hiện **dựng bằng Java**, **không có layout XML cho từng màn**. Dễ nhầm nếu chỉ tìm `res/layout/*.xml`: hiện không phải nguồn giao diện chính.

| Tab (index) | Nội dung | Nơi dựng |
|---|---|---|
| **Sources [0]** | Chọn APK; tìm app đã cài; web; xuất bản gốc; mô tả APK; chọn launcher | `MainActivity.onCreate` — `uiSections.panels[0]`; giao diện danh sách riêng ở `InstalledAppPicker` |
| **Styles [1]** | Mặc định 3 style; Add more style; icon picker/preview; lưu/xóa cấu hình | `MainActivity.onCreate` — `panels[1]`, và `addStyleRow()`, `refreshStyleNumbers()`, `displayIcon()` |
| **Signing [2]** | Chọn kiểu ký, custom/portable P12, xem fingerprint | `MainActivity.onCreate` — `panels[2]`, và `SigningPanel`, `PortableKeyDialog` |
| **Output [3]** | Patch, gộp thành standalone, xuất file, cài gói | `MainActivity.onCreate` — `panels[3]` |

**Muốn đổi visual hierarchy:** có thể đổi thứ tự hiển thị button/view **trong cùng panel**, nhưng giữ các tham chiếu tới nút, enabled/disabled state, listener và chức năng thực tế. **Không hoán đổi chỉ số panel** mà không rà toàn bộ `uiSections.show(0..3)` và các luồng chuyển tab sau khi báo lỗi/thành công.

## 4. Các vùng trong `MainActivity.java`: “nên sửa” / “không sửa”

Dựa trên những phương thức hiện có, **ưu tiên tìm theo tên phương thức thay vì dòng cố định**, vì số dòng thay đổi sau mỗi commit.

### 4.1. Vùng có thể sửa theo yêu cầu thẩm mỹ

- **`onCreate()` — phần khởi tạo layout:** ScrollView, `LinearLayout`, header, card, settings button, status text, thứ tự và khoảng cách các nhóm.
- **`onCreate()` — các nút trong `uiSections.panels[0..3]`:** label, padding, style XML, icon, gravity, kích cỡ, tổ chức view.
- **`addStyleRow()`:** bố cục hàng Style, EditText, preview ImageView, ô chọn background, nút chọn icon/xóa style.
- **`refreshStyleNumbers()`:** cách hiển thị `Style N`, hint/nội dung nút (giữ mapping theo index thật).
- **`sectionTitle()`, `text()`, `dp()`:** helper hiển thị, font, spacing.

### 4.2. Vùng **không sửa** nếu chỉ đổi giao diện

- **`onActivityResult()`, `openImages()`, `openFile()`, `grantReadAccess()`:** request code + picker + URI permissions; thay ID hoặc thứ tự có thể phá chọn ảnh/APK/keystore.
- **`inspectSource()`:** async import; `selectionSerial`, `currentPackage`, `launcherOptions`; chạm sai gây hiện kết quả/launcher của APK khác.
- **`startPatch()`:** phân biệt các hàng trống/không hoàn chỉnh; `namesToPatch/iconsToPatch/backgroundsToPatch`, `signingChoice`, `PackageSets`; không thay logic compact style hay chuyển package.
- **`saveCurrentStyles()`, `clearEditors()`:** thay đổi dữ liệu/hành vi persistence; sửa giao diện thì chỉ can thiệp `addStyleRow`, tránh ảnh hưởng preset.
- **`displayIcon()`:** chỉ sửa text/placeholder và presentation; **giữ** `previewSerial`, `styleRows.contains(row)`, luồng preview.
- **`createSigner()`, `inspectSigningIdentity()`, `installedSignatureMatch()`:** ký/chứng chỉ.
- **`requestExport()`, `exportSignedApk()`, `ensureDocumentName()`:** tên xuất và URI, trạng thái file đã ký; không “rút gọn” vì đã có lỗi dùng tên package lượt trước.
- **`installFromUri()`, helper copy/stream/file:** IO và cài đặt.

**Ví dụ an toàn:** đổi `FlatUi.button(this,patchButton,R.drawable.ic_flat_package)` sang icon/bố cục khác trong `onCreate`, **không xóa** `patchButton.setOnClickListener(...startPatch(...))`.

## 5. Quy ước hiển thị và dữ liệu phải giữ nguyên

- **Nút nguồn:** chọn file/ứng dụng đã cài/web; giữ lời gọi inspector và lựa chọn launcher. Không tái sử dụng một Button giữa hai luồng bất đồng bộ nếu làm thay đổi listener.
- **Styles:** mở sẵn **3 hàng** nhưng **không yêu cầu nhập đủ 3**; có thể Add more style; các hàng trống hoàn toàn bị bỏ qua; cặp thiếu tên hoặc icon bị từ chối. UI mới phải giữ `StyleRow` và `styleRows` là dữ liệu thực, không chuyển sang 3 mảng cứng.
- **Chọn icon:** sự kiện gắn `pendingStyle` đúng hàng; khi người dùng quay lại picker không được điền ảnh sai hàng.
- **Background icon:** `0=white`, `1=transparent`; đổi chữ/skin của Spinner nhưng không đảo giá trị bên dưới.
- **Signing:** UI có thể đổi cách trình bày, nhưng phải giữ `SigningPanel.Choice`, chế độ ký, alias và password logic; không lưu hay log mật khẩu.
- **Output:** nút Patch/Single/Export/Install có trạng thái enabled/disabled khi chưa có đầu vào, đang chạy hoặc xuất xong; đừng biến một nút disabled thành nút bấm có thể chạy sai trạng thái.
- **Status/progress:** `UiSections.busy(boolean)` đang hiện/ẩn horizontal indeterminate ProgressBar. Đổi hình thức thanh tiến độ được; không được gọi nhầm tiến độ phần trăm nếu chưa có metrics thực.
- **Ngôn ngữ và theme:** `UiSettings.wrap`, `UiSettings.dark` và `theme` quyết định hiển thị; kiểm tra cả EN/VI, Light/Dark.
- **Thương hiệu:** `brand_icon.png` là **icon chung cho Refurbished và static shortcut Change Display**; không đổi riêng cho một phía mà không có yêu cầu rõ ràng.

## 6. Đổi `strings.xml` mà không làm hỏng hành vi

Hai file chính:

```text
app/src/main/res/values/strings.xml
app/src/main/res/values-vi/strings.xml
```

- Giữ **`name` của string** ở cả hai bản; chỉ thay phần text.
- Giữ nguyên định dạng đối số (`%1$d`, `%1$s` v.v.). Ví dụ `status_incomplete_style` cần số thứ tự style — không xóa placeholder.
- Kiểm tra chuỗi có dấu `'`, `&`, `<`, dấu nháy kép theo quy tắc XML Android, tránh build lỗi vì escape.
- Đối chiếu cả `res/values/arrays.xml` và `values-vi/arrays.xml`: **không tự đảo thứ tự** lựa chọn language `auto/en/vi` và theme `auto/light/dark` vì code lấy bằng chỉ số.
- Một số thông báo vẫn đang hardcode tiếng Anh trong Java (ví dụ `MainActivity.displayIcon`, `WebDownloadActivity`, `InstalledAppPicker`). Có thể chuyển **chỉ text hiển thị** sang resources, nhưng không được thay giá trị file extension, MIME, đường dẫn, package, key preferences, action/intent hoặc thông số dùng làm logic.
- Đổi chữ "Change Display" ở **app chính** không đồng nghĩa đổi tên shortcut trong APK đích. Shortcut label hiện do **`patcher/ApkPatcher`** tạo và dialog title nằm trong **`inject`**. Đó là **hai vùng ngoài phạm vi UI-only**; muốn đổi phải có yêu cầu bổ sung và cân nhắc tương thích.

## 7. Đổi theme, icon và thương hiệu

**Flat UI** hiện dùng `FlatUi.button`, `tab`, `icon` và XML selector. Có thể tăng bán kính góc, chuyển sang Material-like, đổi spacing, kích thước chạm, tints. Nếu chuyển sang palette mới, kiểm tra contrast Light/Dark và `state_enabled=false`.

**Header/tab:** `UiSections.show()` tự đổi background, màu text và icon cho tab đang chọn. Đừng chỉ sửa màu nút ban đầu mà bỏ qua trạng thái selected.

**Icon vector:** `res/drawable/ic_flat_*.xml`; đổi `android:pathData` hoặc tạo resource mới, sau đó cập nhật ID tương ứng trong `FlatUi/UiSections/MainActivity`. **Không cần sửa `patcher/IconNormalizer.java`**: lớp đó xử lý icon do người dùng nhập cho APK đích, không xử lý icon trang trí UI.

**Icon thương hiệu:** `res/drawable-nodpi/brand_icon.png` đang khai báo trong manifest Refurbished và được app đọc khi patch để truyền sang shortcut. Thay ảnh **phải có yêu cầu riêng**, giữ PNG hợp lệ (đọc được, CRC/IEND), vì đã từng xảy ra upload PNG bị lỗi. Không thay ảnh bằng ảnh minh họa tìm trên web/phiên bản tự dựng khác nếu người dùng không yêu cầu.

## 8. Có nên chuyển sang layout XML?

**Không bắt buộc.** Hiện phần lớn UI được tạo bằng Java nên có thể redesign nhanh trực tiếp trong các vùng View của `MainActivity` và helper `FlatUi`.

Nếu muốn chuyển từng phần thành XML để bảo trì dễ hơn:

1. Giữ nguyên `MainActivity`, `UiSections` và các logic callback; tách **chỉ layout** của từng panel ra file XML `res/layout/` khi cần.
2. Dùng `LayoutInflater` và `findViewById` để tham chiếu các View cũ, sau đó nối **đúng listener và state**.
3. Trước tiên chuyển 1 vùng tĩnh như header/tab hoặc Signing panel, **không chuyển cả bốn màn một lần**.
4. Styles là danh sách động; nên tách thành `StyleRowView`/adapter riêng nếu cần. Không quay lại ba nút/ba hàng cố định.
5. Đối chiếu mọi luồng import → Styles → Signing → Output, save/cancel picker, rotate/recreate, Light/Dark, EN/VI.

**Không đổi package/class `inject` chỉ vì app chính chuyển từ Java View sang XML.**

## 9. Checklist chỉnh giao diện nhanh — scope tối thiểu

| Yêu cầu | Chỉ nên sửa |
|---|---|
| Đổi font/khoảng cách/border/radius nút | `FlatUi.java`, `flat_button_*.xml` |
| Đổi hình icon trong nút | `ic_flat_*.xml`; nếu cần ID mới thì nơi sử dụng trong `app` |
| Đổi tab dưới/trên, selected state | `UiSections.java`; text trong `strings.xml` |
| Đổi giao diện Sources / Styles / Output | **Chỉ** phần View dựng trong `MainActivity.onCreate()`; Styles thêm `addStyleRow()` |
| Đổi giao diện Signing | `SigningPanel.java`, phần View của `MainActivity.onCreate()` |
| Đổi ngôn ngữ/chuỗi/nhãn | `values/strings.xml`, `values-vi/strings.xml`, arrays hiển thị; các literal UI được phép chuyển sang string |
| Đổi màu light/dark và hộp thoại Settings | `UiSettings.java`, `FlatUi.java`, `UiSections.java` |
| Đổi giao diện chọn ứng dụng đã cài | Chỉ phần tạo View trong `InstalledAppPicker.java` |
| Đổi giao diện trình duyệt tích hợp | Chỉ phần tạo View trong `WebDownloadActivity.java` |
| Đổi giao diện hộp thoại mật khẩu | `PortableKeyDialog.java` và phần trình bày `SigningPanel.java` |
| Đổi icon launcher thương hiệu | `brand_icon.png` **chỉ theo yêu cầu rõ ràng**, giữ dùng chung với shortcut |

### Quy trình làm việc khuyến nghị khi yêu cầu chỉnh UX/UI

1. Xác định **vùng visual** và file liên quan theo bảng trên; đọc đúng đoạn thay vì chỉnh toàn repo.
2. Tạo thay đổi ít nhất có thể; **không chạy refactor logic** kèm một commit giao diện.
3. Giữ nguyên field/ID của View, callbacks, `StyleRow`, `uiSections.show(index)`, state button, xử lý signer và request codes.
4. Kiểm tra Java compile / XML syntax nếu có khả năng; kiểm tra mọi chỗ tham chiếu `R.drawable`, `R.string`.
5. Kiểm tra các tình huống UI: bật app, điều hướng 4 tab, add/remove style, chọn ảnh, light/dark, EN/VI, nút disabled và status.
6. Commit **chỉ các file UI** lên `main` nếu được yêu cầu; ghi rõ **đã/ chưa build và test**. **Không tự chạy GitHub Actions**.
7. **Không tăng `versionCode`**, không đổi khóa ký, không sửa DEX/patcher khi người dùng chỉ yêu cầu hình thức.

## 10. Tiêu chí hoàn thành một thay đổi UX/UI-only

Một bản chỉnh giao diện được coi là **đúng phạm vi** khi:

- File sửa nằm trong **whitelist mục 1** và cần thiết cho yêu cầu cụ thể.
- Các luồng chọn nguồn APK, số styles động, ký, patch, xuất và cài đặt vẫn có nút/callback tương ứng.
- Không thay đổi logic dữ liệu, tên resource contract, DEX aliaswi hoặc chữ ký APK đích.
- Giao diện đủ dễ đọc và dễ bấm ở Light/Dark, bản EN/VI.
- Tính năng Change Display trong APK đích không bị ảnh hưởng bởi bất kỳ thay đổi visual nào của app Refurbished.

**Nhớ ngắn gọn: sửa vẻ ngoài của Refurbished → chỉ sửa View và tài nguyên UI trong `app/`. Giữ `patcher/` và `inject/` nguyên vẹn.**
