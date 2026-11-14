import { computed, reactive, ref, watch, type ComputedRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

type TaskFilters = { search: string; status: string; priority: string }
type SavedFilter = { name: string; filters: TaskFilters }

export function useTaskFilters(key: ComputedRef<string>) {
  const filters = reactive<TaskFilters>({ search: '', status: '', priority: '' })
  const savedFilters = ref<SavedFilter[]>([])
  const selectedFilter = ref('')

  watch(key, (storageKey) => {
    Object.assign(filters, { search: '', status: '', priority: '' })
    selectedFilter.value = ''
    savedFilters.value = []
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey) ?? '[]')
      if (Array.isArray(saved)) {
        savedFilters.value = saved.filter((item) =>
          typeof item?.name === 'string' &&
          typeof item?.filters?.search === 'string' &&
          typeof item?.filters?.status === 'string' &&
          typeof item?.filters?.priority === 'string',
        )
      }
    } catch {
      ElMessage.warning('无法读取已保存的视图')
    }
  }, { immediate: true })

  function store(views: SavedFilter[]) {
    try {
      localStorage.setItem(key.value, JSON.stringify(views))
      savedFilters.value = views
      return true
    } catch {
      ElMessage.error('无法保存视图，请检查浏览器存储设置')
      return false
    }
  }

  function loadFilter(name: string) {
    const view = savedFilters.value.find((item) => item.name === name)
    if (view) Object.assign(filters, view.filters)
  }

  async function saveFilter() {
    const storageKey = key.value
    try {
      const { value } = await ElMessageBox.prompt('为当前筛选条件命名', '保存任务视图', {
        inputValidator: (name) => !!name?.trim() && name.trim().length <= 40 || '请输入 1–40 个字符',
      })
      if (storageKey !== key.value) return
      const name = value.trim()
      if (savedFilters.value.some((item) => item.name === name)) {
        ElMessage.warning('该视图名称已存在')
        return
      }
      if (store([...savedFilters.value, { name, filters: { ...filters } }])) selectedFilter.value = name
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error('无法保存任务视图')
    }
  }

  const hasSelectedFilter = computed(() => savedFilters.value.some((item) => item.name === selectedFilter.value))

  function removeFilter() {
    if (store(savedFilters.value.filter((item) => item.name !== selectedFilter.value))) selectedFilter.value = ''
  }

  return { filters, savedFilters, selectedFilter, hasSelectedFilter, loadFilter, saveFilter, removeFilter }
}
