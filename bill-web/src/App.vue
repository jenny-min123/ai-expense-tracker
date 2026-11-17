<script setup>
import { computed, defineAsyncComponent, onMounted, onUnmounted, reactive, ref } from 'vue'
import { createBill, deleteBill, fetchBills, fetchCategories, parseAiBill, updateBill } from './api/bill'

/** 图表库体积较大，打开统计弹窗时再加载 */
const StatsModal = defineAsyncComponent(() => import('./components/StatsModal.vue'))

/** 分类接口不可用时的兜底数据 */
const DEFAULT_CATEGORIES = [
  { name: '餐饮', type: 'EXPENSE', icon: '🍜' },
  { name: '交通', type: 'EXPENSE', icon: '🚗' },
  { name: '购物', type: 'EXPENSE', icon: '🛒' },
  { name: '住房', type: 'EXPENSE', icon: '🏠' },
  { name: '医疗', type: 'EXPENSE', icon: '🏥' },
  { name: '娱乐', type: 'EXPENSE', icon: '🎮' },
  { name: '通讯', type: 'EXPENSE', icon: '📱' },
  { name: '教育', type: 'EXPENSE', icon: '📚' },
  { name: '其他', type: 'EXPENSE', icon: '📌' },
  { name: '工资', type: 'INCOME', icon: '💰' },
  { name: '奖金', type: 'INCOME', icon: '🎁' },
  { name: '理财', type: 'INCOME', icon: '📈' },
  { name: '兼职', type: 'INCOME', icon: '💼' },
  { name: '红包', type: 'INCOME', icon: '🧧' },
  { name: '退款', type: 'INCOME', icon: '↩️' },
  { name: '其他收入', type: 'INCOME', icon: '💵' },
]

/** 获取本地日期 YYYY-MM-DD */
function todayString() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const PAGE_SIZE = 10

const categories = ref([...DEFAULT_CATEGORIES])
const bills = ref([])
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const listLoading = ref(false)

const aiText = ref('')
const aiLoading = ref(false)

const form = reactive({
  amount: '',
  billType: 'EXPENSE',
  categoryName: '餐饮',
  billDate: todayString(),
  note: '',
})
const submitting = ref(false)

/** 右侧记一笔面板 */
const composeVisible = ref(false)

function closeCompose() {
  composeVisible.value = false
}

/* ---------- 编辑账单 ---------- */
const editVisible = ref(false)
const editingBill = ref(null)
const editSaving = ref(false)
const editForm = reactive({
  amount: '',
  billType: 'EXPENSE',
  categoryName: '',
  billDate: '',
  note: '',
})

const editCategoryOptions = computed(() =>
  categories.value.filter((item) => (item.type || 'EXPENSE') === editForm.billType),
)

/* ---------- 删除账单 ---------- */
const deleteTarget = ref(null)
const deleting = ref(false)

/* ---------- 收支统计 ---------- */
const statsVisible = ref(false)

/** 当前收支类型下的分类选项 */
const categoryOptions = computed(() =>
  categories.value.filter((item) => (item.type || 'EXPENSE') === form.billType),
)

/** 切换收支类型时，保证分类仍然合法 */
function setBillType(type) {
  if (form.billType === type) return
  form.billType = type
  if (!categoryOptions.value.some((item) => item.name === form.categoryName)) {
    form.categoryName = categoryOptions.value[0]?.name || ''
  }
}

/** 编辑弹窗里切换收支类型 */
function setEditBillType(type) {
  if (editForm.billType === type) return
  editForm.billType = type
  if (!editCategoryOptions.value.some((item) => item.name === editForm.categoryName)) {
    editForm.categoryName = editCategoryOptions.value[0]?.name || ''
  }
}

const toast = reactive({ visible: false, type: 'success', text: '' })
let toastTimer = null

function showToast(text, type = 'success') {
  toast.text = text
  toast.type = type
  toast.visible = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.visible = false
  }, 3200)
}

/** 分类图标 */
function categoryIcon(name) {
  const found = categories.value.find((item) => item.name === name)
  return found?.icon || '📌'
}

