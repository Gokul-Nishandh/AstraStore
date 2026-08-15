import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ToastProvider } from './components/ui/Toast'
import { AuthProvider } from './lib/auth'
import { AuthGuard } from './components/auth/AuthGuard'
import { AdminGuard } from './components/auth/AdminGuard'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ScrollToTop } from './components/layout/ScrollToTop'
import { Shell } from './components/layout/Shell'
import { RouteFallback } from './components/layout/RouteFallback'

import { LandingPage } from './pages/Landing'
import { LoginPage } from './pages/Login'
import { RegisterPage } from './pages/Register'
import { NotFoundPage } from './pages/NotFound'

/* The signed-in application is split out of the landing bundle. A visitor
   who never signs in should not download the object explorer, and an
   administrator's charting code should not be in the critical path of the
   marketing page. */
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPassword').then((m) => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/ResetPassword').then((m) => ({ default: m.ResetPasswordPage })))
const OnboardingPage = lazy(() => import('./pages/Onboarding').then((m) => ({ default: m.OnboardingPage })))

const DrivePage = lazy(() => import('./pages/Drive').then((m) => ({ default: m.DrivePage })))
const RecentPage = lazy(() => import('./pages/Recent').then((m) => ({ default: m.RecentPage })))
const StarredPage = lazy(() => import('./pages/Starred').then((m) => ({ default: m.StarredPage })))
const TrashPage = lazy(() => import('./pages/Trash').then((m) => ({ default: m.TrashPage })))
const ObjectExplorerPage = lazy(() => import('./pages/ObjectExplorer').then((m) => ({ default: m.ObjectExplorerPage })))
const ObjectDetailPage = lazy(() => import('./pages/ObjectDetail').then((m) => ({ default: m.ObjectDetailPage })))
const ApiKeysPage = lazy(() => import('./pages/ApiKeys').then((m) => ({ default: m.ApiKeysPage })))
const SettingsPage = lazy(() => import('./pages/Settings').then((m) => ({ default: m.SettingsPage })))

const OverviewPage = lazy(() => import('./pages/Overview').then((m) => ({ default: m.OverviewPage })))
const ClusterHealthPage = lazy(() => import('./pages/ClusterHealth').then((m) => ({ default: m.ClusterHealthPage })))
const UsersPage = lazy(() => import('./pages/Users').then((m) => ({ default: m.UsersPage })))
const AuditLogPage = lazy(() => import('./pages/AuditLog').then((m) => ({ default: m.AuditLogPage })))

const PrivacyPage = lazy(() => import('./pages/legal/Privacy').then((m) => ({ default: m.PrivacyPage })))
const TermsPage = lazy(() => import('./pages/legal/Terms').then((m) => ({ default: m.TermsPage })))
const SupportPage = lazy(() => import('./pages/legal/Support').then((m) => ({ default: m.SupportPage })))
const DataDeletionPage = lazy(() => import('./pages/legal/DataDeletion').then((m) => ({ default: m.DataDeletionPage })))

export default function App() {
  return (
    <ErrorBoundary>
      <ToastProvider>
        <BrowserRouter>
          <AuthProvider>
            <ScrollToTop />
            <Suspense fallback={<RouteFallback />}>
              <Routes>
                <Route path="/" element={<LandingPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                <Route path="/reset-password" element={<ResetPasswordPage />} />

                {/* Legal pages are public and must stay reachable without an
                    account — a data-deletion policy behind a login is not a
                    policy anyone can act on. */}
                <Route path="/privacy" element={<PrivacyPage />} />
                <Route path="/terms" element={<TermsPage />} />
                <Route path="/support" element={<SupportPage />} />
                <Route path="/data-deletion" element={<DataDeletionPage />} />

                {/* Outside the Shell: onboarding owns the whole viewport. */}
                <Route
                  path="/onboarding"
                  element={
                    <AuthGuard>
                      <OnboardingPage />
                    </AuthGuard>
                  }
                />

                <Route
                  path="/dashboard"
                  element={
                    <AuthGuard>
                      <Shell />
                    </AuthGuard>
                  }
                >
                  <Route index element={<DrivePage />} />
                  <Route path="recent" element={<RecentPage />} />
                  <Route path="starred" element={<StarredPage />} />
                  <Route path="trash" element={<TrashPage />} />
                  <Route path="objects" element={<ObjectExplorerPage />} />
                  <Route path="objects/:objectId" element={<ObjectDetailPage />} />
                  <Route path="api-keys" element={<ApiKeysPage />} />
                  <Route path="settings" element={<SettingsPage />} />

                  <Route element={<AdminGuard />}>
                    <Route path="overview" element={<OverviewPage />} />
                    <Route path="cluster" element={<ClusterHealthPage />} />
                    <Route path="users" element={<UsersPage />} />
                    <Route path="audit-log" element={<AuditLogPage />} />
                  </Route>

                  {/* The old System page became Overview; keep bookmarks alive. */}
                  <Route path="system" element={<Navigate to="/dashboard/overview" replace />} />

                  {/* A mistyped path under /dashboard is still a 404, not a
                      silent bounce to the drive. */}
                  <Route path="*" element={<NotFoundPage inShell />} />
                </Route>

                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </Suspense>
          </AuthProvider>
        </BrowserRouter>
      </ToastProvider>
    </ErrorBoundary>
  )
}
