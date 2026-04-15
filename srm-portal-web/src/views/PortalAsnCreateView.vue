<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalApi, type PoDetail, type PoSummary } from '../api/portal'

const route = useRoute()
const router = useRouter()

const pos = ref<PoSummary[]>([])
const purchaseOrderId = ref<number | null>(null)
const poDetail = ref<PoDetail | null>(null)
const shipDate = ref(new Date().toISOString().slice(0, 10))
const etaDate = ref<string | null>(null)
const carrier = ref('')
const trackingNo = ref('')
const remark = ref('')
const receiverName = ref('')
const receiverPhone = ref('')
const receiverAddress = ref('')
const recognizing = ref(false)
const logisticsFile = ref<File | null>(null)
const shipByLine = ref<Record<number, string>>({})

const qPo = computed(() => {
  const v = route.query.poId
  return v != null && v !== '' ? Number(v) : null
})

async function loadPos() {
  const r = await portalApi.listPos()
  pos.value = r.data.filter((p) => p.status === 'RELEASED')
  if (qPo.value != null) {
    const hit = pos.value.find((p) => p.id === qPo.value)
    if (hit) purchaseOrderId.value = hit.id
  } else if (pos.value.length === 1) {
    purchaseOrderId.value = pos.value[0].id
  }
}

async function loadPo() {
  poDetail.value = null
  shipByLine.value = {}
  if (purchaseOrderId.value == null) return
  const r = await portalApi.getPo(purchaseOrderId.value)
  poDetail.value = r.data
  for (const l of r.data.lines) {
    const open = Math.max(0, Number(l.qty) - Number(l.receivedQty || 0))
    shipByLine.value[l.id] = open > 0 ? String(open) : '0'
  }
}

watch(purchaseOrderId, () => {
  loadPo()
})

onMounted(async () => {
  await loadPos()
  await loadPo()
})

async function submit() {
  if (purchaseOrderId.value == null || !poDetail.value) {
    ElMessage.warning('请选择订单')
    return
  }
  const lines = poDetail.value.lines
    .map((l) => ({
      purchaseOrderLineId: l.id,
      shipQty: Number(shipByLine.value[l.id] || 0),
    }))
    .filter((x) => x.shipQty > 0)
  if (!lines.length) {
    ElMessage.warning('请至少一行填写大于 0 的发货数量')
    return
  }
  try {
    const created = await portalApi.createAsn({
      purchaseOrderId: purchaseOrderId.value,
      shipDate: shipDate.value,
      etaDate: etaDate.value || null,
      carrier: carrier.value || null,
      trackingNo: trackingNo.value || null,
      remark: remark.value || null,
      receiverName: receiverName.value || null,
      receiverPhone: receiverPhone.value || null,
      receiverAddress: receiverAddress.value || null,
      lines,
    })
    if (logisticsFile.value) {
      await portalApi.uploadAsnLogisticsAttachment(created.data.id, logisticsFile.value)
    }
    ElMessage.success('已提交 ASN')
    router.push('/asn')
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '失败')
  }
}

function isImage(file: File) {
  return file.type.startsWith('image/')
}

function fillFromParsed(parsed: Partial<{ carrier: string; trackingNo: string; name: string; phone: string; address: string }>) {
  // 只回填空字段，避免覆盖用户手工输入
  if (!carrier.value && parsed.carrier) carrier.value = parsed.carrier
  if (!trackingNo.value && parsed.trackingNo) trackingNo.value = parsed.trackingNo
  if (!receiverName.value && parsed.name) receiverName.value = parsed.name
  if (!receiverPhone.value && parsed.phone) receiverPhone.value = parsed.phone
  if (!receiverAddress.value && parsed.address) receiverAddress.value = parsed.address
}

async function ocrAndFill(file: File) {
  recognizing.value = true
  try {
    // 双通道：二维码（更准的运单号） + OCR（更准的抬头/收货信息）
    const qr = await decodeQrAndParse(file).catch(() => ({}))
    const ocr = await ocrRecognizeAndParse(file).catch(() => ({}))
    const merged = mergeParsed(qr, ocr)
    fillFromParsed(merged)
    if (merged.trackingNo || merged.name || merged.phone || merged.address || merged.carrier) {
      ElMessage.success('已识别并回填信息')
    } else {
      ElMessage.info('未识别到信息，可手工填写')
    }
  } catch {
    ElMessage.warning('识别失败，可继续手工填写')
  } finally {
    recognizing.value = false
  }
}

