<template>
  <div class="login_container">
    <div class="login_box">
      <div class="avatar_box">
        <Avatar
          size="80"
          src="/static/images/grantforge-logo.png">
        </Avatar>
      </div>
      <div class="login_form">
        <Login ref="form" @on-submit="handlerSubmit">
          <UserName name="username"/>
          <Poptip trigger="focus"
                  placement="right"
                  width="240">
            <Password name="password"
                      :rules="passwordRule"
                      placeholder="至少6位密码，区分大小写"
                      @on-change="handlerChangePassword">
            </Password>
            <template #content>
              <div class="register-tip">
                <div class="register-tip-title"
                     :class="passwordTip.class">
                  强度：{{ passwordTip.strong }}
                </div>
                <Progress hide-info
                          :percent="passwordTip.percent"
                          :stroke-width="6"
                          :stroke-color="passwordTip.color">
                </Progress>
                <div class="register-tip-desc">
                  请至少输入 6 个字符。请不要使用容易被猜到的密码。
                </div>
              </div>
            </template>
          </Poptip>
          <Password name="passwordConfirm"
                    placeholder="确认密码"
                    :rules="passwordConfirmRule">
          </Password>
          <div class="login_btn">
            <Space>
              <Button size="large" to="/auth/login">登录</Button>
              <Submit>注册</Submit>
            </Space>
          </div>
        </Login>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { UserEntity } from '@/entity/UserEntity'
import UserService from '@/services/UserService'
import { Message } from 'view-ui-plus'

export default {
  data () {
    const validatePassCheck = (rule: any, value: string, callback: any) => {
      // @ts-ignore
      if (value !== this.$refs.form.formValidate.password) {
        callback(new Error('两次输入的密码不匹配！'))
      } else {
        callback()
      }
    }
    return {
      passwordRule: [
        {
          required: true, message: '密码不能为空！', trigger: 'change'
        },
        {
          min: 6, message: '密码不能少于6位！', trigger: 'change'
        }
      ],
      passwordConfirmRule: [
        {
          required: true, message: '确认密码不能为空！', trigger: 'change'
        },
        { validator: validatePassCheck, trigger: 'change' }
      ],
      passwordLen: 0
    }
  },
  computed: {
    passwordTip (): any {
      let strong = '强'
      let className = 'strong'
      // @ts-ignore
      const percent = this.passwordLen > 10 ? 10 : this.passwordLen
      let color = '#19be6b'
      // @ts-ignore
      if (this.passwordLen < 6) {
        strong = '太短'
        className = 'low'
        color = '#ed4014'
        // @ts-ignore
      } else if (this.passwordLen < 10) {
        strong = '中'
        className = 'medium'
        color = '#ff9900'
      }
      return {
        strong,
        // @ts-ignore
        class: 'register-tip-' + this.passwordLen < 6 ? 'low' : (this.passwordLen < 10 ? 'medium' : 'strong'),
        percent: percent * 10,
        color
      }
    }
  },
  methods: {
    handlerChangePassword (val: string) {
      // @ts-ignore
      this.passwordLen = val.length
    },
    handlerSubmit (valid: any, { username, password }: any) {
      if (valid) {
        const user = new UserEntity()
        user.username = username
        user.password = password
        UserService.saveOrUpdate(user)
          .then(response => {
            if (response.code === 2000) {
              Message.success('注册成功')
            }
          })
      }
    }
  }
}
</script>

<style scoped>
.login_container {
  height: 75vh;
}

.login_box {
  width: 450px;
  height: 310px;
  background-color: #ecf5ff;
  border-radius: 20px;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);

  .avatar_box {
  //border: 1px solid #eee; border-radius: 50%; padding: 10px; //box-shadow: 0 0 1px #ddd; position: absolute; left: 50%; transform: translate(-50%, -50%);
  }
}

.login_form {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 20px;
  box-sizing: border-box;
}

.login_btn {
  display: flex;
  justify-content: flex-end;
}

.register {
  margin: 0 auto !important;
}

.register .ivu-poptip, .register .ivu-poptip-rel {
  display: block;
}

.register-tip {
  text-align: left;
}

.register-tip-low {
  color: #ed4014;
}

.register-tip-medium {
  color: #ff9900;
}

.register-tip-strong {
  color: #19be6b;
}

.register-tip-title {
  font-size: 14px;
}

.register-tip-desc {
  white-space: initial;
  font-size: 14px;
  margin-top: 6px;
}
</style>
