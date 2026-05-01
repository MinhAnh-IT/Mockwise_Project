import { Check } from 'lucide-react';

type Props = {
  text: string;
};

export default function PricingItem({ text }: Props) {
  return (
    <div className="flex items-start gap-3 text-sm font-medium text-on-surface">
      <div className="mt-0.5 p-0.5 bg-secondary/10 rounded-full">
        <Check className="w-3.5 h-3.5 text-secondary" />
      </div>
      {text}
    </div>
  );
}
