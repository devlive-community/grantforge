<template>
  <Modal v-model="visible"
         width="40%"
         :mask-closable="false"
         @cancel="handlerCancel">
    <template #header>
      <font-awesome-icon :icon="['fas', 'location-arrow']"/>
      分配权限
    </template>
    <div>
      <Space direction="vertical" type="flex">
        <CheckboxGroup v-model="assignedRoles">
          <Tooltip v-for="role in data?.content"
                   v-bind:key="role.id"
                   :content="role.description">
            <Checkbox border
                      :label="role.id">
              {{ role.name }}
            </Checkbox>
          </Tooltip>
        </CheckboxGroup>
      </Space>
      <Spin size="large"
            fix
            :show="loading">
      </Spin>
    </div>
    <template #footer>
      <Button type="primary"
              size="small"
              :loading="assignedLoading"
              @click="handlerAssign">
        分配权限
      </Button>
    </template>
  </Modal>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { PageEntity, PageResponseEntity } from '@/entity/PageEntity'
import RoleService from '@/services/RoleService'
import { UserEntity } from '@/entity/UserEntity'
import UserService from '@/services/UserService'
import { AssignRoleEntity } from '@/entity/RoleEntity'
import { Message } from 'view-ui-plus'

export default defineComponent({
  name: 'UserAssignRole',
  props: {
    isVisible: {
      type: Boolean,
      default: () => false
    },
    info: {
      type: UserEntity
    }
  },
  created () {
    this.page = new PageEntity()
    this.page.size = 0
    this.handlerInitialize()
  },
  data () {
    return {
      loading: false,
      assignedRoles: Array<number>(),
      assignedLoading: false,
      page: null as unknown as PageEntity,
      data: null as unknown as PageResponseEntity
    }
  },
  methods: {
    handlerInitialize () {
      this.loading = true
      this.assignedRoles = []
      this.info
        ?.roles
        ?.forEach(value => {
          this.assignedRoles.push(value.id as number)
        })
      RoleService.getAllByPage(this.page)
        .then(response => {
          this.data = response.data as PageResponseEntity
        })
        .finally(() => {
          this.loading = false
        })
    },
    handlerAssign () {
      this.assignedLoading = true
      const configure: AssignRoleEntity = new AssignRoleEntity()
      configure.id = this.info?.id
      configure.values = this.assignedRoles
      UserService.assignRole(configure)
        .then(response => {
          if (response.code === 2000) {
            Message.success('分配成功')
            this.handlerCancel()
          }
        })
        .finally(() => {
          this.assignedLoading = false
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
