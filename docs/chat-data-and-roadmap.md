# ConnectSpace: thiết kế DB và lộ trình kết bạn, chat

**Trạng thái:** tài liệu thiết kế, chưa phải migration hay API đã triển khai.
Tài liệu này dùng cho cả BE và FE. Sau mỗi bước, cập nhật mục Tiến độ và ghi
lại quyết định mới trước khi làm bước tiếp theo.

## 1. Quyết định sản phẩm

- Hai người **được nhắn tin trước khi kết bạn**. Tùy chọn chỉ nhận tin từ bạn bè làm sau.
- Có nhóm **công khai** và **riêng tư**. Nhóm công khai có thể cho vào ngay
  hoặc yêu cầu duyệt; nhóm riêng tư chỉ vào bằng lời mời.
- Thành viên mới chỉ thấy tin nhắn nhóm **từ lúc tham gia**. Rời nhóm rồi vào
  lại là một đợt tham gia mới; không thấy tin trong thời gian vắng mặt.
- Chặn người dùng ngăn yêu cầu kết bạn, mở chat/gửi tin riêng và mời nhóm mới;
  không tự động xóa lịch sử cũ. Quyền xem nhóm chung vẫn theo vai trò nhóm.
- REST + DB là nguồn dữ liệu gốc; realtime chỉ giúp cập nhật nhanh. Refresh hoặc
  mất kết nối phải khôi phục được trạng thái.

**Chưa thuộc MVP:** mã hóa đầu-cuối, cuộc gọi, voice, đính kèm, reactions,
push notification, tìm toàn văn. Không quảng bá chat là mã hóa đầu-cuối khi
chưa có thiết kế và triển khai riêng cho tính năng đó.

## 2. Nền tảng và nguyên tắc dữ liệu

- MySQL 8.4; mọi FK người dùng trỏ đến `users.id` hiện có (`BIGINT`), không dùng
  email làm khóa quan hệ. `users` hiện có `id`, `name`, `email`, `password_hash`,
  `auth_provider`, `provider_id`, `created_at`.
- Dùng `BIGINT` cho ID, `DATETIME(6)`/UTC cho thời gian, `utf8mb4` cho văn bản.
  Giá trị enum lưu dạng chuỗi ổn định; đổi tên giá trị cần migration.
- Mỗi thay đổi schema dùng migration có phiên bản và được thử trên MySQL 8.4.
  BE dùng Flyway và `spring.jpa.hibernate.ddl-auto=validate`. DB cũ đã có
  `users` cần baseline ở V1 một lần trước khi chạy image BE mới. Không tạo
  đè/xóa bảng production. H2/context-load test không xác nhận migration đã đúng
  trên MySQL.
- Dùng FK, `UNIQUE`, `CHECK`, index cùng với kiểm tra ở service. Mặc định không
  cascade xóa toàn bộ lịch sử khi xóa user; cần chính sách xóa/ẩn danh riêng.
- **Tạo bảng theo từng bước**, không tạo cả sơ đồ tương lai trong migration đầu.

## 3. Mô hình dữ liệu đích

```text
users
 ├─ friend_requests (lịch sử lời mời)
 ├─ friendships (quan hệ đã chấp nhận)
 ├─ user_blocks (chặn có hướng)
 └─ conversation_memberships ── conversations
                                  ├─ direct_conversations
                                  ├─ group_conversations
                                  └─ messages ── message_attachments

group_conversations ── group_join_requests / group_invitations
```

### 3.1 Kết bạn và chặn

| Bảng | Cột chính | Bất biến |
| --- | --- | --- |
| `friend_requests` | `id`, `user_low_id`, `user_high_id`, `requested_by_id`, `status`, `created_at`, `responded_at` | `low < high`, người gửi phải là một trong cặp. `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`. Lưu lịch sử nhưng tối đa một request `PENDING`/cặp. |
| `friendships` | `user_low_id`, `user_high_id`, `created_at` | PK là cặp ID được sắp xếp; chỉ một dòng cho A-B, không có dòng B-A. |
| `user_blocks` | `blocker_user_id`, `blocked_user_id`, `created_at` | PK có hướng; A chặn B không đồng nghĩa B chặn A. Hai ID phải khác nhau. |

Với MySQL, để giữ lịch sử request nhưng vẫn chống hai request `PENDING`, có
thể dùng hai generated column trả về cặp ID khi `status='PENDING'`, còn trạng
thái khác trả `NULL`, rồi tạo unique index trên cặp đó. Cú pháp và hành vi
đồng thời phải có test MySQL thực tế trước khi merge migration.

Chấp nhận lời mời và thêm `friendships` phải trong một transaction, cùng lúc
kiểm tra block/trạng thái. Gửi trùng, hai bên gửi cùng lúc, hủy khi bên kia
đang chấp nhận đều phải cho kết quả xác định, không tạo bản ghi trùng.

### 3.2 Cuộc trò chuyện và thành viên

