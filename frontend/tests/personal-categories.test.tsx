import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { toast } from 'sonner';
import { PersonalCategories } from '@/components/personal/categories';
import { ApiError } from '@/lib/api';

const write = vi.hoisted(() => vi.fn());
vi.mock('@/lib/hooks/use-categories', () => ({
  useCategories: () => ({ data: [{ id: 'category-1', name: '短袖' }] }),
  useWriteCategory: () => ({ mutateAsync: write, isPending: false }),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
beforeEach(() => vi.clearAllMocks());

it('creates a trimmed category and allows renaming it', async () => {
  write.mockResolvedValue({});
  render(<PersonalCategories />);
  fireEvent.change(screen.getByLabelText('categoryName'), { target: { value: '  外套  ' } });
  fireEvent.click(screen.getByRole('button', { name: 'add' }));
  await waitFor(() => expect(write).toHaveBeenCalledWith({ name: '外套', id: undefined }));
  await waitFor(() => expect(screen.getByLabelText('categoryName')).toHaveValue(''));
  fireEvent.click(screen.getByRole('button', { name: 'rename' }));
  expect(screen.getByLabelText('categoryName')).toHaveValue('短袖');
  fireEvent.change(screen.getByLabelText('categoryName'), { target: { value: '夏装' } });
  fireEvent.click(screen.getByRole('button', { name: 'save' }));
  await waitFor(() => expect(write).toHaveBeenCalledWith({ name: '夏装', id: 'category-1' }));
});

it('explains an in-use deletion without clearing the category from the page', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  write.mockRejectedValue(new ApiError('In use', 409, undefined));
  render(<PersonalCategories />);
  fireEvent.click(screen.getByRole('button', { name: 'delete' }));
  await waitFor(() => expect(toast.error).toHaveBeenCalledWith('categoryInUse'));
  expect(screen.getByText('短袖')).toBeInTheDocument();
});

it('resets a deleted category that was being renamed', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  write.mockResolvedValue({});
  render(<PersonalCategories />);
  fireEvent.click(screen.getByRole('button', { name: 'rename' }));
  fireEvent.click(screen.getByRole('button', { name: 'delete' }));
  await waitFor(() => expect(screen.getByLabelText('categoryName')).toHaveValue(''));
  expect(screen.getByRole('button', { name: 'add' })).toBeDisabled();
});
