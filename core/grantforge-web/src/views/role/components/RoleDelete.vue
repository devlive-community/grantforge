<template>
  <Modal v-model="visible"
         width="30%"
         :mask-closable="false">
    <template #header>
      <font-awesome-icon :icon="['fas', 'trash']"/>
      删除路由 [{{ info.code }}]
    </template>
    <div>
      <Space direction="vertical" type="flex">
        <span>
          要确认，请在下面的框中输入 <Tag color="error">{{ info.code }}</Tag>
        </span>
        <Input v-model="confirmDelete"
               placeholder="请输入要删除的路由编码">
        </Input>
      </Space>
    </div>
    <template #footer>
      <Button type="error"
              size="long"
              :disabled="confirmDelete !== info?.code"
              :loading="loading"
              @click="handlerDelete">
        删除
      </Button>
    </template>
  </Modal>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { Message } from 'view-ui-plus'
import { RoleEntity } from '@/entity/RoleEntity'
import RoleService from '@/services/RoleService'

export default defineComponent({
  name: 'RoleDelete',
  props: {
    isVisible: {
      type: Boolean,
      default: () => false
    },
    info: {
      type: RoleEntity
    }
  },
  data () {
    return {
      loading: false,
      confirmDelete: null as unknown as string
    }
  },
  methods: {
    handlerDelete () {
      this.loading = true
      RoleService.delete(this.info?.id as number)
        .then(response => {
          if (response.code === 2000) {
            Message.success('删除成功')
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