| Bảng | Cột chính | Bất biến |
| --- | --- | --- |
| `conversations` | `id`, `kind`, `created_at`, `last_message_id` (nullable) | `kind = DIRECT` hoặc `GROUP`. `last_message_id` là cursor/cache để hiển thị inbox, cập nhật cùng transaction với tin nhắn. |
| `direct_conversations` | `conversation_id`, `user_low_id`, `user_high_id` | FK đến `conversations`; `UNIQUE(user_low_id,user_high_id)` để một cặp chỉ có một chat riêng. |
| `group_conversations` | `conversation_id`, `name`, `description`, `visibility`, `join_policy`, `created_at`, `updated_at` | `visibility = PUBLIC/PRIVATE`; `join_policy = OPEN/APPROVAL/INVITE_ONLY`. Public dùng OPEN/APPROVAL; private dùng INVITE_ONLY. |
| `conversation_memberships` | `id`, `conversation_id`, `user_id`, `role`, `joined_at`, `left_at`, `visible_after_message_id`, `last_read_message_id`, `muted_until`, `archived_at` | Mỗi lần vào nhóm là một dòng/đợt tham gia. Tối đa một membership đang hoạt động/user/conversation. `role = OWNER/ADMIN/MEMBER`; chat riêng chỉ dùng MEMBER. |
| `group_join_requests` | `id`, `conversation_id`, `user_id`, `status`, `created_at`, `resolved_at`, `resolved_by_id` | Yêu cầu vào nhóm công khai cần duyệt; tối đa một yêu cầu pending/user/nhóm. |
| `group_invitations` | `id`, `conversation_id`, `invited_user_id`, `invited_by_id`, `status`, `created_at`, `expires_at`, `resolved_at` | Lời mời vào nhóm; không có hai lời mời còn hiệu lực/user/nhóm. |

`OWNER` nằm ở membership, không lưu thêm `owner_user_id` ở bảng nhóm để tránh
hai nguồn sự thật. Mỗi nhóm phải có đúng một owner đang hoạt động; chuyển quyền
và owner rời nhóm cần transaction. Unique index trên owner đang hoạt động
có thể dùng generated column nullable; invariant “nhóm luôn có owner” cần
kiểm tra ở service vì FK đơn không biểu diễn được điều kiện này.

Chat riêng tạo đúng hai membership. Người dùng không “rời” chat riêng; có thể
archive/ẩn khỏi inbox theo từng người. Mở lại chat cũ không tạo conversation mới.

Khi vào nhóm, đặt `visible_after_message_id = conversations.last_message_id`.
Query tin nhắn chỉ trả `messages.id > visible_after_message_id` cho đợt tham
gia đang hoạt động. Tạo membership và gửi tin phải đồng bộ/lock trên cùng
conversation trong transaction để mốc hiển thị không bị race. Rời nhóm đặt
`left_at`; vào lại tạo membership mới. Thành viên đã rời không còn quyền đọc
tin nhóm, kể cả tin cũ, trừ khi sau này đổi chính sách một cách rõ ràng.

### 3.3 Tin nhắn và tệp

| Bảng | Cột chính | Bất biến |
| --- | --- | --- |
| `messages` | `id`, `conversation_id`, `sender_membership_id`, `client_message_id`, `kind`, `body`, `created_at`, `edited_at`, `deleted_at` | Phân trang theo ID trong từng conversation. `client_message_id` do client tạo cho retry idempotent. Ban đầu `kind = TEXT`; mở rộng `SYSTEM/ATTACHMENT` sau. `deleted_at` là xóa mềm cho mọi người. |
| `message_attachments` | `id`, `message_id`, `object_key`, `mime_type`, `byte_size`, `checksum`, `created_at` | Chỉ tạo ở bước đính kèm. Lưu metadata và object key, không lưu binary trong MySQL. |

FK của `messages` phải đảm bảo `sender_membership_id` thuộc **cùng**
`conversation_id` (ví dụ composite FK). Service kiểm tra membership còn hoạt
động tại lúc gửi. `UNIQUE(sender_membership_id, client_message_id)` chống
nhân đôi khi retry; `client_message_id` bắt buộc có giá trị.

`last_message_id`, `last_read_message_id`, `visible_after_message_id` là
cursor. Trước khi cập nhật phải xác minh message thuộc đúng conversation và
cursor đọc không lùi. FK đến `messages` cần thiết kế cẩn thận vì có vòng tham
chiếu `conversations` ↔ `messages`; không thêm FK đơn cho phép trỏ đến tin của
conversation khác.

### 3.4 Index và kiểm tra quyền

- `messages(conversation_id, id)` cho paging theo cursor, không dùng offset
  trên lịch sử chat dài.
- `conversation_memberships(user_id, left_at, conversation_id)` cho inbox;
  unique active membership trên cặp `(conversation_id, user_id)`.
- Index request kết bạn theo người nhận + status, friendship theo cả hai chiều;
  index join request/invitation theo nhóm và user.
- `group_conversations(visibility, join_policy, created_at)` cho tìm nhóm.
- Mọi API đọc/ghi kiểm tra JWT **và** membership, block, vai trò nhóm. Không tin
  `user_id` hay `role` từ client. User C không thể đọc chat riêng của A-B.
