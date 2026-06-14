3. Báo cáo & Phân tích (Analytics) — DAU/MAU, số buổi phỏng vấn theo track/level, phễu hoàn thành, điểm trung bình AI chấm. "Tổng quan" hiện chỉ là dashboard tĩnh; cần số
   liệu để ra quyết định nội dung.

4. Nhật ký kiểm toán (Audit log) — ai chặn/bỏ chặn , last login, ai sửa câu hỏi/blueprint,, question-bank, vị trí, cấp độ, đăng nhập admin/user. Quan trọng khi có nhiều admin và để truy vết sự cố.

5. Thông báo / Announcement — gửi banner bảo trì, thông báo tính năng mới tới user.

6. Phản hồi & Hỗ trợ — chỗ user báo lỗi câu hỏi sai/testcase sai (bạn đã gặp ambiguous-testcase), report nội dung.

Tuỳ định hướng sản phẩm

7. Gói & Thanh toán (Subscription/Billing) — nếu MockWise có bản trả phí.
8. Phân quyền admin (RBAC) — phân biệt super-admin vs người chỉ duyệt nội dung.

  ---
Gợi ý ưu tiên của tôi: làm #1 (Practice/Coding management) và #2 (Judge monitoring) trước — vì dữ liệu đã tồn tại, bạn đang thao tác thủ công/SSH, và rủi ro vận hành cao nhất
nằm ở đó. #4 Audit log làm nền tảng nên cài sớm vì càng để lâu càng khó bổ sung hồi tố.