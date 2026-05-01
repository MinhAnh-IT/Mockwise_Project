export type Testimonial = {
  quote: string;
  author: string;
  role: string;
  avatar: string;
};

export const FEATURED_TESTIMONIAL: Testimonial = {
  quote:
    '"MockWise là người cố vấn mà tôi không biết mình cần. Phản hồi về các câu hỏi hành vi đã giúp tôi chuyển từ \'trung bình\' sang \'xuất sắc\' chỉ sau ba buổi học."',
  author: 'Sarah Chen',
  role: 'Nhà thiết kế sản phẩm cấp cao tại Google',
  avatar:
    'https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-4.0.3&auto=format&fit=crop&w=256&h=256&q=80',
};
