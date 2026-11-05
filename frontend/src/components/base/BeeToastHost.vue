<script setup lang="ts">
/**
 * Toast 宿主：全应用只挂一次（`App.vue`），从 store 读队列并渲染。
 * 桌面右下角 24px 内缩；移动端底部居中、距 TabBar 上 16px。
 */
import BeeToast from './BeeToast.vue'
import { useToastStore } from '@/stores/ui-toast'

const store = useToastStore()

function onDismiss(id: number): void {
  store.dismiss(id)
}
</script>

<template>
  <Teleport to="body">
    <div class="bee-toast-host">
      <TransitionGroup name="bee-toast">
        <BeeToast v-for="toast in store.list" :key="toast.id" :toast="toast" @dismiss="onDismiss" />
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.bee-toast-host {
  position: fixed;
  right: var(--sp-6);
  bottom: var(--sp-6);
  z-index: 1100;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sp-2);
  pointer-events: none;
}

.bee-toast-host :deep(.bee-toast) {
  pointer-events: auto;
}

.bee-toast-enter-active,
.bee-toast-leave-active {
  transition:
    opacity var(--dur-base) var(--ease-out),
    transform var(--dur-base) var(--ease-out);
}

.bee-toast-enter-from,
.bee-toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.bee-toast-leave-active {
  position: absolute;
  right: 0;
}

@media (max-width: 767px) {
  .bee-toast-host {
    right: var(--sp-4);
    left: var(--sp-4);
    bottom: calc(var(--tabbar-h) + var(--sp-4));
    align-items: center;
  }

  .bee-toast-host :deep(.bee-toast) {
    width: 100%;
    max-width: 360px;
  }
}
</style>
