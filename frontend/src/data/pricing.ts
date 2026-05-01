export type PricingPlan = {
  id: string;
  name: string;
  description: string;
  price: string;
  priceSuffix?: string;
  features: string[];
  ctaLabel: string;
  highlighted?: boolean;
  cardClassName: string;
  ctaClassName: string;
};

export const PRICING_PLANS: PricingPlan[] = [
  {
    id: 'free',
    name: 'Miễn phí',
    description: 'Dành cho người mới bắt đầu',
    price: '0đ',
    priceSuffix: '/tháng',
    features: [
      '2 buổi phỏng vấn mỗi tháng',
      'Phản hồi AI cơ bản',
      'Truy cập kho tài liệu chung',
    ],
    ctaLabel: 'Bắt đầu ngay',
    cardClassName:
      'bg-surface-container-lowest p-10 rounded-3xl border border-outline-variant flex flex-col hover:shadow-xl transition-all',
    ctaClassName:
      'w-full py-4 border-2 border-secondary text-secondary rounded-2xl font-bold hover:bg-secondary/5 transition-colors',
  },
  {
    id: 'pro',
    name: 'Chuyên nghiệp (Pro)',
    description: 'Cho các ứng viên nghiêm túc',
    price: '490.000đ',
    priceSuffix: '/tháng',
    features: [
      'Không giới hạn buổi phỏng vấn',
      'Phân tích AI chuyên sâu',
      'Theo dõi ngôn ngữ cơ thể & giọng điệu',
      'Ưu tiên hỗ trợ 24/7',
    ],
    ctaLabel: 'Nâng cấp ngay',
    highlighted: true,
    cardClassName:
      'bg-white p-10 rounded-3xl border-2 border-secondary flex flex-col relative shadow-2xl scale-105 z-10',
    ctaClassName:
      'w-full py-4 bg-secondary text-on-secondary rounded-2xl font-bold hover:opacity-90 transition-opacity shadow-lg shadow-secondary/30',
  },
  {
    id: 'enterprise',
    name: 'Doanh nghiệp',
    description: 'Cho đội ngũ và tổ chức',
    price: 'Liên hệ',
    features: [
      'Quản lý tập trung cho đội ngũ',
      'Tùy chỉnh câu hỏi theo công ty',
      'Báo cáo tiến độ chi tiết',
      'Tích hợp API hệ thống',
    ],
    ctaLabel: 'Liên hệ kinh doanh',
    cardClassName:
      'bg-surface-container-lowest p-10 rounded-3xl border border-outline-variant flex flex-col hover:shadow-xl transition-all',
    ctaClassName:
      'w-full py-4 bg-on-surface text-surface-container-lowest rounded-2xl font-bold hover:opacity-90 transition-opacity',
  },
];
