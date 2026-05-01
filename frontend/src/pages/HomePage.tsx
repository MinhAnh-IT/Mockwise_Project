import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import CtaSection from '@/components/home/CtaSection';
import FeaturesSection from '@/components/home/FeaturesSection';
import HeroSection from '@/components/home/HeroSection';
import PricingSection from '@/components/home/PricingSection';

export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <main className="pt-16">
        <HeroSection />
        <FeaturesSection />
        <PricingSection />
        <CtaSection />
      </main>
      <Footer />
    </div>
  );
}
