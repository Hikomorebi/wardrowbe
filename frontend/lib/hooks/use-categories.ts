'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from 'next-auth/react';
import { queryKeys } from './query-keys';
import { api } from '@/lib/api';
import { useSetTokenIfAvailable } from './use-session-token';

export interface ClothingCategory {
  id: string;
  name: string;
}
export const categoryKey = queryKeys.clothingCategories;

export function useCategories(enabled = true) {
  const { status } = useSession();
  useSetTokenIfAvailable();
  return useQuery({
    queryKey: categoryKey,
    queryFn: () => api.get<ClothingCategory[]>('/categories'),
    enabled: enabled && status === 'authenticated',
  });
}

export function useWriteCategory() {
  const qc = useQueryClient();
  useSetTokenIfAvailable();
  return useMutation({
    mutationFn: ({ id, name, remove }: { id?: string; name?: string; remove?: boolean }) =>
      remove
        ? api.delete(`/categories/${id}`)
        : id
          ? api.patch(`/categories/${id}`, { name })
          : api.post('/categories', { name }),
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: categoryKey }),
        qc.invalidateQueries({ queryKey: queryKeys.items.all }),
        qc.invalidateQueries({ queryKey: queryKeys.everyItem }),
        qc.invalidateQueries({ queryKey: queryKeys.outfits.all }),
        qc.invalidateQueries({ queryKey: queryKeys.everyOutfit }),
        qc.invalidateQueries({ queryKey: queryKeys.calendarOutfits.all }),
      ]);
    },
  });
}
