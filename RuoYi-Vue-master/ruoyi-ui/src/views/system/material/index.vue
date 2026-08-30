<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="资料标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入资料标题"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学科" prop="subjectName">
        <el-select v-model="queryParams.subjectName" placeholder="请选择学科" clearable>
          <el-option
            v-for="item in dict.type.edu_subject"
            :key="item.value"
            :label="item.label"
            :value="item.label"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="年级" prop="gradeName">
        <el-select v-model="queryParams.gradeName" placeholder="请选择年级" clearable>
          <el-option
            v-for="item in dict.type.edu_grade"
            :key="item.value"
            :label="item.label"
            :value="item.label"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="上架状态" prop="shelfStatus">
        <el-select v-model="queryParams.shelfStatus" placeholder="请选择状态" clearable>
          <el-option label="已上架" value="1" />
          <el-option label="已下架" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:material:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete()"
          v-hasPermi="['system:material:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:material:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="materialList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="materialId" width="60" />
      <el-table-column label="封面" align="center" width="80">
        <template slot-scope="scope">
          <image-preview v-if="scope.row.coverUrl" :src="scope.row.coverUrl" :width="40" :height="40" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="资料标题" align="center" prop="title" :show-overflow-tooltip="true" />
      <el-table-column label="学科" align="center" prop="subjectName" width="80" />
      <el-table-column label="年级" align="center" prop="gradeName" width="80" />
      <el-table-column label="价格" align="center" width="80">
        <template slot-scope="scope">
          <span v-if="scope.row.price === 0 || scope.row.price == null" style="color: #67C23A;">免费</span>
          <span v-else style="color: #E6A23C;">¥{{ scope.row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column label="文件名" align="center" prop="fileName" :show-overflow-tooltip="true" />
      <el-table-column label="文件大小" align="center" width="90">
        <template slot-scope="scope">
          <span>{{ formatFileSize(scope.row.fileSize) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="销量" align="center" prop="saleCount" width="60" />
      <el-table-column label="上架状态" align="center" width="80">
        <template slot-scope="scope">
          <el-tag :type="scope.row.shelfStatus === '1' ? 'success' : 'info'">
            {{ scope.row.shelfStatus === '1' ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传者" align="center" prop="uploadUserName" width="100" />
      <el-table-column label="上传时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="240">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:material:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            :icon="scope.row.shelfStatus === '1' ? 'el-icon-close' : 'el-icon-check'"
            @click="handleToggleShelf(scope.row)"
          >{{ scope.row.shelfStatus === '1' ? '下架' : '上架' }}</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:material:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="资料标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入资料标题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入资料编码（留空自动生成）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学科" prop="subjectName">
              <el-select v-model="form.subjectName" placeholder="请选择学科" style="width: 100%;">
                <el-option
                  v-for="item in dict.type.edu_subject"
                  :key="item.value"
                  :label="item.label"
                  :value="item.label"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年级" prop="gradeName">
              <el-select v-model="form.gradeName" placeholder="请选择年级" style="width: 100%;">
                <el-option
                  v-for="item in dict.type.edu_grade"
                  :key="item.value"
                  :label="item.label"
                  :value="item.label"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" placeholder="0为免费" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上架状态" prop="shelfStatus">
              <el-radio-group v-model="form.shelfStatus">
                <el-radio label="1">上架</el-radio>
                <el-radio label="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="封面图" prop="coverUrl">
              <image-upload v-model="form.coverUrl" :limit="1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="资料文件" prop="filePath">
              <file-upload v-model="form.filePath" :max-size="10" :file-types="['pdf']" />
              <div style="color: #909399; font-size: 12px; margin-top: 4px;">仅支持PDF格式，单文件不超过10MB</div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介" prop="intro">
              <el-input v-model="form.intro" type="textarea" :rows="3" placeholder="请输入资料简介" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listMaterial, getMaterial, delMaterial, addMaterial, updateMaterial, changeShelfStatus } from "@/api/system/material"

export default {
  name: "Material",
  dicts: ['edu_subject', 'edu_grade'],
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      materialList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        title: null,
        subjectName: null,
        gradeName: null,
        shelfStatus: null
      },
      form: {},
      rules: {
        title: [
          { required: true, message: "资料标题不能为空", trigger: "blur" }
        ],
        subjectName: [
          { required: true, message: "请选择学科", trigger: "change" }
        ],
        gradeName: [
          { required: true, message: "请选择年级", trigger: "change" }
        ],
        filePath: [
          { required: true, message: "请上传资料文件", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listMaterial(this.queryParams).then(response => {
        this.materialList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        materialId: null,
        materialCode: null,
        title: null,
        subjectName: null,
        gradeName: null,
        price: 0,
        intro: null,
        filePath: null,
        fileName: null,
        fileSize: null,
        coverUrl: null,
        shelfStatus: '1',
        saleCount: 0,
        remark: null
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.materialId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "新增资料"
    },
    handleUpdate(row) {
      this.reset()
      const materialId = row.materialId || this.ids[0]
      getMaterial(materialId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改资料"
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.materialId != null) {
            updateMaterial(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addMaterial(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      const materialIds = row.materialId || this.ids
      if (!materialIds || materialIds.length === 0) {
        this.$modal.msgWarning("请选择要删除的资料")
        return
      }
      this.$modal.confirm('是否确认删除所选资料？').then(function() {
        return delMaterial(materialIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleToggleShelf(row) {
      const newStatus = row.shelfStatus === '1' ? '0' : '1'
      const action = newStatus === '1' ? '上架' : '下架'
      this.$modal.confirm('是否确认' + action + '资料"' + row.title + '"？').then(() => {
        return changeShelfStatus(row.materialId, newStatus)
      }).then(() => {
        this.$modal.msgSuccess(action + "成功")
        this.getList()
      }).catch(() => {})
    },
    handleExport() {
      this.download('system/material/export', {
        ...this.queryParams
      }, `material_${new Date().getTime()}.xlsx`)
    },
    formatFileSize(bytes) {
      if (!bytes) return '-'
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / 1024 / 1024).toFixed(2) + ' MB'
    }
  }
}
</script>
