'use client';

import { useState, useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Sidebar } from '@/components/sidebar';
import { MobileSidebar } from '@/components/mobile-sidebar';
import { MobileNav } from '@/components/mobile-nav';
import { Header } from '@/components/header';
import { AiRouteGuard } from '@/components/ai-route-guard';
import { OfflineIndicator } from '@/components/offline-indicator';
import { UploadQueueIndicator } from '@/components/upload-queue-indicator';
import { ImageLightbox } from '@/components/image-lightbox';
import { LightboxProvider } from '@/lib/lightbox-context';
import { PersonalNavigation } from '@/components/personal/navigation';
import { useFeatures } from '@/lib/hooks/use-features';
import { useAuth } from '@/lib/hooks/use-auth';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const { data: features, isLoading: featuresLoading } = useFeatures();
  const personal = features?.personal_mode === true;
  useEffect(() => {
    if (personal && pathname === "/dashboard") router.replace("/dashboard/personal/wardrobe");
  }, [personal, pathname, router]);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const t = useTranslations('dashboard');

  const { user, isAuthenticated, isLoading, error } = useAuth();

  useEffect(() => {
    // If auth check completed and user is not authenticated, redirect to login
    if (!isLoading && !isAuthenticated) {
      router.push('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  // Check onboarding status from API user
  useEffect(() => {
    if (user && user.onboarding_completed === false) {
      router.push('/onboarding');
    }
  }, [user, router]);

  if (isLoading || featuresLoading || (personal && pathname === "/dashboard")) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
          <p className="text-sm text-muted-foreground">{t('layout.loading')}</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <LightboxProvider>
      <div className="min-h-screen bg-background">
        {personal ? <PersonalNavigation /> : <><Sidebar /><MobileSidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} /></>}
        <div className="lg:pl-72">
          <Header onMenuClick={() => setSidebarOpen(true)} />
          <main className="py-6 px-4 sm:px-6 lg:px-8 pb-20 lg:pb-6 overflow-x-hidden">
            <AiRouteGuard>{children}</AiRouteGuard>
          </main>
        </div>
        {!personal && <MobileNav />}
        <OfflineIndicator />
        <UploadQueueIndicator />
        <ImageLightbox />
      </div>
    </LightboxProvider>
  );
}
