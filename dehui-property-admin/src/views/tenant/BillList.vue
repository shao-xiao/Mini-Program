<template>
  <div class="finance-bill-page">
    <el-empty v-if="!hasPermission(billPermissions.view)" description="当前角色无权查看账单" />

    <template v-else>
      <header class="page-heading">
        <div>
          <h1>财务账单</h1>
          <p>计费依据核算、草稿调整、审核锁定、收款与发票版本统一闭环</p>
        </div>
        <div class="page-heading-actions">
          <el-button
            plain
            :icon="tenantPaneCollapsed ? Expand : Fold"
            :aria-label="tenantPaneCollapsed ? '展开租户公司栏' : '收起租户公司栏'"
            :aria-expanded="!tenantPaneCollapsed"
            @click="tenantPaneCollapsed = !tenantPaneCollapsed"
          >{{ tenantPaneCollapsed ? '展开租户' : '收起租户' }}</el-button>
          <el-button
            v-if="selectedTenant && hasPermission('tenant:view')"
            plain
            :icon="User"
            @click="goTenantWorkspace"
          >租户档案</el-button>
          <el-tooltip content="刷新财务工作区" placement="bottom">
            <el-button
              :icon="Refresh"
              :loading="directoryLoading || loading"
              circle
              aria-label="刷新财务工作区"
              @click="refreshFinanceWorkspace"
            />
          </el-tooltip>
          <el-button v-if="hasPermission('bill:add')" type="primary" :icon="Plus" @click="openCreateDialog">
            新增账单
          </el-button>
        </div>
      </header>

      <main :class="['finance-workspace-shell', { 'is-tenant-collapsed': tenantPaneCollapsed }]">
        <el-tooltip v-if="tenantPaneCollapsed" content="展开租户公司" placement="right">
          <button
            type="button"
            class="collapsed-tenant-rail"
            aria-label="展开租户公司栏"
            @click="tenantPaneCollapsed = false"
          >
            <el-icon aria-hidden="true"><OfficeBuilding /></el-icon>
          </button>
        </el-tooltip>

        <BillTenantDirectoryPane
          v-else
          :tenants="filteredTenants"
          :selected-id="selectedTenantId"
          :search="tenantSearch"
          :loading="directoryLoading"
          :error-text="directoryError"
          @update:search="tenantSearch = $event"
          @select="selectTenant"
          @retry="loadFinanceDirectory"
        />

        <section class="finance-detail-pane" aria-label="租户财务工作区">
          <header :class="['finance-context-header', { 'has-context-controls': selectedTenant }]">
            <div class="finance-context-title">
              <span class="context-eyebrow">{{ selectedTenant ? '当前租户' : '查看范围' }}</span>
              <h2>{{ selectedTenant?.tenantName || '全部租户' }}</h2>
              <p v-if="selectedTenant">
                {{ [selectedTenant.tenantCode, selectedTenant.contactPerson, selectedTenant.contactPhone].filter(Boolean).join(' · ') || '未配置联系人' }}
              </p>
              <p v-else>跨租户查看账单、收款和发票，不用于创建无归属账单</p>
            </div>

            <div class="finance-context-controls">
              <label v-if="selectedTenant" class="contract-filter">
                <span>合同</span>
                <el-select
                  :model-value="selectedContractId"
                  clearable
                  filterable
                  placeholder="全部合同"
                  aria-label="按合同筛选财务数据"
                  @change="selectContract"
                >
                  <el-option
                    v-for="contract in selectedTenantContracts"
                    :key="contract.id"
                    :label="contractOptionLabel(contract)"
                    :value="contract.id"
                  />
                </el-select>
              </label>
              <span v-if="selectedContract" class="room-summary">
                租赁空间：{{ selectedContract.roomSummary || '未配置' }}
              </span>
            </div>
          </header>

          <div v-loading="summaryLoading" class="finance-summary-region">
            <div v-if="summaryError" class="summary-error" role="alert">
              <span>{{ summaryError }}</span>
              <el-button link type="primary" @click="loadSummary">重试</el-button>
            </div>
            <MetricPanel v-else class="finance-metric-panel" :definitions="billWorkspaceConfig.metrics" :data="summaryStats" />
          </div>

          <el-tabs :model-value="activeTab" class="finance-tabs" @tab-change="changeActiveTab">
            <el-tab-pane label="财务概览" name="overview">
              <div class="finance-overview">
                <section class="overview-section">
                  <header>
                    <h3>账款状态</h3>
                    <span>{{ routeScopeLabel || '全部租户' }}</span>
                  </header>
                  <dl class="overview-status-list">
                    <div><dt>已收账单</dt><dd>{{ summaryStats.paidCount }} 笔</dd></div>
                    <div><dt>待收账单</dt><dd>{{ summaryStats.unpaidCount }} 笔</dd></div>
                    <div><dt>逾期账单</dt><dd>{{ summaryStats.overdueCount }} 笔</dd></div>
                    <div><dt>逾期金额</dt><dd>{{ money(summaryStats.overdueAmount) }}</dd></div>
                  </dl>
                </section>
                <section class="overview-section">
                  <header><h3>当前范围</h3></header>
                  <dl class="overview-status-list">
                    <div><dt>租户</dt><dd>{{ selectedTenant?.tenantName || '全部租户' }}</dd></div>
                    <div><dt>合同</dt><dd>{{ selectedContract?.contractNumber || '全部合同' }}</dd></div>
                    <div><dt>账单总数</dt><dd>{{ summaryStats.totalCount }} 笔</dd></div>
                    <div><dt>未收金额</dt><dd>{{ money(summaryStats.unpaidAmount) }}</dd></div>
                  </dl>
                </section>
              </div>
            </el-tab-pane>

            <el-tab-pane label="账单明细" name="bills">
              <DataWorkspace
                class="bill-detail-workspace"
                :workspace-config="billGridWorkspaceConfig"
                :views="workspaceViews"
                :active-view-id="activeViewId"
                :view-dirty="isViewDirty"
                :filter-logic="tablePrefs.filterLogic"
                :filter-logic-visible="tablePrefs.filters.length > 1"
                :filter-items="activeFilterItems"
                :query-notices="queryNotices"
                @change-view="switchBillView"
                @create-view="createTableView"
                @duplicate-view="duplicateTableView"
                @rename-view="renameTableView"
                @delete-view="deleteTableView"
                @save-view="saveBillView"
                @remove-filter="removeViewFilter"
              >
                <template #toolbar>
                  <DhicDataToolbar
                    :workspace-config="billWorkspaceConfig"
                    allow-custom-fields
                    allow-column-layout
                    field-config-saveable
                    :view-name="activeViewName"
                    :field-config-dirty="isViewDirty"
                    :columns="orderedColumns"
                    :visible-columns="tablePrefs.visibleColumns"
                    :column-order="tablePrefs.columnOrder"
                    :fixed-columns="tablePrefs.fixedColumns"
                    :custom-columns="tablePrefs.customColumns"
                    :filter-options="filterOptions"
                    :filters="tablePrefs.filters"
                    :filter-logic="tablePrefs.filterLogic"
                    :filter-logics="filterLogicOptions"
                    :sort-options="sortOptions"
                    :sorts="tablePrefs.sorts"
                    :sort-key="tablePrefs.sortKey"
                    :sort-order="tablePrefs.sortOrder"
                    :sort-count="sortCount"
                    :group-options="groupOptions"
                    :groups="tablePrefs.groups"
                    :group-key="tablePrefs.groupKey"
                    :group-count="groupCount"
                    :row-density="tablePrefs.rowDensity"
                    :active-filter-count="activeFilterCount"
                    :filter-items="activeFilterItems"
                    :import-export-loading-action-ids="exporting ? ['export'] : []"
                    @update:visible-columns="updateVisibleColumns"
                    @update:column-order="updateColumnOrder"
                    @update:fixed-columns="updateFixedColumns"
                    @update:filters="updateFilters"
                    @update:filter-logic="updateFilterLogic"
                    @update:sort-key="updateSortKey"
                    @update:sort-order="updateSortOrder"
                    @update:sorts="updateSorts"
                    @update:group-key="updateGroupKey"
                    @update:groups="updateGroups"
                    @update:row-density="updateRowDensity"
                    @reset-view="resetTableView"
                    @save-field-config="saveBillView"
                    @workspace-action="handleWorkspaceAction"
                  >
                    <template #right>
                      <div class="bill-toolbar-scope" role="status" aria-live="polite">
                        <span class="bill-toolbar-scope-label">当前范围</span>
                        <el-tag type="info" effect="plain">{{ routeScopeLabel || '全部租户' }}</el-tag>
                        <span class="view-hint">
                          已加载 <strong>{{ resultScope.loadedRecords }}</strong> / 共 <strong>{{ resultScope.serverTotal }}</strong> 条
                        </span>
                      </div>
                      <el-tooltip content="刷新账单列表" placement="bottom">
                        <el-button
                          class="bill-toolbar-refresh"
                          :icon="Refresh"
                          circle
                          aria-label="刷新账单列表"
                          @click="loadWorkspaceData"
                        />
                      </el-tooltip>
                    </template>
                  </DhicDataToolbar>
                </template>

                <template #grid>
                  <WorkspaceDataGrid
                    :loading="loading"
                    :error-text="loadError"
                    retryable
                    :rows="tableRows"
                    :columns="visibleOrderedColumns"
                    row-key="id"
                    :current-page="pagination.currentPage"
                    :page-size="pagination.pageSize"
                    :density="tablePrefs.rowDensity"
                    :empty-text="tableEmptyText"
                    :sort-state="activeTableSort"
                    :data-mode="billWorkspaceConfig.query.dataMode"
                    :row-class-name="billRowClassName"
                    :column-width="columnWidth"
                    :column-fixed="columnFixed"
                    :is-column-sortable="isColumnSortable"
                    :show-overflow-tooltip="column => ['title', 'tenantName', 'contractNumber', 'remark'].includes(column.key)"
                    :operation-column="{ width: 126, fixed: 'right', align: 'left' }"
                    height="100%"
                    @column-resize="handleHeaderDragEnd"
                    @sort-change="handleTableSortChange"
                    @retry="loadWorkspaceData"
                    aria-label="账单管理数据表格"
                  >
                    <template #cell-billNumber="{ row }">
                      <el-button link type="primary" @click.stop="openBillDetails(row)">
                        {{ row.billNumber || row.code || '-' }}
                      </el-button>
                    </template>
                    <template #operations="{ row }">
                      <div class="action-cell">
                        <el-button
                          v-if="primaryBillAction(row)"
                          size="small"
                          :type="primaryBillAction(row).type"
                          plain
                          :loading="isBillActionPending(row)"
                          :disabled="isBillActionPending(row)"
                          @click="runPrimaryBillAction(row)"
                        >
                          {{ primaryBillAction(row).label }}
                        </el-button>
                        <el-button v-else size="small" text @click="openBillDetails(row)">查看</el-button>
                        <el-dropdown
                          trigger="click"
                          :disabled="isBillActionPending(row)"
                          @command="command => handleBillCommand(command, row)"
                        >
                          <el-button
                            size="small"
                            text
                            circle
                            :icon="MoreFilled"
                            :disabled="isBillActionPending(row)"
                            aria-label="更多账单操作"
                          />
                          <template #dropdown>
                            <el-dropdown-menu>
                              <el-dropdown-item command="view">查看详情</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission(billPermissions.update)" command="edit" :disabled="!canEditBill(row)">编辑</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('bill:submit')" command="submit" :disabled="row.workflowStatus !== 'DRAFT'">提交审核</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('bill:audit')" command="approve" :disabled="row.workflowStatus !== 'PENDING_REVIEW'">审核通过</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('bill:audit')" command="reject" :disabled="row.workflowStatus !== 'PENDING_REVIEW'">驳回草稿</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('bill:pay')" command="pay" :disabled="!(row.auditStatus === 'APPROVED' && row.status !== 'PAID' && row.status !== 'CANCELLED')">确认收款</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('bill:adjustment')" command="adjustmentBill" :disabled="row.workflowStatus !== 'APPROVED'">创建补差/红冲单</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('finance:invoice:upload')" command="invoice" :disabled="row.workflowStatus !== 'APPROVED'">{{ isInvoiced(row) ? '新增发票版本' : '上传发票' }}</el-dropdown-item>
                              <el-dropdown-item command="preview" :disabled="!hasInvoiceFile(row)">预览发票</el-dropdown-item>
                              <el-dropdown-item command="download" :disabled="!hasInvoiceFile(row)">下载发票</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission('finance:invoice:void')" command="deleteInvoice" :disabled="!isInvoiced(row)">作废当前发票</el-dropdown-item>
                              <el-dropdown-item v-if="hasPermission(billPermissions.void)" command="void" :disabled="!canVoidBill(row)">作废账单</el-dropdown-item>
                              <el-dropdown-item
                                v-if="hasPermission(billPermissions.delete)"
                                command="delete"
                                class="bill-delete-menu-item"
                                divided
                                :disabled="!canDeleteBill(row)"
                                :title="deleteBillHint(row)"
                              >删除账单</el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                      </div>
                    </template>
                  </WorkspaceDataGrid>
                </template>

                <template #footer>
                  <div class="pagination-wrapper">
                    <el-pagination
                      v-model:current-page="pagination.currentPage"
                      v-model:page-size="pagination.pageSize"
                      :total="pagination.total"
                      :page-sizes="[10, 20, 50, 100]"
                      layout="total, sizes, prev, pager, next, jumper"
                      @current-change="loadWorkspaceData"
                      @size-change="handleSizeChange"
                    />
                  </div>
                </template>
              </DataWorkspace>
            </el-tab-pane>

            <el-tab-pane label="资产计费依据" name="bases">
              <section class="charge-basis-panel">
                <header class="charge-basis-toolbar">
                  <div>
                    <h3>待生成账单的资产计费依据</h3>
                    <p>楼宇物业费、能耗、停车和会议只在此形成统一财务账单。</p>
                  </div>
                  <div class="charge-basis-actions">
                    <el-select v-model="basisFilters.sourceType" clearable placeholder="全部来源" style="width:130px" @change="loadChargeBases">
                      <el-option label="物业费" value="PROPERTY" />
                      <el-option label="租金" value="RENT" />
                      <el-option label="能耗" value="ENERGY" />
                      <el-option label="停车" value="PARKING" />
                      <el-option label="会议" value="MEETING" />
                    </el-select>
                    <el-date-picker v-model="basisFilters.billingPeriod" type="month" value-format="YYYY-MM" placeholder="账期" @change="loadChargeBases" />
                    <el-button :loading="basisLoading" :disabled="Boolean(basisActionLoading)" @click="loadChargeBases">刷新</el-button>
                    <el-button
                      v-if="hasPermission('asset-charge-basis:generate')"
                      :loading="basisActionLoading === 'property'"
                      :disabled="Boolean(basisActionLoading)"
                      @click="syncPropertyBases"
                    >同步物业费依据</el-button>
                    <el-button
                      v-if="hasPermission('asset-charge-basis:generate')"
                      :loading="basisActionLoading === 'parking'"
                      :disabled="Boolean(basisActionLoading)"
                      @click="syncParkingBases"
                    >同步停车费依据</el-button>
                    <el-button
                      v-if="hasPermission('asset-charge-basis:generate')"
                      type="primary"
                      :loading="basisActionLoading === 'generate'"
                      :disabled="!selectedBasisIds.length || Boolean(basisActionLoading)"
                      @click="generateBasisBills"
                    >生成统一账单</el-button>
                  </div>
                </header>
                <el-alert v-if="basisError" type="error" :closable="false" show-icon :title="basisError" />
                <el-table
                  v-loading="basisLoading"
                  :data="chargeBases"
                  row-key="id"
                  @selection-change="rows => selectedBasisIds = rows.filter(row => row.status === 'CONFIRMED').map(row => row.id)"
                >
                  <el-table-column type="selection" width="48" :selectable="row => row.status === 'CONFIRMED'" />
                  <el-table-column prop="code" label="依据编号" min-width="165" />
                  <el-table-column prop="sourceType" label="来源" width="95">
                    <template #default="{ row }">{{ basisSourceText(row.sourceType) }}</template>
                  </el-table-column>
                  <el-table-column prop="tenantName" label="租户" min-width="160" />
                  <el-table-column prop="billingPeriod" label="账期" width="105" />
                  <el-table-column prop="quantity" label="数量" width="95" align="right" />
                  <el-table-column prop="unitPriceSnapshot" label="单价快照" width="115" align="right" />
                  <el-table-column prop="amount" label="金额" width="115" align="right" />
                  <el-table-column prop="status" label="状态" width="110" />
                  <el-table-column prop="billNumber" label="财务账单" min-width="155" />
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane label="收款记录" name="payments">
              <FinanceRecordPanel
                title="收款记录"
                subtitle="真实线下全额收款流水"
                :rows="paymentRows"
                :columns="paymentColumns"
                :loading="paymentLoading"
                :error-text="paymentError"
                :total="paymentPagination.total"
                :page="paymentPagination.currentPage"
                :page-size="paymentPagination.pageSize"
                empty-text="当前范围暂无收款记录"
                @retry="loadPaymentRecords"
                @row-click="openLedgerBill"
                @update:page="changePaymentPage"
                @update:page-size="changePaymentPageSize"
              >
                <template #operations="{ row }">
                  <el-button size="small" @click="openLedgerBill(row)">查看账单</el-button>
                </template>
              </FinanceRecordPanel>
            </el-tab-pane>

            <el-tab-pane label="发票管理" name="invoices">
              <FinanceRecordPanel
                title="发票管理"
                subtitle="账单关联的真实发票登记与附件"
                :rows="invoiceRows"
                :columns="invoiceColumns"
                :loading="invoiceLoading"
                :error-text="invoiceError"
                :total="invoicePagination.total"
                :page="invoicePagination.currentPage"
                :page-size="invoicePagination.pageSize"
                :operation-width="190"
                empty-text="当前范围暂无发票记录"
                @retry="loadInvoiceRecords"
                @row-click="openLedgerBill"
                @update:page="changeInvoicePage"
                @update:page-size="changeInvoicePageSize"
              >
                <template #operations="{ row }">
                  <div class="action-cell">
                    <el-button size="small" @click="openLedgerBill(row)">查看账单</el-button>
                    <el-dropdown trigger="click" @command="command => handleInvoiceLedgerCommand(command, row)">
                      <el-button size="small" aria-label="更多发票操作">更多</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="preview" :disabled="!row.hasInvoiceFile">预览附件</el-dropdown-item>
                          <el-dropdown-item command="download" :disabled="!row.hasInvoiceFile">下载附件</el-dropdown-item>
                          <el-dropdown-item v-if="hasPermission('finance:invoice:upload')" command="replace">替换附件</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </FinanceRecordPanel>
            </el-tab-pane>
          </el-tabs>
        </section>
      </main>
    </template>

    <BillDetailDrawer v-model="detailDrawerVisible" :bill="detailBill" />

    <input
      ref="invoiceInputRef"
      type="file"
      accept=".pdf,.ofd,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
      class="hidden-file-input"
      @change="handleInvoiceFileChange"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="账单编号" prop="billNumber">
          <el-input
            v-model="form.billNumber"
            :maxlength="64"
            :placeholder="isEditingBill ? '请输入唯一账单编号' : '留空则系统自动生成'"
          />
        </el-form-item>

        <el-form-item label="租户" prop="tenantId">
          <el-select
            v-model="form.tenantId"
            filterable
            placeholder="请选择租户"
            style="width: 100%"
            @change="handleTenantChange"
          >
            <el-option
              v-for="tenant in editableTenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="合同">
          <el-select
            v-model="form.contractId"
            clearable
            filterable
            placeholder="可不选，适用于手工费用"
            style="width: 100%"
            @change="handleContractChange"
          >
            <el-option
              v-for="contract in selectableContracts"
              :key="contract.id"
              :label="contractOptionLabel(contract)"
              :value="contract.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="账单类型" prop="billType">
          <el-select v-model="form.billType" style="width: 100%" @change="handleBillTypeChange">
            <el-option
              v-for="type in billTypeOptions"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="留空则按账期和类型生成" />
        </el-form-item>

        <el-form-item label="账期开始" prop="periodStart">
          <el-date-picker v-model="form.periodStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="账期结束" prop="periodEnd">
          <el-date-picker v-model="form.periodEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="计算金额" prop="calculatedAmount">
          <el-input-number v-model="form.calculatedAmount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>

        <el-form-item label="调整金额" prop="adjustmentAmount">
          <el-input-number v-model="form.adjustmentAmount" :precision="2" style="width: 100%" />
        </el-form-item>

        <el-form-item v-if="Number(form.adjustmentAmount || 0) !== 0" label="调整原因" prop="adjustmentReason">
          <el-input v-model="form.adjustmentReason" type="textarea" :rows="2" placeholder="调整金额不为 0 时必填，并写入审计日志" />
        </el-form-item>

        <el-form-item label="最终金额">
          <el-input :model-value="money(Number(form.calculatedAmount || 0) + Number(form.adjustmentAmount || 0))" disabled />
        </el-form-item>

        <el-form-item label="到期日" prop="dueDate">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="费用说明、线下缴费说明等" />
        </el-form-item>

        <CustomFieldEditor
          v-if="activeCustomFields.length"
          v-model="form.customFields"
          :columns="activeCustomFields"
        />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="isEditingBill ? hasPermission(billPermissions.update) : hasPermission('bill:add')" type="primary" :loading="saving" @click="saveBill">{{ submitButtonText }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold, MoreFilled, OfficeBuilding, Plus, Refresh, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import MetricPanel from '../../components/MetricPanel.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import CustomFieldEditor from '../../components/CustomFieldEditor.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import request from '../../utils/request'
import { downloadBlob } from '../../utils/downloadBlob'
import { hasPermission } from '../../utils/permission'
import { buildCsvText } from '../../utils/csvExport'
import { buildCustomFieldForm, customFieldRecordDisplayValue } from '../../utils/customFieldValues'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import {
  billAuditStatusOptions,
  billInvoiceStatusOptions,
  billPayStatusOptions,
  billSourceTypeOptions,
  billTypeOptions,
  createBillWorkspaceConfig
} from '../../workspaceConfigs/financeWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'
import BillDetailDrawer from './finance/BillDetailDrawer.vue'
import BillTenantDirectoryPane from './finance/BillTenantDirectoryPane.vue'
import FinanceRecordPanel from './finance/FinanceRecordPanel.vue'
import {
  contractsForFinanceTenant,
  filterFinanceTenants,
  financeScopeParams,
  mergeFinanceRouteQuery,
  resolveFinanceRouteScope
} from './finance/financeBillWorkspace.js'

const route = useRoute()
const router = useRouter()
const directoryReady = ref(false)
const financeScope = computed(() => directoryReady.value
  ? resolveFinanceRouteScope(route.query, tenants.value, contracts.value)
  : {
      tenantId: String(route.query.tenantId || '').toLowerCase() === 'all' ? null : positiveRouteId(route.query.tenantId),
      contractId: positiveRouteId(route.query.contractId),
      tab: ['overview', 'bills', 'bases', 'payments', 'invoices'].includes(String(route.query.tab || 'bills')) ? String(route.query.tab || 'bills') : 'bills',
      allTenants: String(route.query.tenantId || '').toLowerCase() === 'all' || !positiveRouteId(route.query.tenantId)
    })
const routeScopeParams = computed(() => financeScopeParams(financeScope.value))
const selectedTenantId = computed(() => financeScope.value.tenantId)
const selectedContractId = computed(() => financeScope.value.contractId)
const activeTab = computed(() => financeScope.value.tab)
const selectedTenant = computed(() => tenants.value.find(item => Number(item.id) === Number(selectedTenantId.value)) || null)
const selectedContract = computed(() => contracts.value.find(item => Number(item.id) === Number(selectedContractId.value)) || null)
const selectedTenantContracts = computed(() => contractsForFinanceTenant(contracts.value, selectedTenantId.value))
const filteredTenants = computed(() => filterFinanceTenants(tenants.value, tenantSearch.value))
const routeScopeLabel = computed(() => {
  const parts = []
  if (selectedTenant.value) parts.push(selectedTenant.value.tenantName)
  if (selectedContract.value) parts.push(selectedContract.value.contractNumber)
  return parts.length ? `关联范围：${parts.join(' / ')}` : ''
})
const bills = ref([])
const tenants = ref([])
const contracts = ref([])
const billTenantOptions = reactive([])
const billWorkspaceConfig = createBillWorkspaceConfig({ tenantOptions: billTenantOptions })
const billGridWorkspaceConfig = {
  ...billWorkspaceConfig,
  title: '账单明细',
  subtitle: '当前租户与合同范围内的服务端账单列表',
  metrics: []
}
const billPermissions = billWorkspaceConfig.permissions
const loading = ref(false)
const loadError = ref('')
const directoryLoading = ref(false)
const directoryError = ref('')
const tenantSearch = ref('')
const tenantPaneCollapsed = ref(false)
const saving = ref(false)
const exporting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const invoiceInputRef = ref(null)
const currentInvoiceBill = ref(null)
const pagination = reactive(createPagination(20))
const paymentRows = ref([])
const paymentLoading = ref(false)
const paymentError = ref('')
const paymentPagination = reactive(createPagination(20))
const invoiceRows = ref([])
const invoiceLoading = ref(false)
const invoiceError = ref('')
const invoicePagination = reactive(createPagination(20))
const chargeBases = ref([])
const selectedBasisIds = ref([])
const basisLoading = ref(false)
const basisActionLoading = ref('')
const basisError = ref('')
const basisFilters = reactive({
  status: 'CONFIRMED',
  sourceType: '',
  billingPeriod: new Date().toISOString().slice(0, 7)
})
const routeInteractionsReady = ref(false)
const directoryRequestGuard = createLatestRequestGuard()
const summaryRequestGuard = createLatestRequestGuard()
const paymentRequestGuard = createLatestRequestGuard()
const invoiceRequestGuard = createLatestRequestGuard()
const summaryLoading = ref(false)
const summaryError = ref('')
const summaryStats = reactive(emptySummary())
const editingBillId = ref(null)
const detailDrawerVisible = ref(false)
const detailBill = ref(null)
const billActionState = reactive(new Map())

const paymentColumns = [
  { key: 'paymentNumber', label: '收款编号', type: 'text', minWidth: 170, required: true },
  { key: 'tenantName', label: '租户', type: 'text', minWidth: 170 },
  { key: 'contractNumber', label: '合同编号', type: 'text', minWidth: 160 },
  { key: 'amount', label: '收款金额', type: 'money', currency: 'CNY', width: 130, align: 'right' },
  { key: 'payChannel', label: '收款渠道', type: 'text', width: 120 },
  { key: 'transactionNumber', label: '交易号', type: 'text', minWidth: 170 },
  { key: 'paidAt', label: '收款时间', type: 'datetime', minWidth: 170 },
  { key: 'operatorName', label: '操作人', type: 'text', width: 120 },
  { key: 'billNumber', label: '账单编号', type: 'text', minWidth: 165 },
  { key: 'status', label: '状态', type: 'status', width: 100 }
]

const invoiceColumns = [
  { key: 'invoiceNumber', label: '发票记录编号', type: 'text', minWidth: 170, required: true },
  { key: 'tenantName', label: '租户', type: 'text', minWidth: 170 },
  { key: 'contractNumber', label: '合同编号', type: 'text', minWidth: 160 },
  { key: 'invoiceTitle', label: '发票抬头', type: 'text', minWidth: 180 },
  { key: 'taxNumber', label: '税号', type: 'text', minWidth: 180 },
  { key: 'amount', label: '发票金额', type: 'money', currency: 'CNY', width: 130, align: 'right' },
  { key: 'invoiceStatus', label: '开票状态', type: 'status', options: billInvoiceStatusOptions, width: 120 },
  { key: 'issuedAt', label: '开票时间', type: 'datetime', minWidth: 170 },
  { key: 'billNumber', label: '账单编号', type: 'text', minWidth: 165 },
  { key: 'invoiceFileName', label: '附件', type: 'text', minWidth: 180 }
]

const defaultForm = () => ({
  billNumber: '',
  tenantId: null,
  contractId: null,
  billType: 'PROPERTY',
  title: '',
  periodStart: '',
  periodEnd: '',
  calculatedAmount: null,
  adjustmentAmount: 0,
  adjustmentReason: '',
  dueDate: '',
  remark: '',
  customFields: {}
})

const form = reactive(defaultForm())

const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  activeCustomFields,
  saveCustomFieldRecord,
  orderedColumns,
  visibleOrderedColumns,
  tableRows,
  tableEmptyText,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  serverQueryParams,
  queryNotices,
  queryRequestGuard: listRequestGuard,
  debouncedQueryTask: debouncedBillQuery,
  disposeQueryLifecycle,
  resultScope,
  activeFilterItems,
  activeFilterCount,
  groupCount,
  sortCount,
  activeTableSort,
  updateVisibleColumns,
  updateColumnOrder,
  updateFixedColumns,
  columnWidth,
  columnFixed,
  handleHeaderDragEnd,
  handleTableSortChange,
  isColumnSortable,
  updateFilterLogic,
  updateFilters,
  updateSortKey,
  updateSortOrder,
  updateSorts,
  updateGroupKey,
  updateGroups,
  updateRowDensity,
  resetTableView,
  switchTableView,
  createTableView,
  renameTableView,
  duplicateTableView,
  deleteTableView,
  saveCurrentView,
  discardCurrentViewChanges,
  removeViewFilter
} = useDhicTableView(workspaceTableOptions(billWorkspaceConfig, {
  records: () => bills.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: billGroupLabel
}))

