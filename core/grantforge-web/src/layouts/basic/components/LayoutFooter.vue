<template>
  <div style="background-color: #E6E8EB; padding: 0">
    <Footer>
      <GlobalFooter :links="links" :copyright="copyright"/>
    </Footer>
  </div>
</template>
<script lang="ts">
export default {
  name: 'FastDocLayoutFooter',
  data () {
    return {
      links: [
        {
          key: '文档',
          title: '文档',
          href: 'https://authx.devlive.org',
          blankTarget: true
        },
        {
          key: 'github',
          icon: 'logo-github',
          href: 'https://github.com/devlive-community/authx',
          blankTarget: true
        },
        {
          key: '条款',
          title: '条款',
          href: '',
          blankTarget: true
        }
      ],
      copyright: 'Copyright © 2023 Devlive Community All Rights Reserved'
    }
  }
}
</script>
