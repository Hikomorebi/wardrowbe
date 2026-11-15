import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ClothingCard } from '@/components/personal/clothing-card';
import type { Item } from '@/lib/types';

vi.mock('next/image', () => ({
  default: ({ fill, ...props }: React.ImgHTMLAttributes<HTMLImageElement> & { fill?: boolean }) => <img {...props} />,
}));
vi.mock('@/lib/hooks/use-translated-constants', () => ({
  useSeasonLabel: () => (value: string) => value,
}));

const item = {
  id: 'shirt-1', name: '蓝色毛衣', type: 'sweater', category_name: '毛衣',
  image_url: '/shirt.jpg', condition: 'good', season: ['winter'], tags: {}, is_archived: false,
} as Item;

describe('clothing card actions', () => {
  it('offers one matching link and keeps details, archive and delete as distinct actions', () => {
    const onOpen = vi.fn(), onArchive = vi.fn(), onDelete = vi.fn();
    render(<ClothingCard item={item} locationName="衣柜" busy={false} onOpen={onOpen} onArchive={onArchive} onDelete={onDelete} />);
    expect(screen.getAllByRole('link')).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'matchingOutfits' })).toHaveAttribute('href', '/dashboard/personal/outfits?item=shirt-1');
    expect(screen.queryByText('createOutfit')).not.toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'season' })).toHaveTextContent('winter');
    expect(screen.getByText('衣柜')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'editItem：蓝色毛衣' }));
    fireEvent.click(screen.getByRole('button', { name: 'archive' }));
    fireEvent.click(screen.getByRole('button', { name: 'delete' }));
    expect(onOpen).toHaveBeenCalledOnce();
    expect(onArchive).toHaveBeenCalledOnce();
    expect(onDelete).toHaveBeenCalledOnce();
  });

  it('offers restore for archived clothing and disables mutations while saving', () => {
    render(<ClothingCard item={{ ...item, is_archived: true }} busy onOpen={vi.fn()} onArchive={vi.fn()} onDelete={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'restore' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'delete' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'archive' })).not.toBeInTheDocument();
  });
});