applyRouteQueryFilters()
watch(() => JSON.stringify(serverQueryParams.value), () => debouncedBillQuery.schedule())
watch(() => JSON.stringify(routeScopeParams.value), () => {
  if (!routeInteractionsReady.value) return
  clearScopedFinanceData()
  resetToFirstPage(pagination)
  resetToFirstPage(paymentPagination)
  resetToFirstPage(invoicePagination)
  debouncedBillQuery.schedule()
  loadActiveLedger()
}, { flush: 'sync' })
watch(
  () => JSON.stringify([route.query.billType, route.query.sourceType, route.query.keyword]),
  applyRouteQueryFilters
)
watch(activeTab, () => {
  if (routeInteractionsReady.value) loadActiveLedger()
}, { flush: 'sync' })

function applyRouteQueryFilters() {
  const routeBillType = String(route.query.billType || '').trim().toUpperCase()
  const routeSourceType = String(route.query.sourceType || '').trim().toUpperCase()
  const routeKeyword = String(route.query.keyword || '').trim()
  const managedKeys = new Set(['billType', 'sourceType', 'keyword'])
  const filters = tablePrefs.filters.filter(item => !managedKeys.has(item.key))
  if (billTypeOptions.some(item => item.value === routeBillType)) {
    filters.push({ key: 'billType', operator: 'equals', value: routeBillType })
  }
  if (billSourceTypeOptions.some(item => item.value === routeSourceType)) {
    filters.push({ key: 'sourceType', operator: 'equals', value: routeSourceType })
  }
  if (routeKeyword) {
    filters.push({ key: 'keyword', operator: 'contains', value: routeKeyword })
  }
  if (filters.length !== tablePrefs.filters.length || routeBillType || routeSourceType || routeKeyword) {
    updateFilters(filters)
  }
}

