<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

// 统一的请求辅助函数：
// 1. 所有地址都使用相对路径，这样开发时由 Vite 代理到 8081，生产时由
//    Spring Boot 直接处理，前端代码不需要区分两种环境。
// 2. 后端接口约定返回 { success, message, ... }，这里统一解析 JSON。
// 3. 如果服务端返回的不是 JSON（例如网关错误页面），也转换成前端可显示
//    的失败对象，避免调用方再次处理 JSON 解析异常。
const api = async (url, options = {}) => {
  const response = await fetch(url, options)
  let payload
  try {
    payload = await response.json()
  } catch {
    payload = { success: false, message: `服务器返回了无效响应 (${response.status})` }
  }
  if (!response.ok && payload.success !== false) {
    payload.success = false
    payload.message ||= `请求失败 (${response.status})`
  }
  return payload
}

// 文档相关状态。
// documents 保存后端返回的文档 ID 列表；filenameMap 保存 ID 到原文件名的映射。
// documentStats 单独缓存每个文档的块数量，避免把异步加载状态混在文档列表中。
const documents = ref([])
const filenameMap = ref({})
const documentStats = ref({})
const selectedDocumentId = ref('')
const totalChunks = ref(0)

// 计算属性会随着 selectedDocumentId 或 filenameMap 自动更新。
// 当后端没有返回文件名时，使用文档 ID 前 8 位作为可读的备用名称。
const selectedFilename = computed(() => {
  if (!selectedDocumentId.value) return ''
  return filenameMap.value[selectedDocumentId.value] || shortDocumentName(selectedDocumentId.value)
})

const chatPlaceholder = computed(() =>
  selectedFilename.value ? `在 ${selectedFilename.value} 中搜索问题...` : '在所有文档中搜索问题...',
)

// 问答区域状态：messages 是按显示顺序排列的消息；sending 用于防止重复提交，
// 也用于切换输入框、发送按钮和“正在生成”提示。
// uploadInput 是隐藏的原生文件选择框，通过模板 ref 由上传区域触发点击。
const messages = ref([])
const question = ref('')
const sending = ref(false)
const uploadInput = ref(null)
const isDragging = ref(false)
const uploadState = ref({ visible: false, type: '', message: '' })

function shortDocumentName(id) {
  // ID 通常是 UUID，完整展示会挤压布局，因此只保留短前缀。
  return `文档 ${id?.substring(0, 8) || ''}...`
}

function filenameFor(id) {
  // 优先显示原始文件名；历史数据没有映射时回退到短 ID。
  return filenameMap.value[id] || shortDocumentName(id)
}

function showUploadStatus(message, type = '') {
  // type 对应 style.css 中的 loading/success/error 样式。
  uploadState.value = { visible: true, type, message }
}

function chooseFile() {
  // 上传区域本身是按钮，点击它等价于点击隐藏的 input[type=file]。
  uploadInput.value?.click()
}

function onFileSelected(event) {
  // 文件选择框只处理第一个文件，与原 HTML 页面的单文件上传行为一致。
  const file = event.target.files?.[0]
  if (file) uploadFile(file)
  // Allow selecting the same file again after a failed upload.
  event.target.value = ''
}

function onDrop(event) {
  // 拖拽结束后无论是否有文件，都要恢复普通边框状态。
  isDragging.value = false
  const file = event.dataTransfer.files?.[0]
  if (file) uploadFile(file)
}

async function uploadFile(file) {
  // 后端 /api/rag/upload 使用 MultipartFile，因此必须使用 FormData，不能手动
  // 设置 JSON Content-Type（浏览器会自动补充 multipart boundary）。
  const formData = new FormData()
  formData.append('file', file)
  showUploadStatus('正在上传和处理文档...', 'loading')

  try {
    const result = await api('/api/rag/upload', { method: 'POST', body: formData })
    if (!result.success) {
      showUploadStatus(`✗ ${result.message || '上传失败'}`, 'error')
      return
    }

    showUploadStatus(`✓ ${result.message} - 共生成 ${result.chunks} 个文档块`, 'success')
    // 上传成功后刷新列表、统计，并自动切换到刚上传的文档范围。
    await refreshDocuments()
    if (result.documentId && documents.value.includes(result.documentId)) {
      selectedDocumentId.value = result.documentId
    }
  } catch (error) {
    showUploadStatus(`✗ 上传失败: ${error.message}`, 'error')
  }
}

