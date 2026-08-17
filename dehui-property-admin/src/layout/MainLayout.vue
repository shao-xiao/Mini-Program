<template>
  <el-container class="layout-container">
    <el-aside width="var(--dhic-sidebar-width)" class="aside">
      <div class="brand">
        <img src="../assets/dehui-logo.png" class="logo" alt="德汇创新中心标志" />
        <div>
          <div class="brand-title">德汇创新中心</div>
          <div class="brand-subtitle">物业管理系统</div>
        </div>
      </div>

      <el-menu
        :default-active="$route.path"
        router
        class="side-menu"
        background-color="var(--dhic-sidebar-bg)"
        text-color="var(--dhic-sidebar-text)"
        active-text-color="#ffffff"
        aria-label="后台主导航"
      >
        <template v-for="item in orderedMenus" :key="menuKey(item)">
          <el-menu-item
            v-if="item.path"
            :index="item.path"
            :class="menuItemClass('root', '', item)"
            @click.capture="suppressMenuClickAfterDrag"
          >
            <div
              class="menu-row"
              :data-menu-key="menuKey(item)"
              data-menu-level="root"
              data-menu-parent=""
              @click.capture="suppressMenuClickAfterDrag"
              @pointerdown="startMenuPointerDrag($event, 'root', '', item)"
            >
              <span>{{ item.title }}</span>
            </div>
          </el-menu-item>

          <el-sub-menu
            v-else
            :index="item.index"
            :class="menuItemClass('root', '', item)"
          >
            <template #title>
              <div
                class="menu-row"
                :data-menu-key="menuKey(item)"
                data-menu-level="root"
                data-menu-parent=""
                @click.capture="suppressMenuClickAfterDrag"
                @pointerdown="startMenuPointerDrag($event, 'root', '', item)"
              >
                <span>{{ item.title }}</span>
              </div>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="menuKey(child)"
              :index="child.path"
              :class="menuItemClass('child', menuKey(item), child)"
              @click.capture="suppressMenuClickAfterDrag"
            >
              <div
                class="menu-row child-row"
                :data-menu-key="menuKey(child)"
                data-menu-level="child"
                :data-menu-parent="menuKey(item)"
                @click.capture="suppressMenuClickAfterDrag"
                @pointerdown="startMenuPointerDrag($event, 'child', menuKey(item), child)"
              >
                <span>{{ child.title }}</span>
              </div>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-context" aria-label="当前位置">
          <span class="system-title">物业管理平台</span>
          <span class="header-context-separator" aria-hidden="true"></span>
          <strong class="current-page-title">{{ currentPageTitle }}</strong>
        </div>

        <div class="right">
          <span class="user-role" :title="roleText">{{ roleSummary }}</span>
          <span class="user-account">
            <span class="user-avatar" aria-hidden="true">{{ usernameInitial }}</span>
            <span class="username">{{ username }}</span>
          </span>
          <span class="header-actions-divider" aria-hidden="true"></span>
          <el-button
            class="change-password-btn"
            size="small"
            text
            :icon="Lock"
            aria-label="修改当前账户密码"
            @click="openPasswordDialog"
          >
            修改密码
          </el-button>
          <el-button
            class="logout-btn"
            size="small"
            plain
            type="danger"
            :icon="SwitchButton"
            aria-label="退出后台管理系统"
            @click="logout"
          >
            退出
          </el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="90px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            @keyup.enter="submitPasswordChange"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="passwordSubmitting"
          @click="submitPasswordChange"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Lock, SwitchButton } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCurrentRoles, getVisibleMenuSections } from '../config/access'
import { clearAdminSession } from '../utils/authStorage'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()

const username = localStorage.getItem('username') || '用户'
const usernameInitial = username.trim().slice(0, 1).toUpperCase() || '用'

const currentRoles = getCurrentRoles()
const MENU_ORDER_STORAGE_KEY = 'dhic-side-menu-order-v2'
const MENU_LONG_PRESS_DELAY = 420
const MENU_DRAG_MOVE_TOLERANCE = 6