const formRules = {
  billNumber: [
    {
      validator: (_rule, value, callback) => {
        const normalized = String(value ?? '').trim()
        if (editingBillId.value !== null && !normalized) {
          callback(new Error('账单编号不能为空'))
        } else if (normalized.length > 64) {
          callback(new Error('账单编号不能超过64个字符'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  billType: [{ required: true, message: '请选择账单类型', trigger: 'change' }],
  periodStart: [{ required: true, message: '请选择账期开始日期', trigger: 'change' }],
  periodEnd: [{ required: true, message: '请选择账期结束日期', trigger: 'change' }],
  calculatedAmount: [
    { required: true, message: '请输入计算金额', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (!value || value <= 0) {
          callback(new Error('计算金额必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  adjustmentReason: [{
    validator: (_rule, value, callback) => {
      if (Number(form.adjustmentAmount || 0) !== 0 && !String(value || '').trim()) {
        callback(new Error('调整金额不为 0 时必须填写调整原因'))
      } else callback()
    },
    trigger: 'blur'
  }],
  dueDate: [{ required: true, message: '请选择到期日', trigger: 'change' }]
}

const editableTenants = computed(() => {
  return tenants.value.filter(item => item.status !== 'INACTIVE' || item.id === form.tenantId)
})

const selectableContracts = computed(() => {
  return contracts.value.filter(item => {
    const tenantMatched = !form.tenantId || item.tenantId === form.tenantId
    const statusMatched = item.status === 'ACTIVE' || item.id === form.contractId
    return tenantMatched && statusMatched
  })
})

const isEditingBill = computed(() => editingBillId.value !== null)
const dialogTitle = computed(() => (isEditingBill.value ? '编辑账单' : '新增账单'))
const submitButtonText = computed(() => (isEditingBill.value ? '保存草稿' : '保存草稿'))

function emptySummary() {
  return {
    totalCount: 0,
    totalAmount: 0,
    paidAmount: 0,
    unpaidAmount: 0,
    paidCount: 0,
    unpaidCount: 0,
    overdueCount: 0,
    overdueAmount: 0
  }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

function summaryFilterParams() {
  const { page, pageSize, sorts, groupBy, ...filters } = serverQueryParams.value
  return { ...filters, ...routeScopeParams.value }
}

async function loadBillList() {
  if (!hasPermission(billPermissions.view)) return
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/bills', {
      params: {
        ...serverQueryParams.value,
        ...routeScopeParams.value,
        ...pageParams(pagination)
      },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    bills.value = page.records
    if (!listRequestGuard.isCurrent(requestId)) return
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    bills.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '账单加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadFinanceDirectory({ normalizeRoute = true } = {}) {
  if (!hasPermission(billPermissions.view)) return
  const requestId = directoryRequestGuard.start()
  directoryLoading.value = true
  directoryError.value = ''
  try {
    const [tenantData, contractData] = await Promise.all([
      request.get('/bills/tenant-directory', { silent: true }),
      request.get('/bills/contracts', { silent: true })
    ])
    if (!directoryRequestGuard.isCurrent(requestId)) return
    const nextTenants = Array.isArray(tenantData) ? tenantData : []
    const nextContracts = Array.isArray(contractData) ? contractData : []
    tenants.value = nextTenants
    contracts.value = nextContracts
    billTenantOptions.splice(0, billTenantOptions.length, ...nextTenants.map(tenant => ({
      label: tenantLabel(tenant),
      value: tenant.id
    })))
    if (normalizeRoute) await normalizeFinanceRoute(nextTenants, nextContracts)
    directoryReady.value = true
  } catch (error) {
    if (!directoryRequestGuard.isCurrent(requestId)) return
    tenants.value = []
    contracts.value = []
    billTenantOptions.splice(0)
    directoryReady.value = false
    directoryError.value = requestErrorMessage(error, '租户财务目录加载失败')
  } finally {
    if (directoryRequestGuard.isCurrent(requestId)) directoryLoading.value = false
  }
}

async function loadSummary() {
  const requestId = summaryRequestGuard.start()
  summaryLoading.value = true
  summaryError.value = ''
  try {
    const data = await request.get('/bills/summary', {
      params: summaryFilterParams(),
      silent: true
    })
    if (!summaryRequestGuard.isCurrent(requestId)) return
    const nextSummary = emptySummary()
    Object.keys(nextSummary).forEach(key => {
      nextSummary[key] = Number(data?.[key] || 0)
    })
    Object.assign(summaryStats, nextSummary)
  } catch (error) {
    if (!summaryRequestGuard.isCurrent(requestId)) return
    Object.assign(summaryStats, emptySummary())
    summaryError.value = requestErrorMessage(error, '财务汇总加载失败')
  } finally {
    if (summaryRequestGuard.isCurrent(requestId)) summaryLoading.value = false
  }
}

async function loadWorkspaceData() {
  await Promise.all([loadBillList(), loadSummary()])
}

async function loadBills() {
  await Promise.all([
    loadWorkspaceData(),
    loadFinanceDirectory({ normalizeRoute: false }),
    loadActiveLedger()
  ])
}

async function loadPaymentRecords() {
  const requestId = paymentRequestGuard.start()
  paymentLoading.value = true
  paymentError.value = ''
  try {
    const data = await request.get('/bills/payments', {
      params: { ...routeScopeParams.value, ...pageParams(paymentPagination) },
      silent: true
    })
    if (!paymentRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    paymentRows.value = page.records
    paymentPagination.total = page.total
  } catch (error) {
    if (!paymentRequestGuard.isCurrent(requestId)) return
    paymentRows.value = []
    paymentPagination.total = 0
    paymentError.value = requestErrorMessage(error, '收款记录加载失败')
  } finally {
    if (paymentRequestGuard.isCurrent(requestId)) paymentLoading.value = false
  }
}

async function loadInvoiceRecords() {
  const requestId = invoiceRequestGuard.start()
  invoiceLoading.value = true
  invoiceError.value = ''
  try {
    const data = await request.get('/bills/invoices', {
      params: { ...routeScopeParams.value, ...pageParams(invoicePagination) },
      silent: true
    })
    if (!invoiceRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    invoiceRows.value = page.records
    invoicePagination.total = page.total
  } catch (error) {
    if (!invoiceRequestGuard.isCurrent(requestId)) return
    invoiceRows.value = []
    invoicePagination.total = 0
    invoiceError.value = requestErrorMessage(error, '发票记录加载失败')
  } finally {
    if (invoiceRequestGuard.isCurrent(requestId)) invoiceLoading.value = false
  }
}

async function loadActiveLedger() {
  if (activeTab.value === 'payments') await loadPaymentRecords()
  else if (activeTab.value === 'invoices') await loadInvoiceRecords()
  else if (activeTab.value === 'bases') await loadChargeBases()
}

async function loadChargeBases() {
  basisLoading.value = true
  basisError.value = ''
  try {
    chargeBases.value = await request.get('/bills/charge-bases', {
      params: {
        status: basisFilters.status,
        sourceType: basisFilters.sourceType || undefined,
        billingPeriod: basisFilters.billingPeriod || undefined,
        tenantId: selectedTenantId.value || undefined
      },
      silent: true
    })
    selectedBasisIds.value = []
  } catch (error) {
    chargeBases.value = []
    basisError.value = requestErrorMessage(error, '资产计费依据加载失败')
  } finally {
    basisLoading.value = false
  }
}

async function runBasisAction(action, task) {
  if (basisActionLoading.value) return
  basisActionLoading.value = action
  try {
    await task()
  } finally {
    basisActionLoading.value = ''
  }
}

async function syncPropertyBases() {
  if (!basisFilters.billingPeriod) return ElMessage.warning('请选择账期')
  await runBasisAction('property', async () => {
    const result = await request.post('/bills/charge-bases/property/sync', { billingPeriod: basisFilters.billingPeriod })
    ElMessage.success(`已确认 ${Number(result.confirmedCount || 0)} 条物业费依据`)
    await loadChargeBases()
  })
}

async function syncParkingBases() {
  if (!basisFilters.billingPeriod) return ElMessage.warning('请选择账期')
  await runBasisAction('parking', async () => {
    const result = await request.post('/bills/charge-bases/parking/sync', { billingPeriod: basisFilters.billingPeriod })
    ElMessage.success(`已确认 ${Number(result.confirmedCount || 0)} 条停车费依据`)
    await loadChargeBases()
  })
}

async function generateBasisBills() {
  await runBasisAction('generate', async () => {
    const result = await request.post('/bills/charge-bases/generate', { ids: selectedBasisIds.value })
    ElMessage.success(`已生成 ${Number(result.generatedCount || 0)} 张统一财务草稿账单`)
    if (Number(result.failureCount || 0) > 0) {
      ElMessage.warning(`${result.failureCount} 条依据未生成，请检查租户、金额或当前状态`)
    }
    await Promise.all([loadChargeBases(), loadWorkspaceData()])
  })
}

function basisSourceText(value) {
  return { RENT: '租金', PROPERTY: '物业费', ENERGY: '能耗', PARKING: '停车', MEETING: '会议' }[value] || value || '-'
}

function clearScopedFinanceData() {
  bills.value = []
  pagination.total = 0
  paymentRows.value = []
  paymentPagination.total = 0
  invoiceRows.value = []
  invoicePagination.total = 0
  Object.assign(summaryStats, emptySummary())
  loadError.value = ''
  paymentError.value = ''
  invoiceError.value = ''
  summaryError.value = ''
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  loadWorkspaceData()
}

function changePaymentPage(page) {
  paymentPagination.currentPage = page
  loadPaymentRecords()
}

function changePaymentPageSize(pageSize) {
  paymentPagination.pageSize = pageSize
  resetToFirstPage(paymentPagination)
  loadPaymentRecords()
}

function changeInvoicePage(page) {
  invoicePagination.currentPage = page
  loadInvoiceRecords()
}

function changeInvoicePageSize(pageSize) {
  invoicePagination.pageSize = pageSize
  resetToFirstPage(invoicePagination)
  loadInvoiceRecords()
}

async function normalizeFinanceRoute(nextTenants = tenants.value, nextContracts = contracts.value) {
  const scope = resolveFinanceRouteScope(route.query, nextTenants, nextContracts)
  const query = mergeFinanceRouteQuery(route.query, scope)
  if (!sameRouteQuery(route.query, query)) await router.replace({ path: '/bills', query })
}

function selectTenant(tenant) {
  navigateFinanceScope({ tenantId: tenant?.id || null, contractId: null, tab: activeTab.value })
}

function selectContract(contractId) {
  navigateFinanceScope({
    tenantId: selectedTenantId.value,
    contractId: positiveRouteId(contractId),
    tab: activeTab.value
  })
}

function changeActiveTab(tab) {
  navigateFinanceScope({
    tenantId: selectedTenantId.value,
    contractId: selectedContractId.value,
    tab
  })
}

function navigateFinanceScope(scope, extraQuery = {}) {
  router.push({
    path: '/bills',
    query: {
      ...mergeFinanceRouteQuery(route.query, scope),
      ...extraQuery
    }
  })
}

function goTenantWorkspace() {
  if (!selectedTenantId.value || !hasPermission('tenant:view')) return
  router.push({
    path: '/tenant/workspace',
    query: {
      tenantId: selectedTenantId.value,
      ...(selectedContractId.value ? { contractId: selectedContractId.value } : {}),
      tab: 'bills'
    }
  })
}

async function refreshFinanceWorkspace() {
  await loadFinanceDirectory()
  await Promise.all([loadWorkspaceData(), loadActiveLedger()])
}

function sameRouteQuery(left, right) {
  const normalize = source => Object.keys(source || {})
    .sort()
    .reduce((result, key) => {
      const value = source[key]
      if (value !== undefined && value !== null && value !== '') result[key] = String(value)
      return result
    }, {})
  return JSON.stringify(normalize(left)) === JSON.stringify(normalize(right))
}

async function switchBillView(viewId) {
  if (viewId === activeViewId.value) return
  if (isViewDirty.value) {
    try {
      await ElMessageBox.confirm('当前视图有未保存的调整，是否保存后切换？', '切换视图', {
        distinguishCancelAndClose: true,
        confirmButtonText: '保存并切换',
        cancelButtonText: '放弃调整'
      })
      saveCurrentView()
    } catch (action) {
      if (action === 'cancel') discardCurrentViewChanges()
      else return
    }
  }
  switchTableView(viewId)
}

function saveBillView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function billGroupLabel(value) {
  const options = [
    ...billTypeOptions,
    ...billSourceTypeOptions,
    ...billPayStatusOptions,
    ...billAuditStatusOptions,
    ...billInvoiceStatusOptions
  ]
  return options.find(option => String(option.value) === String(value))?.label || value || '未填写'
}

function handleWorkspaceAction(actionId) {
  if (actionId === 'export') exportBills()
}

function resetForm() {
  editingBillId.value = null
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  if (!hasPermission('bill:add')) {
    ElMessage.warning('无新增账单权限')
    return
  }
  resetForm()
  form.tenantId = selectedTenantId.value
  form.contractId = selectedContractId.value
  dialogVisible.value = true
}

async function openBillDetails(row) {
  detailBill.value = row
  detailDrawerVisible.value = true
  try {
    const detail = await request.get(`/bills/${row.id}`, { silent: true })
    detailBill.value = detail || row
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '账单详情加载失败')
  }
}

function openEditDialog(row) {
  if (!canEditBill(row)) {
    ElMessage.warning('已收款账单不能编辑')
    return
  }
  resetForm()
  editingBillId.value = row.id
  Object.assign(form, {
    billNumber: row.billNumber || row.code || '',
    tenantId: row.tenantId ?? null,
    contractId: row.contractId ?? null,
    billType: row.billType || 'PROPERTY',
    title: row.title || '',
    periodStart: row.periodStart || '',
    periodEnd: row.periodEnd || '',
    calculatedAmount: Number(row.calculatedTotal ?? row.amount ?? 0),
    adjustmentAmount: Number(row.adjustmentTotal || 0),
    adjustmentReason: '',
    dueDate: row.dueDate || '',
    remark: row.remark || '',
    customFields: buildCustomFieldForm(activeCustomFields.value, row?.customFields || {})
  })
  dialogVisible.value = true
}

async function saveBill() {
  if (!(isEditingBill.value ? hasPermission(billPermissions.update) : hasPermission('bill:add'))) {
    ElMessage.warning('无账单维护权限')
    return
  }
  if (!Number.isFinite(Number(form.calculatedAmount)) || Number(form.calculatedAmount) <= 0) {
    await formRef.value?.validateField('calculatedAmount').catch(() => false)
    ElMessage.warning('计算金额必须大于0')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      billNumber: form.billNumber?.trim() || null,
      tenantId: Number(form.tenantId),
      contractId: form.contractId ? Number(form.contractId) : null,
      billType: form.billType,
      sourceType: 'MANUAL',
      title: form.title?.trim() || null,
      periodStart: form.periodStart,
      periodEnd: form.periodEnd,
      calculatedAmount: form.calculatedAmount,
      adjustmentAmount: form.adjustmentAmount || 0,
      adjustmentReason: form.adjustmentReason?.trim() || null,
      amount: Number(form.calculatedAmount || 0) + Number(form.adjustmentAmount || 0),
      version: isEditingBill.value ? Number(bills.value.find(item => item.id === editingBillId.value)?.version || 0) : undefined,
      dueDate: form.dueDate,
      remark: form.remark?.trim() || null
    }
    if (isEditingBill.value) {
      await request.put(`/bills/${editingBillId.value}`, payload)
      await persistBillCustomFields(editingBillId.value)
      ElMessage.success('账单草稿已更新')
    } else {
      const created = await request.post('/bills', payload)
      const createdId = created?.id || created?.data?.id
      if (createdId) {
        await persistBillCustomFields(createdId)
      } else if (activeCustomFields.value.length) {
        ElMessage.info('扩展字段已创建，请保存主记录后再次编辑该账单填写扩展字段')
      }
      ElMessage.success('账单已保存为草稿')
    }
    dialogVisible.value = false
    resetForm()
    await loadBills()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '账单保存失败')
  } finally {
    saving.value = false
  }
}

function billActionKey(row) {
  return String(row?.id ?? '')
}

function isBillActionPending(row) {
  const key = billActionKey(row)
  return Boolean(key) && billActionState.has(key)
}

function startBillAction(row, action) {
  const key = billActionKey(row)
  if (!key || billActionState.has(key)) return false
  billActionState.set(key, action)
  return true
}

function finishBillAction(row) {
  const key = billActionKey(row)
  if (key) billActionState.delete(key)
}

async function submitBill(row) {
  if (!startBillAction(row, 'submit')) return
  try {
    await ElMessageBox.confirm(`确定提交账单「${row.billNumber}」审核吗？提交后需先驳回才能继续调整。`, '提交审核', {
      type: 'warning'
    })
    await request.post(`/bills/${row.id}/submit`, { version: Number(row.version || 0) })
    ElMessage.success('账单已提交审核')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || error?.message || '提交审核失败')
  } finally {
    finishBillAction(row)
  }
}

async function approveBill(row) {
  if (!startBillAction(row, 'approve')) return
  try {
    await ElMessageBox.confirm(
      `确定发布账单「${row.billNumber}」给租户吗？发布后，该租户已绑定联系人可在小程序账单中心查看。`,
      '审核通过确认',
      { type: 'warning' }
    )
    await request.post(`/bills/${row.id}/approve`, { auditRemark: '后台审核通过', version: Number(row.version || 0) })
    ElMessage.success('账单已发布，租户小程序可见')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '审核失败')
    }
  } finally {
    finishBillAction(row)
  }
}

async function rejectBill(row) {
  if (!startBillAction(row, 'reject')) return
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因', `驳回账单 ${row.billNumber}`, {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：金额或账期需调整'
    })
    await request.post(`/bills/${row.id}/reject`, { auditRemark: result.value || '后台驳回', version: Number(row.version || 0) })
    ElMessage.success('账单已驳回')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '驳回失败')
    }
  } finally {
    finishBillAction(row)
  }
}

async function payBill(row) {
  if (!startBillAction(row, 'pay')) return
  try {
    await ElMessageBox.confirm(`确定已线下收取账单「${row.billNumber}」的款项 ${money(row.finalAmount ?? row.amount)} 元吗？`, '确认收款', {
      type: 'warning'
    })
    await request.post(`/bills/${row.id}/pay`)
    ElMessage.success('收款确认成功')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '收款失败')
    }
  } finally {
    finishBillAction(row)
  }
}

