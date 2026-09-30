<template>
  <div>
    <Card dis-hover>
      <template #title>
        JSON 美化工具
      </template>
      <template #extra>
        <Space>
          <Button size="small"
                  :loading="loading"
                  @click="handlerPretty">
            格式化
          </Button>
          <Button size="small"
                  :loading="loading"
                  @click="handlerCompression">
            压缩
          </Button>
        </Space>
      </template>
      <div>
        <Row>
          <Col span="12">
            <VAceEditor v-model:value="leftContent"
                        :options="{ useWorker: true }"
                        style="min-height: 400px">
            </VAceEditor>
          </Col>
          <Col span="12">
            <VAceEditor v-model:value="rightContent"
                        readonly
                        style="min-height: 400px">
            </VAceEditor>
          </Col>
        </Row>
        <Spin :show="loading"
              fix>
        </Spin>
      </div>
    </Card>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { VAceEditor } from 'vue3-ace-editor'
import JsonService from '@/services/JsonService'

export default defineComponent({
  name: 'JsonPrettyView',
  components: {
    VAceEditor
  },
  data () {
    return {
      leftContent: null as unknown as string,
      rightContent: null as unknown as string,
      loading: false
    }
  },
  methods: {
    handlerPretty () {
      this.loading = true
      JsonService.doPretty(this.leftContent)
        .then(response => {
          this.rightContent = response.data
        })
        .finally(() => {
          this.loading = false
        })
    },
    handlerCompression () {
      this.loading = true
      JsonService.doCompression(this.leftContent)
        .then(response => {
          this.rightContent = response.data
        })
        .finally(() => {
          this.loading = false
        })
    }
  }
})
</script>
