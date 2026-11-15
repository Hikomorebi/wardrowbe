'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from 'next-auth/react';
import { api } from '@/lib/api';
import { useSetTokenIfAvailable } from './use-session-token';

export interface StorageLocation {
  id: string;
  name: string;
}
export const locationKey = ['storageLocations'] as const;

export function useLocations() {
  const { status } = useSession();
  useSetTokenIfAvailable();
  return useQuery({
    queryKey: locationKey,
    queryFn: () => api.get<StorageLocation[]>('/locations'),
    enabled: status === 'authenticated',
  });
}

export function useWriteLocation() {
  const qc = useQueryClient();
  useSetTokenIfAvailable();
  return useMutation({
    mutationFn: ({ id, name, remove }: { id?: string; name?: string; remove?: boolean }) =>
      remove
        ? api.delete(`/locations/${id}`)
        : id
          ? api.patch(`/locations/${id}`, { name })
          : api.post('/locations', { name }),
    onSuccess: () => qc.invalidateQueries({ queryKey: locationKey }),
  });
}