/** 金额格式化：25.5 -> 25.50 */
function formatAmount(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return String(value ?? '')
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 来源文案 */
function sourceLabel(sourceType) {
  return sourceType === 'AI' ? 'AI' : '手动'
}

/** 加载账单列表 */
async function loadBills(targetPage = page.value) {
  listLoading.value = true
  try {
    const data = await fetchBills(targetPage, PAGE_SIZE)
    bills.value = data?.records || []
    total.value = Number(data?.total || 0)
    pages.value = Number(data?.pages || 0)
    page.value = Number(data?.current || targetPage)
  } catch (error) {
    bills.value = []
    showToast(error.message || '账单加载失败', 'error')
  } finally {
    listLoading.value = false
  }
}

/** 翻页 */
function changePage(targetPage) {
  if (listLoading.value || targetPage < 1 || targetPage > pages.value) return
  loadBills(targetPage)
}

/** 加载分类 */
async function loadCategories() {
  try {
    const data = await fetchCategories()
    if (Array.isArray(data) && data.length > 0) {
      categories.value = data.map((item) => ({
        name: item.name,
        type: item.type || 'EXPENSE',
        icon: item.icon || '📌',
      }))
      if (!categoryOptions.value.some((item) => item.name === form.categoryName)) {
        form.categoryName = categoryOptions.value[0]?.name || ''
      }
    }
  } catch {
    // 分类加载失败时使用兜底分类，不打扰用户
  }
}

/** AI 智能记账 */
async function handleAiParse() {
  const text = aiText.value.trim()
  if (!text) {
    showToast('请先输入一句话，例如：今天打车花了25块5', 'error')
    return
  }
  aiLoading.value = true
  try {
    const bill = await parseAiBill(text)
    aiText.value = ''
    const typeLabel = bill?.billType === 'INCOME' ? '收入' : '支出'
    const tip = bill?.categoryName
      ? `已记${typeLabel}：${bill.categoryName} ¥${formatAmount(bill.amount)}`
      : 'AI 记账成功'
    showToast(tip, 'success')
    page.value = 1
    await loadBills(1)
  } catch (error) {
    showToast(error.message || 'AI 解析失败，请手动记账', 'error')
  } finally {
    aiLoading.value = false
  }
}

/** 手动记账 */
async function handleManualSubmit() {
  const amount = Number(form.amount)
  if (!form.amount || Number.isNaN(amount) || amount <= 0) {
    showToast('请输入正确的金额', 'error')
    return
  }
  if (!form.categoryName) {
    showToast('请选择分类', 'error')
    return
  }
  if (!categoryOptions.value.some((item) => item.name === form.categoryName)) {
    showToast('分类与收支类型不匹配，请重新选择', 'error')
    return
  }
  if (!form.billDate) {
    showToast('请选择日期', 'error')
    return
  }
  submitting.value = true
  try {
    await createBill({
      amount: Number(amount.toFixed(2)),
      billType: form.billType,
      categoryName: form.categoryName,
      billDate: form.billDate,
      note: form.note.trim(),
      sourceType: 'MANUAL',
    })
    showToast(form.billType === 'INCOME' ? '收入记账成功' : '支出记账成功', 'success')
    form.amount = ''
    form.note = ''
    await loadBills(1)
  } catch (error) {
    showToast(error.message || '提交失败，请稍后重试', 'error')
  } finally {
    submitting.value = false
  }
}

/** 打开编辑弹窗 */
function openEdit(bill) {
  editingBill.value = bill
  editForm.amount = bill.amount ?? ''
  editForm.billType = bill.billType === 'INCOME' ? 'INCOME' : 'EXPENSE'
  editForm.categoryName = bill.categoryName || ''
  editForm.billDate = bill.billDate || todayString()
  editForm.note = bill.note || ''
  editVisible.value = true
}

/** 关闭编辑弹窗 */
function closeEdit() {
  if (editSaving.value) return
  editVisible.value = false
}

/** 保存编辑 */
async function submitEdit() {
  const amount = Number(editForm.amount)
  if (!editForm.amount || Number.isNaN(amount) || amount <= 0) {
    showToast('请输入正确的金额', 'error')
    return
  }
  if (!editCategoryOptions.value.some((item) => item.name === editForm.categoryName)) {
    showToast('分类与收支类型不匹配，请重新选择', 'error')
    return
  }
  if (!editForm.billDate) {
    showToast('请选择日期', 'error')
    return
  }
  if (!editingBill.value) return
  editSaving.value = true
  try {
    await updateBill(editingBill.value.id, {
      amount: Number(amount.toFixed(2)),
      billType: editForm.billType,
      categoryName: editForm.categoryName,
      billDate: editForm.billDate,
      note: editForm.note.trim(),
    })
    showToast('账单已更新', 'success')
    editVisible.value = false
    await loadBills(page.value)
  } catch (error) {
    showToast(error.message || '保存失败，请稍后重试', 'error')
  } finally {
    editSaving.value = false
  }
}

/** 打开删除确认 */
function askDelete(bill) {
  deleteTarget.value = bill
}

/** 确认删除 */
async function confirmDelete() {
  if (!deleteTarget.value || deleting.value) return
  deleting.value = true
  try {
    await deleteBill(deleteTarget.value.id)
    showToast('账单已删除', 'success')
    deleteTarget.value = null
    // 删除当前页最后一条时自动回到上一页
    const target = bills.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
    await loadBills(target)
  } catch (error) {
    showToast(error.message || '删除失败，请稍后重试', 'error')
  } finally {
    deleting.value = false
  }
}

/** 带正负号的金额文案 */
function signedAmount(bill) {
  const sign = bill?.billType === 'INCOME' ? '+' : '-'
  return `${sign}¥${formatAmount(bill?.amount)}`
}

/** Esc 关闭弹窗 */
function handleKeydown(event) {
  if (event.key !== 'Escape') return
  if (editVisible.value) {
    closeEdit()
  } else if (deleteTarget.value && !deleting.value) {
    deleteTarget.value = null
  }
}

onMounted(() => {
  loadCategories()
  loadBills(1)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  clearTimeout(toastTimer)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="page" :class="{ 'compose-open': composeVisible }">
    <div class="container">
      <header class="hero">
        <div class="brand">
          <span class="brand-logo">¥</span>
          <div>
            <h1>AI 智能记账</h1>
            <p>一句话记一笔账，剩下的交给 AI</p>
          </div>
        </div>
        <div class="hero-actions">
          <button class="btn btn-primary compose-btn" type="button" @click="composeVisible = true">
            ＋ 记一笔
          </button>
          <button class="btn stats-btn" type="button" @click="statsVisible = true">
            <svg
              class="stats-icon"
              viewBox="0 0 24 24"
              width="16"
              height="16"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            >
              <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
            </svg>
            收支统计
          </button>
          <div class="hero-total">
            <span class="hero-total-label">累计账单</span>
            <strong>{{ total }}<small> 笔</small></strong>
          </div>
        </div>
      </header>

      <section class="card">
        <div class="card-title">
          <h2>账单列表</h2>
          <span class="hint">第 {{ page }} / {{ Math.max(pages, 1) }} 页 · 共 {{ total }} 笔</span>
        </div>

        <div class="table-wrap">
          <table class="bill-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>类型</th>
                <th>分类</th>
                <th class="num">金额</th>
                <th>备注</th>
                <th>来源</th>
                <th class="op-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="listLoading">
                <td class="empty" colspan="7">加载中…</td>
              </tr>
              <tr v-else-if="bills.length === 0">
                <td class="empty" colspan="7">还没有账单，记一笔试试吧</td>
              </tr>
              <template v-else>
                <tr v-for="bill in bills" :key="bill.id">
                  <td class="date">{{ bill.billDate }}</td>
                  <td>
                    <span class="type-badge" :class="bill.billType === 'INCOME' ? 'type-income' : 'type-expense'">
                      {{ bill.billType === 'INCOME' ? '收入' : '支出' }}
                    </span>
                  </td>
                  <td>
                    <span class="category-chip">{{ categoryIcon(bill.categoryName) }} {{ bill.categoryName }}</span>
                  </td>
                  <td class="num amount" :class="bill.billType === 'INCOME' ? 'amount-income' : 'amount-expense'">
                    {{ bill.billType === 'INCOME' ? '+' : '-' }}¥{{ formatAmount(bill.amount) }}
                  </td>
                  <td class="note" :title="bill.note || ''">{{ bill.note || '—' }}</td>
                  <td>
                    <span class="badge" :class="bill.sourceType === 'AI' ? 'badge-ai' : 'badge-manual'">
                      {{ sourceLabel(bill.sourceType) }}
                    </span>
                  </td>
                  <td class="actions">
                    <button class="link-btn" type="button" @click="openEdit(bill)">编辑</button>
                    <button class="link-btn danger" type="button" @click="askDelete(bill)">删除</button>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button class="btn btn-ghost" :disabled="page <= 1 || listLoading" @click="changePage(page - 1)">
            上一页
          </button>
          <span class="page-indicator">{{ page }} / {{ Math.max(pages, 1) }}</span>
          <button class="btn btn-ghost" :disabled="page >= pages || listLoading" @click="changePage(page + 1)">
            下一页
          </button>
        </div>
      </section>
    </div>

    <!-- 编辑账单弹窗 -->
    <transition name="modal">
      <div v-if="editVisible" class="modal-mask" @click.self="closeEdit">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="edit-title">
          <div class="modal-head">
            <h3 id="edit-title">编辑账单</h3>
            <button class="icon-btn" type="button" aria-label="关闭" @click="closeEdit">×</button>
          </div>
          <form class="modal-form" @submit.prevent="submitEdit">
            <div class="field">
              <label>收支类型</label>
              <div class="type-switch" role="group" aria-label="收支类型">
                <button
                  type="button"
                  :class="{ active: editForm.billType === 'EXPENSE' }"
                  @click="setEditBillType('EXPENSE')"
                >
                  支出
                </button>
                <button
                  type="button"
                  :class="{ active: editForm.billType === 'INCOME' }"
                  @click="setEditBillType('INCOME')"
                >
                  收入
                </button>
              </div>
            </div>
            <div class="field">
              <label for="edit-amount">金额（元）</label>
              <input
                id="edit-amount"
                v-model="editForm.amount"
                type="number"
                min="0.01"
                step="0.01"
                inputmode="decimal"
              />
            </div>
            <div class="field">
              <label for="edit-category">分类</label>
              <select id="edit-category" v-model="editForm.categoryName">
                <option v-for="item in editCategoryOptions" :key="item.name" :value="item.name">
                  {{ item.icon }} {{ item.name }}
                </option>
              </select>
            </div>
            <div class="field">
              <label for="edit-date">日期</label>
              <input id="edit-date" v-model="editForm.billDate" type="date" />
            </div>
            <div class="field field-full">
              <label for="edit-note">备注</label>
              <input id="edit-note" v-model="editForm.note" type="text" maxlength="500" placeholder="选填" />
            </div>
            <div class="modal-actions">
              <button class="btn btn-ghost" type="button" :disabled="editSaving" @click="closeEdit">取消</button>
              <button class="btn btn-primary" type="submit" :disabled="editSaving">
                {{ editSaving ? '保存中…' : '保存修改' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </transition>

    <!-- 删除确认弹窗 -->
    <transition name="modal">
      <div v-if="deleteTarget" class="modal-mask" @click.self="deleteTarget = null">
        <div class="modal modal-small" role="alertdialog" aria-modal="true" aria-labelledby="delete-title">
          <div class="modal-head">
            <h3 id="delete-title">删除账单</h3>
          </div>
          <p class="modal-text">
            确定要删除这笔账单吗？删除后无法恢复。
            <br />
            <strong>
              {{ deleteTarget.billDate }} · {{ deleteTarget.categoryName }} · {{ signedAmount(deleteTarget) }}
            </strong>
          </p>
          <div class="modal-actions">
            <button class="btn btn-ghost" type="button" @click="deleteTarget = null">取消</button>
            <button class="btn btn-danger" type="button" :disabled="deleting" @click="confirmDelete">
              {{ deleting ? '删除中…' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <transition name="toast">
      <div v-if="toast.visible" class="toast" :class="`toast-${toast.type}`" role="status">
        {{ toast.text }}
      </div>
    </transition>

    <div v-if="composeVisible" class="compose-mask" aria-hidden="true" @click="closeCompose"></div>
    <aside v-if="composeVisible" class="compose-panel" role="dialog" aria-labelledby="compose-title">
      <div class="compose-head">
        <h2 id="compose-title">记一笔</h2>
        <button class="icon-btn" type="button" aria-label="关闭" @click="closeCompose">×</button>
      </div>

      <div class="compose-ai">
        <div class="ai-row">
          <input
            v-model="aiText"
            class="ai-input"
            type="text"
            placeholder="试试输入：今天打车花了25块5"
            :disabled="aiLoading"
            @keyup.enter="handleAiParse"
          />
          <button class="btn btn-primary ai-btn" :disabled="aiLoading" @click="handleAiParse">
            <span v-if="aiLoading" class="spinner" aria-hidden="true"></span>
            {{ aiLoading ? 'AI 解析中…' : 'AI 智能记账' }}
          </button>
        </div>
        <p class="compose-hint">口语也行，自动识别收支、金额、分类和日期</p>
      </div>

      <div class="compose-divider"></div>

      <form class="compose-form" @submit.prevent="handleManualSubmit">
        <div class="field">
          <label>收支类型</label>
          <div class="type-switch" role="group" aria-label="收支类型">
            <button
              type="button"
              :class="{ active: form.billType === 'EXPENSE' }"
              @click="setBillType('EXPENSE')"
            >
              支出
            </button>
            <button
              type="button"
              :class="{ active: form.billType === 'INCOME' }"
              @click="setBillType('INCOME')"
            >
              收入
            </button>
          </div>
        </div>
        <div class="field">
          <label for="compose-amount">金额（元）</label>
          <input
            id="compose-amount"
            v-model="form.amount"
            type="number"
            min="0.01"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
          />
        </div>
        <div class="field">
          <label for="compose-category">分类</label>
          <select id="compose-category" v-model="form.categoryName">
            <option v-for="item in categoryOptions" :key="item.name" :value="item.name">
              {{ item.icon }} {{ item.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="compose-date">日期</label>
          <input id="compose-date" v-model="form.billDate" type="date" />
        </div>
        <div class="field">
          <label for="compose-note">备注</label>
          <input
            id="compose-note"
            v-model="form.note"
            type="text"
            maxlength="500"
            placeholder="选填，例如：午饭"
          />
        </div>
        <button class="btn btn-primary compose-submit" type="submit" :disabled="submitting">
          {{ submitting ? '提交中…' : '提交' }}
        </button>
      </form>
    </aside>

    <StatsModal v-if="statsVisible" @close="statsVisible = false" />
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 40px 20px 80px;
  background:
    radial-gradient(900px 420px at 12% -8%, rgba(99, 102, 241, 0.14), transparent 60%),
    radial-gradient(700px 360px at 92% 0%, rgba(56, 189, 248, 0.1), transparent 60%),
    var(--bg);
}

.container {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部品牌区 */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 6px 8px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-logo {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 15px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(99, 102, 241, 0.35);
}

.hero h1 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 0.5px;
  color: var(--text);
}

.hero p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--muted);
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stats-btn {
  height: 44px;
  padding: 0 18px;
  color: #fff;
  font-weight: 600;
  background: linear-gradient(135deg, #4f46e5, #7c3aed 55%, #0ea5e9);
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.3);
}

.stats-btn:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 30px rgba(79, 70, 229, 0.38);
}

.stats-icon {
  flex-shrink: 0;
}

.hero-total {
  text-align: right;
  padding: 10px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid var(--border);
  backdrop-filter: blur(8px);
}

.hero-total-label {
  display: block;
  font-size: 12px;
  color: var(--muted);
}

.hero-total strong {
  font-size: 22px;
  color: var(--primary-dark);
}

.hero-total small {
  font-size: 12px;
  color: var(--muted);
  font-weight: 500;
}

/* 卡片 */
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 22px 24px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.05);
}

.card-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.card-title h2 {
  margin: 0;
  font-size: 17px;
  color: var(--text);
}

.hint {
  font-size: 12px;
  color: var(--muted);
}

/* AI 区域 */
.ai-card {
  background:
    linear-gradient(135deg, rgba(99, 102, 241, 0.07), rgba(139, 92, 246, 0.04)),
    var(--card);
  border-color: rgba(99, 102, 241, 0.22);
}

.ai-row {
  display: flex;
  gap: 12px;
}

.ai-input {
  flex: 1;
  height: 48px;
  padding: 0 16px;
  font-size: 15px;
  color: var(--text);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.ai-input::placeholder {
  color: #9aa3b2;
}

.ai-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.14);
}

.ai-input:disabled {
  background: #f8fafc;
  color: var(--muted);
}

.ai-btn {
  height: 48px;
  padding: 0 22px;
  white-space: nowrap;
  font-size: 15px;
}

/* 手动表单 */
.manual-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 140px;
}

.field-grow {
  flex: 1;
  min-width: 200px;
}

.field-type {
  min-width: 150px;
}

/* 收支类型切换 */
.type-switch {
  display: flex;
  gap: 4px;
  height: 42px;
  padding: 4px;
  background: #f1f5f9;
  border: 1px solid var(--border);
  border-radius: 10px;
}

.type-switch button {
  flex: 1;
  min-width: 56px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.18s, color 0.18s, box-shadow 0.18s;
}

.type-switch button.active {
  background: #fff;
  color: var(--primary-dark);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
}

.field label {
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
}

.field input,
.field select {
  height: 42px;
  padding: 0 12px;
  font-size: 14px;
  color: var(--text);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.field input:focus,
.field select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.14);
}

.submit-btn {
  height: 42px;
  padding: 0 26px;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.2s, opacity 0.2s, background 0.2s;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
  box-shadow: none;
}

.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.3);
}

