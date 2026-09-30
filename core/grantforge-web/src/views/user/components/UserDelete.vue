<template>
  <Modal v-model="visible"
         width="30%"
         title="分配用户权限"
         :mask-closable="false">
    <template #header>
      <font-awesome-icon :icon="['fas', 'trash']"/>
      删除用户
    </template>
    <div>
      <Space direction="vertical" type="flex" align="center">
        要确认，请在下面的框中输入
        <Title level="5">
          {{ info.name }}
        </Title>
        <Input v-model="confirmDeleteUser"
               placeholder="请输入要删除的用户名">
        </Input>
      </Space>
    </div>
    <template #footer>
      <Button type="error"
              size="long"
              :disabled="confirmDeleteUser !== info?.name"
              :loading="deleteLoading"
              @click="handlerDelete">
        删除
      </Button>
    </template>
  </Modal>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { UserEntity } from '@/entity/UserEntity'
import UserService from '@/services/UserService'
import { Message } from 'view-ui-plus'

export default defineComponent({
  name: 'UserDelete',
  props: {
    isVisible: {
      type: Boolean,
      default: () => false
    },
    info: {
      type: UserEntity
    }
  },
  data () {
    return {
      deleteLoading: false,
      confirmDeleteUser: null as unknown as string
    }
  },
  methods: {
    handlerDelete () {
      this.deleteLoading = true
      const configure: UserEntity = new UserEntity()
      configure.id = this.info?.id
      UserService.delete(configure)
        .then(response => {
          if (response.code === 2000) {
            Message.success('删除成功')
            this.handlerCancel()
          }
        })
        .finally(() => {
          this.deleteLoading = false
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
