export type NavItem = {
  label: string;
  href: string;
};

export type NavGroup = {
  title: string;
  items: NavItem[];
};

// Public landing-page navigation: anchors into homepage sections.
export const HEADER_NAV_GUEST: NavItem[] = [
  { label: 'Tính năng', href: '/#features' },
  { label: 'Bảng giá', href: '/#pricing' },
];

// Authenticated app navigation: top-level routes the user can move between.
export const HEADER_NAV_USER: NavItem[] = [
  { label: 'Luyện tập', href: '/practice' },
  { label: 'Lịch sử', href: '/history' },
  { label: 'Thanh toán', href: '/payment' },
  { label: 'Bảng giá', href: '/#pricing' },
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