function mergeParsed(
  qr: Partial<{ carrier: string; trackingNo: string; name: string; phone: string; address: string }>,
  ocr: Partial<{ carrier: string; trackingNo: string; name: string; phone: string; address: string }>,
) {
  // 运单号：二维码优先（OCR 容易把数字识别错）
  const trackingNo = qr.trackingNo || ocr.trackingNo
  // 其它字段：OCR 优先（二维码未必包含）
  return {
    carrier: ocr.carrier || qr.carrier,
    trackingNo,
    name: ocr.name || qr.name,
    phone: ocr.phone || qr.phone,
    address: ocr.address || qr.address,
  }
}

async function ocrRecognizeAndParse(file: File) {
  const { createWorker } = await import('tesseract.js')
  const worker = await createWorker('chi_sim+eng')
  try {
    // 尽量保留空格（手机号常被分组）
    // tesseract 参数是字符串 map，这里避免 TS 约束过强
    await (worker as unknown as { setParameters: (p: Record<string, string>) => Promise<void> }).setParameters({
      preserve_interword_spaces: '1',
    })
  } catch {
    // ignore
  }
  try {
    const img = await loadImageFromFile(file)
    // 针对面单：分别识别“顶部抬头/运单号”“右上收货人信息”两块，命中率更高
    const head = await ocrRegion(worker, img, { x: 0, y: 0, w: 1, h: 0.22 }, 2.2)
    // 收货人信息框位置在右上偏中：做两种更大裁剪叠加，避免截图比例变化导致漏掉
    const receiverA = await ocrRegion(worker, img, { x: 0.45, y: 0.04, w: 0.55, h: 0.42 }, 2.8)
    const receiverB = await ocrRegion(worker, img, { x: 0.5, y: 0.06, w: 0.5, h: 0.38 }, 3.0)
    // 再加一档更大的右上区域兜底，覆盖“收货人/手机/地址”三行
    const receiverC = await ocrRegion(worker, img, { x: 0.4, y: 0.02, w: 0.6, h: 0.55 }, 3.0)
    // 地址可能更靠下，再加一档偏下区域（避免只识别到姓名/手机，漏掉地址）
    const receiverD = await ocrRegion(worker, img, { x: 0.4, y: 0.10, w: 0.6, h: 0.58 }, 3.0)
    const allText = `${head}\n${receiverA}\n${receiverB}\n${receiverC}\n${receiverD}`.replace(/\r/g, '\n')
    return parseFieldsFromText(allText)
  } finally {
    await worker.terminate()
  }
}

