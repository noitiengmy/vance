# Vance

**Vance** là không gian làm việc Android theo kiến trúc mô-đun, dùng để kiểm tra, tân trang, ký, xuất và cài đặt các gói ứng dụng thuộc họ APK. Mã định danh ứng dụng Vance được cài đặt trên thiết bị là `com.maico.vance`.

## Sản phẩm và kiến trúc mô-đun

Vance là ứng dụng chủ (host) và môi trường làm việc chung; từng chức năng được tổ chức thành mô-đun độc lập. Dashboard và bộ đăng ký mô-đun (module registry) được thiết kế để có thể bổ sung chức năng mà không phải tạo một ứng dụng Android riêng cho mỗi chức năng.

| Mô-đun | Định danh trong mã inject | Trạng thái |
|---|---|---|
| Furbish (tên trên giao diện: Refurbish) | `ipa.maico.furbish` | Mô-đun đầu tiên đã tích hợp; kế thừa quy trình Refurbished Display |
| Monitor | `ipa.maico.monitor` | Đã dành tên, chưa triển khai |
| Explorer | `ipa.maico.explorer` | Đã dành tên, chưa triển khai |
| Inspect Layout | `ipa.maico.inspectlayout` | Đã dành tên, chưa triển khai |
| Scan Agent | `ipa.maico.scanagent` | Đã dành tên, chưa triển khai |
| MoreTime | `ipa.maico.moretime` | Đã dành tên, chưa triển khai |
| SurfWeb | `ipa.maico.surfweb` | Đã dành tên, chưa triển khai; dự kiến chứa WebView tích hợp |

Các định danh đã dành trước chỉ là cam kết về namespace, **không** có nghĩa các mô-đun tương ứng đã được viết hoặc hoạt động.

## Các quyết định kỹ thuật

### Định danh ứng dụng và namespace mã nguồn

- Application ID của ứng dụng Vance cài trên máy: `com.maico.vance`.
- Namespace Android/Java hiện vẫn là `com.maico.refurbished`. Đây là lựa chọn có chủ đích: Application ID quyết định định danh gói được cài, còn việc đổi namespace mã nguồn/tài nguyên đòi hỏi đồng bộ các tham chiếu tới lớp R và mã Java, không cần thiết chỉ để đổi tên sản phẩm.
- Activity chính vẫn là `com.maico.refurbished.MainActivity`.
- Tên hiển thị của bản chính là **Vance**; bản debug dùng tên **Vance-debug** để dễ phân biệt trên thiết bị.
- Giữ ổn định Application ID, trừ khi có kế hoạch chuyển đổi rõ ràng. Thay đổi ID sẽ tạo ứng dụng cài đặt khác và không tự di chuyển dữ liệu của ứng dụng cũ.

### Ranh giới process và bảo mật

- Activity điều phối của mã inject: `ipa.maico.vance.VanceActivity`.
- Giao diện Vance và Activity của Furbish trong APK đích chạy ở process chuyên dụng `:vance`.
- Các process thuộc cùng một package thông thường dùng chung UID và vùng dữ liệu riêng của package. Process riêng tách biệt heap và vòng đời thực thi, **không** tạo định danh bảo mật Android mới.
- Các mô-đun thông thường không được tự ý chạy trong process chính của ứng dụng đích. Ngoại lệ dự kiến là một **agent tối thiểu** của Scan Agent dùng để quan sát các đối tượng runtime trong process đó; xem lộ trình ở cuối README.
- Giao tiếp giữa agent trong process chính và giao diện Vance trong `:vance` phải qua IPC phù hợp (Binder, Messenger hoặc AIDL khi cần). Không được giả định hai process chia sẻ đối tượng Java trực tiếp.
- **DEX không phải process**: class thuộc DEX inject có thể chạy cùng process với Activity gốc nếu component tương ứng được Android khởi tạo ở process đó.

### DEX và nạp mô-đun