async function exportBills() {
  if (!hasPermission(billPermissions.export)) {
    ElMessage.warning('无账单查看权限')
    return
  }
  exporting.value = true
  try {
    const rows = await loadAllWorkspaceRecords(({ page, pageSize }) => request.get('/bills', {
      params: {
        ...serverQueryParams.value,
        ...routeScopeParams.value,
        page,
        pageSize
      },
      silent: true
    }))
    const columns = [
      { label: '序号', valueGetter: (_row, index) => index + 1 },
      ...visibleOrderedColumns.value.map(column => ({
        label: column.label,
        valueGetter: row => billExportValue(row, column)
      }))
    ]
    const csv = buildCsvText(rows, columns)
    downloadBlob(
      new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }),
      `账单导出-${new Date().toISOString().slice(0, 10)}.csv`
    )
    ElMessage.success(`已按当前查询导出 ${rows.length} 条账单`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function billExportValue(row, column) {
  if (column.custom) return customFieldRecordDisplayValue(row, column)
  if (['billType', 'auditStatus', 'payStatus', 'invoiceStatus'].includes(column.key)) {
    return billOptionText(row[column.key])
  }
  const value = row?.[column.key]
  return value === undefined || value === null || value === '' ? '-' : value
}

async function createAdjustmentBill(row) {
  if (!startBillAction(row, 'adjustment')) return
  try {
    const amountResult = await ElMessageBox.prompt(
      '请输入调整金额：正数为补差，负数为红冲',
      `创建后续调整单 ${row.billNumber}`,
      {
        inputType: 'number',
        inputPlaceholder: '例如 100 或 -100',
        inputValidator: value => Number(value) !== 0 || '调整金额不能为 0'
      }
    )
    const reasonResult = await ElMessageBox.prompt('请输入补差或红冲原因', '调整原因', {
      inputType: 'textarea',
      inputValidator: value => Boolean(String(value || '').trim()) || '调整原因不能为空'
    })
    await request.post(`/bills/${row.id}/adjustments`, {
      amount: Number(amountResult.value),
      reason: String(reasonResult.value).trim()
    })
    ElMessage.success('后续调整草稿已创建，请按正常流程提交审核')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || error?.message || '创建调整单失败')
  } finally {
    finishBillAction(row)
  }
}

