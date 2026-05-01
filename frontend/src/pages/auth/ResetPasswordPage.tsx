import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { confirmForgotPasswordOtp } from '@/api/auth';
import { ApiError } from '@/api/client';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import OtpInput from '@/components/auth/OtpInput';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';
import PasswordInput from '@/components/form/PasswordInput';

const PASSWORD_HINT =
  'Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và ký tự đặc biệt.';
const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,}$/;
const OTP_LENGTH = 6;

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState(searchParams.get('email') ?? '');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setPasswordError(null);

    if (otp.length !== OTP_LENGTH) {
      setError('Vui lòng nhập đủ 6 chữ số OTP.');
      return;
    }
    if (!PASSWORD_RULE.test(newPassword)) {
      setPasswordError(PASSWORD_HINT);
      return;
    }

    setSubmitting(true);
    try {
      await confirmForgotPasswordOtp({ email: email.trim(), otp, newPassword });
      navigate('/login?reset=1', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setError('OTP không đúng hoặc đã hết hạn.');
      } else {
        setError(err instanceof Error ? err.message : 'Đặt lại mật khẩu thất bại.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Đặt lại mật khẩu"
      subtitle="Nhập mã OTP và mật khẩu mới của bạn"
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
            required
          />
        </Field>

        <Field label="Mã OTP" required>
          <OtpInput value={otp} onChange={setOtp} autoFocus />
        </Field>

        <Field
          label="Mật khẩu mới"
          htmlFor="newPassword"
          required
          hint={PASSWORD_HINT}
          error={passwordError ?? undefined}
        >
          <PasswordInput
            id="newPassword"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="Tạo mật khẩu mới"
            invalid={!!passwordError}
            minLength={8}
            required
            autoComplete="new-password"
          />
        </Field>

        <Button type="submit" loading={submitting} fullWidth>
          Đặt lại mật khẩu
        </Button>
      </form>
    </AuthLayout>
  );
}
