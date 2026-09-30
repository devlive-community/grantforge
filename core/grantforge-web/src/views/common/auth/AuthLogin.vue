<template>
  <div class="h-screen flex items-center justify-center">
    <div class="w-full max-w-md px-4 mx-auto">
      <ShadcnCard class="w-full">
        <template #title>
          <div class='flex items-center justify-center'>
            <ShadcnAvatar src="/static/images/grantforge-logo.png" alt="GrantForge"/>
          </div>
        </template>

        <div class="px-6 py-8 relative">
          <ShadcnForm v-model="formState" @on-submit="onSubmit">
            <ShadcnFormItem name="username"
                            label="用户名"
                            :rules="[
                              { required: true, message: '请输入用户名！' }
                            ]">
              <ShadcnInput v-model="formState.username" name="username" placeholder="请输入用户名">
                <template #prefix>
                  <ShadcnIcon icon="User" size="18"/>
                </template>
              </ShadcnInput>
            </ShadcnFormItem>

            <ShadcnFormItem name="password"
                            label="密码"
                            :rules="[
                              { required: true, message: '请输入密码！' }
                            ]">
              <ShadcnInput v-model="formState.password" name="password" type="password" placeholder="请输入密码">
                <template #prefix>
                  <ShadcnIcon icon="Lock" size="18"/>
                </template>
              </ShadcnInput>
            </ShadcnFormItem>

            <ShadcnSpace wrap>
              <ShadcnButton class="w-full"
                            submit
                            :disabled="loading"
                            :loading="loading">
                登录
              </ShadcnButton>

              <ShadcnDivider class="text-sm text-gray-400 py-2"
                             orientation="center"
                             text="还没有用户？">
              </ShadcnDivider>

              <ShadcnButton class="w-full text-center"
                            type="default"
                            to="/auth/register">
                注册
              </ShadcnButton>
            </ShadcnSpace>
          </ShadcnForm>
        </div>
      </ShadcnCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getCurrentInstance, ref } from 'vue'
import AuthService from '@/services/auth'
import router from '@/router'

const { proxy } = getCurrentInstance()!

const loading = ref(false)
const formState = ref({
  username: null,
  password: null
})

const onSubmit = () => {
  AuthService.doAuth(formState.value)
             .then(response => {
               if (response?.data.code === 2000) {
                 AuthService.saveAuth(response?.data?.data)
                 setTimeout(() => {
                   router.push('/')
                 }, 200)
               }
             })
             .catch(error => {
               const message = error?.response ? error?.response?.data.message : error.message
               proxy?.$Message.error({
                 content: message,
                 showIcon: true
               })
             })
}
</script>