function parseFieldsFromText(
  text: string,
): { carrier?: string; trackingNo?: string; name?: string; phone?: string; address?: string } {
  const onlyCnNum = (s: string) => s.replace(/[^\u4e00-\u9fa50-9]/g, '')
  const onlyDigits = (s: string) => s.replace(/\D/g, '')
  const normalizeDashes = (s: string) =>
    s
      .replace(/[—–−_－﹣‑]/g, '-') // 各种破折号/连字符统一为 '-'
      .replace(/\s*-\s*/g, '-') // 去掉连字符两侧空白
  const onlyAddrChars = (s: string) =>
    normalizeDashes(s).replace(/[^\u4e00-\u9fa50-9\-]/g, '') // 地址允许 '-'（如 10-1-301）

  const raw = (text || '').replace(/\r/g, '\n')
  // OCR 结果通常会有随机空格/换行，尽量保留中文内容，但合并多余空白以利于正则
  const compact = raw
    .replace(/[：:]/g, ':')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n+/g, '\n')
    .trim()

  const lineText = compact.replace(/\n/g, ' ')
  const firstLine = compact.split('\n').map((x) => x.trim()).filter(Boolean)[0] || ''

  // 强规则：若存在“收货人信息”，优先在其后的片段里解析，避免误取发货地址等
  const receiverBlock = (() => {
    const idx = compact.indexOf('收货人信息')
    if (idx < 0) return ''
    return compact.slice(idx, Math.min(compact.length, idx + 220))
  })()
  const receiverText = receiverBlock ? receiverBlock.replace(/\n/g, ' ') : ''
  // 若 OCR 没识别出“收货人信息”标题，则改为围绕“收货人/收件人”关键词截取一段
  const receiverText2 = (() => {
    if (receiverText) return receiverText
    const m = compact.match(/(收货人|收件人|联系人|签收人)/)
    if (!m || m.index == null) return ''
    const start = Math.max(0, m.index - 30)
    return compact.slice(start, Math.min(compact.length, m.index + 220)).replace(/\n/g, ' ')
  })()

  const pickByLabel = (label: RegExp, maxLen: number) => {
    const hay = receiverText2 || lineText
    const m = hay.match(new RegExp(`${label.source}\\s*:?\\s*([^\\n]{1,${maxLen}})`, 'i'))
    return m?.[1]?.trim()
  }

  const carrier =
    // 物流公司名称通常在抬头，二维码里也可能带
    pickByLabel(/承运商|物流公司|承运单位/, 80) ||
    // 抬头兜底：包含“物流/快递/运输/有限公司”等关键词时取第一行
    (/(物流|快递|运输|有限公司)/.test(firstLine) ? firstLine.slice(0, 80) : undefined)

  const carrierNorm = (() => {
    if (!carrier) return undefined
    // 去掉多余空白；部分 OCR 会在公司名后多识别一个“人”
    let v = carrier.replace(/\s+/g, ' ').trim()
    v = v.replace(/\s*人\s*$/, '').trim()
    // 公司名内部一般不需要空格
    v = v.replace(/\s+/g, '')
    v = onlyCnNum(v)
    return v || undefined
  })()

  const trackingNoRaw =
    // 运单号通常在顶部，优先在全局文本里找
    (lineText.match(/(运单号|运单号码|快递单号|物流单号|单号)\s*:?[\s]*([0-9][0-9\s-]{5,30}[0-9])/i)?.[2] || undefined)
  const trackingNo = trackingNoRaw ? onlyDigits(trackingNoRaw) : undefined

  // 物流单常见：收货人:王强  手机:134 8541 5535  地址:山西省...
  // 优先精准标签，其次兜底“联系人/收件人”等
  const nameRaw =
    // OCR 容错：收货人可能被识别成“收货入/收贷人”等相近字
    pickByLabel(/收货[人入]|收[件收]人|联系[人入]|签收[人入]/, 30) ||
    undefined
  // 仅保留 2–6 个中文（避免把后面的手机号/杂项带进去）
  const name = nameRaw?.match(/([\u4e00-\u9fa5]{2,6})/)?.[1]?.trim() || undefined

  const phoneRaw =
    pickByLabel(/手机/, 40) ||
    pickByLabel(/手机号|联系电话|电话|联系方式|收货人联系方式/, 40) ||
    undefined
  // 提取电话号码并去掉空格/短横线（支持 11 位手机号及一般座机）
  const phoneMatched = phoneRaw?.match(/(\+?\d[\d\s-]{6,30}\d)/)?.[1]
  const phone = phoneMatched ? onlyDigits(phoneMatched) : undefined

  // 地址可能比较长，允许更长截取，并尽量去掉“地址:”前缀残留
  const addressRaw =
    pickByLabel(/地址/, 120) ||
    pickByLabel(/收货地址|收件地址/, 120) ||
    undefined
  let address = addressRaw?.replace(/^(地址)\s*/i, '').replace(/^:/, '').trim() || undefined
  // 避免把“发货地址/发站/到站”等误当收货地址：若包含“发货”字样且有收货人信息块，则放弃
  if (address && receiverBlock && /(发货|发站|发出|发地)/.test(address)) {
    address = undefined
  }

  // --- 强兜底：面单 OCR 常把标签丢掉，仅保留“王强 13485415535 山西省...”这类内容 ---
  const all = (receiverText2 || lineText).replace(/[：:]/g, ' ').replace(/\s+/g, ' ').trim()

  // 手机号兜底：允许被空格/短横线分隔（如 134 8541 5535）
  const phone2 = (() => {
    if (phone) return phone
    const m = all.match(/(1[\d\s-]{9,20})/)
    if (!m?.[1]) return undefined
    const digits = onlyDigits(m[1])
    return digits.length === 11 ? digits : undefined
  })()

  // 运单号兜底：抓 8–20 位数字串（排除手机号），取最像运单号的
  const digitRuns = Array.from(all.matchAll(/\b(\d{8,20})\b/g)).map((m) => m[1])
  const trackingNo2 =
    trackingNo ||
    digitRuns.find((d) => d !== phone2 && d.length >= 8 && d.length <= 14) ||
    digitRuns.find((d) => d !== phone2) ||
    undefined

  // 姓名兜底：在手机号前 12 个字符内找 2–4 个中文
  let name2 = name
  if (!name2) {
    const m = all.match(/收货[人入]\s*([^\u4e00-\u9fa5]*)([\u4e00-\u9fa5]{2,6})/)
    if (m?.[2]) name2 = m[2]
  }
  if (!name2 && phone2) {
    const idx = all.indexOf(phone2)
    if (idx > 0) {
      const pre = all.slice(Math.max(0, idx - 16), idx)
      const m = pre.match(/([\u4e00-\u9fa5]{2,4})\s*$/)
      if (m?.[1]) name2 = m[1]
    }
  }

  // 地址兜底：手机号后面开始截取，直到遇到明显非地址字段（金额/公斤/件等），且要包含地名关键字
  let address2 = address
  if (!address2) {
    // OCR 可能把地址拆行或夹杂空格/各种破折号，改为宽松截取后再清洗
    const m = all.match(/地址\s*[:：]?\s*([\s\S]{6,200})/i)
    if (m?.[1]) {
      const tail = m[1]
      const cut = tail.split(/(运费|合计|件数|重量|体积|kg|KG|元|￥|收货人签字|发货人签字|代收)/)[0].trim()
      if (cut.length >= 6) address2 = cut
    }
  }
  if (!address2) {
    // 容错：OCR 可能把“地址”分成“地 址”或缺失冒号，直接在 compact 里找“地.?址”后面的内容
    const compactOneLine = compact.replace(/\n/g, ' ')
    const m = compactOneLine.match(/地\s*址\s*[:：]?\s*([\u4e00-\u9fa50-9\-\s]{6,220})/i)
    if (m?.[1]) {
      const cut = m[1].split(/(运费|合计|件数|重量|体积|kg|KG|元|￥|收货人签字|发货人签字|代收)/)[0].trim()
      if (cut.length >= 6) address2 = cut
    }
  }
  if (!address2) {
    // 最终兜底：从收货信息块里找第一个像地址的片段（含 省/市/县/区 等）
    const pool = (receiverText2 || compact.replace(/\n/g, ' ')).replace(/[：:]/g, ' ')
    const mm = pool.match(/([\u4e00-\u9fa5]{1,10}(省|自治区)[\u4e00-\u9fa50-9\-\s]{6,120})/)
    if (mm?.[1]) {
      address2 = mm[1].trim()
    }
  }
  if (!address2 && phone2) {
    const idx = all.indexOf(phone2)
    if (idx >= 0) {
      const post = all.slice(idx + phone2.length).trim()
      const cut = post.split(/(运费|合计|件数|重量|体积|kg|KG|元|￥)/)[0].trim()
      if (/[省市县区镇街路号楼单元室期]/.test(cut) && cut.length >= 6) {
        address2 = cut.slice(0, 160)
      }
    }
  }

  const nameNorm = name2 ? onlyCnNum(name2) : undefined
  const trackingNorm = trackingNo2 ? onlyDigits(trackingNo2) : undefined
  const phoneNorm = phone2 ? onlyDigits(phone2) : undefined
  const addressNorm = (() => {
    if (!address2) return undefined
    // 清理尾部误识别的“运费信息”等（会被中文/数字过滤保留下来，需先剔除）
    let v = address2
      .replace(/(运费信息|运费详情|费用信息|运费)$/g, '')
      .replace(/(运费信息|运费详情|费用信息|运费)$/g, '')
      .trim()
    v = onlyAddrChars(v)
    return v || undefined
  })()

  return { carrier: carrierNorm, trackingNo: trackingNorm, name: nameNorm, phone: phoneNorm, address: addressNorm }
}

