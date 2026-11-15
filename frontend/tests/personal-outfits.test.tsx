import { fireEvent, render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, expect, it, vi } from 'vitest';
import { PersonalOutfits } from '@/components/personal/outfits';
import type { Outfit } from '@/lib/types';

const mocks = vi.hoisted(() => ({
  params: new URLSearchParams(), outfits: [] as Outfit[], useOutfits: vi.fn(),
}));
vi.mock('next/navigation', () => ({
  useSearchParams: () => mocks.params,
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock('next/image', () => ({
  default: (props: React.ImgHTMLAttributes<HTMLImageElement>) => <img {...props} />,
}));
vi.mock('@/lib/hooks/use-outfits', () => ({ useOutfits: mocks.useOutfits }));
vi.mock('@/lib/hooks/use-items', () => ({
  useItem: (id: string) => ({ data: id === 'shirt-1' ? { id, name: '蓝色毛衣', category_name: '毛衣', thumbnail_url: '/shirt.jpg' } : undefined }),
}));
vi.mock('@/lib/hooks/use-translated-constants', () => ({ useTypeLabel: () => (value: string) => value }));
vi.mock('@/lib/hooks/use-studio', () => ({
  useCreateStudioOutfit: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePatchOutfit: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));
vi.mock('@/components/shared/item-picker', () => ({ ItemPicker: () => <div /> }));
const outfit = {
  id: 'outfit-1', name: '冬季组合', items: [
    { id: 'shirt-1', name: '蓝色毛衣', type: 'sweater', thumbnail_url: '/shirt.jpg' },
    { id: 'pants-1', name: '白色长裤', type: 'pants', thumbnail_url: '/pants.jpg' },
  ],
} as Outfit;
function mount() {
  return render(<QueryClientProvider client={new QueryClient()}><PersonalOutfits /></QueryClientProvider>);
}
beforeEach(() => {
  mocks.params = new URLSearchParams();
  mocks.outfits = [outfit];
  mocks.useOutfits.mockImplementation(() => ({ data: { outfits: mocks.outfits, has_more: false } }));
});

it('shows the source separately and only its companion items in saved matching outfits', () => {
  mocks.params = new URLSearchParams('item=shirt-1');
  mount();
  expect(mocks.useOutfits).toHaveBeenCalledWith({ source: 'manual', is_lookbook: true, item_id: 'shirt-1' }, 1, 24);
  expect(screen.getByText('currentItem')).toBeInTheDocument();
  const result = within(screen.getByRole('article'));
  expect(result.getByRole('img', { name: '白色长裤' })).toBeInTheDocument();
  expect(result.queryByRole('img', { name: '蓝色毛衣' })).not.toBeInTheDocument();
  expect(result.getByRole('link', { name: '白色长裤' })).toHaveAttribute('href', '/dashboard/personal/wardrobe?item=pants-1');
  expect(screen.queryByRole('button', { name: 'createOutfit' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'editOutfit' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'outfits' })).toHaveAttribute('href', '/dashboard/personal/outfits');
});

it('keeps creating outfits in My outfits', () => {
  mount();
  fireEvent.click(screen.getByRole('button', { name: 'createOutfit' }));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(screen.getByLabelText('name')).toBeInTheDocument();
});

it('explains how to associate clothing when there is no saved match', () => {
  mocks.params = new URLSearchParams('item=shirt-1');
  mocks.outfits = [];
  mount();
  expect(screen.getByText('emptyMatchingOutfits')).toBeInTheDocument();
  expect(screen.queryByText('emptyOutfits')).not.toBeInTheDocument();
});

it('explains saved single-item outfits without listing the source as a companion', () => {
  mocks.params = new URLSearchParams('item=shirt-1');
  mocks.outfits = [{ ...outfit, items: [outfit.items[0]] }];
  mount();
  expect(screen.getByText('noCompanionItems')).toBeInTheDocument();
  expect(within(screen.getByRole('article')).queryByRole('link')).not.toBeInTheDocument();
});