- Nhóm private không hiện trong danh sách khám phá công khai.

## 4. Lộ trình: mỗi bước đều thử được trên UI

Dùng hai tài khoản A/B và một cửa sổ ẩn danh (thêm C khi thử nhóm). Mỗi bước
gồm migration nếu cần, BE API + test, FE UI + build/lint, sau đó thử luồng thật.
Không merge một bước chỉ có DB/API mà chưa có cách kiểm tra trên UI.

| Bước | DB/API | UI và tiêu chí nghiệm thu |
| --- | --- | --- |
| **1. Vào ứng dụng** | Baseline migration DB hiện có; API tìm user có phân trang, không trả email nếu không cần. | `/app` bảo vệ bằng JWT, có tìm người dùng. A đăng nhập tìm thấy B; logout/hết token không vào được. Dùng style deep-ocean hiện tại. |
| **2. Kết bạn** | `friend_requests`, `friendships`; API gửi, hủy, chấp nhận, từ chối, liệt kê. | A gửi lời mời; B thấy và chấp nhận; cả hai thấy trạng thái. Thử gửi trùng, tự gửi cho mình, hủy/từ chối. |
| **3. Chat riêng REST** | `conversations`, `direct_conversations`, `conversation_memberships`, `messages`; API mở chat, gửi và lấy tin theo cursor. | A nhắn B dù chưa kết bạn; B refresh vẫn thấy. Mở lại không tạo chat trùng; C không đọc được. |
| **4. Inbox và đã đọc** | Dùng các cursor và `client_message_id`; API inbox, đánh dấu đã đọc, gửi idempotent. | Inbox sắp theo tin mới, có unread; xem tin cũ; retry không tạo bản sao. |
| **5. Realtime** | Kênh realtime có xác thực, reconnect và đồng bộ lại qua REST; chưa cần bảng DB mới. | A/B cùng mở chat, tin đến ngay; ngắt mạng/refresh vẫn đúng, không trùng. Thử qua proxy dev và Nginx production. |
| **6. Chặn** | `user_blocks`; API chặn/bỏ chặn, áp quy tắc cho chat riêng, kết bạn và mời nhóm. | A chặn B: B không gửi tin/mời được; lịch sử cũ vẫn theo chính sách. |
| **7. Nhóm riêng** | `group_conversations`, membership role, `group_invitations`; API tạo nhóm, mời, nhận lời mời, rời nhóm. | A tạo nhóm mời B/C; cả ba chat được, D không xem được. Người vào sau không thấy tin cũ. |
| **8. Nhóm công khai** | `visibility`, `join_policy`, `group_join_requests`; API khám phá, tham gia hoặc xin duyệt. | B tìm nhóm OPEN và vào ngay; nhóm APPROVAL cần admin duyệt; nhóm PRIVATE không lộ ra. |
| **9. Quản trị nhóm** | API đổi thông tin, vai trò/thành viên, chuyển owner, rời/vào lại. | Thử đủ vai trò; người vào lại không xem tin trong lúc vắng mặt. |
| **10. Quyền nhận tin** | Tạo `user_privacy_settings` khi đến bước này; `ANYONE/FRIENDS_ONLY`. | A bật Friends only: người lạ không mở/gửi chat mới được; bạn bè vẫn được. |
| **11. Tệp đính kèm** | `message_attachments` và chính sách object storage/upload. | A gửi ảnh/file hợp lệ, B xem/tải được; file sai loại/quá lớn bị từ chối. |

**Mốc MVP: hết bước 5** đã có kết bạn, chat riêng và realtime. Quyết định
“được nhắn trước khi kết bạn” áp dụng ngay từ bước 3, không đợi bước 10.

## 5. Triển khai và phát hành

1. Mỗi bước làm trên feature branch/PR riêng; FE và BE là hai repo nên ghi rõ
   contract API và thứ tự deploy. Không sửa trực tiếp `main`.
2. Migration tạo theo bước và chạy thử với MySQL 8.4 trên bản restore từ backup.
   Test H2/context-load chỉ kiểm tra ứng dụng khởi động, không kiểm tra đầy đủ
   generated columns, unique, FK và dữ liệu production.
3. Trước khi deploy DB: có backup đã thử restore, review migration tương thích
   với phiên bản BE đang chạy. Deploy migration/BE trước, FE sau khi API sẵn sàng.
4. Sau deploy: smoke test bằng hai tài khoản trên UI, xem health/log và RAM VM;
   chuẩn bị rollback code mà không cần xóa dữ liệu vừa tạo.

## 6. Tiến độ

- [ ] Bước 1: khu vực sau login và tìm người dùng
- [ ] Bước 2: kết bạn
- [ ] Bước 3: chat riêng REST
- [ ] Bước 4: inbox và đã đọc
- [ ] Bước 5: realtime (MVP)
- [ ] Bước 6: chặn người dùng
- [ ] Bước 7: nhóm riêng
- [ ] Bước 8: nhóm công khai
- [ ] Bước 9: quản trị nhóm
- [ ] Bước 10: quyền nhận tin
- [ ] Bước 11: tệp đính kèm
