import { useState } from 'react';
import { RotateCcw, Volume2, VolumeX } from 'lucide-react';
import { questionAudioUrl, regenerateAudio } from '@/api/questionBank';
import { Button, type ToastState } from '@/components/admin/ui';

/**
 * Audio management for a BEHAVIORAL / CORE question: plays the existing TTS
 * clip and lets an admin (re)generate it. Shared by the question list cards
 * and the edit forms. CODING questions have no audio, so callers should not
 * render this for them.
 *
 * Note: regeneration runs TTS against the question's **saved** text. Editing
 * the text and saving the form already re-synthesizes automatically; this
 * button is for recovering audio that failed to generate or forcing a refresh.
 */
export default function QuestionAudioRow({
  questionId,
  audioKey,
  onToast,
  onUpdated,
}: {
  questionId: string;
  audioKey: string | null;
  onToast: (t: ToastState) => void;
  onUpdated: (audioKey: string) => void;
}) {
  const [busy, setBusy] = useState(false);

  const regenerate = async () => {
    setBusy(true);
    try {
      const key = await regenerateAudio(questionId);
      onUpdated(key);
      onToast({ kind: 'success', text: 'Đã tạo lại âm thanh.' });
    } catch (err) {
      onToast({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Tạo âm thanh thất bại.',
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      {audioKey ? (
        <>
          <Volume2 className="h-4 w-4 shrink-0 text-secondary" />
          <audio
            key={audioKey}
            controls
            preload="none"
            src={questionAudioUrl(audioKey)}
            className="h-8 max-w-[16rem] flex-1"
          />
          <Button
            variant="ghost"
            loading={busy}
            onClick={regenerate}
            className="!py-1 text-xs"
            title="Tạo lại âm thanh từ nội dung đã lưu"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            Tạo lại
          </Button>
        </>
      ) : (
        <>
          <span className="inline-flex items-center gap-1.5 text-xs text-on-surface-variant">
            <VolumeX className="h-4 w-4" />
            Chưa có âm thanh
          </span>
          <Button
            variant="outline"
            loading={busy}
            onClick={regenerate}
            className="!py-1 text-xs"
          >
            <Volume2 className="h-3.5 w-3.5" />
            Tạo âm thanh
          </Button>
        </>
      )}
    </div>
  );
}
