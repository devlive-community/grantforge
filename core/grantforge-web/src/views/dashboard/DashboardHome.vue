<template>
  <div class="home">
    <Card dis-hover>
      <template #title>
        欢迎使用 GrantForge 权限管理系统。
      </template>
      <Row>
        <Col v-for="item in items"
             v-bind:key="item.title"
             span="6">
          <NumberInfo :sub-title="item.title"
                      :total="item.value"
                      status="up">
          </NumberInfo>
        </Col>
      </Row>
    </Card>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import OverviewService from '@/services/OverviewService'

export default defineComponent({
  name: 'DashboardView',
  data () {
    return {
      items: []
    }
  },
  created () {
    this.handlerInitialize()
  },
  methods: {
    handlerInitialize () {
      OverviewService.getSummary()
        .then(response => {
          this.items = response.data
        })
    }
  }
})
</script>
