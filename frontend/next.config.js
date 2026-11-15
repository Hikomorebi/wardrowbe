const createNextIntlPlugin = require('next-intl/plugin');

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  images: {
    unoptimized: true,
  },
  // Skip type checking and linting during build (already done in CI)
  typescript: {
    ignoreBuildErrors: false,
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
  // /api/v1/* is proxied by app/api/v1/[...path]/route.ts rather than a rewrite here, because
  // rewrites() is serialized into routes-manifest.json at build time and so cannot honor a
  // runtime BACKEND_URL in the prebuilt image.
};

const withNextIntl = createNextIntlPlugin();
module.exports = withNextIntl(nextConfig);