function billOptionText(value) {
  const options = [
    ...billTypeOptions,
    ...billSourceTypeOptions,
    ...billPayStatusOptions,
    ...billAuditStatusOptions,
    ...billInvoiceStatusOptions
  ]
  return options.find(option => String(option.value) === String(value))?.label || value || '-'
}

function selectInvoiceFile(row) {
  if (!hasPermission('finance:invoice:upload')) {
    ElMessage.warning('无发票管理权限')
    return
  }
  currentInvoiceBill.value = row
  if (invoiceInputRef.value) {
    invoiceInputRef.value.value = ''
    invoiceInputRef.value.click()
  }
}

async function handleInvoiceFileChange(event) {
  const file = event.target.files?.[0]
  const row = currentInvoiceBill.value
  if (!file || !row) return

  if (!/\.(pdf|ofd|jpe?g|png)$/i.test(file.name)) {
    ElMessage.error('仅支持 PDF、OFD、JPG、JPEG、PNG 发票文件')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  try {
    await request.post(`/bills/${row.id}/invoice`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('发票新版本已上传')
    await loadBills()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '发票上传失败')
  } finally {
    currentInvoiceBill.value = null
    if (invoiceInputRef.value) invoiceInputRef.value.value = ''
  }
}

async function downloadInvoice(row) {
  try {
    const blob = await request.get(`/bills/${row.id}/invoice/download`, {
      responseType: 'blob'
    })
    downloadBlob(blob, row.invoiceFileName || `${row.billNumber}.pdf`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '发票下载失败')
  }
}

async function previewInvoice(row) {
  if (!hasInvoiceFile(row)) {
    ElMessage.warning('请先上传发票')
    return
  }

  const previewWindow = window.open('', '_blank')
  try {
    const blob = await request.get(`/bills/${row.id}/invoice/download`, {
      responseType: 'blob'
    })
    downloadBlob(blob, row.invoiceFileName || `${row.billNumber}.pdf`, {
      previewWindow,
      download: false
    })
  } catch (error) {
    if (previewWindow) previewWindow.close()
    ElMessage.error(error?.response?.data?.message || error?.message || '发票预览失败')
  }
}

async function deleteInvoice(row) {
  if (!startBillAction(row, 'void-invoice')) return
  try {
    await ElMessageBox.confirm(`确定作废账单「${row.billNumber}」当前有效发票吗？历史版本和附件仍将保留。`, '作废发票确认', {
      type: 'warning'
    })
    await request.delete(`/bills/${row.id}/invoice`, { params: { reason: '后台作废当前发票' } })
    ElMessage.success('当前发票已作废')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '发票作废失败')
    }
  } finally {
    finishBillAction(row)
  }
}

