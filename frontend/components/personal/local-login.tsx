'use client';

import { useState } from 'react';
import { signIn } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';

export function LocalLogin({ getCallbackUrl }: { getCallbackUrl: () => string }) {
  const t = useTranslations('personal');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  return (
    <form
      className="space-y-4"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(false);
        const fields = new FormData(event.currentTarget);
        try {
          const result = await signIn('local', {
            email: fields.get('email'),
            password: fields.get('password'),
            redirect: false,
          });
          if (result?.ok && !result.error) window.location.assign(getCallbackUrl());
          else setError(true);
        } catch {
          setError(true);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="local-email">{t('email')}</Label>
        <Input id="local-email" name="email" type="email" autoComplete="username" required />
      </div>
      <div className="space-y-2">
        <Label htmlFor="local-password">{t('password')}</Label>
        <Input
          id="local-password"
          name="password"
          type="password"
          autoComplete="current-password"
          maxLength={128}
          required
        />
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {t('loginError')}
        </p>
      )}
      <Button className="w-full" disabled={busy}>
        {busy ? t('saving') : t('login')}
      </Button>
    </form>
  );
}
