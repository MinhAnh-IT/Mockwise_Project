import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
import { completeProfile } from '@/api/auth';
import { listPositionLevels, listPositionTracks } from '@/api/catalog';
import { useAuth } from '@/auth/useAuth';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';
import Select from '@/components/form/Select';
import type { Language, PositionLevel, PositionTrack } from '@/types/profile';

// Comma/newline-separated free-text → normalized lowercase tokens (mirrors
// RegisterPage so behaviour is identical to the password sign-up path).
function splitTokens(raw: string): string[] {
  const seen = new Set<string>();
  for (const part of raw.split(/[,\n]/)) {
    const token = part.trim().toLowerCase();
    if (token) seen.add(token);
  }
  return [...seen];
}

/**
 * First-time profile completion for social-login accounts. Google/GitHub give
 * us email + name but none of the required track/level, so a fresh OAuth
 * user is routed here before entering the app. If the profile already exists
 * (e.g. the user navigates here directly), bounce to /profile.
 */
export default function CompleteProfilePage() {
  const navigate = useNavigate();
  const { profile, refreshProfile } = useAuth();

  const [fullName, setFullName] = useState('');
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

  // Already has a profile → nothing to complete.
  useEffect(() => {
    if (profile) navigate('/profile', { replace: true });
  }, [profile, navigate]);

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
      await completeProfile({
        fullName: fullName.trim(),
        trackId,
        levelId,
        experience: expNum,
        preferredLanguage,
        ...(yicrNum !== undefined ? { yearsInCurrentRole: yicrNum } : {}),
        ...(techStack.length ? { techStack } : {}),
        ...(industries.length ? { industries } : {}),
      });
      await refreshProfile();
      navigate('/profile', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể lưu hồ sơ. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Hoàn thiện hồ sơ"
      subtitle="Một vài thông tin để MockWise cá nhân hoá câu hỏi cho bạn"
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-8">
        <FormError message={error ?? catalogError} />

        <section className="space-y-4">
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
        </section>

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
          Hoàn tất
        </Button>
      </form>
    </AuthLayout>
  );
}
