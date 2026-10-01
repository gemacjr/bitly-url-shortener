import type { NextConfig } from "next";

// The browser only ever talks to this Next.js origin; /api/* is proxied to Spring Boot,
// so there is no CORS to configure. Short links themselves live on the backend domain.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8090";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/:path*` }];
  },
};

export default nextConfig;
