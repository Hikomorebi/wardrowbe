import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PersonalWardrobe } from '@/components/personal/wardrobe';
import { LocalLogin } from '@/components/personal/local-login';

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  patch: vi.fn(),
  signIn: vi.fn(),
  params: new URLSearchParams(),
  item: undefined as unknown,
}));
vi.mock('next/navigation', () => ({
  useSearchParams: () => mocks.params,
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock('next/image', () => ({
  default: (props: React.ImgHTMLAttributes<HTMLImageElement>) => <img {...props} />,
}));
vi.mock('next-auth/react', () => ({ signIn: mocks.signIn }));
vi.mock('@/components/item-detail-dialog', () => ({ ItemDetailDialog: () => null }));
vi.mock('@/lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/api')>()),
  api: { patch: mocks.patch },
}));
vi.mock('@/lib/hooks/use-items', () => ({
  useItems: () => ({ data: { items: [], total: 0, has_more: false }, isLoading: false }),
  useItem: () => ({ data: mocks.item }),
  useCreateItem: () => ({ mutateAsync: mocks.create }),
  useReplaceItemImage: () => ({ mutateAsync: vi.fn() }),
}));
vi.mock('@/lib/hooks/use-locations', () => ({
  useLocations: () => ({ data: [{ id: 'location-1', name: 'Bedroom' }] }),
}));
vi.mock('@/lib/hooks/use-translated-constants', () => ({
  useClothingTypes: () => [{ value: 't-shirt', label: 'T-shirt' }],
  useTypeLabel: () => (value: string) => value,
  useSeasonLabel: () => (value: string) => value,
}));

function mount(component: React.ReactNode) {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      {component}
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  mocks.create.mockReset();
  mocks.patch.mockReset();
  mocks.signIn.mockReset();
  mocks.item = undefined;
  mocks.params = new URLSearchParams();
});

describe('personal clothing forms', () => {
  it('submits photo, condition, storage location and multiple seasons together', async () => {
    mocks.create.mockResolvedValue({ id: 'new' });
    mount(<PersonalWardrobe />);
    fireEvent.click(screen.getByRole('button', { name: 'addItem' }));
    fireEvent.change(screen.getByLabelText('image'), {
      target: { files: [new File(['photo'], 'shirt.jpg', { type: 'image/jpeg' })] },
    });
    fireEvent.change(within(screen.getByRole('dialog')).getByLabelText('category'), {
      target: { value: 't-shirt' },
    });
    fireEvent.change(within(screen.getByRole('dialog')).getByLabelText('condition'), {
      target: { value: 'excellent' },
    });
    fireEvent.change(within(screen.getByRole('dialog')).getByLabelText('location'), {
      target: { value: 'location-1' },
    });
    fireEvent.click(screen.getByLabelText('spring'));
    fireEvent.click(screen.getByLabelText('summer'));
    fireEvent.submit(screen.getByRole('button', { name: 'save' }).closest('form')!);
    await waitFor(() => expect(mocks.create).toHaveBeenCalledOnce());
    const body: FormData = mocks.create.mock.calls[0][0];
    expect(body.get('condition')).toBe('excellent');
    expect(body.get('storage_location_id')).toBe('location-1');
    expect(body.getAll('season')).toEqual(['spring', 'summer']);
    expect(body.get('type')).toBe('t-shirt');
    expect(body.get('image')).toBeInstanceOf(File);
    expect(body.get('skip_ai')).toBe('true');
  });

  it('sends explicit clears when removing condition, location and season', async () => {
    mocks.params = new URLSearchParams('item=existing');
    mocks.item = {
      id: 'existing',
      type: 't-shirt',
      condition: 'good',
      storage_location_id: 'location-1',
      season: ['winter'],
      tags: {},
    };
    mocks.patch.mockResolvedValue({});
    mount(<PersonalWardrobe />);
    fireEvent.change(within(screen.getByRole('dialog')).getByLabelText('condition'), { target: { value: '' } });
    fireEvent.change(within(screen.getByRole('dialog')).getByLabelText('location'), { target: { value: '' } });
    fireEvent.click(screen.getByLabelText('winter'));
    fireEvent.submit(screen.getByRole('button', { name: 'save' }).closest('form')!);
    await waitFor(() =>
      expect(mocks.patch).toHaveBeenCalledWith(
        '/items/existing',
        expect.objectContaining({ condition: null, storage_location_id: null, season: [] }),
      ),
    );
  });
});

it('shows a failed password login and allows another attempt', async () => {
  mocks.signIn.mockResolvedValue({ ok: false, error: 'CredentialsSignin' });
  mount(<LocalLogin getCallbackUrl={() => '/dashboard'} />);
  fireEvent.change(screen.getByLabelText('email'), { target: { value: 'owner@example.com' } });
  fireEvent.change(screen.getByLabelText('password'), { target: { value: 'incorrect' } });
  fireEvent.submit(screen.getByRole('button', { name: 'login' }).closest('form')!);
  expect(await screen.findByRole('alert')).toHaveTextContent('loginError');
  expect(mocks.signIn).toHaveBeenCalledWith('local', {
    email: 'owner@example.com',
    password: 'incorrect',
    redirect: false,
  });
  expect(screen.getByRole('button', { name: 'login' })).toBeEnabled();
});
