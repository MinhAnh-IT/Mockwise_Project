import SocialIcon from '@/components/ui/SocialIcon';
import { FOOTER_GROUPS } from '@/data/navigation';
import { SITE } from '@/data/site';
import { SOCIAL_LINKS } from '@/data/social';

export default function Footer() {
  return (
    <footer className="bg-surface-container border-t border-outline-variant/30 mt-auto">
      <div className="max-w-7xl mx-auto px-6 md:px-12 py-16">
        <div className="grid grid-cols-2 md:grid-cols-12 gap-10 md:gap-8">
          <div className="col-span-2 md:col-span-4">
            <a href="#" className="text-xl font-bold tracking-tight text-on-surface inline-block mb-4">
              {SITE.name}
            </a>
            <p className="text-on-surface-variant text-sm leading-relaxed max-w-sm mb-6">
              {SITE.tagline}
            </p>
            <div className="flex gap-3">
              {SOCIAL_LINKS.map(({ label, href, Icon }) => (
                <SocialIcon
                  key={label}
                  label={label}
                  href={href}
                  icon={<Icon className="w-4 h-4" />}
                />
              ))}
            </div>
          </div>

          {FOOTER_GROUPS.map((group) => (
            <div key={group.title} className="col-span-1 md:col-span-2">
              <h4 className="text-sm font-bold text-on-surface mb-4">{group.title}</h4>
              <ul className="space-y-3">
                {group.items.map((item) => (
                  <li key={item.label}>
                    <a
                      href={item.href}
                      className="text-sm text-on-surface-variant hover:text-secondary transition-colors"
                    >
                      {item.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>

      <div className="border-t border-outline-variant/30">
        <div className="max-w-7xl mx-auto px-6 md:px-12 py-6 flex flex-col md:flex-row md:justify-between items-center gap-3">
          <p className="text-xs text-on-surface-variant">{SITE.copyright}</p>
          <p className="text-xs text-on-surface-variant">{SITE.madeIn}</p>
        </div>
      </div>
    </footer>
  );
}