async function decodeQrAndParse(file: File): Promise<{
  carrier?: string
  trackingNo?: string
  name?: string
  phone?: string
  address?: string
}> {
  const { BrowserMultiFormatReader } = await import('@zxing/browser')
  const reader = new BrowserMultiFormatReader()

  const url = URL.createObjectURL(file)
  try {
    const img = await loadImage(url)
    const canvases = buildQrCandidateCanvases(img)
    let txt = ''
    for (const c of canvases) {
      try {
        const result = await reader.decodeFromCanvas(c)
        txt = result?.getText?.() ? result.getText() : ''
        if (txt) break
      } catch {
        // try next
      }
    }
    // 若二维码内容是 URL，则走后端抓取解析（避免 CORS，且比 OCR 稳定）
    if (/^https?:\/\//i.test(txt.trim())) {
      const r = await portalApi.parseLogisticsByUrl(txt.trim())
      return {
        carrier: r.data.carrier || undefined,
        trackingNo: r.data.trackingNo || undefined,
        name: r.data.receiverName || undefined,
        phone: r.data.receiverPhone || undefined,
        address: r.data.receiverAddress || undefined,
      }
    }
    return parseFieldsFromText(txt)
  } finally {
    URL.revokeObjectURL(url)
  }
}

function buildQrCandidateCanvases(img: HTMLImageElement) {
  const w = img.naturalWidth || img.width
  const h = img.naturalHeight || img.height
  // 物流单二维码通常在右上角：准备多种裁剪/缩放尝试
  const regions = [
    { x: 0.72, y: 0.02, w: 0.26, h: 0.26, scale: 3.5 },
    { x: 0.68, y: 0.0, w: 0.32, h: 0.32, scale: 3.2 },
    { x: 0.75, y: 0.0, w: 0.25, h: 0.35, scale: 3.0 },
    { x: 0.64, y: 0.0, w: 0.36, h: 0.4, scale: 3.4 },
    { x: 0.6, y: 0.0, w: 0.4, h: 0.45, scale: 3.2 },
    { x: 0.0, y: 0.0, w: 1.0, h: 1.0, scale: 1.6 }, // 最后兜底全图放大
  ]
  const out: HTMLCanvasElement[] = []
  for (const r of regions) {
    const sx = Math.max(0, Math.floor(r.x * w))
    const sy = Math.max(0, Math.floor(r.y * h))
    const sw = Math.max(1, Math.floor(r.w * w))
    const sh = Math.max(1, Math.floor(r.h * h))
    const canvas = document.createElement('canvas')
    canvas.width = Math.floor(sw * r.scale)
    canvas.height = Math.floor(sh * r.scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) continue
    ctx.imageSmoothingEnabled = false
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height)
    // 简单二值化增强二维码对比
    const id = ctx.getImageData(0, 0, canvas.width, canvas.height)
    const d = id.data
    for (let i = 0; i < d.length; i += 4) {
      const g = (d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114) | 0
      const v = g > 160 ? 255 : 0
      d[i] = v
      d[i + 1] = v
      d[i + 2] = v
      d[i + 3] = 255
    }
    ctx.putImageData(id, 0, 0)
    out.push(canvas)
  }
  return out
}

