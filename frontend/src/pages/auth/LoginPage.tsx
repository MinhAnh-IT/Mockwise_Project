import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '@/api/client';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';
import PasswordInput from '@/components/form/PasswordInput';
import { useAuth } from '@/auth/useAuth';

type LocationState = { from?: string } | null;

export default function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const fallbackPath = (location.state as LocationState)?.from ?? '/profile';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signIn(email.trim(), password);
      navigate(fallbackPath, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        navigate(`/verify-account?email=${encodeURIComponent(email.trim())}`);
        return;
      }
      if (err instanceof ApiError && err.status === 401) {
        setError('Email hoặc mật khẩu không đúng.');
      } else {
        setError(err instanceof Error ? err.message : 'Đã có lỗi xảy ra. Vui lòng thử lại.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Đăng nhập"
      subtitle="Tiếp tục hành trình luyện tập phỏng vấn của bạn"
      footer={
        <>
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-semibold text-secondary hover:underline">
            Đăng ký miễn phí
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <FormError message={error} />

        <Field label="Email" htmlFor="email" required>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="ban@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </Field>

        <Field label="Mật khẩu" htmlFor="password" required>
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </Field>

        <div className="flex justify-end">
          <Link
            to="/forgot-password"
            className="text-sm font-medium text-secondary hover:underline"
          >
            Quên mật khẩu?
          </Link>
        </div>

        <Button type="submit" loading={submitting} fullWidth>
          Đăng nhập
        </Button>
      </form>
    </AuthLayout>
  );
}
