<template>
  <div class="min-h-screen">
    <ShadcnLayout>
      <LayoutHeader :is-logined="isLogined" :user-info="userInfo"/>

      <ShadcnLayoutWrapper>
        <ShadcnLayoutSider v-if="isLogined" collapsible :defaultCollapsed="false" trigger>
          <div class="space-y-4">
            <div class="flex items-center space-x-2">
              <ShadcnIcon icon="Home"/>
              <span>Home</span>
            </div>
          </div>

          <template #collapsed>
            <div class="space-y-4">
              <div class="flex items-center space-x-2">
                <ShadcnIcon icon="Home"/>
              </div>
            </div>
          </template>
        </ShadcnLayoutSider>
        <ShadcnLayoutMain>
          <ShadcnLayoutContent class="min-h-screen">
            <RouterView/>
          </ShadcnLayoutContent>
          <ShadcnLayoutFooter class="bg-blue-400 h-24">Footer</ShadcnLayoutFooter>
        </ShadcnLayoutMain>
      </ShadcnLayoutWrapper>
    </ShadcnLayout>
  </div>
</template>

<script lang="ts">
import AuthService from '@/services/auth'
import { defineComponent } from 'vue'
import UserService from '@/services/UserService'
import MenuService from '@/services/MenuService'
import { MenuEntity } from '@/entity/MenuEntity'
import LayoutHeader from '@/layouts/basic/components/LayoutHeader.vue'

export default defineComponent({
  name: 'LayoutContainer',
  components: { LayoutHeader },
  created()
  {
    this.handlerInitialize()
  },
  data()
  {
    return {
      isLogined: false,
      userInfo: { name: '', id: 0 },
      menus: Array<MenuEntity>()
    }
  },
  methods: {
    handlerInitialize()
    {
      const auth: string = AuthService.getAuth()
      const username: string = AuthService.getAuthUserName()
      if (auth) {
        this.isLogined = true
        UserService.getInfoByUserName(username)
                   .then(response => {
                     if (response.code === 2000) {
                       this.userInfo = response.data
                       this.handlerInitializeMenus()
                     }
                     else {
                       this.userInfo.name = username
                     }
                   })
      }
    },
    handlerInitializeMenus()
    {
      MenuService.getMenusByUser(this.userInfo.id)
                 .then(response => {
                   this.menus = response.data
                 })
    }
  }
})
</script>
