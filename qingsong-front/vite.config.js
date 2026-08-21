import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  define: {
    'process.env': {}
  },
  server: {
    host: '0.0.0.0',
    port: 80,
    strictPort: true,
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'unsafe-none'
    },
    hmr: {
      overlay: false
    },
    proxy: {
      // 后端主接口：局域网访问时由 dev server 转发，避免前端写死 localhost
      '/user-config': { target: 'http://localhost:8088', changeOrigin: true },
      '/ai': { target: 'http://localhost:8088', changeOrigin: true },
      '/api': { target: 'http://localhost:8088', changeOrigin: true },
      '/role-phrases': { target: 'http://localhost:8088', changeOrigin: true },
      '/roles': { target: 'http://localhost:8088', changeOrigin: true },
      '/role': { target: 'http://localhost:8088', changeOrigin: true },
      '/tools': { target: 'http://localhost:8088', changeOrigin: true },
      '/message': { target: 'http://localhost:8088', changeOrigin: true },
      '/admin': { target: 'http://localhost:8088', changeOrigin: true }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-ui': ['naive-ui', '@heroicons/vue'],
          'vendor-markdown': ['marked', 'dompurify', 'highlight.js'],
          'vendor-utils': ['@vueuse/core']
        }
      }
    },
    minify: 'esbuild',
    chunkSizeWarningLimit: 1000
  },
  css: {
    preprocessorOptions: {
      scss: {
        charset: false
      }
    }
  },
  resolve: {
    alias: {
      '@': '/src'
    }
  }
})