async function refreshDocuments() {
  // 同步文档列表和文件名映射。列表接口成功后，再并行请求每个文档的块数量，
  // 最后请求总块数，这样文档管理和统计区域都能显示最新数据。
  try {
    const result = await api('/api/rag/documents')
    if (!result.success) throw new Error(result.message || '获取文档列表失败')

    documents.value = result.documents || []
    filenameMap.value = result.filenameMap || {}

    // 如果当前选中的文档已被其他操作删除，清空问答范围，避免继续发送无效 ID。
    if (selectedDocumentId.value && !documents.value.includes(selectedDocumentId.value)) {
      selectedDocumentId.value = ''
    }

    await Promise.all(documents.value.map(loadDocumentStats))
    await refreshTotalChunks()
  } catch (error) {
    console.error('获取文档列表失败:', error)
  }
}

async function loadDocumentStats(documentId) {
  // 单个文档的统计请求失败不会阻断其他文档的加载，因此只记录错误日志。
  try {
    const result = await api(`/api/rag/document/${encodeURIComponent(documentId)}/status`)
    if (result.success) documentStats.value[documentId] = result.chunks || 0
  } catch (error) {
    console.error(`获取文档 ${documentId} 统计失败:`, error)
  }
}

async function refreshTotalChunks() {
  // /status 返回向量库总块数，用于左侧统计卡片。
  try {
    const result = await api('/api/rag/status')
    if (result.success) totalChunks.value = result.totalChunks || 0
  } catch (error) {
    console.error('获取统计信息失败:', error)
  }
}

function selectDocument(id) {
  // 列表项和下拉框共享同一个选中状态，任一入口切换后问答范围都会同步变化。
  selectedDocumentId.value = id
}

function refreshDocumentSelect() {
  // 下拉框刷新复用完整刷新逻辑，确保选项、文件名和统计数据同时更新。
  refreshDocuments()
}

async function deleteDocument(documentId) {
  // 删除是不可逆操作，先让用户确认；@click.stop 防止同时触发列表项选择。
  if (!window.confirm(`确定要删除文档 ${documentId.substring(0, 8)}... 吗？`)) return

  try {
    const result = await api(`/api/rag/document/${encodeURIComponent(documentId)}`, { method: 'DELETE' })
    if (!result.success) throw new Error(result.message || '删除失败')
    window.alert('文档删除成功')
    await refreshDocuments()
  } catch (error) {
    window.alert(`删除失败: ${error.message}`)
  }
}

async function clearAllDocuments() {
  // 清空接口会删除整个 Milvus 集合，成功后同时重置选择范围和聊天记录。
  if (!window.confirm('确定要清空所有文档吗？此操作不可恢复！')) return

  try {
    const result = await api('/api/rag/clear', { method: 'POST' })
    if (!result.success) throw new Error(result.message || '清空失败')
    window.alert('所有文档已清空')
    selectedDocumentId.value = ''
    messages.value = []
    await refreshDocuments()
  } catch (error) {
    window.alert(`清空失败: ${error.message}`)
  }
}

