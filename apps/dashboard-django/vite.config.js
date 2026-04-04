import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  root: "frontend",
  build: {
    outDir: "static",
    manifest: true,
    rollupOptions: {
      input: "frontend/src/main.jsx",
    },
  },
  server: {
    port: 5173,
    origin: "http://localhost:5173",
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./frontend/src/test-setup.js"],
    globals: true,
    root: ".",
  },
});