function canVoidBill(row) {
  return hasPermission(billPermissions.void)
    && ['DRAFT', 'PENDING_REVIEW', 'APPROVED'].includes(row.workflowStatus)
    && !['PAID', 'CANCELLED'].includes(row.payStatus || row.status)
    && Number(row.paidAmount || 0) <= 0
}

function canDeleteBill(row) {
  return hasPermission(billPermissions.delete) && !isBillActionPending(row)
}

function deleteBillHint() {
  return '删除前将由服务端核验状态、权限、收款、发票、附件和来源关联'
}

function canEditBill(row) {
  return hasPermission(billPermissions.update) && row.workflowStatus === 'DRAFT' && Number(row.paidAmount || 0) <= 0
}

function primaryBillAction(row) {
  if (row.workflowStatus === 'DRAFT' && hasPermission('bill:submit')) {
    return { label: '提交审核', type: 'primary' }
  }
  if (hasPermission('bill:audit') && row.workflowStatus === 'PENDING_REVIEW') {
    return { label: '审核通过', type: 'primary' }
  }
  if (row.workflowStatus === 'APPROVED' && row.status !== 'PAID' && row.status !== 'CANCELLED' && hasPermission('bill:pay')) {
    return { label: '确认收款', type: 'success' }
  }
  if (canEditBill(row)) {
    return { label: '编辑', type: 'default' }
  }
  return null
}

