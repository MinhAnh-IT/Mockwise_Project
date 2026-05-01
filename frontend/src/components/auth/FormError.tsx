import { AlertCircle } from 'lucide-react';

type Props = { message?: string | null };

export default function FormError({ message }: Props) {
  if (!message) return null;
  return (
    <div className="flex items-start gap-2 px-3 py-2.5 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
      <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
      <span>{message}</span>
    </div>
  );
}
