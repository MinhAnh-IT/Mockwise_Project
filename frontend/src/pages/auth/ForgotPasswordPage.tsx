import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { sendForgotPasswordOtp } from '@/api/auth';
import { ApiError } from '@/api/client';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await sendForgotPasswordOtp(email.trim());
      navigate(`/reset-password?email=${encodeURIComponent(email.trim())}`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError('Không tìm thấy tài khoản với email này.');
      } else if (err instanceof ApiError && err.status === 429) {
        setError('Mã OTP trước vẫn còn hiệu lực, hãy kiểm tra email của bạn.');
      } else {
        setError(err instanceof Error ? err.message : 'Không thể gửi OTP.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Quên mật khẩu"
      subtitle="Nhập email đã đăng ký, chúng tôi sẽ gửi mã OTP để bạn đặt lại mật khẩu."
      footer={
        <Link to="/login" className="font-semibold text-secondary hover:underline">
          Quay về đăng nhập
        </Link>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <FormError message={error} />

        <Field label="Email" htmlFor="email" required>
          <Input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="ban@example.com"
            required
            autoComplete="email"
            autoFocus
          />
        </Field>

        <Button type="submit" loading={submitting} fullWidth>
          Gửi mã OTP
        </Button>
      </form>
    </AuthLayout>
  );
}