async function sendMessage() {
  // 发送流程：
  // 1. 读取并校验问题，立即把用户消息加入界面。
  // 2. 根据是否选择文档拼接 documentId 查询参数。
  // 3. 等待后端完成“嵌入 -> 检索 -> 生成”，再追加助手消息。
  // 4. 无论成功失败，都在 finally 中恢复输入和按钮状态。
  const message = question.value.trim()
  if (!message || sending.value) return

  messages.value.push({
    role: 'user',
    content: message,
    context: selectedDocumentId.value
      ? `（针对文档 ${filenameFor(selectedDocumentId.value)}）`
      : '（针对所有文档）',
  })
  question.value = ''
  sending.value = true
  await scrollChatToBottom()

  try {
    // URLSearchParams 会正确编码中文、空格和特殊字符，避免手工拼接 URL。
    const params = new URLSearchParams({ message })
    if (selectedDocumentId.value) params.set('documentId', selectedDocumentId.value)
    const result = await api(`/api/rag/chat?${params.toString()}`)
    messages.value.push({
      role: 'assistant',
      content: result.success ? result.response : `错误: ${result.message || '问答失败'}`,
      context: result.success
        ? selectedDocumentId.value
          ? `（基于文档 ${filenameFor(selectedDocumentId.value)} 的回答）`
          : '（基于所有文档的回答）'
        : '',
    })
  } catch (error) {
    messages.value.push({ role: 'assistant', content: `请求失败: ${error.message}`, context: '' })
  } finally {
    sending.value = false
    await scrollChatToBottom()
  }
}

async function scrollChatToBottom() {
  // Vue 的 DOM 更新是异步的，必须等待 nextTick 后再读取 scrollHeight。
  // 这样新消息、加载提示出现后，滚动条总能停在最新内容处。
  await nextTick()
  const container = document.querySelector('.chat-messages')
  if (container) container.scrollTop = container.scrollHeight
}

// 页面首次挂载时加载已有文档，保证刷新浏览器后仍能看到 Milvus 中的数据。
onMounted(refreshDocuments)
</script>

