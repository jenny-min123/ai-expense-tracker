<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchMonthlyStat } from '../api/bill'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const emit = defineEmits(['close'])

/* 图表配色：已通过色盲/对比度校验（深色图表面 #151b2e） */
const SURFACE = '#151b2e'
const C_INCOME = '#3987e5'
const C_EXPENSE = '#d95926'
const C_NET = '#199e70'

const loading = ref(false)
const error = ref('')
const stat = ref(null)
const showTable = ref(false)
const chartEl = ref(null)

let chart = null
let observer = null

function pad2(value) {
  return String(value).padStart(2, '0')
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`
}

function addMonths(month, delta) {
  const [year, monthNo] = month.split('-').map(Number)
  const date = new Date(year, monthNo - 1 + delta, 1)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
}

function monthCount(start, end) {
  const [startYear, startNo] = start.split('-').map(Number)
  const [endYear, endNo] = end.split('-').map(Number)
  return (endYear - startYear) * 12 + (endNo - startNo) + 1
}

const endMonth = ref(currentMonth())
const startMonth = ref(addMonths(endMonth.value, -5))

const months = computed(() => stat.value?.months || [])
const totalIncome = computed(() => Number(stat.value?.totalIncome || 0))
const totalExpense = computed(() => Number(stat.value?.totalExpense || 0))
const net = computed(() => Number(stat.value?.net || 0))
const isEmpty = computed(() => totalIncome.value === 0 && totalExpense.value === 0)

function formatMoney(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function compactNumber(value) {
  const num = Number(value)
  if (Math.abs(num) >= 10000) {
    const wan = num / 10000
    return `${wan % 1 === 0 ? wan.toFixed(0) : wan.toFixed(1)}万`
  }
  return String(num)
}

function axisLabel(month) {
  return `${month.slice(2, 4)}/${month.slice(5)}`
}

/** 声明式渐变，避免依赖 echarts.graphic 的导出方式 */
function gradient(from, to) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: from },
      { offset: 1, color: to },
    ],
  }
}

function renderChart() {
  if (!chart) return
  if (!stat.value) {
    chart.clear()
    return
  }
  const labels = months.value.map((item) => axisLabel(item.month))
  const income = months.value.map((item) => Number(item.income || 0))
  const expense = months.value.map((item) => Number(item.expense || 0))
  const netLine = months.value.map((item) => Number(item.net || 0))

  chart.setOption(
    {
      backgroundColor: 'transparent',
      animationDuration: 900,
      animationEasing: 'cubicOut',
      textStyle: {
        fontFamily:
          'system-ui, -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
      },
      grid: { left: 8, right: 18, top: 54, bottom: 6, containLabel: true },
      legend: {
        top: 2,
        right: 4,
        icon: 'roundRect',
        itemWidth: 10,
        itemHeight: 10,
        itemGap: 18,
        textStyle: { color: '#c3c9d9', fontSize: 12 },
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(255, 255, 255, 0.04)' } },
        backgroundColor: 'rgba(12, 17, 31, 0.96)',
        borderColor: 'rgba(255, 255, 255, 0.12)',
        borderWidth: 1,
        padding: [10, 14],
        textStyle: { color: '#eef2ff', fontSize: 13 },
        valueFormatter: (value) => `¥${formatMoney(value)}`,
        extraCssText: 'border-radius:10px;box-shadow:0 12px 30px rgba(0,0,0,.35);',
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisLine: { lineStyle: { color: '#2a3350' } },
        axisTick: { show: false },
        axisLabel: { color: '#8b93a7', fontSize: 12, margin: 12 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.06)' } },
        axisLabel: { color: '#8b93a7', fontSize: 11, formatter: (value) => compactNumber(value) },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [
        {
          name: '收入',
          type: 'bar',
          data: income,
          barMaxWidth: 18,
          barGap: '14%',
          itemStyle: {
            color: gradient('#4a95ea', '#256abf'),
            borderRadius: [4, 4, 0, 0],
          },
          emphasis: {
            itemStyle: { shadowBlur: 16, shadowColor: 'rgba(57, 135, 229, 0.5)' },
          },
        },
        {
          name: '支出',
          type: 'bar',
          data: expense,
          barMaxWidth: 18,
          itemStyle: {
            color: gradient('#e86a35', '#c44a1c'),
            borderRadius: [4, 4, 0, 0],
          },
          emphasis: {
            itemStyle: { shadowBlur: 16, shadowColor: 'rgba(217, 89, 38, 0.5)' },
          },
        },
        {
          name: '结余',
          type: 'line',
          data: netLine,
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: { width: 2, color: C_NET },
          itemStyle: { color: C_NET, borderColor: SURFACE, borderWidth: 2 },
          areaStyle: { color: gradient('rgba(25, 158, 112, 0.26)', 'rgba(25, 158, 112, 0)') },
          emphasis: { focus: 'series' },
        },
      ],
    },
    true,
  )
}

async function loadStat() {
  if (!startMonth.value || !endMonth.value) return
  if (startMonth.value > endMonth.value) {
    error.value = '开始月份不能晚于结束月份'
    return
  }
  if (monthCount(startMonth.value, endMonth.value) > 36) {
    error.value = '统计范围不能超过 36 个月'
    return
  }
  loading.value = true
  error.value = ''
  try {
    stat.value = await fetchMonthlyStat(startMonth.value, endMonth.value)
    await nextTick()
    renderChart()
  } catch (err) {
    error.value = err.message || '统计加载失败，请稍后重试'
    stat.value = null
    chart?.clear()
  } finally {
    loading.value = false
  }
}

function applyPreset(monthTotal) {
  endMonth.value = currentMonth()
  startMonth.value = addMonths(endMonth.value, -(monthTotal - 1))
  loadStat()
}

function applyThisYear() {
  endMonth.value = currentMonth()
  startMonth.value = `${endMonth.value.slice(0, 4)}-01`
  loadStat()
}

function close() {
  emit('close')
}

function handleKeydown(event) {
  if (event.key === 'Escape') close()
}

onMounted(() => {
  if (chartEl.value) {
    chart = echarts.init(chartEl.value, null, { renderer: 'canvas' })
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(chartEl.value)
  }
  window.addEventListener('keydown', handleKeydown)
  loadStat()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="stats-mask" @click.self="close">
    <div class="stats-modal" role="dialog" aria-modal="true" aria-labelledby="stats-title">
      <header class="stats-head">
        <div>
          <h2 id="stats-title">收支统计</h2>
          <p>按月对比收入与支出，最多查看 36 个月</p>
        </div>
        <button class="close-btn" type="button" aria-label="关闭统计" @click="close">×</button>
      </header>

      <div class="range-bar">
        <div class="range-inputs">
          <label>
            <span>从</span>
            <input v-model="startMonth" type="month" :max="endMonth" @change="loadStat" />
          </label>
          <span class="range-dash">—</span>
          <label>
            <span>到</span>
            <input v-model="endMonth" type="month" :min="startMonth" @change="loadStat" />
          </label>
        </div>
        <div class="presets">
          <button type="button" @click="applyPreset(6)">近 6 个月</button>
          <button type="button" @click="applyPreset(12)">近 12 个月</button>
          <button type="button" @click="applyThisYear">今年</button>
        </div>
      </div>

      <p v-if="error" class="stats-error">{{ error }}</p>

      <div class="kpi-row">
        <div class="kpi">
          <span class="kpi-label"><i class="dot dot-income"></i>总收入</span>
          <strong class="kpi-value">¥{{ formatMoney(totalIncome) }}</strong>
        </div>
        <div class="kpi">
          <span class="kpi-label"><i class="dot dot-expense"></i>总支出</span>
          <strong class="kpi-value">¥{{ formatMoney(totalExpense) }}</strong>
        </div>
        <div class="kpi">
          <span class="kpi-label"><i class="dot dot-net"></i>结余</span>
          <strong class="kpi-value">{{ net < 0 ? '-' : '+' }}¥{{ formatMoney(Math.abs(net)) }}</strong>
        </div>
      </div>

      <div class="chart-card">
        <div ref="chartEl" class="chart" :class="{ 'chart-busy': loading }" aria-hidden="true"></div>
        <div v-if="loading" class="chart-overlay">加载中…</div>
        <div v-else-if="isEmpty" class="chart-overlay">这个时间段还没有账单</div>
      </div>

      <div class="stats-foot">
        <button class="table-toggle" type="button" @click="showTable = !showTable">
          {{ showTable ? '收起数据表' : '查看数据表' }}
        </button>
        <span class="range-note">{{ startMonth }} 至 {{ endMonth }} · 共 {{ months.length }} 个月</span>
      </div>

      <div v-if="showTable" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>月份</th>
              <th class="num">收入</th>
              <th class="num">支出</th>
              <th class="num">结余</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in months" :key="item.month">
              <td>{{ item.month }}</td>
              <td class="num">¥{{ formatMoney(item.income) }}</td>
              <td class="num">¥{{ formatMoney(item.expense) }}</td>
              <td class="num" :class="{ negative: Number(item.net) < 0 }">
                {{ Number(item.net) < 0 ? '-' : '+' }}¥{{ formatMoney(Math.abs(Number(item.net))) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stats-mask {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(4, 7, 16, 0.66);
  backdrop-filter: blur(8px);
  animation: mask-in 0.2s ease-out;
}

.stats-modal {
  width: min(940px, 100%);
  max-height: min(92vh, 940px);
  overflow-y: auto;
  padding: 24px 28px 22px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 22px;
  background:
    radial-gradient(1100px 460px at -10% -20%, rgba(57, 135, 229, 0.2), transparent 60%),
    radial-gradient(900px 420px at 110% 0%, rgba(217, 89, 38, 0.12), transparent 60%),
    #0e1323;
  color: #eef2ff;
  box-shadow: 0 40px 100px rgba(0, 0, 0, 0.55);
  animation: modal-in 0.24s cubic-bezier(0.22, 1, 0.36, 1);
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.18) transparent;
}

.stats-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.stats-head h2 {
  margin: 0;
  font-size: 21px;
  letter-spacing: 0.3px;
}

.stats-head p {
  margin: 5px 0 0;
  font-size: 12px;
  color: #8b93a7;
}

.close-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  color: #aab3c7;
  font-size: 21px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

/* 时间范围 */
.range-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.035);
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: 10px;
}

.range-inputs label {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #8b93a7;
}

.range-inputs input {
  height: 34px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: rgba(8, 12, 24, 0.7);
  color: #e7ecf8;
  font-size: 13px;
  color-scheme: dark;
  outline: none;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.range-inputs input:focus {
  border-color: #3987e5;
  box-shadow: 0 0 0 3px rgba(57, 135, 229, 0.22);
}

.range-dash {
  color: #5c6478;
}

.presets {
  display: flex;
  gap: 6px;
}

.presets button {
  height: 30px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #aab3c7;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.16s, color 0.16s, border-color 0.16s;
}

.presets button:hover {
  border-color: rgba(57, 135, 229, 0.35);
  background: rgba(57, 135, 229, 0.12);
  color: #dce7ff;
}

.stats-error {
  margin: 12px 0 0;
  padding: 9px 12px;
  border: 1px solid rgba(230, 103, 103, 0.35);
  border-radius: 10px;
  background: rgba(230, 103, 103, 0.1);
  color: #ffb4b4;
  font-size: 13px;
}

/* KPI */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.kpi {
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.065), rgba(255, 255, 255, 0.02));
}

.kpi-label {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #9aa4b9;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-income {
  background: #3987e5;
  box-shadow: 0 0 10px rgba(57, 135, 229, 0.7);
}

.dot-expense {
  background: #d95926;
  box-shadow: 0 0 10px rgba(217, 89, 38, 0.65);
}

.dot-net {
  background: #199e70;
  box-shadow: 0 0 10px rgba(25, 158, 112, 0.65);
}

.kpi-value {
  display: block;
  margin-top: 7px;
  font-size: 23px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

/* 图表 */
.chart-card {
  position: relative;
  margin-top: 14px;
  padding: 10px 8px 4px;
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 16px;
  background: #151b2e;
}

.chart {
  width: 100%;
  height: 340px;
  transition: opacity 0.2s;
}

.chart-busy {
  opacity: 0.35;
}

.chart-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #8b93a7;
  font-size: 13px;
  pointer-events: none;
}

/* 底部与数据表 */
.stats-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.table-toggle {
  border: none;
  background: none;
  padding: 4px 0;
  color: #8fb0ff;
  font-size: 12px;
  cursor: pointer;
}

.table-toggle:hover {
  color: #b9cdff;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.range-note {
  font-size: 12px;
  color: #6f778b;
}

.table-wrap {
  margin-top: 10px;
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
}

.table-wrap table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.table-wrap th,
.table-wrap td {
  padding: 10px 14px;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.table-wrap th {
  font-size: 12px;
  font-weight: 600;
  color: #8b93a7;
  background: rgba(255, 255, 255, 0.03);
}

.table-wrap tbody tr:last-child td {
  border-bottom: none;
}

.table-wrap .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.table-wrap .negative {
  color: #e66767;
}

@keyframes mask-in {
  from {
    opacity: 0;
  }
}

@keyframes modal-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.985);
  }
}

@media (max-width: 720px) {
  .stats-mask {
    padding: 12px;
  }

  .stats-modal {
    padding: 18px 16px;
    border-radius: 18px;
  }

  .range-bar {
    align-items: stretch;
  }

  .range-inputs {
    flex-wrap: wrap;
  }

  .presets {
    flex-wrap: wrap;
  }

  .kpi-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .kpi-value {
    font-size: 20px;
  }

  .chart {
    height: 280px;
  }
}
</style>
