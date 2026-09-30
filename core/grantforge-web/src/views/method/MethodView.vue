<template>
  <div>
    <Card>
      <template #title>
        请求方式管理
      </template>
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
                    @click="handlerDetailRole(true, row)">
              <Tooltip content="修改请求方式"
                       transfer>
                <font-awesome-icon :icon="['fas', 'edit']"/>
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
    <MethodDetail v-if="detailMethod.visible"
                  :is-visible="detailMethod.visible"
                  :info="detailMethod.info"
                  @close="handlerDetailRole(false, null)">
    </MethodDetail>
  </div>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { PageEntity, PageResponseEntity } from '@/entity/PageEntity'
import MethodService from '@/services/MethodService'
import RoleUtils from '@/views/role/RoleUtils'
import { MethodEntity } from '@/entity/MethodEntity'
import MethodDetail from '@/views/method/components/MethodDetail.vue'

export default defineComponent({
  name: 'MethodView',
  components: { MethodDetail },
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
      detailMethod: {
        visible: false,
        info: null as unknown as MethodEntity
      }
    }
  },
  methods: {
    handlerInitialize () {
      this.loading = true
      MethodService.getAllByPage(this.page)
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
    handlerDetailRole (value: boolean, info?: MethodEntity) {
      this.detailMethod.info = info as MethodEntity
      this.detailMethod.visible = value
      if (value === false) {
        this.handlerInitialize()
      }
    }
  }
})
</script>
