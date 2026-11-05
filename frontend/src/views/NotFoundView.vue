<script setup lang="ts">
/**
 * 404 页。保留品牌感：蜂巢底纹 + 一句温和的提示 + 回首页。
 */
import BeeButton from '@/components/base/BeeButton.vue'
import HexPattern from '@/components/business/HexPattern.vue'
</script>

<template>
  <div class="bee-not-found">
    <HexPattern class="bee-not-found__pattern" :cols="8" :rows="6" :radius="30" :opacity="0.06" />

    <div class="bee-not-found__inner">
      <p class="bee-not-found__code">404</p>
      <h1 class="bee-not-found__title">这一页飞走了</h1>
      <p class="bee-not-found__desc">链接可能已经失效，或者这本相册已经被删除了。</p>
      <BeeButton size="lg" @click="$router.replace('/timeline')">回到时间轴</BeeButton>
    </div>
  </div>
</template>

<style scoped>
.bee-not-found {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--sp-8);
  background: var(--bg-page);
  overflow: hidden;
}

.bee-not-found__inner {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
  text-align: center;
}

.bee-not-found__code {
  font-family: var(--font-display);
  font-size: 96px;
  line-height: 1;
  font-weight: 700;
  color: var(--honey-200);
  letter-spacing: 0.04em;
}

.bee-not-found__title {
  font-family: var(--font-display);
  font-size: var(--fs-h2);
  line-height: var(--lh-h2);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-not-found__desc {
  max-width: 320px;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-not-found__pattern {
  z-index: 1;
}
</style>
