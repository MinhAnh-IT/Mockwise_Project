import { forwardRef, type SelectHTMLAttributes } from 'react';
import { ChevronDown } from 'lucide-react';

type Props = SelectHTMLAttributes<HTMLSelectElement> & {
  invalid?: boolean;
};

const baseClass =
  'w-full appearance-none px-4 py-2.5 pr-10 rounded-xl border bg-surface-container-lowest text-on-surface focus:outline-none focus:ring-2 focus:ring-secondary/30 transition-colors disabled:opacity-60 disabled:cursor-not-allowed';

const Select = forwardRef<HTMLSelectElement, Props>(function Select(
  { invalid, className = '', children, ...rest },
  ref,
) {
  const borderClass = invalid
    ? 'border-red-400 focus:border-red-500'
    : 'border-outline-variant focus:border-secondary';
  return (
    <div className="relative">
      <select ref={ref} className={`${baseClass} ${borderClass} ${className}`} {...rest}>
        {children}
      </select>
      <ChevronDown className="absolute top-1/2 right-3 -translate-y-1/2 w-4 h-4 text-on-surface-variant pointer-events-none" />
    </div>
  );
});

export default Select;
