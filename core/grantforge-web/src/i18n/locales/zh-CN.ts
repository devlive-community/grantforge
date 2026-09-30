// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Simplified Chinese messages. This dictionary defines the message schema: every other locale must
 * provide exactly the same keys (enforced by the Messages type and script/ci/check_i18n_keys.py).
 */
const zhCN = {
  common: {
    retry: '重试',
    reload: '重新加载',
    loadFailed: '数据暂时无法加载',
    emptyTitle: '暂无数据',
    emptyDescription: '创建第一条记录，开始管理你的工作空间。',
  },
  status: { active: '正常', inactive: '已停用', locked: '已锁定' },
  pagination: {
    total: '共 {count} 条记录',
    perPage: '每页记录数',
    perPageOption: '{size} 条 / 页',
    previous: '上一页',
    next: '下一页',
  },
  controls: {
    dismissToast: '关闭提示',
    closeDialog: '关闭对话框',
    increase: '增大{label}',
    decrease: '减小{label}',
    selectPlaceholder: '请选择',
    selectEmpty: '暂无可选项',
  },
  titles: {
    app: '权限工作台',
    dashboard: '概览',
    users: '用户管理',
    roles: '角色管理',
    menus: '菜单管理',
    methods: '请求方式',
    json: 'JSON 工作台',
    forbidden: '暂无访问权限',
    network: '连接失败',
  },
  layout: {
    skipToContent: '跳转到内容',
    mainNavigation: '主导航',
    openNavigation: '打开导航',
    closeNavigation: '关闭导航',
    groupWorkspace: '工作空间',
    groupAccess: '访问控制',
    groupTools: '开发工具',
    workspaceName: '权限工作空间',
    promoTitle: '构建更清晰的权限边界',
    promoText: '从用户到角色，让每一次授权都有据可依。',
    readDocs: '阅读使用文档',
    breadcrumbRoot: '工作空间',
    quickJump: '快速跳转',
    quickJumpDescription: '搜索工作空间中的页面',
    searchPages: '搜索页面',
    searchPlaceholder: '输入页面名称…',
    noMatchingPages: '没有匹配的页面',
    lightTheme: '切换浅色主题',
    darkTheme: '切换深色主题',
    switchLanguage: 'Switch to English',
    logout: '退出登录',
  },
  errors: {
    network: '无法连接服务，请检查网络后重试',
    unauthorized: '登录已失效，请重新登录',
    forbidden: '你没有执行此操作的权限',
    status: '请求失败（{status}）',
    unavailable: '服务暂时不可用，请稍后重试',
    unavailableWithId: '服务暂时不可用，请稍后重试（请求编号 {id}）',
    tooManyOptions: '可选项数量过多，请联系管理员缩小数据范围',
    generic: '操作失败，请稍后重试',
    navigation: '导航权限暂未加载，可重新获取',
    missingToken: '登录响应缺少有效令牌',
  },
}

/** Shape of every locale dictionary. */
export type Messages = typeof zhCN

export default zhCN
