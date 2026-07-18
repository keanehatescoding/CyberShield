import { defineConfig } from "vitest/config";
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
    // Rules evaluation round-trips through the emulator over HTTP; give it
    // more headroom than the default 5s, especially on a cold-started CI runner.
    testTimeout: 20000,
    hookTimeout: 20000,
    // All *.rules.test.ts files call initializeTestEnvironment against the
    // same projectId and the same emulator (127.0.0.1:8080). Run with the
    // default parallel workers, and two files' authenticatedContext(...)
    // .firestore() calls can race while configuring the underlying
    // Firestore SDK instance, throwing "Firestore has already been started
    // and its settings can no longer be changed" in whichever file loses
    // the race. Running files sequentially avoids that race entirely.
    fileParallelism: false,
  },
});
