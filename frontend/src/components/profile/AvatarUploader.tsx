import { useRef, useState } from 'react';
import { Camera, Loader2 } from 'lucide-react';
import {
  completeAvatarUpload,
  putAvatarBytes,
  reserveAvatarUpload,
} from '@/api/storage';
import Avatar from '@/components/ui/Avatar';

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 5 * 1024 * 1024;

type Props = {
  fullName: string;
  /** Currently displayed avatar URL — short-lived presigned link from the profile API. */
  currentAvatarUrl?: string | null;
  /**
   * Called after upload succeeds. The parent should pass the objectKey to the
   * profile PATCH so the next /profiles fetch returns a fresh presigned URL.
   */
  onUploaded: (objectKey: string) => void;
};

export default function AvatarUploader({ fullName, currentAvatarUrl, onUploaded }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const displayed = previewUrl ?? currentAvatarUrl ?? null;

  const handleSelect = async (file: File) => {
    setError(null);

    if (!ALLOWED_TYPES.includes(file.type)) {
      setError('Định dạng không hỗ trợ. Chỉ chấp nhận JPG, PNG hoặc WEBP.');
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('Ảnh quá lớn — tối đa 5MB.');
      return;
    }

    // Show local preview immediately so the user gets feedback while bytes
    // are being uploaded to MinIO.
    const localUrl = URL.createObjectURL(file);
    setPreviewUrl(localUrl);

    setUploading(true);
    try {
      const reservation = await reserveAvatarUpload({
        contentType: file.type,
        sizeBytes: file.size,
      });
      await putAvatarBytes(reservation.uploadUrl, file);
      await completeAvatarUpload(reservation.objectId);
      onUploaded(reservation.objectKey);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Tải ảnh thất bại.');
      setPreviewUrl(null);
      URL.revokeObjectURL(localUrl);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="flex items-center gap-5">
      <div className="relative">
        <Avatar src={displayed} fullName={fullName} size="lg" />
        {uploading && (
          <div className="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center">
            <Loader2 className="w-6 h-6 text-white animate-spin" />
          </div>
        )}
      </div>

      <div>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low disabled:opacity-60 transition-all"
        >
          <Camera className="w-4 h-4" />
          {currentAvatarUrl || previewUrl ? 'Đổi ảnh' : 'Tải ảnh lên'}
        </button>
        <p className="text-xs text-on-surface-variant mt-2">JPG, PNG hoặc WEBP. Tối đa 5MB.</p>
        {error && <p className="text-xs text-red-600 mt-1">{error}</p>}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={ALLOWED_TYPES.join(',')}
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleSelect(file);
          // Reset so the same file can be picked again after an error.
          e.target.value = '';
        }}
      />
    </div>
  );
}