- Duy trì một DEX lõi ổn định chứa dispatcher Vance, hợp đồng dùng chung/registry, Furbish và các helper gắn chặt với phần lõi.
- Mô-đun Gradle `:inject` biên dịch các class này. Mô-đun `:app` gọi D8 qua tác vụ `generateVanceCoreDex` và đóng gói kết quả dưới tên `vance_core.dex`.
- Patcher chèn DEX đó vào APK đích ở vị trí `classesN.dex` tiếp theo còn trống; **không thay thế DEX gốc**.
- Không tạo một DEX riêng cho từng nút trên dashboard. Chỉ tách mô-đun lớn hoặc mô-đun chuyên biệt cho process chính khi có lý do rõ ràng về kích thước, dependency hoặc nhu cầu runtime.
- Giữ ổn định hợp đồng dùng chung và dispatcher để phát triển từng mô-đun mà không phải liên tục thay đổi giao diện inject.

### Phân tách trách nhiệm

- `:app`: giao diện Vance, chọn gói đầu vào, quản lý khóa ký, xuất và cài đặt.
- `:inject`: mã chạy trong APK đích, gồm dispatcher, registry/hợp đồng mô-đun và runtime của các mô-đun.
- `:patcher`: kiểm tra gói APK, chuẩn bị/ghép split, chỉnh sửa manifest/tài nguyên, chèn DEX và xác minh đầu ra đã ký.
- Logic patch riêng cho Furbish phải được tách khỏi phần điều hướng Vance và các dịch vụ dùng chung.
- Chức năng Output về lâu dài phải độc lập với từng mô-đun. Luồng thực thi hiện tại vẫn gọi Furbish; `PatchPlan` đa mô-đun là công việc dự kiến.

### Bảo toàn ứng dụng đích và khả năng tương thích

- Bảo toàn package name, logic ứng dụng gốc, các DEX gốc, tài nguyên không liên quan và khai báo manifest không liên quan.
- Thêm component/tài nguyên inject với định danh namespace đã kiểm tra xung đột.
- Không thay thế Activity launcher gốc; phương thức truy cập Vance hiện tại là **static shortcut** bổ sung.
- Duy trì giao diện Vance trong `:vance`; không đưa các mô-đun thông thường vào process chính của APK đích.
- Xác minh đầu ra sau khi ký: chữ ký APK, package identity, DEX inject, các component dự kiến, launcher alias và shortcut resources.
- Không báo thành công hoặc xuất APK được mô tả là hợp lệ nếu patch, ký hoặc xác minh thất bại.
- Không cam kết tương thích runtime với mọi ứng dụng. Dynamic feature, kiểm tra integrity theo ứng dụng, ràng buộc native ABI, launcher bất thường, split phức tạp và dữ liệu OBB ngoài APK đều cần kiểm thử riêng. Chỉ cảnh báo giới hạn; không âm thầm vượt qua cơ chế bảo vệ của ứng dụng.

### Ký và cài đặt

- Tất cả APK trong cùng bộ split phải được ký nhất quán bằng cùng một định danh ký được chọn.
- Không thể cập nhật đè thông thường khi cùng package name nhưng chứng chỉ ký mới không tương thích với chứng chỉ đã cài.
- Khi đó, Vance lưu tạm đầy đủ bộ APK ở vùng dữ liệu riêng, cảnh báo việc gỡ ứng dụng sẽ xóa dữ liệu của ứng dụng đích, yêu cầu Android xác nhận gỡ và chỉ gửi yêu cầu cài mới sau khi hệ thống xác nhận đã gỡ. Android có thể tiếp tục yêu cầu người dùng xác nhận cài đặt.
- Nếu người dùng hủy gỡ ứng dụng, Vance không được tiếp tục cài bản mới. Không tự gỡ ứng dụng hoặc xóa dữ liệu khi chưa được xác nhận.
- Dùng lại khóa ký tương thích là cách ưu tiên khi cần bảo toàn dữ liệu của ứng dụng đích.
- Phân biệt cache icon/launcher với lỗi package/chứng chỉ; không tự xóa dữ liệu ứng dụng để sửa lỗi cache launcher.

### Quy tắc phát triển mô-đun tiếp theo