<template>
  <!-- 页面外层：保留原 HTML 页面的渐变背景和居中最大宽度布局。 -->
  <main class="page-shell">
    <!-- 顶部标题区：只展示产品名称、模型说明和后端端口信息。 -->
    <header class="header">
      <div class="brand-mark" aria-hidden="true">⌘</div>
      <div>
        <h1>RAG 知识库问答系统</h1>
        <p>基于火山引擎多模态嵌入的智能问答系统</p>
        <p class="header-meta">服务运行在端口: 8081 <span>|</span> 支持文档隔离管理</p>
      </div>
    </header>

    <div class="main-layout">
      <!-- 左侧栏包含三个相互独立的功能区：上传、文档管理、统计。 -->
      <aside class="sidebar">
        <!-- 文件上传区同时支持点击选择和拖拽放置。 -->
        <section class="card upload-card">
          <div class="section-heading"><span class="heading-icon">↥</span><h2>文档上传</h2></div>
          <!-- .prevent 阻止浏览器把文件直接打开；上传区域内的隐藏 input
               只负责选择文件，不会改变按钮的点击行为。 -->
          <button
            class="upload-area"
            :class="{ dragover: isDragging }"
            type="button"
            @click="chooseFile"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="onDrop"
          >
            <span class="upload-icon">⇧</span>
            <strong>拖拽文件到此处或点击选择</strong>
            <span>支持 PDF、TXT、DOCX 等</span>
            <input ref="uploadInput" type="file" accept=".pdf,.txt,.docx,.doc" hidden @change="onFileSelected" />
          </button>
          <!-- 上传状态只在有消息时渲染，避免初始页面留下空白提示框。 -->
          <div v-if="uploadState.visible" class="status" :class="uploadState.type">
            <span v-if="uploadState.type === 'loading'" class="spinner" aria-hidden="true"></span>
            {{ uploadState.message }}
          </div>
        </section>

        <!-- 文档管理区：列表项点击可直接选择问答范围，删除按钮单独调用删除接口。 -->
        <section class="card document-card">
          <div class="section-heading"><span class="heading-icon">▤</span><h2>文档管理</h2></div>
          <div class="global-actions">
            <button class="btn btn-small btn-success" type="button" @click="refreshDocuments">↻ 刷新</button>
            <button class="btn btn-small btn-danger" type="button" @click="clearAllDocuments">⌫ 清空全部</button>
          </div>
          <div class="document-list">
            <div v-if="documents.length === 0" class="empty-state">暂无文档，请上传文档</div>
            <!-- 使用 v-for 渲染后端返回的 ID；:key 保证列表增删时 DOM 稳定。 -->
            <article
              v-for="documentId in documents"
              :key="documentId"
              class="document-item"
              :class="{ selected: selectedDocumentId === documentId }"
              tabindex="0"
              @click="selectDocument(documentId)"
              @keydown.enter="selectDocument(documentId)"
            >
              <div class="document-info">
                <div class="document-name" :title="filenameFor(documentId)">{{ filenameFor(documentId) }}</div>
                <div class="document-id">{{ documentId }}</div>
                <div class="document-stats"><span class="stat-badge">{{ documentStats[documentId] ?? '加载中...' }} 块</span></div>
              </div>
              <div class="document-actions">
                <!-- stop 防止删除按钮点击同时选中文档。 -->
                <button class="btn btn-small btn-danger icon-button" type="button" title="删除文档" @click.stop="deleteDocument(documentId)">×</button>
              </div>
            </article>
          </div>
        </section>

        <!-- 统计值来自响应式状态，接口刷新后 Vue 会自动更新数字。 -->
        <section class="card stats-card">
          <div class="section-heading"><span class="heading-icon">◫</span><h2>统计信息</h2></div>
          <div class="stats">
            <div class="stat-item"><div class="value">{{ documents.length }}</div><div class="label">文档总数</div></div>
            <div class="stat-item"><div class="value">{{ totalChunks }}</div><div class="label">文档块总数</div></div>
          </div>
        </section>
      </aside>

      <!-- 右侧问答区：文档范围选择、消息列表和输入表单。 -->
      <section class="card chat-card">
        <div class="section-heading"><span class="heading-icon">◌</span><h2>智能问答</h2><span class="live-indicator">在线</span></div>
        <div class="chat-container">
          <!-- v-model 让下拉框和 selectedDocumentId 双向同步。 -->
          <div class="document-selector">
            <label for="documentSelect">选择文档范围:</label>
            <select id="documentSelect" v-model="selectedDocumentId">
              <option value="">所有文档</option>
              <option v-for="documentId in documents" :key="documentId" :value="documentId">{{ filenameFor(documentId) }}</option>
            </select>
            <button class="btn btn-small" type="button" title="刷新文档范围" @click="refreshDocumentSelect">↻ 刷新列表</button>
          </div>

          <!-- aria-live 让辅助技术感知新回答；消息内容使用 {{ }} 文本插值，
               不会把模型返回的内容当作 HTML 执行。 -->
          <div class="chat-messages" aria-live="polite">
            <div v-if="messages.length === 0" class="empty-state chat-empty">
              <span class="empty-icon">◌</span>
              <strong>请选择文档范围后开始问答</strong>
              <span>上传文档后，可以针对全部资料或某一个文档提问</span>
            </div>
            <!-- role 决定气泡颜色和“用户/助手”标签，context 显示文档范围。 -->
            <article v-for="(item, index) in messages" :key="`${item.role}-${index}`" class="message" :class="item.role">
              <div class="label">{{ item.role === 'user' ? '用户' : '助手' }}</div>
              <div class="content">{{ item.content }}</div>
              <div v-if="item.context" class="context-info">{{ item.context }}</div>
            </article>
            <article v-if="sending" class="message assistant pending-message">
              <div class="label">助手</div>
              <div class="content"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span> 正在生成回答...</div>
            </article>
          </div>

          <!-- 使用 form + submit，同时支持点击发送和按 Enter 发送。 -->
          <form class="chat-input-area" @submit.prevent="sendMessage">
            <input v-model="question" type="text" :placeholder="chatPlaceholder" :disabled="sending" autocomplete="off" />
            <button class="btn send-btn" type="submit" :disabled="sending || !question.trim()">
              <span v-if="sending" class="spinner" aria-hidden="true"></span><span v-else>➤</span> {{ sending ? '生成回答中...' : '发送' }}
            </button>
          </form>
        </div>
      </section>
    </div>

    <footer class="footer">RAG Knowledge Base <span>•</span> Spring Boot API</footer>
  </main>
</template>
