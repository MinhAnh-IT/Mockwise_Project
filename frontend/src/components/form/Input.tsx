import { forwardRef, type InputHTMLAttributes } from 'react';

type Props = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
};

const baseClass =
  'w-full px-4 py-2.5 rounded-xl border bg-surface-container-lowest text-on-surface placeholder:text-on-surface-variant/60 focus:outline-none focus:ring-2 focus:ring-secondary/30 transition-colors disabled:opacity-60 disabled:cursor-not-allowed';

const Input = forwardRef<HTMLInputElement, Props>(function Input(
  { invalid, className = '', ...rest },
  ref,
) {
  const borderClass = invalid
    ? 'border-red-400 focus:border-red-500'
    : 'border-outline-variant focus:border-secondary';
  return <input ref={ref} className={`${baseClass} ${borderClass} ${className}`} {...rest} />;
});

export default Input;
