<template>
  <Exception type="403">
    <template #actions>
      <Button type="primary"
              @click="handlerGoLogin">
        返回登录
      </Button>
    </template>
  </Exception>
</template>
<script>
import router from '@/router'
import SupportUtils from '@/utils/support'

export default {
  methods: {
    handlerGoLogin () {
      localStorage.removeItem(SupportUtils.token)
      localStorage.removeItem(SupportUtils.username)
      setTimeout(() => {
        router.push('/auth/login')
      }, 100)
    }
  }
}
</script>