1. Giữ ổn định ID mô-đun và đăng ký thông qua Vance module registry.
2. Mặc định chạy giao diện và logic quản trị mô-đun trong `:vance`.
3. Chỉ đặt agent/hook tối thiểu vào process chính khi thực sự cần quan sát đối tượng runtime ở đó.
4. Dùng IPC khi vượt ranh giới process; không giả định chung bộ nhớ Java.
5. Giữ DEX lõi ổn định, chỉ chia DEX khi có lý do kỹ thuật đáng kể.
6. Explorer dùng PackageManager và những đường dẫn/dữ liệu mà Vance được phép truy cập; không giả định quyền truy cập không giới hạn vào `/data/app` hay dữ liệu riêng của ứng dụng khác.
7. Monitor, Inspect Layout, Scan Agent, MoreTime và SurfWeb tiếp tục là các mô-đun độc lập.
8. SurfWeb dự kiến là mô-đun WebView tích hợp.
9. Ổn định Furbish và hoàn thành kiểm thử hồi quy trước khi tổng quát hóa Output thành `PatchPlan` đa mô-đun.
10. Hạn chế đưa code patch riêng của từng mô-đun vào UI/điều hướng dùng chung.

## Cấu trúc repository

- `:app` — ứng dụng Vance builder được cài trên thiết bị.
- `:inject` — runtime và các mô-đun được inject vào APK đích.
- `:patcher` — bộ máy biến đổi APK dùng chung.
- [`furbish.md`](furbish.md) — các quyết định kế thừa từ Refurbished Display, quy tắc và giới hạn tương thích riêng của Furbish.
- [`VANCE_ARCHITECTURE.md`](VANCE_ARCHITECTURE.md) — tệp dẫn hướng tương thích tới tài liệu kiến trúc chính; các quyết định chi tiết được duy trì tại README này.
- [`Note.md`](Note.md), [`UX_UI.md`](UX_UI.md) — tài liệu bổ sung của dự án.

## Trạng thái và xác minh hiện tại

Vance shell và tích hợp Furbish đã chạy trên thiết bị của người phát triển. Đã xác nhận quy trình tân trang/xuất gói thành công và luồng xác nhận gỡ → xác nhận cài khi chứng chỉ ký của bản mới khác bản đang cài. Đây là cột mốc kiểm thử cơ bản, **chưa** chứng minh tương thích rộng với nhiều loại APK.

Chưa thực hiện build trong lần sửa tài liệu này. Mọi thay đổi code tiếp theo cần được build và kiểm thử thực tế trên thiết bị. Cột mốc gần nhất vẫn là kiểm thử hồi quy và sửa lỗi Furbish bên trong Vance trước khi triển khai các mô-đun khác.

---

## Phương án mở rộng dự kiến: phương thức thứ hai để mở Vance và nền tảng giám sát ứng dụng đích

> **Trạng thái: đề xuất kỹ thuật, chưa triển khai.** Phần này là lộ trình phát triển từng bước; không mô tả tính năng đang hoạt động. Giữ nguyên static shortcut **Change Display** hiện có làm phương thức truy cập thứ nhất.

### 1. Mục tiêu

- Tạo **phương thức truy cập thứ hai** để mở `VanceActivity`/mô-đun quản lý từ chính giao diện của APK đích, kể cả trên launcher không hiển thị static shortcut.
- Ưu tiên **không sửa bytecode/DEX gốc**, không thay thế launcher Activity, không chiếm quyền điều khiển `Application.onCreate()` hoặc `Activity.onCreate()` của app gốc.
- Mã của cơ chế mới nằm trong DEX inject, được đăng ký bằng các khai báo manifest bổ sung do patcher xử lý.
- Hình thành nền tảng **Scan Agent** mở rộng dần: trước tiên quan sát Activity và hiển thị nút nổi kéo thả; về sau thêm các bộ quan sát UI, hiệu năng, network và I/O tùy khả năng can thiệp/tương thích thực tế.
- Giữ các mô-đun giao diện và quản trị trong `:vance`; agent nhỏ gọn mới chạy cùng process gốc.

### 2. Kiến trúc đề xuất

