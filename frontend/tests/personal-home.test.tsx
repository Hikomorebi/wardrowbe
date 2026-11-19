import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DashboardLayout from '@/app/dashboard/layout';

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  push: vi.fn(),
  legacy: vi.fn(),
  auth: {
    user: { onboarding_completed: true } as { onboarding_completed: boolean } | undefined,
    isAuthenticated: true,
    isLoading: false,
  },
  features: {
    data: { personal_mode: true } as { personal_mode: boolean } | undefined,
    isLoading: false,
  },
}));

vi.mock('next/navigation', () => ({
  usePathname: () => '/dashboard',
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}));
vi.mock('@/lib/hooks/use-features', () => ({ useFeatures: () => mocks.features }));
vi.mock('@/lib/hooks/use-auth', () => ({ useAuth: () => mocks.auth }));

function renderHome() {
  function LegacyDashboard() {
    mocks.legacy();
    return <div>Legacy dashboard</div>;
  }
  return render(<DashboardLayout><LegacyDashboard /></DashboardLayout>);
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.auth.user = { onboarding_completed: true };
  mocks.auth.isAuthenticated = true;
  mocks.auth.isLoading = false;
  mocks.features.data = { personal_mode: true };
  mocks.features.isLoading = false;
});
afterEach(cleanup);

describe('personal home entry', () => {
  it('sends an authenticated user to the wardrobe without mounting legacy queries', async () => {
    renderHome();
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledExactlyOnceWith('/dashboard/personal/wardrobe'));
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.legacy).not.toHaveBeenCalled();
  });

  it('sends an anonymous user only to login and keeps visible feedback during navigation', async () => {
    mocks.auth.user = undefined;
    mocks.auth.isAuthenticated = false;
    const screen = renderHome();
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledExactlyOnceWith('/login'));
    expect(mocks.push).not.toHaveBeenCalled();
    expect(screen.getByText('layout.loading')).toBeVisible();
    expect(mocks.legacy).not.toHaveBeenCalled();
  });

  it('waits for authentication before choosing the personal destination', () => {
    mocks.auth.isLoading = true;
    mocks.auth.isAuthenticated = false;
    renderHome();
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.legacy).not.toHaveBeenCalled();
  });

  it('redirects to login even while public feature flags are loading', async () => {
    mocks.auth.user = undefined;
    mocks.auth.isAuthenticated = false;
    mocks.features.data = undefined;
    mocks.features.isLoading = true;
    renderHome();
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledExactlyOnceWith('/login'));
    expect(mocks.push).not.toHaveBeenCalled();
  });

  it('sends an incomplete profile only to onboarding', async () => {
    mocks.auth.user = { onboarding_completed: false };
    renderHome();
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledExactlyOnceWith('/onboarding'));
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.legacy).not.toHaveBeenCalled();
  });
});
