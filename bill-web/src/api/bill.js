import http from './http'

/** AI 智能记账：传一句话，后端解析并直接入库 */
export function parseAiBill(text) {
  return http.post('/api/ai/parse', { text })
}

/** 手动记账 */
export function createBill(bill) {
  return http.post('/api/bills', bill)
}

/** 更新账单 */
export function updateBill(id, bill) {
  return http.put(`/api/bills/${id}`, bill)
}

/** 删除账单 */
export function deleteBill(id) {
  return http.delete(`/api/bills/${id}`)
}

/** 分页查询账单，返回 MyBatis-Plus IPage 结构 */
export function fetchBills(page = 1, size = 10) {
  return http.get('/api/bills', { params: { page, size } })
}

/** 查询账单分类 */
export function fetchCategories() {
  return http.get('/api/categories')
}
