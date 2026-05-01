import { Loader2 } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  loading?: boolean;
  fullWidth?: boolean;
  children: ReactNode;
};

const VARIANT_CLASS: Record<Variant, string> = {
  primary:
    'bg-secondary text-on-secondary hover:opacity-90 shadow-sm shadow-secondary/20 disabled:opacity-60',
  secondary:
    'border border-outline-variant text-on-surface hover:bg-surface-container-low disabled:opacity-60',
  ghost: 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low',
};

const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  {
    variant = 'primary',
    loading = false,
    fullWidth = false,
    disabled,
    children,
    className = '',
    type = 'button',
    ...rest
  },
  ref,
) {
  const widthClass = fullWidth ? 'w-full' : '';
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl font-semibold text-sm transition-all ${VARIANT_CLASS[variant]} ${widthClass} ${className}`}
      {...rest}
    >
      {loading && <Loader2 className="w-4 h-4 animate-spin" />}
      {children}
    </button>
  );
});

export default Button;
