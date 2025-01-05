<template>
  <div class="border-b">
    <div class="container">
      <div class="flex items-center">
        <div class="mr-3">
          <ShadcnLink link="/" class="pr-6 mt-1">
            <ShadcnAvatar src="/static/images/logo.png" alt="AuthX"/>
          </ShadcnLink>
        </div>

        <ShadcnLayoutHeader/>

        <ShadcnSpace>
          <div class="mr-3 mt-2.5 items-center">
            <ShadcnTooltip content="反馈" position="bottom">
              <ShadcnLink link="https://github.com/devlive-community/authx" external target="_blank">
                <ShadcnIcon icon="CircleHelp" :size="20"/>
              </ShadcnLink>
            </ShadcnTooltip>
          </div>

          <ShadcnSpace v-if="!isLogined">
            <ShadcnButton to="/auth/login">
              登录
            </ShadcnButton>

            <ShadcnButton to="/auth/register" type="default">
              注册
            </ShadcnButton>
          </ShadcnSpace>

          <div v-else>
            <ShadcnDropdown position="right">
              <template #trigger>
                <ShadcnAvatar :src="userInfo?.name" :alt="userInfo?.name"/>
              </template>

              <ShadcnDropdownItem>
                <div class="flex flex-col space-y-1">
                  <p class="text-sm font-medium leading-none text-center">{{ userInfo?.name }}</p>
                  <p class="text-xs leading-none text-muted-foreground"></p>
                </div>
              </ShadcnDropdownItem>

              <ShadcnDropdownItem @on-click="logout">
                <ShadcnSpace>
                  <ShadcnIcon icon="LogOut"/>
                  <span>退出</span>
                </ShadcnSpace>
              </ShadcnDropdownItem>
            </ShadcnDropdown>
          </div>
        </ShadcnSpace>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import router from '@/router'
import AuthService from '@/services/auth'

interface Props
{
  isLogined: boolean
  userInfo: any
}

withDefaults(defineProps<Props>(), {
  isLogined: false,
  userInfo: null
})

const logout = () => {
  AuthService.logout()
  router.push('/auth/login')
}
</script>
