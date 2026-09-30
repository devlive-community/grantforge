<template>
  <Drawer v-model="visible"
          width="50%"
          :title="title"
          :mask-closable="false">
    <Form :model="formState">
      <Row :gutter="32">
        <Col span="12">
          <FormItem label="名称"
                    label-position="top">
            <Input v-model="formState.name"
                   placeholder="请输入名称"/>
          </FormItem>
        </Col>
        <Col span="12">
          <FormItem label="编码"
                    label-position="top">
            <Input v-model="formState.code"
                   placeholder="请输入编码"/>
          </FormItem>
        </Col>
      </Row>
      <FormItem label="描述"
                label-position="top">
        <Input v-model="formState.description"
               type="textarea"
               :rows="4"
               placeholder="请输入描述"/>
      </FormItem>
    </Form>
    <div class="basic-drawer-footer">
      <Button type="primary"
              :loading="loading"
              @click="handlerSave">
        保存
      </Button>
    </div>
  </Drawer>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { Message } from 'view-ui-plus'
import { clone } from 'lodash'
import { MethodEntity } from '@/entity/MethodEntity'
import MethodService from '@/services/MethodService'

export default defineComponent({
  name: 'MethodDetail',
  props: {
    isVisible: {
      type: Boolean,
      default: () => false
    },
    info: {
      type: MethodEntity
    }
  },
  created () {
    this.handlerInitialize()
  },
  data () {
    return {
      title: '新增请求方式',
      formState: null as unknown as MethodEntity,
      loading: false
    }
  },
  methods: {
    handlerInitialize () {
      if (this.info) {
        this.title = `修改请求方式 [${this.info.name}]`
        this.formState = clone(this.info)
      } else {
        this.formState = new MethodEntity()
      }
    },
    handlerSave () {
      this.loading = true
      MethodService.saveOrUpdate(this.formState)
        .then(response => {
          if (response.code === 2000) {
            Message.success(`${this.title}成功`)
            this.handlerCancel()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },
    handlerCancel () {
      this.visible = false
    }
  },
  computed: {
    visible: {
      get (): boolean {
        return this.isVisible
      },
      set (value: boolean) {
        this.$emit('close', value)
      }
    }
  }
})
</script>
<style scoped>
.basic-drawer-footer {
  width: 100%;
  position: absolute;
  bottom: 0;
  left: 0;
  border-top: 1px solid #e8e8e8;
  padding: 10px 16px;
  text-align: right;
  background: #fff;
}
</style>
