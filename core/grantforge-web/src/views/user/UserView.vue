<template>
  <div>
    <Table :loading="loading"
           :columns="headers"
           :data="data?.content">
      <template #avatar="{ row }">
        <Avatar v-if="row.avatar"
                :src="row.avatar">
        </Avatar>
        <Avatar v-else>
          {{ row.name }}
        </Avatar>
      </template>
      <template #permission="{ row }">
        <Tooltip v-for="role in row.roles"
                 transfer
                 v-bind:key="role.name"
                 :content="role.description">
          <Tag color="primary">
            {{ role.name }}
          </Tag>
        </Tooltip>
      </template>
      <template #active="{ row }">
        <Switch v-model="row.active"
                disabled>
        </Switch>
      </template>
      <template #action="{ row }">
        <Space>
          <Button type="primary"
                  size="small"
                  shape="circle"
                  @click="handlerAssignRole(true, row)">
            <Tooltip content="分配权限"
                     transfer>
              <font-awesome-icon :icon="['fas', 'location-arrow']"/>
            </Tooltip>
          </Button>
          <Button type="error"
                  size="small"
                  shape="circle"
                  @click="handlerDeleteUser(true, row)">
            <Tooltip content="删除用户"
                     transfer>
              <font-awesome-icon :icon="['fas', 'trash']"/>
            </Tooltip>
          </Button>
        </Space>
      </template>
    </Table>
    <Page v-if="data?.content"
          show-sizer
          show-elevator
          show-total
          style="margin-top: 10px;"
          :modelValue="data.number"
          :total="data.totalElements"
          :page-size="data.size"
          @on-page-size-change="handlerSizeChange"
          @on-change="handlerIndexChange">
    </Page>
    <UserAssignRole v-if="assignRole.visible"
                    :is-visible="assignRole.visible"
                    :info="assignRole.info"
                    @close="handlerAssignRole(false, null)">
    </UserAssignRole>
    <UserDelete v-if="deleteUser.visible"
                :is-visible="deleteUser.visible"
                :info="deleteUser.info"
                @close="handlerDeleteUser(false, null)">
    </UserDelete>
  </div>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { PageEntity, PageResponseEntity } from '@/entity/PageEntity'
import UserService from '@/services/UserService'
import UserUtils from '@/views/user/UserUtils'
import UserAssignRole from '@/views/user/components/UserAssignRole.vue'
import { UserEntity } from '@/entity/UserEntity'
import UserDelete from '@/views/user/components/UserDelete.vue'

export default defineComponent({
  name: 'UserView',
  components: { UserDelete, UserAssignRole },
  created () {
    this.page = new PageEntity()
    this.handlerInitialize()
  },
  data () {
    return {
      page: null as unknown as PageEntity,
      loading: false,
      headers: UserUtils.headers,
      data: null as unknown as PageResponseEntity,
      assignRole: {
        visible: false,
        info: null as unknown as UserEntity
      },
      deleteUser: {
        visible: false,
        info: null as unknown as UserEntity
      }
    }
  },
  methods: {
    handlerInitialize () {
      this.loading = true
      UserService.getAllByPage(this.page)
        .then(response => {
          this.data = response.data
        })
        .finally(() => {
          this.loading = false
        })
    },
    handlerSizeChange (size: number) {
      this.page.size = size
      this.handlerInitialize()
    },
    handlerIndexChange (index: number) {
      this.page.page = index
      this.handlerInitialize()
    },
    handlerAssignRole (value: boolean, info?: UserEntity) {
      this.assignRole.info = info as UserEntity
      this.assignRole.visible = value
      if (value === false) {
        this.handlerInitialize()
      }
    },
    handlerDeleteUser (value: boolean, info?: UserEntity) {
      this.deleteUser.info = info as UserEntity
      this.deleteUser.visible = value
      if (value === false) {
        this.handlerInitialize()
      }
    }
  }
})
</script>
