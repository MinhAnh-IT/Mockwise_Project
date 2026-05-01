import { Facebook, Github, Linkedin, Youtube } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

export type SocialLink = {
  label: string;
  href: string;
  Icon: LucideIcon;
};

export const SOCIAL_LINKS: SocialLink[] = [
  { label: 'Facebook', href: '#', Icon: Facebook },
  { label: 'LinkedIn', href: '#', Icon: Linkedin },
  { label: 'YouTube', href: '#', Icon: Youtube },
  { label: 'GitHub', href: '#', Icon: Github },
];