async function loadImage(url: string) {
  const img = new Image()
  img.crossOrigin = 'anonymous'
  img.src = url
  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve()
    img.onerror = () => reject(new Error('image load failed'))
  })
  return img
}

async function loadImageFromFile(file: File) {
  const url = URL.createObjectURL(file)
  try {
    return await loadImage(url)
  } finally {
    URL.revokeObjectURL(url)
  }
}

async function ocrRegion(
  worker: unknown,
  img: HTMLImageElement,
  region: { x: number; y: number; w: number; h: number },
  scale: number,
) {
  const w = img.naturalWidth || img.width
  const h = img.naturalHeight || img.height
  const sx = Math.max(0, Math.floor(region.x * w))
  const sy = Math.max(0, Math.floor(region.y * h))
  const sw = Math.max(1, Math.floor(region.w * w))
  const sh = Math.max(1, Math.floor(region.h * h))
  const canvas = document.createElement('canvas')
  canvas.width = Math.floor(sw * scale)
  canvas.height = Math.floor(sh * scale)
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''
  ctx.imageSmoothingEnabled = true
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height)
  // 提升对比度：轻量二值化（不太激进，保留中文笔画）
  const id = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const d = id.data
  for (let i = 0; i < d.length; i += 4) {
    const g = (d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114) | 0
    const v = g > 235 ? 255 : g < 40 ? 0 : g
    d[i] = v
    d[i + 1] = v
    d[i + 2] = v
  }
  ctx.putImageData(id, 0, 0)
  const blob: Blob = await new Promise((resolve) => canvas.toBlob((b) => resolve(b as Blob), 'image/png'))
  const fileObj = new File([blob], 'region.png', { type: 'image/png' })
  const r = await (worker as { recognize: (f: File) => Promise<{ data?: { text?: string } }> }).recognize(fileObj)
  return (r.data?.text || '').replace(/\r/g, '\n')
}

