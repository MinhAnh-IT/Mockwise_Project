import { Link } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { useStartHref } from '@/lib/cta';
import PricingItem from '@/components/ui/PricingItem';
import { PRICING_PLANS, type PricingPlan } from '@/data/pricing';

export default function PricingSection() {
  return (
    <section id="pricing" className="scroll-mt-16 py-24 px-6 md:px-12 bg-surface">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-16">
          <h2 className="text-4xl font-bold text-on-surface mb-4">Gói dịch vụ linh hoạt</h2>
          <p className="text-on-surface-variant text-lg">
            Chọn gói phù hợp để nâng tầm kỹ năng phỏng vấn của bạn.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-stretch">
          {PRICING_PLANS.map((plan) => (
            <PricingPlanCard key={plan.id} plan={plan} />
          ))}
        </div>
      </div>
    </section>
  );
}

function PricingPlanCard({ plan }: { plan: PricingPlan }) {
  const startHref = useStartHref();
  const { status, role } = useAuth();
  // 'upgrade' goes straight to checkout for signed-in users; guests register
  // first, and admins (no payment surface) land on the console.
  const upgradeHref =
    status === 'authenticated' ? (role === 'ADMIN' ? '/admin' : '/payment') : '/register';
  const ctaHref = plan.ctaKind === 'upgrade' ? upgradeHref : startHref;

  return (
    <div className={plan.cardClassName}>
      {plan.highlighted && (
        <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-secondary text-on-secondary px-4 py-1.5 rounded-full text-[10px] font-black tracking-widest uppercase shadow-lg">
          Phổ biến nhất
        </div>
      )}
      <div className="mb-8">
        <h3 className="text-xl font-bold mb-2">{plan.name}</h3>
        <p className="text-on-surface-variant text-sm mb-6">{plan.description}</p>
        <div className="flex items-baseline gap-1">
          <span className="text-4xl font-black text-on-surface">{plan.price}</span>
          {plan.priceSuffix && (
            <span className="text-on-surface-variant text-sm">{plan.priceSuffix}</span>
          )}
        </div>
      </div>
      <div className="flex-grow space-y-4 mb-10">
        {plan.features.map((feature) => (
          <PricingItem key={feature} text={feature} />
        ))}
      </div>
      {plan.ctaKind && (
        <Link to={ctaHref} className={`block text-center ${plan.ctaClassName}`}>
          {plan.ctaLabel}
        </Link>
      )}
    </div>
  );
}
