import { useCallback } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import PracticeCodingWorkspace from '@/components/problems/PracticeCodingWorkspace';

/**
 * Full-screen practice workspace for one problem. Difficulty (if known) is
 * carried in router state from the list so the header can badge it without an
 * extra fetch; the workspace fetches the full problem itself.
 */
export default function ProblemWorkspacePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const difficulty = (location.state as { difficulty?: string } | null)?.difficulty ?? null;

  const onExit = useCallback(() => navigate('/problems'), [navigate]);

  if (!id) {
    navigate('/problems', { replace: true });
    return null;
  }

  return (
    <PracticeCodingWorkspace problemId={id} difficulty={difficulty} onExit={onExit} />
  );
}