```text
APK đích sau khi patch
├── DEX gốc (giữ nguyên)
├── classesN.dex (mã inject)
├── Process chính của app đích
│   ├── Application / Activity gốc
│   ├── VanceBootstrapProvider     [DEX inject]
│   ├── VanceLifecycleObserver     [DEX inject]
│   ├── VanceFloatingControl       [DEX inject]
│   └── ScanAgent / các observer   [DEX inject; bổ sung dần]
│
└── Process :vance
    ├── ipa.maico.vance.VanceActivity
    ├── Furbish / Change Display
    └── Các màn hình mô-đun khác
```

**Lưu ý:** cùng một DEX có thể chứa class chạy ở nhiều process. DEX là đơn vị đóng gói bytecode, **không** phải đơn vị phân chia process hoặc bộ nhớ.

### 3. Bootstrap qua ContentProvider

Patcher dự kiến bổ sung một `ContentProvider` **không exported**, có authorities duy nhất theo package đích, được khai báo chạy trong **process chứa launcher Activity chính**.

- Android tạo Provider trong quá trình khởi tạo process tương ứng, thông thường **trước `Application.onCreate()`**.
- `Provider.onCreate()` chỉ đăng ký các observer cần thiết; tuyệt đối không làm I/O nặng hay khởi động UI tại đây.
- Provider là **class do dự án tự viết trong DEX inject**; Android chỉ cung cấp cơ chế khởi tạo và quản lý component.
- Nếu `<application android:process>` hoặc launcher Activity gốc có `android:process` riêng, patcher phải xác định process thực tế. Không mặc định tên process chính luôn bằng package name.
- Phải giữ nguyên khai báo process của các component gốc. Nếu có nhiều Activity chạy ở process khác, callback được đăng ký ở một process **không tự quan sát** Activity của process còn lại; hỗ trợ đa process là giai đoạn riêng.
- Khi triển khai cần kiểm chứng việc tạo Provider tại process thích hợp, xung đột authorities/class name, target Android API, multidex và tình huống class loading bất thường. Tránh khởi tạo cùng một agent nhiều lần.

**Phác thảo manifest (tên và authority chỉ mang tính minh họa):**

```xml
<provider
    android:name="ipa.maico.scanagent.VanceBootstrapProvider"
    android:authorities="TEN_PACKAGE_DICH.vance.bootstrap"
    android:exported="false" />
```

Không đặt `android:process=":vance"` cho Provider này: nếu làm vậy, lifecycle observer chỉ theo dõi các Activity trong process `:vance`, thay vì Activity gốc. Với trường hợp launcher Activity nằm ở process riêng, cần xử lý process đó một cách có chủ đích, không áp dụng manifest minh họa một cách máy móc.

### 4. ActivityLifecycleCallbacks — bộ quan sát đầu tiên

Trong `Provider.onCreate()`, lấy đối tượng `Application` của process hiện tại và đăng ký `Application.ActivityLifecycleCallbacks`.

- Theo dõi `onActivityCreated/Started/Resumed/Paused/Stopped/Destroyed` của Activity **cùng process**.
- Callback cung cấp chính **đối tượng `Activity`**, từ đó lấy `Window`, `DecorView`, content root và trạng thái UI qua API công khai.
- Không cần dò vị trí `onCreate()` trong bytecode gốc, không cần biết tên class Activity trước khi chạy.
- Lifecycle callback **không phải hook mọi phương thức**: không tự báo click của nút gốc, sự kiện WebView, lời gọi HTTP, truy vấn database hoặc I/O.
- Callback được gọi đồng bộ trên main thread. Nếu code agent ném exception, xử lý nặng hoặc giữ tham chiếu Activity sai cách, **vẫn có thể làm chậm/crash/leak app gốc** dù không sửa DEX gốc.

### 5. Nút nổi kéo thả để mở Vance — phương thức truy cập thứ hai

Bản đầu tiên chỉ cần gắn một nút nhỏ vào **view hierarchy của Activity đích đang hiển thị**, thông qua `Window.getDecorView()` hoặc content root phù hợp.

