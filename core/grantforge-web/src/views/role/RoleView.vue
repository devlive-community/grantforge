<template>
  <div>
    <Card dis-hover>
      <template #title>
        路由管理
      </template>
      <template #extra>
        <Tooltip content="添加路由">
          <Button type="primary"
                  shape="circle"
                  size="small"
                  @click="handlerDetailRole(true, undefined)">
            <font-awesome-icon :icon="['fas', 'plus']"/>
          </Button>
        </Tooltip>
      </template>
      <Table :loading="loading"
             :columns="headers"
             :data="data?.content">
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
                    @click="handlerAssignMenu(true, row)">
              <Tooltip content="分配菜单"
                       transfer>
                <font-awesome-icon :icon="['fas', 'location-arrow']"/>
              </Tooltip>
            </Button>
            <Button type="primary"
                    size="small"
                    shape="circle"
                    @click="handlerDetailRole(true, row)">
              <Tooltip content="修改路由"
                       transfer>
                <font-awesome-icon :icon="['fas', 'edit']"/>
              </Tooltip>
            </Button>
            <Button type="error"
                    size="small"
                    shape="circle"
                    @click="handlerDeleteRole(true, row)">
              <Tooltip content="删除路由"
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
    </Card>
    <RoleDetail v-if="detailRole.visible"
                :is-visible="detailRole.visible"
                :info="detailRole.info"
                @close="handlerDetailRole(false, undefined)">
    </RoleDetail>
    <RoleDelete v-if="deleteRole.visible"
                :is-visible="deleteRole.visible"
                :info="deleteRole.info"
                @close="handlerDeleteRole(false, undefined)">
    </RoleDelete>
    <RoleAssignMenu v-if="assignMenu.visible"
                    :is-visible="assignMenu.visible"
                    :info="assignMenu.info"
                    @close="handlerAssignMenu(false, undefined)">
    </RoleAssignMenu>
  </div>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { PageEntity, PageResponseEntity } from '@/entity/PageEntity'
import RoleUtils from '@/views/role/RoleUtils'
import RoleService from '@/services/RoleService'
import RoleDetail from '@/views/role/components/RoleDetail.vue'
import { RoleEntity } from '@/entity/RoleEntity'
import RoleDelete from '@/views/role/components/RoleDelete.vue'
import { MenuEntity } from '@/entity/MenuEntity'
import RoleAssignMenu from '@/views/role/components/RoleAssignMenu.vue'

export default defineComponent({
  name: 'RoleView',
  components: { RoleAssignMenu, RoleDelete, RoleDetail },
  created () {
    this.page = new PageEntity()
    this.handlerInitialize()
  },
  data () {
    return {
      page: null as unknown as PageEntity,
      loading: false,
      headers: RoleUtils.headers,
      data: null as unknown as PageResponseEntity,
      detailRole: {
        visible: false,
        info: null as unknown as RoleEntity
      },
      deleteRole: {
        visible: false,
        info: null as unknown as RoleEntity
      },
      assignMenu: {
        visible: false,
        info: null as unknown as MenuEntity
      }
    }
  },
  methods: {
    handlerInitialize () {
      this.loading = true
      RoleService.getAllByPage(this.page)
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
    handlerDetailRole (value: boolean, info?: RoleEntity) {
      this.detailRole.info = info as RoleEntity
      this.detailRole.visible = value
      if (value === false) {
        this.handlerInitialize()
      }
    },
    handlerDeleteRole (value: boolean, info?: RoleEntity) {
      this.deleteRole.info = info as RoleEntity
      this.deleteRole.visible = value
      if (value === false) {
        this.handlerInitialize()
      }
    },
    handlerAssignMenu (value: boolean, info?: MenuEntity) {
      this.assignMenu.info = info as MenuEntity
      this.assignMenu.visible = value
      if (value === false) {
        this.handlerInitialize()
      }
    }
  }
})
</script>