.btn-primary:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(79, 70, 229, 0.36);
}

.btn-ghost {
  color: var(--text);
  background: #fff;
  border-color: var(--border);
}

.btn-ghost:not(:disabled):hover {
  border-color: #c7cdf9;
  color: var(--primary-dark);
  background: #f7f8ff;
}

.btn-danger {
  color: #fff;
  background: linear-gradient(135deg, #f87171, #dc2626);
  box-shadow: 0 8px 18px rgba(220, 38, 38, 0.25);
}

.btn-danger:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(220, 38, 38, 0.32);
}

/* 表格 */
.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 12px;
}

.bill-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  min-width: 820px;
}

.bill-table th,
.bill-table td {
  padding: 13px 16px;
  text-align: left;
  border-bottom: 1px solid var(--border-soft);
}

.bill-table th {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--muted);
  background: #f8fafc;
}

.bill-table tbody tr:last-child td {
  border-bottom: none;
}

.bill-table tbody tr:not(:has(.empty)):hover {
  background: #fafbff;
}

.bill-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.bill-table .amount {
  font-weight: 600;
}

.bill-table .amount-income {
  color: #059669;
}

.bill-table .amount-expense {
  color: #b91c1c;
}

.bill-table .date {
  color: var(--muted);
  white-space: nowrap;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 13px;
  white-space: nowrap;
}

