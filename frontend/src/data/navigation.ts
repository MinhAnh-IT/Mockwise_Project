export type NavItem = {
  label: string;
  href: string;
  /** Only render for ADMIN accounts (the dropdown filters on this). */
  adminOnly?: boolean;
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

// Authenticated header nav: only the primary action (Luyện tập) and the
// upgrade driver (Bảng giá). Account-related items (history, payment, profile)
// live in the avatar dropdown — see USER_MENU_ITEMS.
export const HEADER_NAV_USER: NavItem[] = [
  { label: 'Luyện tập', href: '/practice' },
  { label: 'Luyện đề', href: '/problems' },
  { label: 'Bảng giá', href: '/#pricing' },
];

// Items shown in the avatar dropdown menu (in order). Sign-out is rendered
// separately so it can sit below a divider.
export const USER_MENU_ITEMS: NavItem[] = [
  { label: 'Hồ sơ cá nhân', href: '/profile' },
  { label: 'Lịch sử phỏng vấn', href: '/history' },
  { label: 'Thanh toán', href: '/payment' },
  { label: 'Trang quản trị', href: '/admin', adminOnly: true },
];

export const FOOTER_GROUPS: NavGroup[] = [
  {
    title: 'Sản phẩm',
    items: [
      { label: 'Phỏng vấn Hành vi', href: '/#features' },
      { label: 'Phỏng vấn Kỹ thuật', href: '/#features' },
      { label: 'Phỏng vấn Lập trình', href: '/#features' },
      { label: 'Bảng giá', href: '/#pricing' },
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
