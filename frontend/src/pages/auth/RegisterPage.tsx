import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
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
import type { Language, PositionLevel, PositionTrack } from '@/types/profile';

const PASSWORD_HINT =
  'Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và ký tự đặc biệt.';

const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,}$/;

// Comma/newline-separated free-text → list of normalized lowercase tokens.
// Backend re-normalizes anyway; we mirror it client-side so chip preview and
// length-cap hints match what gets persisted.
function splitTokens(raw: string): string[] {
  const seen = new Set<string>();
  for (const part of raw.split(/[,\n]/)) {
    const token = part.trim().toLowerCase();
    if (token) seen.add(token);
  }
  return [...seen];
}

export default function RegisterPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [city, setCity] = useState('');
  const [experience, setExperience] = useState('0');
  const [trackId, setTrackId] = useState('');
  const [levelId, setLevelId] = useState('');
  const [preferredLanguage, setPreferredLanguage] = useState<Language>('VI');
  const [yearsInCurrentRole, setYearsInCurrentRole] = useState('');
  const [techStackInput, setTechStackInput] = useState('');
  const [industriesInput, setIndustriesInput] = useState('');

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

    const expNum = Number(experience);
    const yicrNum = yearsInCurrentRole.trim() === '' ? undefined : Number(yearsInCurrentRole);
    if (yicrNum !== undefined && yicrNum > expNum) {
      setError('Số năm ở vị trí hiện tại không thể lớn hơn tổng số năm kinh nghiệm.');
      return;
    }

    const techStack = splitTokens(techStackInput);
    const industries = splitTokens(industriesInput);
    if (techStack.length > 20) {
      setError('Tech stack tối đa 20 mục.');
      return;
    }
    if (industries.length > 10) {
      setError('Lĩnh vực ngành tối đa 10 mục.');
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
          experience: expNum,
          preferredLanguage,
          ...(yicrNum !== undefined ? { yearsInCurrentRole: yicrNum } : {}),
          ...(techStack.length ? { techStack } : {}),
          ...(industries.length ? { industries } : {}),
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
      size="md"
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-semibold text-secondary hover:underline">
            Đăng nhập
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-8">
        <FormError message={error ?? catalogError} />

        <FormSection title="Tài khoản" description="Thông tin đăng nhập của bạn.">
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
        </FormSection>

        <FormSection
          title="Hồ sơ chuyên môn"
          description="Giúp MockWise điều chỉnh độ khó và chủ đề câu hỏi cho bạn."
        >
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

            <Field label="Số năm kinh nghiệm" htmlFor="experience" required>
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
        </FormSection>

        <details className="group rounded-2xl border border-outline-variant bg-surface-container-low/40 open:bg-surface-container-low/70 transition-colors">
          <summary className="cursor-pointer list-none px-5 py-4 flex items-center justify-between gap-3 select-none">
            <div>
              <h2 className="text-sm font-semibold text-on-surface">Tuỳ chọn nâng cao</h2>
              <p className="text-xs text-on-surface-variant mt-0.5">
                Tech stack, ngành nghề, ngôn ngữ — giúp cá nhân hoá tốt hơn (có thể bỏ qua).
              </p>
            </div>
            <ChevronDown className="w-4 h-4 text-on-surface-variant shrink-0 transition-transform group-open:rotate-180" />
          </summary>
          <div className="px-5 pb-5 pt-1 space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Field
                label="Số năm ở vị trí hiện tại"
                htmlFor="yearsInCurrentRole"
                hint="Hiệu chỉnh độ khó nếu bạn vừa đổi vị trí."
              >
                <Input
                  id="yearsInCurrentRole"
                  type="number"
                  min={0}
                  value={yearsInCurrentRole}
                  onChange={(e) => setYearsInCurrentRole(e.target.value)}
                  placeholder="vd: 1"
                />
              </Field>

              <Field label="Ngôn ngữ ưu tiên" htmlFor="preferredLanguage">
                <Select
                  id="preferredLanguage"
                  value={preferredLanguage}
                  onChange={(e) => setPreferredLanguage(e.target.value as Language)}
                >
                  <option value="VI">Tiếng Việt</option>
                  <option value="EN">English</option>
                </Select>
              </Field>
            </div>

            <Field
              label="Tech stack"
              htmlFor="techStack"
              hint="Cách nhau bởi dấu phẩy. Tối đa 20 mục."
            >
              <Input
                id="techStack"
                value={techStackInput}
                onChange={(e) => setTechStackInput(e.target.value)}
                placeholder="java, spring, postgresql"
              />
            </Field>

            <Field
              label="Lĩnh vực ngành"
              htmlFor="industries"
              hint="Cách nhau bởi dấu phẩy. Tối đa 10 mục."
            >
              <Input
                id="industries"
                value={industriesInput}
                onChange={(e) => setIndustriesInput(e.target.value)}
                placeholder="fintech, ecommerce"
              />
            </Field>
          </div>
        </details>

        <Button type="submit" loading={submitting} fullWidth>
          Tạo tài khoản
        </Button>
      </form>
    </AuthLayout>
  );
}

type FormSectionProps = {
  title: string;
  description?: string;
  children: ReactNode;
};

function FormSection({ title, description, children }: FormSectionProps) {
  return (
    <section className="space-y-4">
      <header>
        <h2 className="text-sm font-semibold text-on-surface">{title}</h2>
        {description && (
          <p className="text-xs text-on-surface-variant mt-0.5">{description}</p>
        )}
      </header>
      <div className="space-y-4">{children}</div>
    </section>
  );
}
