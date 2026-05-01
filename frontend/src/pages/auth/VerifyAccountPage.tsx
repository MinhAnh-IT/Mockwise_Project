import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { confirmVerifyOtp, sendVerifyOtp } from '@/api/auth';
import { ApiError } from '@/api/client';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import OtpInput from '@/components/auth/OtpInput';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';

const OTP_LENGTH = 6;
const RESEND_COOLDOWN_SECONDS = 60;

export default function VerifyAccountPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState(searchParams.get('email') ?? '');
  const [otp, setOtp] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(id);
  }, [cooldown]);

  const handleResend = async () => {
    if (!email || cooldown > 0) return;
    setError(null);
    setInfo(null);
    try {
      await sendVerifyOtp(email.trim());
      setCooldown(RESEND_COOLDOWN_SECONDS);
      setInfo('Đã gửi mã OTP mới tới email của bạn.');
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        setError('Mã OTP trước vẫn còn hiệu lực, vui lòng kiểm tra email.');
      } else if (err instanceof ApiError && err.status === 404) {
        setError('Không tìm thấy tài khoản với email này.');
      } else {
        setError(err instanceof Error ? err.message : 'Không thể gửi OTP.');
      }
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (otp.length !== OTP_LENGTH) {
      setError('Vui lòng nhập đủ 6 chữ số OTP.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await confirmVerifyOtp({ email: email.trim(), otp });
      navigate('/login?verified=1', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setError('OTP không đúng hoặc đã hết hạn.');
      } else {
        setError(err instanceof Error ? err.message : 'Xác thực thất bại.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Xác thực email"
      subtitle="Nhập mã 6 số đã gửi tới email của bạn để kích hoạt tài khoản"
      footer={
        <Link to="/login" className="font-semibold text-secondary hover:underline">
          Quay về đăng nhập
        </Link>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <FormError message={error} />
        {info && (
          <div className="px-3 py-2.5 rounded-xl bg-secondary-fixed/50 border border-secondary/30 text-sm text-on-secondary-fixed">
            {info}
          </div>
        )}

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

        <Button type="submit" loading={submitting} fullWidth>
          Xác thực
        </Button>

        <div className="text-center text-sm text-on-surface-variant">
          Chưa nhận được?{' '}
          <button
            type="button"
            onClick={handleResend}
            disabled={cooldown > 0 || !email}
            className="font-semibold text-secondary hover:underline disabled:opacity-50 disabled:no-underline disabled:cursor-not-allowed"
          >
            {cooldown > 0 ? `Gửi lại sau ${cooldown}s` : 'Gửi lại OTP'}
          </button>
        </div>
      </form>
    </AuthLayout>
  );
}
