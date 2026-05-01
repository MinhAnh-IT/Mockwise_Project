import { SITE } from '@/data/site';

type Props = {
  className?: string;
};

export default function Logo({ className = 'h-8 w-auto' }: Props) {
  return <img src="/logo.png" alt={SITE.name} className={className} loading="lazy" />;
}
