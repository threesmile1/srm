import { watch, type Ref } from 'vue'

/** 与开发计划 T11-③「列表记住组织筛选」一致 */
export const PROC_ORG_STORAGE_PREFIX = 'srm.admin.procOrg.'

/**
 * 在采购组织列表加载后恢复上次选择的组织，并在变更时写入 sessionStorage。
 * @param pageKey 各页面唯一键，如 po-list、gr-list
 * @param preferredOrgId 无本地记忆时优先使用的组织（如当前登录用户的默认采购组织）
 */
export function usePersistedProcurementOrg(
  orgId: Ref<number | null>,
  orgs: Ref<{ id: number }[]>,
  pageKey: string,
  preferredOrgId?: () => number | null | undefined,
) {
  const key = PROC_ORG_STORAGE_PREFIX + pageKey

  watch(
    orgs,
    (list) => {
      if (!list.length) return
      const raw = sessionStorage.getItem(key)
      let saved: number | null = null
      if (raw) {
        const n = Number(raw)
        if (!Number.isNaN(n) && list.some((o) => o.id === n)) saved = n
      }
      if (saved != null) {
        orgId.value = saved
        return
      }
      const pref = preferredOrgId?.()
      if (pref != null && list.some((o) => o.id === pref)) {
        orgId.value = pref
        return
      }
      if (orgId.value == null || !list.some((o) => o.id === orgId.value)) {
        orgId.value = list[0].id
      }
    },
    { immediate: true },
  )

  watch(orgId, (v) => {
    if (v != null) sessionStorage.setItem(key, String(v))
  })
}
