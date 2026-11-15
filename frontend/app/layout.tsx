import type { Metadata, Viewport } from 'next';
import { Inter } from 'next/font/google';
import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages } from 'next-intl/server';
import './globals.css';
import { Providers } from './providers';
import { FilingFooter } from '@/components/filing-footer';
import { LOCALE_METADATA, type SupportedLocale } from '@/lib/i18n/locales';

export const dynamic = 'force-dynamic';

const inter = Inter({ subsets: ['latin', 'latin-ext'], variable: '--font-inter', display: 'swap' });

export const metadata: Metadata = {
  title: process.env.PERSONAL_MODE === 'true' ? '我的衣橱' : 'Wardrowbe',
  description: process.env.PERSONAL_MODE === 'true'
    ? '个人衣物、存放位置与穿搭记录'
    : 'AI-powered wardrobe management and outfit recommendations',
  icons: {
    icon: [
      { url: '/favicon.ico', sizes: 'any' },
      { url: '/icon-16.png', sizes: '16x16', type: 'image/png' },
      { url: '/icon-32.png', sizes: '32x32', type: 'image/png' },
      { url: '/icon-192.png', sizes: '192x192', type: 'image/png' },
      { url: '/icon-512.png', sizes: '512x512', type: 'image/png' },
    ],
    apple: [
      { url: '/apple-touch-icon.png', sizes: '180x180', type: 'image/png' },
    ],
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html lang={locale} dir={LOCALE_METADATA[locale as SupportedLocale]?.dir ?? 'ltr'} suppressHydrationWarning>
      <body className={`${inter.variable} font-sans`}>
        <NextIntlClientProvider messages={messages} locale={locale}>
          <Providers>{children}</Providers>
          <FilingFooter recordNumber={process.env.ICP_RECORD_NUMBER} />
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
