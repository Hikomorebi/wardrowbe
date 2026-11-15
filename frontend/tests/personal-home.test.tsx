import { render, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import DashboardLayout from '@/app/dashboard/layout';

const mocks = vi.hoisted(() => ({ replace: vi.fn(), legacy: vi.fn() }));
vi.mock('next/navigation', () => ({ usePathname: () => '/dashboard', useRouter: () => ({ replace: mocks.replace, push: vi.fn() }) }));
vi.mock('@/lib/hooks/use-features', () => ({ useFeatures: () => ({ data: { personal_mode: true }, isLoading: false }) }));
vi.mock('@/lib/hooks/use-auth', () => ({ useAuth: () => ({ user: { onboarding_completed: true }, isAuthenticated: true, isLoading: false }) }));

it('redirects the personal home without mounting legacy weather and dashboard queries', async () => {
  function LegacyDashboard() { mocks.legacy(); return null; }
  render(<DashboardLayout><LegacyDashboard /></DashboardLayout>);
  await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith('/dashboard/personal/wardrobe'));
  expect(mocks.legacy).not.toHaveBeenCalled();
});