function runPrimaryBillAction(row) {
  const action = primaryBillAction(row)?.label
  if (action === '提交审核') submitBill(row)
  else if (action === '审核通过') approveBill(row)
  else if (action === '确认收款') payBill(row)
  else if (action === '编辑') openEditDialog(row)
}

function handleBillCommand(command, row) {
  if (command === 'view') openBillDetails(row)
  else if (command === 'edit') openEditDialog(row)
  else if (command === 'submit') submitBill(row)
  else if (command === 'approve') approveBill(row)
  else if (command === 'reject') rejectBill(row)
  else if (command === 'pay') payBill(row)
  else if (command === 'adjustmentBill') createAdjustmentBill(row)
  else if (command === 'invoice') selectInvoiceFile(row)
  else if (command === 'preview') previewInvoice(row)
  else if (command === 'download') downloadInvoice(row)
  else if (command === 'deleteInvoice') deleteInvoice(row)
  else if (command === 'void') voidBill(row)
  else if (command === 'delete') deleteBill(row)
}

function openLedgerBill(row) {
  if (!row?.billNumber) return
  navigateFinanceScope({
    tenantId: selectedTenantId.value,
    contractId: selectedContractId.value,
    tab: 'bills'
  }, { keyword: row.billNumber })
}

function handleInvoiceLedgerCommand(command, row) {
  const billRow = {
    id: row.billId,
    billNumber: row.billNumber,
    invoiceFileName: row.invoiceFileName,
    invoiceStatus: row.invoiceStatus,
    hasInvoiceFile: row.hasInvoiceFile
  }
  if (command === 'preview') previewInvoice(billRow)
  else if (command === 'download') downloadInvoice(billRow)
  else if (command === 'replace') selectInvoiceFile(billRow)
}

async function voidBill(row) {
  if (!startBillAction(row, 'void-bill')) return
  try {
    await ElMessageBox.confirm(
      `确定作废账单「${row.billNumber}」吗？作废后租户小程序不再显示，原始账单与审计轨迹仍保留。`,
      '作废账单确认',
      { type: 'warning' }
    )
    await request.post(`/bills/${row.id}/void`, {
      version: Number(row.version || 0),
      reason: '后台手动作废账单'
    }, { silent: true })
    ElMessage.success('账单已作废')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || error?.message || '账单作废失败')
    }
  } finally {
    finishBillAction(row)
  }
}

async function deleteBill(row) {
  if (!startBillAction(row, 'delete-bill')) return
  try {
    const eligibility = await request.post(`/bills/${row.id}/record/check`, {
      version: Number(row.version || 0)
    }, { silent: true })
    if (!eligibility?.allowed) {
      ElMessage.warning(eligibility?.blockedReason || '当前账单不允许删除')
      return
    }
    const confirmedBillNumber = eligibility.billNumber || row.billNumber
    const { value: reason } = await ElMessageBox.prompt(
      `账单「${confirmedBillNumber}」将从管理端汇总和租户小程序中移除，但审计轨迹仍会保留。请输入删除原因。`,
      '填写删除原因',
      {
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：清理隔离测试数据',
        inputValidator: value => {
          const normalized = String(value || '').trim()
          if (!normalized) return '删除原因不能为空'
          return normalized.length <= 500 || '删除原因不能超过500个字符'
        }
      }
    )
    const { value: confirmBillNumber } = await ElMessageBox.prompt(
      `删除不同于作废，页面没有恢复入口；服务端已完成财务历史与来源关联核验。请输入完整账单编号「${confirmedBillNumber}」确认。`,
      '删除账单',
      {
        type: 'error',
        confirmButtonType: 'danger',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        inputPlaceholder: confirmedBillNumber,
        inputValidator: value => value === confirmedBillNumber || '账单编号不一致，请输入完整账单编号'
      }
    )
    const shouldGoToPreviousPage = bills.value.length === 1 && pagination.currentPage > 1
    await request.delete(`/bills/${row.id}/record`, {
      data: {
        version: Number(row.version || 0),
        reason: String(reason || '').trim(),
        confirmBillNumber
      },
      silent: true
    })
    if (detailBill.value?.id === row.id) {
      detailDrawerVisible.value = false
      detailBill.value = null
    }
    if (shouldGoToPreviousPage) pagination.currentPage -= 1
    ElMessage.success('账单已删除，审计记录仍保留')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(requestErrorMessage(error, '账单删除失败'))
    }
  } finally {
    finishBillAction(row)
  }
}

function handleTenantChange() {
  form.contractId = null
}

function handleContractChange(contractId) {
  const contract = contracts.value.find(item => Number(item.id) === Number(contractId))
  if (!contract) return
  form.tenantId = contract.tenantId
  if (form.billType === 'RENT' && contract.rentAmount) {
    form.calculatedAmount = Number(contract.rentAmount)
  }
}

function handleBillTypeChange() {
  if (form.billType === 'RENT') handleContractChange(form.contractId)
  else if (!isEditingBill.value) form.calculatedAmount = null
}

function positiveRouteId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function contractOptionLabel(contract) {
  const rent = contract.rentAmount ? ` | 租金${contract.rentAmount}` : ''
  const rooms = contract.roomSummary ? ` | ${contract.roomSummary}` : ''
  return `${contract.contractNumber}${rooms}${rent}`
}

function isInvoiced(row) {
  return row.invoiceStatus === 'INVOICED'
}

function hasInvoiceFile(row) {
  return isInvoiced(row) && (row.hasInvoiceFile === true || row.hasInvoiceFile === 1 || Boolean(row.invoiceFileName))
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function billRowClassName({ row }) {
  if (row.__group) return 'view-group-row'
  if (row.status === 'PAID') return 'row-success'
  if (row.status === 'CANCELLED') return 'row-muted'
  if (row.overdue) return 'row-danger'
  return ''
}

async function persistBillCustomFields(entityId) {
  if (!activeCustomFields.value.length) return
  const result = await saveCustomFieldRecord({ id: entityId, customFields: form.customFields }, form.customFields)
  if (result.success === false) throw new Error(result.message || '自定义字段保存失败')
}

onMounted(async () => {
  tenantPaneCollapsed.value = window.matchMedia('(max-width: 1180px)').matches
  await loadFinanceDirectory()
  routeInteractionsReady.value = true
  await Promise.all([loadWorkspaceData(), loadActiveLedger()])
})

onBeforeUnmount(() => {
  disposeQueryLifecycle()
  directoryRequestGuard.dispose()
  summaryRequestGuard.dispose()
  paymentRequestGuard.dispose()
  invoiceRequestGuard.dispose()
})
</script>

<style scoped>
.finance-bill-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 10px;
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: var(--dhic-page-bg);
}

.page-heading,
.page-heading-actions,
.finance-context-header,
.finance-context-controls,
.contract-filter,
.summary-error,
.overview-section header,
.overview-status-list div,
.action-cell {
  display: flex;
  align-items: center;
}

.page-heading {
  justify-content: space-between;
  gap: 16px;
  min-height: 50px;
}

.page-heading h1 {
  margin: 0;
  color: var(--dhic-text);
  font-size: 21px;
  letter-spacing: 0;
  line-height: 1.2;
}