const baseMenus = computed(() => getVisibleMenuSections(currentRoles))
const menuOrder = ref(readMenuOrder())
const draggingMenu = ref(null)
const dropIndicator = ref(null)
const suppressMenuClickUntil = ref(0)
const orderedMenus = computed(() => applyMenuOrder(baseMenus.value, menuOrder.value))

function menuKey(item) {
  return item?.path || item?.index || item?.menuCode || item?.menuName || item?.title || ''
}

function normalizeMenuOrder(value) {
  const root = Array.isArray(value?.root) ? value.root.filter(Boolean) : []
  const children = {}

  if (value?.children && typeof value.children === 'object') {
    Object.entries(value.children).forEach(([parentKey, childKeys]) => {
      if (Array.isArray(childKeys)) {
        children[parentKey] = childKeys.filter(Boolean)
      }
    })
  }

  return { root, children }
}

function readMenuOrder() {
  try {
    return normalizeMenuOrder(JSON.parse(localStorage.getItem(MENU_ORDER_STORAGE_KEY) || '{}'))
  } catch (error) {
    console.warn('menu order parse failed', error)
    return { root: [], children: {} }
  }
}

function persistMenuOrder(nextOrder) {
  const normalizedOrder = normalizeMenuOrder(nextOrder)
  menuOrder.value = normalizedOrder
  localStorage.setItem(MENU_ORDER_STORAGE_KEY, JSON.stringify(normalizedOrder))
}

function sortByKeys(items, orderedKeys) {
  const orderMap = new Map(orderedKeys.map((key, index) => [key, index]))

  return [...items].sort((left, right) => {
    const leftIndex = orderMap.has(menuKey(left)) ? orderMap.get(menuKey(left)) : Number.MAX_SAFE_INTEGER
    const rightIndex = orderMap.has(menuKey(right)) ? orderMap.get(menuKey(right)) : Number.MAX_SAFE_INTEGER

    if (leftIndex !== rightIndex) {
      return leftIndex - rightIndex
    }

    return items.indexOf(left) - items.indexOf(right)
  })
}

function applyMenuOrder(items, orderState) {
  const orderedRootMenus = sortByKeys(items, orderState.root)

  return orderedRootMenus.map(item => {
    if (!Array.isArray(item.children) || item.children.length === 0) {
      return item
    }

    return {
      ...item,
      children: sortByKeys(item.children, orderState.children[menuKey(item)] || [])
    }
  })
}

function startMenuPointerDrag(event, level, parentKey, item) {
  if (event.button !== 0) return

  const key = menuKey(item)
  if (!key) return

  const longPressTimer = window.setTimeout(() => {
    activateMenuDrag()
  }, MENU_LONG_PRESS_DELAY)

  draggingMenu.value = {
    level,
    parentKey,
    key,
    startY: event.clientY,
    currentY: event.clientY,
    longPressTimer,
    armed: false,
    active: false
  }

  window.addEventListener('pointermove', onMenuPointerMove)
  window.addEventListener('pointerup', stopMenuPointerDrag, { once: true })
  window.addEventListener('pointercancel', stopMenuPointerDrag, { once: true })
  window.addEventListener('click', suppressWindowClickAfterDrag, true)
}

function onMenuPointerMove(event) {
  const dragState = draggingMenu.value
  if (!dragState) return

  dragState.currentY = event.clientY

  if (!dragState.armed) {
    if (Math.abs(event.clientY - dragState.startY) > MENU_DRAG_MOVE_TOLERANCE) {
      cancelPendingMenuDrag()
    }
    return
  }

  if (!dragState.active) {
    dragState.active = true
  }

  reorderMenuByPointer(event.clientY)
}

function activateMenuDrag() {
  const dragState = draggingMenu.value
  if (!dragState || dragState.armed) return

  dragState.armed = true
  document.body.classList.add('menu-sort-active')
}