.note {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #4b5563;
}

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.badge-ai {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.16), rgba(139, 92, 246, 0.16));
  color: #5b21b6;
}

.badge-manual {
  background: #f1f5f9;
  color: #475569;
}

/* 收支类型标签 */
.type-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.type-income {
  background: #ecfdf5;
  color: #047857;
}

.type-expense {
  background: #fef2f2;
  color: #b91c1c;
}

/* 行内操作按钮 */
.bill-table .op-col {
  text-align: right;
}

.bill-table .actions {
  white-space: nowrap;
  text-align: right;
}

.link-btn {
  padding: 4px 8px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--primary-dark);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.link-btn:hover {
  background: #eef2ff;
}

.link-btn.danger {
  color: #dc2626;
}

.link-btn.danger:hover {
  background: #fef2f2;
}

.empty {
  padding: 36px 16px !important;
  text-align: center !important;
  color: var(--muted);
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 18px;
}

.pagination .btn {
  height: 36px;
  padding: 0 18px;
}

.page-indicator {
  min-width: 64px;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

/* 加载动画 */
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(3px);
}

.modal {
  width: min(520px, 100%);
  padding: 22px 24px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.28);
}

.modal-small {
  width: min(420px, 100%);
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.modal-head h3 {
  margin: 0;
  font-size: 17px;
  color: var(--text);
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #f1f5f9;
  color: var(--muted);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.icon-btn:hover {
  background: #e2e8f0;
  color: var(--text);
}

.modal-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.modal-form .field {
  min-width: 0;
}

.field-full {
  grid-column: 1 / -1;
}

.modal-text {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
}

.modal-text strong {
  color: var(--text);
}

.modal-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.modal-actions .btn {
  height: 38px;
  padding: 0 20px;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

/* 轻提示 */
.toast {
  position: fixed;
  z-index: 60;
  left: 50%;
  bottom: 36px;
  transform: translateX(-50%);
  max-width: min(90vw, 520px);
  padding: 12px 20px;
  border-radius: 12px;
  font-size: 14px;
  color: #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.22);
}

.toast-success {
  background: linear-gradient(135deg, #10b981, #059669);
}

.toast-error {
  background: linear-gradient(135deg, #f87171, #dc2626);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px);
}

/* 响应式 */
@media (max-width: 720px) {
  .page {
    padding: 24px 14px 64px;
  }

  .hero {
    align-items: flex-start;
  }

  .hero-total {
    display: none;
  }

  .hero-actions {
    gap: 8px;
  }

  .stats-btn {
    height: 42px;
    padding: 0 14px;
    font-size: 13px;
  }

  .ai-row {
    flex-direction: column;
  }

  .ai-btn {
    width: 100%;
  }

  .manual-form {
    flex-direction: column;
    align-items: stretch;
  }

  .field,
  .field-grow,
  .field-type {
    min-width: 0;
  }

  .submit-btn {
    width: 100%;
  }

  .modal {
    padding: 18px;
  }

  .modal-form {
    grid-template-columns: 1fr;
  }
}

/* 右侧记一笔面板 */
.compose-btn {
  height: 44px;
  padding: 0 18px;
  font-weight: 600;
}

.page.compose-open {
  padding-right: 420px;
}

.compose-panel {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 380px;
  max-width: 100%;
  height: 100vh;
  padding: 22px 24px 28px;
  overflow-y: auto;
  background: var(--card);
  border-left: 1px solid var(--border);
  box-shadow: -18px 0 48px rgba(15, 23, 42, 0.12);
  animation: compose-in 0.22s ease;
}

@keyframes compose-in {
  from {
    opacity: 0;
    transform: translateX(24px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.compose-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.compose-head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.compose-panel .ai-row {
  flex-wrap: wrap;
}

.compose-panel .ai-input {
  min-width: 0;
  height: 42px;
  font-size: 14px;
}

.compose-panel .ai-btn {
  height: 42px;
  padding: 0 14px;
  font-size: 13px;
}

.compose-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--muted);
}

.compose-divider {
  height: 1px;
  background: var(--border-soft);
}

.compose-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.compose-form .field {
  min-width: 0;
}

.compose-submit {
  height: 44px;
  margin-top: 2px;
}

.compose-mask {
  display: none;
}

@media (max-width: 900px) {
  .page.compose-open {
    padding-right: 20px;
  }

  .compose-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 35;
    background: rgba(15, 23, 42, 0.32);
  }

  .compose-panel {
    width: min(420px, 92vw);
  }
}
</style>
