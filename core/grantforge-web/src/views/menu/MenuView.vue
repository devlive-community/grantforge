<template>
  <div>
    <Table :loading="loading"
           :columns="headers"
           :data="data?.content">
      <template #method="{ row }">
        <Tooltip v-for="item in row.methods"
                 v-bind:key="item.id"
                 max-width="300"
                 transfer
                 :content="item.description">
          <Tag>
            {{ item.name }}
          </Tag>
        </Tooltip>
      </template>
      <template #type="{ row }">
        <Tag color="primary">
          {{ row.type.name }}
        </Tag>
      </template>
      <template #newd="{ row }">
        <Switch v-model="row.newd"
                disabled>
        </Switch>
      </template>
      <template #active="{ row }">
        <Switch v-model="row.active"
                disabled>
        </Switch>
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
  </div>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { PageEntity, PageResponseEntity } from '@/entity/PageEntity'
import MenuService from '@/services/MenuService'
import MenuUtils from './MenuUtils'

export default defineComponent({
  name: 'MenuView',
  created () {
    this.page = new PageEntity()
    this.handlerInitialize()
  },
  data () {
    return {
      page: null as unknown as PageEntity,
      loading: false,
      headers: MenuUtils.headers,
      data: null as unknown as PageResponseEntity
    }
  },
  methods: {
    handlerInitialize () {
      this.loading = true
      MenuService.getAllByPage(this.page)
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
    }
  }
})
</script>
