import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * Vite 配置：@ 别名指向 src（与 tsconfig 的 paths 保持同步），
 * 开发期把 /api 代理到后端 8080，避免任何跨域配置。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        /**
         * ECharts 按目录拆成多个 chunk：
         * 1) 只有进成长记录页才会下载，不进首屏；
         * 2) 单文件保持在 esbuild 可高效处理的体积内（整包约 1.2MB，压缩阶段开销大）。
         */
        manualChunks(id: string): string | undefined {
          if (id.includes('node_modules/zrender')) return 'echarts-zrender'
          if (id.includes('node_modules/echarts')) {
            if (id.includes('/lib/chart/') || id.includes('/echarts/charts')) return 'echarts-chart'
            if (id.includes('/lib/component/') || id.includes('/echarts/components')) {
              return 'echarts-component'
            }
            return 'echarts-core'
          }
          return undefined
        },
      },
    },
  },
})