async function onLogisticsFileChange(file: { raw?: File } | File) {
  const f = file instanceof File ? file : file.raw
  if (!f) return
  logisticsFile.value = f
  if (isImage(f)) {
    await ocrAndFill(f)
  } else {
    ElMessage.info('已选择附件（非图片暂不自动识别，可继续手工填写）')
  }
}
</script>

<template>
  <div class="page">
    <div class="head">
      <h2 class="title">新建发货通知</h2>
      <router-link to="/asn">返回列表</router-link>
    </div>
    <p class="rule-hint">
      请先在各订单行完成「确认交期与数量」（回执），再创建发货通知；未回执的行将无法发货。
    </p>
    <el-form label-width="100px" style="margin-top: 16px; max-width: 640px">
      <el-form-item label="采购订单">
        <el-select v-model="purchaseOrderId" placeholder="选择已发布订单" filterable style="width: 100%">
          <el-option v-for="p in pos" :key="p.id" :label="`${p.poNo}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="发货日期">
        <el-date-picker v-model="shipDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="预计到货">
        <el-date-picker v-model="etaDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" clearable />
      </el-form-item>
      <el-form-item label="承运商">
        <el-input v-model="carrier" />
      </el-form-item>
      <el-form-item label="运单号">
        <el-input v-model="trackingNo" />
      </el-form-item>
      <el-form-item label="收货人">
        <el-input v-model="receiverName" placeholder="可从物流单附件自动识别" />
      </el-form-item>
      <el-form-item label="联系方式">
        <el-input v-model="receiverPhone" placeholder="可从物流单附件自动识别" />
      </el-form-item>
      <el-form-item label="收货地址">
        <el-input v-model="receiverAddress" type="textarea" :rows="2" placeholder="可从物流单附件自动识别" />
      </el-form-item>
      <el-form-item label="物流单附件">
        <div style="width: 100%">
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept="image/*,.pdf"
            :on-change="onLogisticsFileChange"
          >
            <el-button :loading="recognizing">选择文件并识别</el-button>
          </el-upload>
          <div v-if="logisticsFile" class="upload-hint">
            已选择：{{ logisticsFile.name }}（提交后会随 ASN 一起上传）
          </div>
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" />
      </el-form-item>
    </el-form>

    <el-table v-if="poDetail" :data="poDetail.lines" stripe style="margin-top: 8px">
      <el-table-column prop="lineNo" label="行" width="50" />
      <el-table-column prop="materialCode" label="物料" width="110" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column prop="qty" label="订购" width="80" />
      <el-table-column prop="receivedQty" label="已收" width="80" />
      <el-table-column label="发货量" width="120">
        <template #default="{ row }">
          <el-input v-model="shipByLine[row.id]" />
        </template>
      </el-table-column>
    </el-table>

    <el-button type="primary" style="margin-top: 16px" :loading="recognizing" @click="submit">提交</el-button>
  </div>
</template>

<style scoped>
.page {
  max-width: none;
  margin: 0;
  padding: 0;
}
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.rule-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  max-width: 720px;
}
.upload-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
</style>
