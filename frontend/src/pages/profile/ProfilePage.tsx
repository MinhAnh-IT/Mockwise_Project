import { LogOut, Mail, MapPin, Briefcase, GraduationCap, Calendar, User } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import Button from '@/components/form/Button';
import Logo from '@/components/ui/Logo';
import { useAuth } from '@/auth/useAuth';
import { SITE } from '@/data/site';

export default function ProfilePage() {
  const { profile, signOut } = useAuth();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);

  if (!profile) return null;

  const initial = profile.fullName.trim().charAt(0).toUpperCase() || '?';

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await signOut();
      navigate('/', { replace: true });
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <header className="px-6 md:px-12 h-16 flex items-center justify-between border-b border-outline-variant/30 bg-surface-container-lowest">
        <Link to="/" aria-label={`${SITE.name} — Trang chủ`} className="flex items-center">
          <Logo className="h-9 w-auto" />
        </Link>
        <Button variant="secondary" onClick={handleSignOut} loading={signingOut}>
          <LogOut className="w-4 h-4" />
          Đăng xuất
        </Button>
      </header>

      <main className="flex-1 px-6 py-10 md:py-16">
        <div className="max-w-3xl mx-auto">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-3xl p-8 md:p-10 shadow-sm">
            <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6 mb-10">
              <div className="w-20 h-20 rounded-full bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center text-3xl font-bold flex-shrink-0">
                {initial}
              </div>
              <div className="text-center sm:text-left">
                <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-1">
                  {profile.fullName}
                </h1>
                <p className="text-on-surface-variant text-sm">
                  {profile.position.trackName} · {profile.position.levelName}
                </p>
              </div>
            </div>

            <dl className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-6">
              <InfoRow icon={<User className="w-4 h-4" />} label="Họ và tên" value={profile.fullName} />
              <InfoRow
                icon={<Briefcase className="w-4 h-4" />}
                label="Lĩnh vực"
                value={profile.position.trackName}
              />
              <InfoRow
                icon={<GraduationCap className="w-4 h-4" />}
                label="Cấp độ"
                value={profile.position.levelName}
              />
              <InfoRow
                icon={<Calendar className="w-4 h-4" />}
                label="Số năm kinh nghiệm"
                value={`${profile.experience} năm`}
              />
              <InfoRow icon={<MapPin className="w-4 h-4" />} label="Thành phố" value={profile.city} />
              <InfoRow
                icon={<Mail className="w-4 h-4" />}
                label="Mã người dùng"
                value={profile.userId}
                mono
              />
            </dl>
          </div>
        </div>
      </main>
    </div>
  );
}

type InfoRowProps = {
  icon: React.ReactNode;
  label: string;
  value: string;
  mono?: boolean;
};

function InfoRow({ icon, label, value, mono }: InfoRowProps) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="flex items-center gap-2 text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
        {icon}
        {label}
      </dt>
      <dd className={`text-on-surface ${mono ? 'font-mono text-sm break-all' : 'text-base font-medium'}`}>
        {value}
      </dd>
    </div>
  );
}
