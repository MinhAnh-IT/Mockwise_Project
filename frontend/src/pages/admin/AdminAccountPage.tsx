import { useMemo, useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { updateMyProfile } from '@/api/profile';
import { useAuth } from '@/auth/useAuth';
import AvatarUploader from '@/components/profile/AvatarUploader';
import {
  AdminShell,
  Button,
  Field,
  Input,
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import type { UserProfileUpdateRequest } from '@/types/profile';

/**
 * Admin self-service account page. Admins live entirely inside the /admin
 * console (see AdminShell) and never touch the user-facing /profile surface,
 * so they get their own slimmed-down editor here — just display name and
 * avatar, none of the interview-context fields (track/level/experience/…)
 * that only matter for candidates.
 */
export default function AdminAccountPage() {
  const { profile, refreshProfile } = useAuth();

  const [fullName, setFullName] = useState(profile?.fullName ?? '');
  // Pending object key from a fresh avatar upload — flushed on Save.
  const [pendingAvatarObjectKey, setPendingAvatarObjectKey] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState>(null);

  const dirtyPayload = useMemo<UserProfileUpdateRequest>(() => {
    if (!profile) return {};
    const diff: UserProfileUpdateRequest = {};
    if (fullName.trim() !== profile.fullName) diff.fullName = fullName.trim();
    if (pendingAvatarObjectKey) diff.avatarObjectKey = pendingAvatarObjectKey;
    return diff;
  }, [profile, fullName, pendingAvatarObjectKey]);

  const isDirty = Object.keys(dirtyPayload).length > 0;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!profile || !isDirty) return;

    if (!fullName.trim()) {
      setError('Họ và tên không được để trống.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await updateMyProfile(profile.userId, dirtyPayload);
      await refreshProfile();
      setPendingAvatarObjectKey(null);
      setToast({ kind: 'success', text: 'Đã cập nhật tài khoản.' });
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setError('Bạn không có quyền cập nhật hồ sơ này.');
      } else {
        setError(err instanceof Error ? err.message : 'Không cập nhật được tài khoản.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AdminShell
      title="Tài khoản của tôi"
      subtitle="Cập nhật tên hiển thị và ảnh đại diện của bạn."
      breadcrumb={[{ label: 'Tổng quan', to: '/admin' }, { label: 'Tài khoản' }]}
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {!profile ? null : (
        <form
          onSubmit={handleSubmit}
          className="max-w-2xl space-y-5 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6 md:p-8"
        >
          <div className="pb-1">
            <AvatarUploader
              fullName={profile.fullName}
              currentAvatarUrl={profile.avatarUrl}
              onUploaded={setPendingAvatarObjectKey}
            />
          </div>

          <Field label="Họ và tên" required error={error ?? undefined}>
            <Input
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Nguyễn Văn A"
              autoComplete="name"
              required
            />
          </Field>

          <div className="flex gap-3 pt-2">
            <Button
              type="submit"
              loading={submitting}
              disabled={!isDirty}
              className="md:px-8"
            >
              {isDirty ? 'Lưu thay đổi' : 'Chưa có thay đổi'}
            </Button>
          </div>
        </form>
      )}
    </AdminShell>
  );
}
