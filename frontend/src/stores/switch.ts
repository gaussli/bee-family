import { defineStore } from 'pinia'
import { ref } from 'vue'
import router from '@/router'
import { useAlbumStore } from './album'
import { useFamilyStore } from './family'
import { usePhotoStore } from './photo'
import { useUploadProgressStore } from './upload-progress'

/**
 * 切换家庭的**唯一协调点**（docs/07 §10.3）。
 *
 * <p>架构约定「store 之间禁止互相 import」在这里按 §10.3 的显式要求破例一次：
 * 13 项清理必须集中于单一函数 `resetAllForFamily`，禁止散落到各组件。
 * 本 store 不承载业务数据，只做跨域协调。</p>
 *
 * <p>执行顺序严格为：先 `cancelAll()` → 再清空 store → 再重挂载 → 最后
 * `router.replace('/timeline')`。</p>
 */
export const useSwitchStore = defineStore('switch', () => {
  /** 图片组件 / 路由视图强制重挂载的 epoch（§10.3 第 9 / 12 项）。 */
  const familyEpoch = ref<number>(0)
  /** 全站搜索关键词（§10.3 第 5 项；TopNav / TopBar 共用）。 */
  const searchKeyword = ref<string>('')
  /** 「查看上传队列」的高亮信号，递增即触发一次 200ms 高亮动画。 */
  const queueFocusNonce = ref<number>(0)

  function setSearchKeyword(value: string): void {
    searchKeyword.value = value
  }

  function clearSearch(): void {
    searchKeyword.value = ''
  }

  function focusUploadQueue(): void {
    queueFocusNonce.value += 1
  }

  /**
   * 切换家庭成功后的 13 项状态全量重置。
   *
   * 1  activeBabyId 按 familyId 分键重读（bindFamily）
   * 2  当前相册 / 相册列表 / 封面候选（album.reset）
   * 3  时间轴分页 / 分组 / 滚动位置 + 照片列表 / 勾选集（photo.reset）
   * 4  灯箱关闭并卸载（photo.closeLightbox + epoch 重挂载）
   * 5  搜索词 / 搜索结果 / 筛选条件（clearSearch）
   * 6  在途业务请求（uploadProgress.cancelAll）
   * 7  统计 / 标签 / 成员列表 / 待审角标（family.reset + 按新家庭重取）
   * 8  上传队列（阻断态 / activeCount / 队列项）清空
   * 9  图片组件强制重挂载（familyEpoch，App.vue 用于 RouterView :key，Lightbox 用于 img :key）
   * 10 路由强制 replace('/timeline')
   * 11 localStorage 家庭相关键（bindFamily 分键，clearLocal 只清分键）
   * 12 图片缓存键 / 骨架 / loading 占位状态（photo.reset + epoch）
   * 13 账号 / token / 主题 / Toast 队列：**保留，不清**（避免误登出 / 闪屏）
   *
   * @param newFamilyId 服务端返回的新激活家庭 id
   */
  function resetAllForFamily(newFamilyId: number): void {
    const progress = useUploadProgressStore()
    const familyStore = useFamilyStore()
    const album = useAlbumStore()
    const photo = usePhotoStore()

    // ---- 先 cancelAll()：第 6 项在途请求 + 第 8 项上传队列 ----
    progress.cancelAll()

    // ---- 再清空 store ----
    clearSearch() // 第 5 项
    photo.reset() // 第 3 / 4 / 12 项：列表、勾选集、分页、灯箱、骨架 / loading 占位
    photo.closeLightbox() // 第 4 项显式关闭并卸载
    album.reset() // 第 2 项：相册列表 / 当前相册 / 封面候选
    familyStore.reset() // 第 7 项：成员列表 / 待审角标 / 统计 / 标签建议
    familyStore.bindFamily(newFamilyId) // 第 1 / 11 项：按新家庭分键重读 activeBabyId

    // 第 13 项：账号 / token / 主题 / Toast 队列在此**刻意不动**（避免误登出 / 闪屏）。
    // 调用方已用切换接口返回的 MeResp 调过 auth.applyMe()。

    // ---- 再重挂载 ----
    familyEpoch.value += 1 // 第 9 / 12 项

    // ---- 最后强制回时间轴，不留旧家庭深链（第 10 项）----
    void router.replace({ name: 'timeline' })
  }

  return {
    familyEpoch,
    searchKeyword,
    queueFocusNonce,
    setSearchKeyword,
    clearSearch,
    focusUploadQueue,
    resetAllForFamily,
  }
})
