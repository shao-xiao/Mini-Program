<template>
  <el-dialog
    :model-value="modelValue"
    :title="tenant?.id ? '编辑租户' : '新增租户'"
    width="min(620px, calc(100vw - 48px))"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="reset"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="116px">
      <div class="form-grid">
        <el-form-item label="租户名称" prop="tenantName" class="wide">
          <el-input v-model.trim="form.tenantName" maxlength="120" />
        </el-form-item>
        <el-form-item label="企业 Logo" class="wide">
          <TenantLogoField
            v-model="form.logoFileId"
            :tenant-id="form.id"
            :preview-url="form.logoUrl"
            :file-name="form.logoFileName"
            :tenant-name="form.tenantName"
            :disabled="saving"
            @update:preview-url="form.logoUrl = $event"
            @update:file-name="form.logoFileName = $event"
            @uploading-change="logoUploading = $event"
          />
        </el-form-item>
        <el-form-item label="企业简称" prop="companyShortName">
          <el-input v-model.trim="form.companyShortName" maxlength="80" />
        </el-form-item>
        <el-form-item label="租户编号">
          <el-input :model-value="tenant?.tenantCode || tenant?.code || '保存后自动生成'" disabled />
        </el-form-item>
        <el-form-item label="主联系人" prop="contactPerson">
          <el-input v-model.trim="form.contactPerson" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model.trim="form.contactPhone" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱" prop="contactEmail">
          <el-input v-model.trim="form.contactEmail" maxlength="120" />
        </el-form-item>
        <el-form-item label="统一社会信用代码" prop="businessLicense">
          <el-input v-model.trim="form.businessLicense" maxlength="64" />
        </el-form-item>
        <el-form-item label="开票抬头" prop="invoiceTitle">
          <el-input v-model.trim="form.invoiceTitle" maxlength="160" placeholder="留空时默认企业全称" />
        </el-form-item>
        <el-form-item label="纳税人识别号" prop="taxNumber">
          <el-input v-model.trim="form.taxNumber" maxlength="64" placeholder="留空时默认统一社会信用代码" />
        </el-form-item>
        <el-form-item label="物业对接人" prop="serviceManagerUserId">
          <el-select v-model="form.serviceManagerUserId" clearable filterable style="width: 100%" placeholder="选择启用的内部员工">
            <el-option
              v-for="manager in serviceManagers"
              :key="manager.id"
              :label="`${manager.name}${manager.phone ? ` · ${manager.phone}` : ''}`"
              :value="manager.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark" class="wide">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="logoUploading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import TenantLogoField from './TenantLogoField.vue'

defineOptions({ name: 'TenantEditorDialog' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  tenant: { type: Object, default: null },
  serviceManagers: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false },
  submitHandler: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref(null)
const form = reactive(defaultForm())
const logoUploading = ref(false)
const initialLogoFileId = ref(null)

const rules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  contactPhone: [{ pattern: /^(?:1\d{10}|0\d{2,3}-?\d{7,8})?$/, message: '联系电话格式不正确', trigger: 'blur' }],
  contactEmail: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function defaultForm() {
  return {
    id: null,
    tenantName: '',
    logoFileId: null,
    logoUrl: '',
    logoFileName: '',
    companyShortName: '',
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    businessLicense: '',
    invoiceTitle: '',
    taxNumber: '',
    serviceManagerUserId: null,
    status: 'ACTIVE',
    remark: ''
  }
}

async function reset() {
  Object.assign(form, defaultForm(), props.tenant ? {
    id: props.tenant.id,
    tenantName: props.tenant.tenantName || props.tenant.name || '',
    logoFileId: props.tenant.logoFileId ?? null,
    logoUrl: props.tenant.logoUrl || '',
    logoFileName: props.tenant.logoFileName || '',
    companyShortName: props.tenant.companyShortName || props.tenant.shortName || '',
    contactPerson: props.tenant.contactName || props.tenant.contactPerson || '',
    contactPhone: props.tenant.phone || props.tenant.contactPhone || '',
    contactEmail: props.tenant.email || props.tenant.contactEmail || '',
    businessLicense: props.tenant.creditCode || props.tenant.businessLicense || '',
    invoiceTitle: props.tenant.invoiceTitle || '',
    taxNumber: props.tenant.taxNumber || '',
    serviceManagerUserId: props.tenant.serviceManagerUserId || null,
    status: props.tenant.status || 'ACTIVE',
    remark: props.tenant.remark || ''
  } : {})
  initialLogoFileId.value = normalizedLogoFileId(form.logoFileId)
  logoUploading.value = false
  await nextTick()
  formRef.value?.clearValidate()
}

async function submit() {
  if (logoUploading.value) {
    ElMessage.warning('请等待企业 Logo 上传完成')
    return
  }
  await formRef.value?.validate()
  const { logoFileId, logoUrl, logoFileName, ...payload } = form
  const normalizedCurrentLogoFileId = normalizedLogoFileId(logoFileId)
  const logoChange = form.id && normalizedCurrentLogoFileId !== initialLogoFileId.value
    ? { logoFileId: normalizedCurrentLogoFileId }
    : null
  const result = await props.submitHandler({ ...payload }, logoChange)
  if (result?.success === false) return
  emit('saved', result)
  emit('update:modelValue', false)
}

function normalizedLogoFileId(value) {
  const fileId = Number(value)
  return Number.isInteger(fileId) && fileId > 0 ? fileId : null
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.wide {
  grid-column: 1 / -1;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .wide {
    grid-column: auto;
  }
}
</style>
