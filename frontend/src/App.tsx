import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '@/auth/AuthContext';
import AdminRoute from '@/auth/AdminRoute';
import ProtectedRoute from '@/auth/ProtectedRoute';
import ScrollToHash from '@/components/layout/ScrollToHash';
import ComingSoonPage from '@/pages/ComingSoonPage';
import HomePage from '@/pages/HomePage';
import LoginPage from '@/pages/auth/LoginPage';
import RegisterPage from '@/pages/auth/RegisterPage';
import VerifyAccountPage from '@/pages/auth/VerifyAccountPage';
import ForgotPasswordPage from '@/pages/auth/ForgotPasswordPage';
import ResetPasswordPage from '@/pages/auth/ResetPasswordPage';
import PracticeCodingPreviewPage from '@/pages/practice/PracticeCodingPreviewPage';
import PracticeIntroPage from '@/pages/practice/PracticeIntroPage';
import PracticePage from '@/pages/practice/PracticePage';
import PracticeQuestionsPage from '@/pages/practice/PracticeQuestionsPage';
import PracticeReportPage from '@/pages/practice/PracticeReportPage';
import PracticeSessionPage from '@/pages/practice/PracticeSessionPage';
import EditProfilePage from '@/pages/profile/EditProfilePage';
import ProfilePage from '@/pages/profile/ProfilePage';
import HistoryPage from '@/pages/history/HistoryPage';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';
import AdminQuestionsPage from '@/pages/admin/AdminQuestionsPage';
import BehavioralFormPage from '@/pages/admin/BehavioralFormPage';
import CoreFormPage from '@/pages/admin/CoreFormPage';
import CodingFormPage from '@/pages/admin/CodingFormPage';
import BlueprintsPage from '@/pages/admin/BlueprintsPage';
import BlueprintFormPage from '@/pages/admin/BlueprintFormPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ScrollToHash />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/home" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify-account" element={<VerifyAccountPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile/edit"
            element={
              <ProtectedRoute>
                <EditProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/practice"
            element={
              <ProtectedRoute>
                <PracticePage />
              </ProtectedRoute>
            }
          />
          {/* Public design-review route — pure mock, no backend/auth. */}
          <Route
            path="/practice/coding/preview"
            element={<PracticeCodingPreviewPage />}
          />
          <Route
            path="/practice/:type"
            element={
              <ProtectedRoute>
                <PracticeIntroPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/practice/:type/session/:sid"
            element={
              <ProtectedRoute>
                <PracticeSessionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/practice/:type/session/:sid/report"
            element={
              <ProtectedRoute>
                <PracticeReportPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/practice/:type/session/:sid/questions"
            element={
              <ProtectedRoute>
                <PracticeQuestionsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/history"
            element={
              <ProtectedRoute>
                <HistoryPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/payment"
            element={
              <ProtectedRoute>
                <ComingSoonPage title="Thanh toán" />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <AdminDashboardPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions"
            element={
              <AdminRoute>
                <AdminQuestionsPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions/new/behavioral"
            element={
              <AdminRoute>
                <BehavioralFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions/new/core"
            element={
              <AdminRoute>
                <CoreFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions/new/coding"
            element={
              <AdminRoute>
                <CodingFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions/behavioral/:id/edit"
            element={
              <AdminRoute>
                <BehavioralFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions/core/:id/edit"
            element={
              <AdminRoute>
                <CoreFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/questions/coding/:id/edit"
            element={
              <AdminRoute>
                <CodingFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/blueprints"
            element={
              <AdminRoute>
                <BlueprintsPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/blueprints/new"
            element={
              <AdminRoute>
                <BlueprintFormPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/blueprints/:id/edit"
            element={
              <AdminRoute>
                <BlueprintFormPage />
              </AdminRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
