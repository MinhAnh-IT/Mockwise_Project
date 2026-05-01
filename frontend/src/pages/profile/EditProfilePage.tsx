import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { listPositionLevels, listPositionTracks } from '@/api/catalog';
import { ApiError } from '@/api/client';
import { updateMyProfile } from '@/api/profile';
import { useAuth } from '@/auth/useAuth';
import FormError from '@/components/auth/FormError';
import Button from '@/components/form/Button';
import Field from '@/components/form/Field';
import Input from '@/components/form/Input';
import Select from '@/components/form/Select';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import AvatarUploader from '@/components/profile/AvatarUploader';
import type {
  PositionLevel,
  PositionTrack,
  UserProfileUpdateRequest,
} from '@/types/profile';

export default function EditProfilePage() {
  const { profile, refreshProfile } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState(profile?.fullName ?? '');
  const [city, setCity] = useState(profile?.city ?? '');
  const [experience, setExperience] = useState(String(profile?.experience ?? 0));
  const [trackId, setTrackId] = useState(profile?.position.trackId ?? '');
  const [levelId, setLevelId] = useState(profile?.position.levelId ?? '');
  // Pending object key from a fresh avatar upload — flushed to the server when
  // the user clicks Save (alongside any other field changes).
  const [pendingAvatarObjectKey, setPendingAvatarObjectKey] = useState<string | null>(null);

  const [tracks, setTracks] = useState<PositionTrack[]>([]);
  const [levels, setLevels] = useState<PositionLevel[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
          setError(
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

  // Detect what fields the user actually changed so PATCH only sends those.
  //
  // Position note: the backend service only updates the position when BOTH
  // trackId and levelId are present in the request — it looks up/creates a
  // Position entity by the (track, level) pair. If we sent only the field that
  // changed, the backend would silently skip the position update. So whenever
  // either changed, we send both.
  const dirtyPayload = useMemo<UserProfileUpdateRequest>(() => {
    if (!profile) return {};
    const diff: UserProfileUpdateRequest = {};
    if (fullName.trim() !== profile.fullName) diff.fullName = fullName.trim();
    if (city.trim() !== profile.city) diff.city = city.trim();
    if (Number(experience) !== profile.experience) diff.experience = Number(experience);
    const trackChanged = trackId !== profile.position.trackId;
    const levelChanged = levelId !== profile.position.levelId;
    if (trackChanged || levelChanged) {
      diff.trackId = trackId;
      diff.levelId = levelId;
    }
    if (pendingAvatarObjectKey) {
      diff.avatarObjectKey = pendingAvatarObjectKey;
    }
    return diff;
  }, [profile, fullName, city, experience, trackId, levelId, pendingAvatarObjectKey]);

  const isDirty = Object.keys(dirtyPayload).length > 0;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!profile || !isDirty) return;

    setSubmitting(true);
    setError(null);
    try {
      await updateMyProfile(profile.userId, dirtyPayload);
      await refreshProfile();
      navigate('/profile', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setError('Bạn không có quyền cập nhật hồ sơ này.');
      } else {
        setError(err instanceof Error ? err.message : 'Không cập nhật được hồ sơ.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (!profile) return null;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-2xl mx-auto">
          <Link
            to="/profile"
            className="inline-flex items-center gap-1 text-sm font-medium text-on-surface-variant hover:text-on-surface mb-4 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Quay về hồ sơ
          </Link>

          <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-2">
            Chỉnh sửa hồ sơ
          </h1>
          <p className="text-on-surface-variant text-sm mb-8">
            Cập nhật thông tin cá nhân và định hướng phỏng vấn của bạn.
          </p>

          <form
            onSubmit={handleSubmit}
            className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 md:p-8 space-y-5"
          >
            <FormError message={error} />

            <div className="pb-1">
              <AvatarUploader
                fullName={profile.fullName}
                currentAvatarUrl={profile.avatarUrl}
                onUploaded={setPendingAvatarObjectKey}
              />
            </div>

            <Field label="Họ và tên" htmlFor="fullName" required>
              <Input
                id="fullName"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Nguyễn Văn A"
                autoComplete="name"
                required
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

            <div className="flex gap-3 pt-2">
              <Button
                type="submit"
                loading={submitting}
                disabled={!isDirty || catalogLoading}
                className="flex-1 md:flex-none md:px-8"
              >
                {isDirty ? 'Lưu thay đổi' : 'Chưa có thay đổi'}
              </Button>
              <Link
                to="/profile"
                className="inline-flex items-center justify-center px-5 py-2.5 rounded-xl font-semibold text-sm border border-outline-variant text-on-surface hover:bg-surface-container-low transition-all"
              >
                Hủy
              </Link>
            </div>
          </form>
        </div>
      </main>

      <Footer />
    </div>
  );
}
