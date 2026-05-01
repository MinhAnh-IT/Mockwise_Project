import { SITE } from '@/data/site';

type Props = {
  className?: string;
};

export default function Logo({ className = 'h-8 w-auto' }: Props) {
  // logo_homepage.png is the horizontal wordmark used everywhere on-page.
  // The square /logo.png is reserved as the browser favicon (see index.html).
  return <img src="/logo_homepage.png" alt={SITE.name} className={className} loading="lazy" />;
}
