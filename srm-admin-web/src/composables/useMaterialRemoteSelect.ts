import { ref, type Ref } from 'vue'
import { masterApi, type Material } from '../api/master'

/** 与后端 {@code normalizeMaterialPageSize} 对齐，远程下拉取 50 条 */
const DEFAULT_PAGE_SIZE = 50

/**
 * 新建单据行上的物料下拉：分页搜索，避免全量 /materials/all + 海量 DOM 卡死页面。
 */
export function useMaterialRemoteSelect(pageSize = DEFAULT_PAGE_SIZE) {
  const materialOptions: Ref<Material[]> = ref([])
  const materialLoading = ref(false)
  const materialById = new Map<number, Material>()

  function rememberMaterials(items: Material[]) {
    for (const m of items) {
      materialById.set(m.id, m)
    }
  }

  function getMaterial(id: number | null | undefined): Material | undefined {
    if (id == null) return undefined
    return materialById.get(id)
  }

  let searchTimer: ReturnType<typeof setTimeout> | null = null

  async function fetchMaterials(keyword: string) {
    materialLoading.value = true
    try {
      const q = keyword.trim()
      const r = await masterApi.listMaterials({
        page: 0,
        size: pageSize,
        q: q.length > 0 ? q : undefined,
      })
      materialOptions.value = r.data.content
      rememberMaterials(r.data.content)
    } finally {
      materialLoading.value = false
    }
  }

  function remoteSearch(query: string) {
    if (searchTimer != null) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      void fetchMaterials(query)
    }, 280)
  }

  /** 首屏默认选项（按编码排序前若干条） */
  async function prefetchInitial() {
    await fetchMaterials('')
  }

  return {
    materialOptions,
    materialLoading,
    remoteSearch,
    prefetchInitial,
    getMaterial,
    rememberMaterials,
  }
}
