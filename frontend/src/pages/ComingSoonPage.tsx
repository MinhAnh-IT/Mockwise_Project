import { Link } from 'react-router-dom';
import { Construction } from 'lucide-react';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';

type Props = {
  title: string;
  description?: string;
};

export default function ComingSoonPage({ title, description }: Props) {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <main className="flex-1 flex items-center justify-center px-6 pt-16 pb-10">
        <div className="text-center max-w-md">
          <div className="w-16 h-16 mx-auto mb-6 rounded-2xl bg-secondary-fixed flex items-center justify-center">
            <Construction className="w-8 h-8 text-on-secondary-fixed" />
          </div>
          <h1 className="text-3xl md:text-4xl font-bold text-on-surface mb-3">{title}</h1>
          <p className="text-on-surface-variant mb-8">
            {description ?? 'Tính năng này đang được phát triển. Quay lại sớm bạn nhé!'}
          </p>
          <Link
            to="/"
            className="inline-flex items-center px-5 py-2.5 rounded-xl font-semibold text-sm bg-secondary text-on-secondary hover:opacity-90 transition-opacity"
          >
            Về trang chủ
          </Link>
        </div>
      </main>
      <Footer />
    </div>
  );
}
