import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register, sendVerifyOtp } from '@/api/auth';
import { listPositionLevels, listPositionTracks } from '@/api/catalog';
import { ApiError } from '@/api/client';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';
import PasswordInput from '@/components/form/PasswordInput';
import Select from '@/components/form/Select';
import type { PositionLevel, PositionTrack } from '@/types/profile';

const PASSWORD_HINT =
  'Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và ký tự đặc biệt.';

const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,}$/;

export default function RegisterPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [city, setCity] = useState('');
  const [experience, setExperience] = useState('0');
  const [trackId, setTrackId] = useState('');
  const [levelId, setLevelId] = useState('');

  const [tracks, setTracks] = useState<PositionTrack[]>([]);
  const [levels, setLevels] = useState<PositionLevel[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [t, l] = await Promise.all([listPositionTracks(), listPositionLevels()]);
        if (cancelled) return;
        setTracks(t);
        setLevels(l);
      } catch (err) {
        if (!cancelled) {
          setCatalogError(
            err instanceof Error ? err.message : 'Không tải được danh mục lĩnh vực/cấp độ.',
          );
        }
      } finally {
        if (!cancelled) setCatalogLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setPasswordError(null);

    if (!PASSWORD_RULE.test(password)) {
      setPasswordError(PASSWORD_HINT);
      return;
    }

    setSubmitting(true);
    try {
      await register({
        account: { email: email.trim(), password },
        profile: {
          fullName: fullName.trim(),
          trackId,
          levelId,
          city: city.trim(),
          experience: Number(experience),
        },
      });
      try {
        await sendVerifyOtp(email.trim());
      } catch {
        // verification page can resend if first send fails
      }
      navigate(`/verify-account?email=${encodeURIComponent(email.trim())}`, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('Email này đã được đăng ký. Bạn vui lòng đăng nhập hoặc dùng email khác.');
      } else {
        setError(err instanceof Error ? err.message : 'Không thể đăng ký. Vui lòng thử lại.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Tạo tài khoản"
      subtitle="Bắt đầu luyện phỏng vấn cùng MockWise"
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-semibold text-secondary hover:underline">
            Đăng nhập
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <FormError message={error ?? catalogError} />

        <Field label="Họ và tên" htmlFor="fullName" required>
          <Input
            id="fullName"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            placeholder="Nguyễn Văn A"
            required
            autoComplete="name"
          />
        </Field>

        <Field label="Email" htmlFor="email" required>
          <Input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="ban@example.com"
            required
            autoComplete="email"
          />
        </Field>

        <Field
          label="Mật khẩu"
          htmlFor="password"
          required
          hint={PASSWORD_HINT}
          error={passwordError ?? undefined}
        >
          <PasswordInput
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Tạo mật khẩu mạnh"
            invalid={!!passwordError}
            minLength={8}
            required
            autoComplete="new-password"
          />
        </Field>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field label="Lĩnh vực" htmlFor="trackId" required>
            <Select
              id="trackId"
              value={trackId}
              onChange={(e) => setTrackId(e.target.value)}
              required
              disabled={catalogLoading}
            >
              <option value="">{catalogLoading ? 'Đang tải...' : 'Chọn lĩnh vực'}</option>
              {tracks.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Cấp độ" htmlFor="levelId" required>
            <Select
              id="levelId"
              value={levelId}
              onChange={(e) => setLevelId(e.target.value)}
              required
              disabled={catalogLoading}
            >
              <option value="">{catalogLoading ? 'Đang tải...' : 'Chọn cấp độ'}</option>
              {levels.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.positionRole}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field label="Thành phố" htmlFor="city" required>
            <Input
              id="city"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              placeholder="Hồ Chí Minh"
              required
            />
          </Field>

          <Field
            label="Số năm kinh nghiệm"
            htmlFor="experience"
            required
          >
            <Input
              id="experience"
              type="number"
              min={0}
              value={experience}
              onChange={(e) => setExperience(e.target.value)}
              required
            />
          </Field>
        </div>

        <Button type="submit" loading={submitting} fullWidth>
          Tạo tài khoản
        </Button>
      </form>
    </AuthLayout>
  );
}