function stopMenuPointerDrag() {
  const dragState = draggingMenu.value
  const shouldSuppressClick = Boolean(dragState?.armed || dragState?.active)

  if (dragState?.longPressTimer) {
    window.clearTimeout(dragState.longPressTimer)
  }

  window.removeEventListener('pointermove', onMenuPointerMove)
  window.removeEventListener('pointercancel', stopMenuPointerDrag)
  document.body.classList.remove('menu-sort-active')
  draggingMenu.value = null
  dropIndicator.value = null

  if (shouldSuppressClick) {
    suppressMenuClickUntil.value = Date.now() + 260
    window.setTimeout(() => {
      window.removeEventListener('click', suppressWindowClickAfterDrag, true)
    }, 280)
  } else {
    window.removeEventListener('click', suppressWindowClickAfterDrag, true)
  }
}

function cancelPendingMenuDrag() {
  const dragState = draggingMenu.value

  if (dragState?.longPressTimer) {
    window.clearTimeout(dragState.longPressTimer)
  }

  window.removeEventListener('pointermove', onMenuPointerMove)
  window.removeEventListener('pointerup', stopMenuPointerDrag)
  window.removeEventListener('pointercancel', stopMenuPointerDrag)
  window.removeEventListener('click', suppressWindowClickAfterDrag, true)
  document.body.classList.remove('menu-sort-active')
  draggingMenu.value = null
  dropIndicator.value = null
}

function suppressMenuClickAfterDrag(event) {
  if (Date.now() > suppressMenuClickUntil.value) return

  event.preventDefault()
  event.stopPropagation()
}

function suppressWindowClickAfterDrag(event) {
  if (!draggingMenu.value?.active && Date.now() > suppressMenuClickUntil.value) return

  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation?.()
}

function reorderMenuByPointer(pointerY) {
  const dragState = draggingMenu.value
  if (!dragState) return

  const targetInfo = findPointerTarget(dragState, pointerY)
  dropIndicator.value = targetInfo
    ? {
        level: dragState.level,
        parentKey: dragState.parentKey,
        key: targetInfo.key,
        position: targetInfo.after ? 'after' : 'before'
      }
    : null

  if (!targetInfo || targetInfo.key === dragState.key) return

  const keys = getCurrentMenuKeys(dragState.level, dragState.parentKey)
  const sourceIndex = keys.indexOf(dragState.key)
  const targetIndex = keys.indexOf(targetInfo.key)

  if (sourceIndex < 0 || targetIndex < 0) return

  const nextKeys = [...keys]
  nextKeys.splice(sourceIndex, 1)

  let insertIndex = targetInfo.after ? targetIndex + 1 : targetIndex

  if (sourceIndex < insertIndex) {
    insertIndex -= 1
  }

  if (insertIndex === sourceIndex) return

  nextKeys.splice(insertIndex, 0, dragState.key)
  saveMenuKeys(dragState.level, dragState.parentKey, nextKeys)
}

function findPointerTarget(dragState, pointerY) {
  const rows = getMenuRows(dragState.level, dragState.parentKey)
  if (rows.length === 0) return null

  let nearest = null

  rows.forEach(row => {
    const rect = row.getBoundingClientRect()
    const centerY = rect.top + rect.height / 2
    const distance = Math.abs(pointerY - centerY)

    if (!nearest || distance < nearest.distance) {
      nearest = {
        key: row.dataset.menuKey,
        after: pointerY > centerY,
        distance
      }
    }
  })

  return nearest
}

function getMenuRows(level, parentKey) {
  const escapedParentKey = CSS.escape(parentKey)
  return Array.from(document.querySelectorAll(`.aside .menu-row[data-menu-level="${level}"][data-menu-parent="${escapedParentKey}"]`))
}

