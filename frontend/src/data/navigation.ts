export type NavItem = {
  label: string;
  href: string;
};

export type NavGroup = {
  title: string;
  items: NavItem[];
};

export const HEADER_NAV_ITEMS: NavItem[] = [
  { label: 'Tính năng', href: '#features' },
  { label: 'Bảng giá', href: '#pricing' },
  { label: 'Đánh giá', href: '#testimonial' },
];

export const FOOTER_GROUPS: NavGroup[] = [
  {
    title: 'Sản phẩm',
    items: [
      { label: 'Phỏng vấn Hành vi', href: '#features' },
      { label: 'Phỏng vấn Kỹ thuật', href: '#features' },
      { label: 'Phỏng vấn Lập trình', href: '#features' },
      { label: 'Bảng giá', href: '#pricing' },
    ],
  },
  {
    title: 'Công ty',
    items: [
      { label: 'Về chúng tôi', href: '#' },
      { label: 'Đội ngũ', href: '#' },
      { label: 'Tuyển dụng', href: '#' },
      { label: 'Liên hệ', href: '#' },
    ],
  },
  {
    title: 'Tài nguyên',
    items: [
      { label: 'Blog', href: '#' },
      { label: 'Hướng dẫn', href: '#' },
      { label: 'Câu hỏi thường gặp', href: '#' },
      { label: 'Trung tâm trợ giúp', href: '#' },
    ],
  },
  {
    title: 'Pháp lý',
    items: [
      { label: 'Chính sách bảo mật', href: '#' },
      { label: 'Điều khoản dịch vụ', href: '#' },
      { label: 'Chính sách Cookie', href: '#' },
      { label: 'Bảo mật dữ liệu', href: '#' },
    ],
  },
];
