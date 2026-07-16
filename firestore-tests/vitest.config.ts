import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
    // Rules evaluation round-trips through the emulator over HTTP; give it
    // more headroom than the default 5s, especially on a cold-started CI runner.
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