function menuItemClass(level, parentKey, item) {
  const dragState = draggingMenu.value
  const indicator = dropIndicator.value
  const isDragging = Boolean(
    dragState &&
    dragState.level === level &&
    dragState.parentKey === parentKey &&
    dragState.key === menuKey(item)
  )
  const isDropTarget = Boolean(
    indicator &&
    indicator.level === level &&
    indicator.parentKey === parentKey &&
    indicator.key === menuKey(item) &&
    (!dragState || dragState.key !== menuKey(item))
  )

  return {
    'menu-item-dragging': isDragging,
    'menu-item-drag-ready': isDragging && dragState.armed && !dragState.active,
    'menu-item-dragging-active': isDragging && dragState.active,
    'menu-drop-before': isDropTarget && indicator.position === 'before',
    'menu-drop-after': isDropTarget && indicator.position === 'after'
  }
}

function getCurrentMenuKeys(level, parentKey) {
  if (level === 'root') {
    return orderedMenus.value.map(menuKey)
  }

  const parentMenu = orderedMenus.value.find(item => menuKey(item) === parentKey)
  return Array.isArray(parentMenu?.children) ? parentMenu.children.map(menuKey) : []
}

function saveMenuKeys(level, parentKey, orderedKeys) {
  const nextOrder = normalizeMenuOrder(menuOrder.value)

  if (level === 'root') {
    nextOrder.root = mergeSavedKeys(orderedKeys, nextOrder.root)
  } else {
    nextOrder.children = {
      ...nextOrder.children,
      [parentKey]: mergeSavedKeys(orderedKeys, nextOrder.children[parentKey] || [])
    }
  }

  persistMenuOrder(nextOrder)
}

function mergeSavedKeys(orderedKeys, savedKeys) {
  return [
    ...orderedKeys,
    ...savedKeys.filter(key => !orderedKeys.includes(key))
  ]
}

const roleText = computed(() => {
  return currentRoles.length > 0 ? currentRoles.join(' / ') : '未分配角色'
})
const roleSummary = computed(() => {
  if (currentRoles.length === 0) return '未分配角色'
  return currentRoles.length === 1 ? currentRoles[0] : `${currentRoles[0]} +${currentRoles.length - 1}`
})
const currentPageTitle = computed(() => String(route.meta?.title || '工作台'))

const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }

  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }

  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

function openPasswordDialog() {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

async function submitPasswordChange() {
  await passwordFormRef.value?.validate()

  passwordSubmitting.value = true
  try {
    await request.patch('/system/me/password', { ...passwordForm })
    ElMessage.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    logout()
  } finally {
    passwordSubmitting.value = false
  }
}

function logout() {
  clearAdminSession()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  width: 100%;
  height: 100dvh;
  min-width: 0;
  overflow: hidden;
  background: var(--dhic-page-bg);
}

.layout-container > :deep(.el-container) {
  min-width: 0;
  overflow: hidden;
}

.aside {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--dhic-sidebar-bg);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
}