.page-heading p {
  margin: 3px 0 0;
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.page-heading-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.page-heading-actions :deep(.el-button + .el-button),
.action-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

.finance-workspace-shell {
  display: grid;
  grid-template-columns: minmax(248px, 272px) minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--dhic-border-soft);
  border-radius: 6px;
  background: var(--dhic-surface, #fff);
  box-shadow: var(--dhic-shadow-subtle);
}

.finance-workspace-shell > * + * {
  border-left: 1px solid var(--dhic-border-soft);
}

.finance-workspace-shell.is-tenant-collapsed {
  grid-template-columns: 44px minmax(0, 1fr);
}

.collapsed-tenant-rail {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  padding: 0;
  background: var(--dhic-card-bg, #fff);
  color: var(--dhic-text-secondary);
  cursor: pointer;
}

.collapsed-tenant-rail :deep(.el-icon) {
  width: 18px;
  height: 18px;
  color: var(--dhic-text-muted);
  font-size: 18px;
}

.collapsed-tenant-rail:focus-visible {
  outline: 2px solid var(--dhic-primary);
  outline-offset: -2px;
}

.finance-detail-pane {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--dhic-surface, #fff);
}

.finance-context-header {
  justify-content: space-between;
  gap: 16px;
  min-height: 72px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.finance-context-title {
  flex: 1 1 auto;
  min-width: 0;
}

.context-eyebrow {
  color: var(--dhic-text-muted);
  font-size: 11px;
}

.finance-context-title h2 {
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--dhic-text);
  font-size: 16px;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.finance-context-title p {
  margin: 3px 0 0;
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.finance-context-controls {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 8px 14px;
  min-width: 0;
}

.contract-filter {
  gap: 8px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
}

.contract-filter :deep(.el-select) {
  width: min(320px, 31vw);
}

.room-summary {
  max-width: 360px;
  overflow: hidden;
  color: var(--dhic-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.finance-summary-region {
  min-height: 0;
  padding: 8px 10px;
  border-bottom: 1px solid var(--dhic-border-soft);
  background: #fafbfc;
}

.finance-metric-panel {
  overflow: hidden;
  border: 1px solid var(--dhic-border-soft);
  border-radius: 6px;
  background: var(--dhic-card-bg);
}

.finance-summary-region :deep(.list-summary-grid) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  margin: 0;
  background: var(--dhic-border-soft);
}

.finance-summary-region :deep(.list-summary-card) {
  min-height: 72px;
  padding: 9px 12px 9px 14px;
  border: 0;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
}

.finance-summary-region :deep(.list-summary-card::before) {
  top: 9px;
  bottom: 9px;
}

.finance-summary-region :deep(.list-summary-card:hover) {
  box-shadow: var(--dhic-shadow-subtle);
  transform: none;
}

.finance-summary-region :deep(.summary-value) {
  margin: 4px 0 2px;
  font-size: 20px;
}

.summary-error {
  justify-content: space-between;
  min-height: 70px;
  padding: 0 12px;
  background: var(--dhic-danger-soft);
  color: var(--dhic-danger);
  font-size: 13px;
}

.finance-tabs {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.finance-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  flex: 0 0 auto;
}

.finance-tabs :deep(.el-tabs__item) {
  height: 42px;
  padding: 0 16px;
  font-size: 13px;
}

.finance-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--dhic-border-soft);
}

.finance-tabs :deep(.el-tabs__content) {
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: auto;
  flex: 1;
  scrollbar-gutter: stable;
}

.finance-tabs :deep(.el-tab-pane) {
  min-width: 0;
  min-height: 100%;
}

.finance-tabs :deep(#pane-bills) {
  height: 100%;
}

.finance-tabs :deep(.data-workspace) {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.finance-tabs :deep(.bill-detail-workspace) {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.finance-tabs :deep(.bill-detail-workspace > .workspace-header),
.finance-tabs :deep(.bill-detail-workspace > .workspace-filter-strip),
.finance-tabs :deep(.bill-detail-workspace > .workspace-query-notices),
.finance-tabs :deep(.bill-detail-workspace > .workspace-toolbar),
.finance-tabs :deep(.bill-detail-workspace > .workspace-footer) {
  flex: 0 0 auto;
}

.finance-tabs :deep(.workspace-header) {
  grid-template-columns: minmax(150px, 210px) minmax(260px, 1fr) auto;
  gap: 16px;
  min-height: 56px;
  padding: 8px 12px;
}

.finance-tabs :deep(.workspace-title-block h2) {
  font-size: 16px;
}

.finance-tabs :deep(.workspace-title-block p) {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.finance-tabs :deep(.workspace-view-entry) {
  justify-content: flex-end;
}

.finance-tabs :deep(.workspace-view-entry .view-select) {
  flex: 1 1 220px;
  max-width: 360px;
}

.finance-tabs :deep(.workspace-filter-strip) {
  min-height: 36px;
  padding: 5px 10px;
}

.finance-tabs :deep(.workspace-query-notices) {
  padding: 6px 10px;
}

.finance-tabs :deep(.workspace-toolbar) {
  padding: 0 10px 8px;
}

.finance-tabs :deep(.dhic-data-toolbar) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 0;
  margin: 0;
  padding: 5px 8px;
  overflow: hidden;
  border-radius: 6px;
  box-shadow: none;
}

.finance-tabs :deep(.toolbar-left) {
  width: auto;
  min-height: 32px;
  gap: 6px;
  padding: 0;
}

.finance-tabs :deep(.toolbar-left > .el-button + .el-button),
.finance-tabs :deep(.toolbar-data-actions .el-button + .el-button) {
  margin-left: 0;
}

.finance-tabs :deep(.toolbar-left > .el-button),
.finance-tabs :deep(.toolbar-data-actions .el-button) {
  padding-right: 10px;
  padding-left: 10px;
}

.finance-tabs :deep(.toolbar-data-actions) {
  margin-left: 2px;
  padding-left: 8px;
}

.finance-tabs :deep(.toolbar-right) {
  display: flex;
  align-items: center;
  gap: 6px;
  width: auto;
  min-height: 32px;
  padding: 0;
  border-top: 0;
  background: transparent;
}

.finance-tabs :deep(.toolbar-right > .el-button--danger.is-link) {
  margin: 0;
}

.bill-toolbar-scope {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bill-toolbar-scope-label {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.bill-toolbar-scope :deep(.el-tag) {
  max-width: min(360px, 45vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bill-toolbar-refresh {
  margin: 0;
}

.finance-tabs :deep(.bill-detail-workspace .workspace-grid) {
  display: flex;
  min-height: 90px;
  padding: 0 10px;
  overflow: hidden;
  flex: 1 1 auto;
}

.finance-tabs :deep(.bill-detail-workspace .workspace-data-grid) {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.finance-tabs :deep(.bill-detail-workspace .el-table) {
  border-radius: 4px 4px 0 0;
}

.finance-tabs :deep(.workspace-footer) {
  padding: 0 10px 4px;
  border-top: 1px solid var(--dhic-border-soft);
  background: var(--dhic-surface, #fff);
}

.finance-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 14px;
}

.overview-section {
  border: 1px solid var(--dhic-border-soft);
  border-radius: 6px;
  background: var(--dhic-surface, #fff);
}

.overview-section header {
  justify-content: space-between;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.overview-section h3 {
  margin: 0;
  color: var(--dhic-text);
  font-size: 14px;
  letter-spacing: 0;
}

.overview-section header span {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.overview-status-list {
  margin: 0;
}

.overview-status-list div {
  justify-content: space-between;
  gap: 20px;
  min-height: 42px;
  padding: 0 12px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.overview-status-list div:last-child {
  border-bottom: 0;
}

.overview-status-list dt {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.overview-status-list dd {
  margin: 0;
  overflow: hidden;
  color: var(--dhic-text);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.view-hint {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.view-hint strong {
  color: var(--dhic-text-secondary);
  font-weight: 600;
}

.hidden-file-input {
  display: none;
}

.action-cell {
  gap: 4px;
}

:global(.bill-delete-menu-item:not(.is-disabled)) {
  color: var(--el-color-danger);
}

.pagination-wrapper {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 42px;
  padding-top: 0;
}

.pagination-wrapper :deep(.el-pagination) {
  padding: 4px 0 0;
}

@media (max-width: 1180px) {
  .finance-workspace-shell:not(.is-tenant-collapsed) {
    grid-template-columns: minmax(220px, 244px) minmax(0, 1fr);
  }

  .finance-context-header.has-context-controls {
    align-items: flex-start;
    flex-direction: column;
  }

  .finance-context-header.has-context-controls .finance-context-controls {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .contract-filter :deep(.el-select) {
    width: min(420px, 58vw);
  }

  .finance-tabs :deep(.workspace-header) {
    grid-template-columns: minmax(140px, 180px) minmax(220px, 1fr);
    gap: 12px;
  }

  .finance-tabs :deep(.workspace-view-entry) {
    grid-column: 2;
    grid-row: 1;
  }

  .finance-tabs :deep(.workspace-header-actions) {
    grid-column: 1 / -1;
  }

  .finance-overview {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (min-width: 901px) and (max-width: 1180px) {
  .finance-summary-region :deep(.list-summary-grid) {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .finance-summary-region :deep(.list-summary-card) {
    min-height: 68px;
    padding: 8px 7px 8px 10px;
  }

  .finance-summary-region :deep(.summary-value) {
    overflow: hidden;
    font-size: 16px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .finance-summary-region :deep(.summary-label),
  .finance-summary-region :deep(.summary-desc) {
    font-size: 11px;
  }
}

@media (max-width: 900px) {
  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .page-heading-actions {
    width: 100%;
    max-width: none;
    justify-content: flex-start;
  }

  .finance-summary-region :deep(.list-summary-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .finance-tabs :deep(.workspace-header) {
    grid-template-columns: minmax(0, 1fr);
  }

  .finance-tabs :deep(.workspace-view-entry) {
    grid-column: 1;
    grid-row: 2;
  }
}

@media (max-width: 640px) {
  .finance-summary-region :deep(.list-summary-grid) {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (min-width: 1320px) {
  .finance-summary-region :deep(.list-summary-grid) {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}

@media (max-width: 1319px) {
  .bill-toolbar-scope :deep(.el-tag) {
    max-width: min(300px, 34vw);
  }
}

@media (max-width: 1080px) {
  .finance-tabs :deep(.workspace-title-block p) {
    display: none;
  }

  .finance-tabs :deep(.workspace-header) {
    min-height: 50px;
  }

  .bill-toolbar-scope-label {
    display: none;
  }

  .bill-toolbar-scope :deep(.el-tag) {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .finance-summary-region :deep(.list-summary-card) {
    transition: none;
  }
}
</style>
