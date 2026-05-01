import { Pencil, Mail, MapPin, Briefcase, GraduationCap, Calendar, User } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import Avatar from '@/components/ui/Avatar';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';

export default function ProfilePage() {
  const { profile } = useAuth();

  if (!profile) return null;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-3xl mx-auto">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-3xl p-8 md:p-10 shadow-sm">
            <div className="flex flex-col sm:flex-row sm:items-start gap-6 mb-10">
              <Avatar
                src={profile.avatarUrl}
                fullName={profile.fullName}
                size="lg"
                className="mx-auto sm:mx-0"
              />
              <div className="flex-1 text-center sm:text-left">
                <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-1">
                  {profile.fullName}
                </h1>
                <p className="text-on-surface-variant text-sm">
                  {profile.position.trackName} · {profile.position.levelName}
                </p>
              </div>
              <Link
                to="/profile/edit"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-outline-variant text-on-surface text-sm font-semibold hover:bg-surface-container-low transition-all self-center sm:self-start"
              >
                <Pencil className="w-4 h-4" />
                Chỉnh sửa
              </Link>
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

      <Footer />
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