.brand {
  height: 68px;
  flex: 0 0 68px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  background: var(--dhic-sidebar-bg-secondary);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.logo {
  width: 44px;
  height: 44px;
  object-fit: contain;
  background: #fff;
  border-radius: 6px;
  padding: 3px;
}

.brand-title {
  overflow: hidden;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand > div {
  min-width: 0;
}

.brand-subtitle {
  overflow: hidden;
  margin-top: 3px;
  color: var(--dhic-sidebar-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-menu {
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  border-right: none;
  padding: 8px 0 14px;
  background: transparent !important;
  scrollbar-width: thin;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  margin: 1px 6px;
  border-radius: 5px;
  color: var(--dhic-sidebar-text);
  font-size: 13px;
  transition:
    background-color 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    opacity 0.16s ease,
    transform 0.16s ease;
}

:deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  padding-left: 40px !important;
  color: var(--dhic-sidebar-text-secondary);
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.055) !important;
  color: #fff;
}

:deep(.el-menu-item.is-active) {
  background: rgba(217, 45, 32, 0.18);
  color: #fff;
  color: #fff !important;
  box-shadow: inset 3px 0 0 var(--dhic-brand-red);
  font-weight: 600;
}

.menu-row {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  cursor: pointer;
  position: relative;
  touch-action: none;
}

.menu-row span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.menu-item-dragging > .el-menu-item),
:deep(.menu-item-dragging > .el-sub-menu__title),
:deep(.el-menu-item.menu-item-dragging) {
  background: rgba(217, 48, 37, 0.24) !important;
  box-shadow:
    inset 4px 0 0 #d93025,
    0 8px 22px rgba(0, 0, 0, 0.22);
}

:deep(.menu-item-drag-ready > .el-menu-item),
:deep(.menu-item-drag-ready > .el-sub-menu__title),
:deep(.el-menu-item.menu-item-drag-ready) {
  background: rgba(217, 48, 37, 0.18) !important;
  box-shadow: inset 4px 0 0 #d93025;
}

:deep(.menu-item-dragging-active > .el-menu-item),
:deep(.menu-item-dragging-active > .el-sub-menu__title),
:deep(.el-menu-item.menu-item-dragging-active) {
  opacity: 0.88;
  transform: translateX(4px) scale(0.985);
}

:deep(.menu-drop-before > .el-menu-item),
:deep(.menu-drop-before > .el-sub-menu__title),
:deep(.el-menu-item.menu-drop-before),
:deep(.menu-drop-after > .el-menu-item),
:deep(.menu-drop-after > .el-sub-menu__title),
:deep(.el-menu-item.menu-drop-after) {
  background: rgba(217, 48, 37, 0.12) !important;
}

:deep(.menu-drop-before > .el-menu-item),
:deep(.menu-drop-before > .el-sub-menu__title),
:deep(.el-menu-item.menu-drop-before) {
  box-shadow: inset 0 3px 0 #ff4d4f;
}

:deep(.menu-drop-after > .el-menu-item),
:deep(.menu-drop-after > .el-sub-menu__title),
:deep(.el-menu-item.menu-drop-after) {
  box-shadow: inset 0 -3px 0 #ff4d4f;
}

:global(.menu-sort-active) {
  cursor: grabbing;
  user-select: none;
}

:global(.menu-sort-active) .aside {
  cursor: grabbing;
}

:global(.menu-sort-active) .aside .menu-row {
  cursor: grabbing;
}

.header {
  height: var(--dhic-header-height);
  flex: 0 0 var(--dhic-header-height);
  background: var(--dhic-header-bg);
  border-bottom: 1px solid var(--dhic-border-color-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 0 var(--dhic-page-padding);
}

.header-context {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.system-title {
  overflow: hidden;
  color: var(--dhic-text-secondary);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-context-separator,
.header-actions-divider {
  width: 1px;
  height: 16px;
  flex: 0 0 1px;
  background: var(--dhic-border-color);
}

.current-page-title {
  overflow: hidden;
  color: var(--dhic-text-primary);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.right {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.right :deep(.el-button + .el-button) {
  margin-left: 0;
}

.user-role {
  max-width: 160px;
  overflow: hidden;
  padding: 3px 8px;
  border-radius: 16px;
  background: var(--dhic-brand-red-light);
  color: var(--dhic-brand-red);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-account {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  padding: 3px 8px 3px 3px;
  border: 1px solid var(--dhic-border-color-light);
  border-radius: 18px;
  background: #f7f8fa;
}

.user-avatar {
  display: inline-flex;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #3c3f44;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.username {
  max-width: 104px;
  overflow: hidden;
  color: var(--dhic-text-regular);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  margin-left: 0;
}

.change-password-btn {
  margin-left: 0;
  color: var(--dhic-text-regular);
}

.change-password-btn:hover,
.change-password-btn:focus {
  color: var(--dhic-brand-red);
  background: var(--dhic-brand-red-light);
}

.main {
  min-width: 0;
  min-height: 0;
  background: var(--dhic-page-bg);
  padding: var(--dhic-page-padding);
  overflow: auto;
  scrollbar-gutter: stable;
}

@media (max-width: 1080px) {
  .right {
    gap: 8px;
  }

  .system-title,
  .header-context-separator {
    display: none;
  }

  .username {
    display: none;
  }

  .user-account {
    padding-right: 3px;
  }
}
</style>
