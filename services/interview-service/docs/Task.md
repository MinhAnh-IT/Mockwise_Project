Luồng interview coding
- Dựa vào profile của user pick blueprint khớp với ứng viên
- Load danh sách câu hỏi từ questionbank lên (random dựa trên số lượng câu hỏi, độ khó (đảm bảo các câu không được trùng nhau)) -> không cần follow up, không cần chọn phức tạp, chỉ dụaw trên blueprint -> lưu snapshot danh sách câu hỏi ở interview service
- load từng câu hỏi lên cho user sau khi submit
- khi user submit đáp án thì load câu tiếp theo lên lập tức và đưa đáp án đó qua judge service để run code với testcase hide + testcase show 
- trả kết quả về cho interview service, bạn define 1 số rule để quyết định xem có gưir code xuống AI đánh giá không, tránh những trường hợp user spam code (Không viết gì, viết cho có cũng nộp tránh xử lý tốn thời gian)
- và khi user submit câu hỏi cuối cùng rồi sau khi đasnh giá thì gọi lại AI để đánh giá tổng quan buổi interview
- Các lượt đánh giá được lưu lại ở inteview service phục vụ hiển thị feedback cho user giống như behavior và coreskill hiện tại
- Lưu ý quan trọng: 
  + Không được gửi tất cả field lên cho fe chỉ gửi những field cần thiết hiển thị và phục vụ cho buôi interview
  + testcase chỉ hiển thị testcase được show, không được gửi hết