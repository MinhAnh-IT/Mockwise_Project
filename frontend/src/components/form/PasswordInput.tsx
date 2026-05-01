import { useState, forwardRef, type InputHTMLAttributes } from 'react';
import { Eye, EyeOff } from 'lucide-react';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  invalid?: boolean;
};

const baseClass =
  'w-full px-4 py-2.5 pr-11 rounded-xl border bg-surface-container-lowest text-on-surface placeholder:text-on-surface-variant/60 focus:outline-none focus:ring-2 focus:ring-secondary/30 transition-colors disabled:opacity-60';

const PasswordInput = forwardRef<HTMLInputElement, Props>(function PasswordInput(
  { invalid, className = '', ...rest },
  ref,
) {
  const [visible, setVisible] = useState(false);
  const borderClass = invalid
    ? 'border-red-400 focus:border-red-500'
    : 'border-outline-variant focus:border-secondary';

  return (
    <div className="relative">
      <input
        ref={ref}
        type={visible ? 'text' : 'password'}
        className={`${baseClass} ${borderClass} ${className}`}
        {...rest}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        className="absolute top-1/2 right-3 -translate-y-1/2 p-1 text-on-surface-variant hover:text-on-surface transition-colors"
      >
        {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
      </button>
    </div>
  );
});

export default PasswordInput;
