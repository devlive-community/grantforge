<template>
  <Modal v-model="visible"
         width="60%"
         :mask-closable="false"
         @cancel="handlerCancel">
    <template #header>
      <font-awesome-icon :icon="['fas', 'location-arrow']"/>
      分配菜单 [{{ info.name }}]
    </template>
    <div>
      <Tree :data="assignedMenus"
            show-checkbox>
      </Tree>
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
        分配菜单
      </Button>
    </template>
  </Modal>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import RoleService from '@/services/RoleService'
import { AssignMenuEntity } from '@/entity/RoleEntity'
import { Message } from 'view-ui-plus'
import { MenuEntity } from '@/entity/MenuEntity'
import { RecursionUtils } from '@/utils/RecursionUtils'

export default defineComponent({
  name: 'RoleAssignMenu',
  props: {
    isVisible: {
      type: Boolean,
      default: () => false
    },
    info: {
      type: MenuEntity
    }
  },
  created () {
    this.handlerInitialize()
  },
  data () {
    return {
      loading: false,
      assignedMenus: Array<MenuEntity>(),
      assignedLoading: false
    }
  },
  methods: {
    handlerInitialize () {
      this.loading = true
      RoleService.getMenusByRole(this.info?.id as number)
        .then(response => {
          this.assignedMenus = response.data
        })
        .finally(() => {
          this.loading = false
        })
    },
    handlerAssign () {
      this.assignedLoading = true
      const configure: AssignMenuEntity = new AssignMenuEntity()
      configure.roleId = this.info?.id
      const menus = new Array<number>()
      RecursionUtils.getAllCheckedNodes(this.assignedMenus as [])
        .forEach(value => {
          if (value.checked) {
            menus.push(value.id as number)
          }
        })
      configure.menus = menus
      RoleService.assignMenu(configure)
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