- Có thể tạo `FrameLayout`/View nhỏ nằm trên UI hiện tại; nút nhận click để mở `ipa.maico.vance.VanceActivity` bằng **explicit Intent**.
- Kéo thả để tránh che vùng tương tác; phân biệt click và drag bằng ngưỡng di chuyển; lưu tọa độ tương đối và giới hạn trong vùng hiển thị an toàn (insets/rotation).
- Không tạo lớp `View` phủ kín màn hình nhận touch. Chỉ vùng nút nhận touch; phần giao diện còn lại vẫn cho app gốc tương tác.
- Gắn nút theo lifecycle và trạng thái attach của decor; tránh gắn trùng khi resume nhiều lần, bỏ tham chiếu/cleanup lúc Activity destroy.
- Bỏ qua Activity inject/`:vance` để không tạo nút trên màn hình quản lý của chính Vance.
- Nút gắn trong cửa sổ Activity **không cần** quyền `SYSTEM_ALERT_WINDOW`; đây cũng **không phải** overlay toàn hệ thống. Dialog, PopupWindow, cửa sổ riêng hoặc bề mặt render đặc biệt có thể che nút.
- Tích hợp cờ bật/tắt, cơ chế chống crash lặp và chế độ không hiển thị nút nếu app đích không tương thích.
- Không thay thế hoặc xóa static shortcut đang hoạt động.

**Luồng dự kiến:**

```text
Android khởi tạo process app đích
  → VanceBootstrapProvider.onCreate()
  → Application.registerActivityLifecycleCallbacks(...)
  → Activity gốc được resume
  → VanceLifecycleObserver nhận Activity
  → VanceFloatingControl gắn nút vào cửa sổ Activity
  → Người dùng chạm nút
  → Explicit Intent mở VanceActivity trong :vance
```

### 6. Mở rộng giám sát theo từng lớp, không nhầm observer với hook

| Giai đoạn | Phạm vi muốn quan sát | Cơ chế khả thi | Giới hạn / nguyên tắc |
|---|---|---|---|
| 1 | Activity lifecycle, phiên foreground, thời lượng hiển thị | `ActivityLifecycleCallbacks` | Chỉ Activity cùng process, không bao phủ toàn bộ hành vi app |
| 2 | Kích thước/layout, cấu trúc View tại những thời điểm chọn lọc | `OnGlobalLayoutListener`, khảo sát view tree khi cần | Không quan sát mọi thay đổi; tránh duyệt liên tục, không ghi dữ liệu nhạy cảm |
| 3 | Hiệu năng/độ phản hồi, bộ nhớ process | `Choreographer`, `Debug`, `Runtime`, lấy mẫu có giới hạn | Đây là đo/ước lượng, không tự biết nguyên nhân từng hàm chậm |
| 4 | WebView/URL/navigation | Tích hợp `WebViewClient` nếu kiểm soát WebView; instrumentation riêng nếu không | Không có callback toàn cục hỗ trợ quan sát mọi WebView mà không can thiệp |
| 5 | Click/touch gốc, Service/Worker, HTTP, database, I/O | Thiết kế hook/instrumentation chuyên biệt theo API/framework | **Không đạt được chỉ bằng ContentProvider + LifecycleCallbacks**; cần đánh giá rủi ro từng hook |
| 6 | Các Activity/process phụ của app đích | Bootstrap/observer theo process và IPC | Không chia sẻ heap/static Java qua process; cần mapping process rõ ràng |

Ưu tiên observer chính thức, thụ động trước. Khi cần hook, phải xác định rõ **điểm hook**, phạm vi Android/API hỗ trợ, chi phí, nguy cơ làm thay đổi ngữ nghĩa gốc, khả năng khôi phục và ma trận kiểm thử. Không mặc định rằng chỉ cần nạp một DEX mới là có thể bắt mọi lời gọi Java hoặc native.

Không tự ý thay `View.OnClickListener`, `Window.Callback`, `WebViewClient` hoặc HTTP client của app gốc trong bản agent cơ bản; các kiểu thay thế này có thể ghi đè callback hiện hữu và làm hỏng chức năng. Không dùng `OnHierarchyChangeListener` như bộ cảm biến tổng quát vì nó không tự theo dõi toàn cây và có thể xung đột với listener gốc.

### 7. Cô lập lỗi, hiệu năng, quyền riêng tư

- Provider và lifecycle callback chỉ đăng ký hoặc xử lý thao tác nhẹ trên main thread; ghi log/serialize/phân tích chuyển sang worker với hàng đợi giới hạn.
- Mỗi observer có **enable/disable**, sampling, giới hạn bộ nhớ và cơ chế gỡ đăng ký; lỗi ở một observer không được kéo sập các observer còn lại.
- Bắt exception có kiểm soát, tránh đệ quy logging hoặc crash loop. Không có cơ chế nào bảo đảm an toàn tuyệt đối khi chạy thêm code trong process gốc.
- Không giữ strong reference tới Activity/View sau khi destroy. Đặc biệt thử nghiệm đổi cấu hình, xoay màn hình, multi-window và khởi chạy lại process.
- Dữ liệu giám sát chỉ thu thập khi được bật rõ ràng; mặc định hạn chế dữ liệu cá nhân, nội dung nhập liệu, token, cookie và thông tin định danh. Không thu thập bí mật của ứng dụng khác.
- Agent không được tự sửa logic nghiệp vụ, thay đổi network requests hoặc bỏ qua cơ chế kiểm tra integrity của app đích.
- Tách log/điều khiển khỏi giao diện quản trị qua IPC; ưu tiên mô hình chỉ đọc.

### 8. Lộ trình triển khai và kiểm thử

1. **Bản 0 — Giữ nguyên Furbish:** duy trì static shortcut, alias switching, kiểm thử hồi quy và toàn bộ quy tắc bảo toàn DEX gốc.
2. **Bản 1 — Bootstrap:** thêm Provider inject + lifecycle observer có thể bật/tắt, chỉ log tên component và các mốc lifecycle; chưa gắn UI.
3. **Bản 2 — Floating Control:** gắn nút kéo thả, click mở VanceActivity `:vance`, xử lý đa Activity, rotation, insets, cleanup và chống trùng.
4. **Bản 3 — Scan Agent:** chuẩn hóa registry các observer, kênh IPC và màn hình xem trạng thái/log; thêm giám sát UI/hiệu năng ở chế độ đọc.
5. **Bản 4 — Hook theo nhu cầu:** nghiên cứu từng hook cụ thể (WebView, touch, network, I/O…) như mô-đun độc lập, chỉ tích hợp sau khi đo tương thích và tác động tới ứng dụng gốc.
6. **Bản 5 — Đa process:** phát hiện process riêng của launcher và các Activity khác, bổ sung bootstrap có kiểm soát theo process và cơ chế tổng hợp dữ liệu.

**Ma trận kiểm thử tối thiểu cho phương thức thứ hai:** APK một Activity; nhiều Activity; launcher là `activity-alias`; app khai báo `android:process` ở cấp application hoặc Activity; app có sẵn ContentProvider; WebView; Jetpack Compose; Dialog/PopupWindow; màn hình fullscreen/immersive; xoay màn hình; foreground/background nhiều lần; multi-window; kill/restart process; APK nhiều DEX; APK split; ứng dụng có kiểm tra integrity. Kiểm tra đồng thời rằng static shortcut và launcher gốc không đổi hành vi.

**Tiêu chí chấp nhận:** app đích vẫn khởi chạy và vận hành thông thường; DEX gốc giữ nguyên; không trùng nút hoặc rò Activity; nút mở đúng Vance trong `:vance`; có thể tắt hoàn toàn agent; khi tính năng không tương thích thì thất bại có kiểm soát và không tuyên bố hỗ trợ giả. Mỗi giai đoạn chỉ được đánh dấu **đã triển khai** sau khi có code, build và kiểm thử thực tế.

*Ghi chú kiến trúc:* ContentProvider là điểm bootstrap, `ActivityLifecycleCallbacks` là bộ quan sát lifecycle đầu tiên, còn `DecorView` chỉ là điểm đặt nút điều khiển. Cả ba **không** phải một hệ thống hook toàn diện. Mục tiêu dài hạn của Scan Agent là mở rộng từng bộ quan sát/hook có kiểm soát mà vẫn giữ nguyên các DEX gốc và giảm tối đa tác động tới luồng thực thi của ứng dụng đích.
