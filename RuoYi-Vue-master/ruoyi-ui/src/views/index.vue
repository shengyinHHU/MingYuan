<template>
  <div class="app-container home">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <h1>名远教育教务管理平台</h1>
        <p class="subtitle">欢迎回来，{{ name || '管理员' }}！这里是教务运营的核心工作台。</p>
        <p class="desc">
          名远教育聚焦 K12 课外辅导，提供排课、可视化课表、教室、报名、考勤、资料等一站式教务管理；
          配合微信小程序面向家长提供课程报名与资料购买服务。
        </p>
      </div>
      <div class="welcome-logo">
        <i class="el-icon-school"></i>
      </div>
    </div>

    <!-- 教务功能入口 -->
    <div class="section">
      <div class="section-title">教务功能</div>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="8" :md="6" :lg="6" v-for="item in eduModules" :key="item.path">
          <div class="entry-card" @click="goTo(item.path)">
            <i :class="item.icon"></i>
            <div class="entry-name">{{ item.name }}</div>
            <div class="entry-desc">{{ item.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 系统管理入口 -->
    <div class="section">
      <div class="section-title">系统管理</div>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="8" :md="6" :lg="6" v-for="item in sysModules" :key="item.path">
          <div class="entry-card mini" @click="goTo(item.path)">
            <i :class="item.icon"></i>
            <div class="entry-name">{{ item.name }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 外部链接 -->
    <div class="section">
      <div class="section-title">快捷链接</div>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" v-for="item in links" :key="item.name">
          <div class="link-card" @click="goTarget(item.url, item.name)">
            <i :class="item.icon"></i>
            <div class="link-info">
              <div class="link-name">{{ item.name }}</div>
              <div class="link-url">{{ item.url || '联系管理员获取' }}</div>
            </div>
            <i class="el-icon-arrow-right link-arrow"></i>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
export default {
  name: "Index",
  data() {
    return {
      // 教务功能快捷入口（路由路径对应 sys_menu 中 parent=系统工具 下的菜单）
      eduModules: [
        { name: '可视化课表', desc: '矩阵式排课管理', path: '/tool/courseTimetable', icon: 'el-icon-date' },
        { name: '课程排课', desc: '课程班次管理', path: '/tool/schedule', icon: 'el-icon-s-order' },
        { name: '教室信息', desc: '校区教室维护', path: '/tool/classroom', icon: 'el-icon-office-building' },
        { name: '课程报名', desc: '家长报名记录', path: '/tool/enrollment', icon: 'el-icon-document-add' },
        { name: '上课记录', desc: '考勤与上课', path: '/tool/attendance', icon: 'el-icon-tickets' },
        { name: '资料管理', desc: '教师资料上架', path: '/tool/material', icon: 'el-icon-folder-opened' },
        { name: '资料订单', desc: '家长购买订单', path: '/tool/materialOrder', icon: 'el-icon-s-goods' },
        { name: '通知公告', desc: '发布机构通知', path: '/system/notice', icon: 'el-icon-bell' }
      ],
      sysModules: [
        { name: '用户管理', path: '/system/user', icon: 'el-icon-user' },
        { name: '角色管理', path: '/system/role', icon: 'el-icon-s-check' },
        { name: '字典管理', path: '/system/dict', icon: 'el-icon-collection' },
        { name: '菜单管理', path: '/system/menu', icon: 'el-icon-menu' }
      ],
      // 外部链接（URL 为占位，请按需替换为机构实际地址）
      links: [
        { name: '机构官网', url: 'https://www.mingyuanedu.com', icon: 'el-icon-link' },
        { name: '教师资源平台', url: 'https://www.mingyuanedu.com/teacher', icon: 'el-icon-reading' },
        { name: '家长服务小程序', url: '', icon: 'el-icon-mobile' }
      ]
    }
  },
  computed: {
    name() {
      return this.$store.getters && this.$store.getters.name
    }
  },
  methods: {
    // 内部路由跳转
    goTo(path) {
      this.$router.push(path).catch(() => {})
    },
    // 外链新窗口打开；空 url 给出提示
    goTarget(href, name) {
      if (!href) {
        this.$message.info('「' + name + '」地址未配置，请联系管理员补充')
        return
      }
      window.open(href, "_blank")
    }
  }
}
</script>

<style scoped lang="scss">
.home {
  font-family: "open sans", "Helvetica Neue", Helvetica, Arial, sans-serif;
  color: #303133;

  .welcome-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 24px;
    margin-bottom: 20px;
    border-radius: 10px;
    background: linear-gradient(135deg, #0f58a8 0%, #2d4a9e 50%, #4a86d8 100%);
    color: #fff;
    box-shadow: 0 8px 20px rgba(15, 88, 168, 0.3);
    position: relative;
    overflow: hidden;
    
    &::before {
      content: '';
      position: absolute;
      top: -50%;
      right: -10%;
      width: 200px;
      height: 200px;
      background: radial-gradient(circle, rgba(255,255,255,0.12) 0%, transparent 70%);
      border-radius: 50%;
    }
    &::after {
      content: '';
      position: absolute;
      bottom: -30%;
      left: 20%;
      width: 150px;
      height: 150px;
      background: radial-gradient(circle, rgba(255,255,255,0.08) 0%, transparent 70%);
      border-radius: 50%;
    }

    .welcome-text {
      flex: 1;
      z-index: 1;
      h1 { 
        margin: 0 0 8px; 
        font-size: 22px; 
        font-weight: 700;
        text-shadow: 0 2px 4px rgba(0,0,0,0.1);
      }
      .subtitle { 
        margin: 0 0 4px; 
        font-size: 14px; 
        opacity: 0.95;
        font-weight: 500;
      }
      .desc { 
        margin: 0; 
        font-size: 13px; 
        opacity: 0.9; 
        line-height: 1.6; 
        max-width: 600px;
      }
    }
    .welcome-logo {
      z-index: 1;
      i { 
        font-size: 56px; 
        opacity: 0.9;
        filter: drop-shadow(0 4px 8px rgba(0,0,0,0.2));
      }
    }
  }

  .section { margin-bottom: 20px; }

  .section-title {
    margin-bottom: 12px;
    padding-left: 10px;
    font-size: 16px;
    font-weight: 700;
    color: #303133;
    border-left: 4px solid #0f58a8;
    position: relative;
    &::after {
      content: '';
      position: absolute;
      bottom: -3px;
      left: 10px;
      width: 24px;
      height: 2px;
      background: linear-gradient(90deg, #4a86d8, transparent);
      border-radius: 2px;
    }
  }

  .entry-card {
    padding: 12px 10px;
    margin-bottom: 8px;
    border: none;
    border-radius: 6px;
    background: #fff;
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    position: relative;
    overflow: hidden;
    text-align: center;
    
    i { 
      font-size: 20px; 
      color: #0f58a8;
      background: linear-gradient(135deg, #e6f0fa 0%, #f0f5fb 100%);
      padding: 6px;
      border-radius: 6px;
      display: inline-block;
      margin-bottom: 6px;
    }
    .entry-name { 
      margin-top: 0; 
      font-size: 13px; 
      font-weight: 600;
      color: #2c3e50;
    }
    .entry-desc { 
      margin-top: 2px; 
      font-size: 11px; 
      color: #7f8c8d;
    }
    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 20px rgba(15, 88, 168, 0.12);
      &::after {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        width: 3px;
        height: 100%;
        background: linear-gradient(180deg, #0f58a8, #4a86d8);
      }
      i {
        transform: scale(1.1);
        transition: transform 0.3s ease;
      }
    }
    &.mini {
      padding: 14px 12px;
      i { 
        font-size: 20px;
        padding: 6px;
        margin-bottom: 6px;
      }
      .entry-name { margin-top: 4px; font-size: 13px; }
    }
  }

  .link-card {
    display: flex;
    align-items: center;
    padding: 12px 14px;
    margin-bottom: 10px;
    border: none;
    border-radius: 8px;
    background: #fff;
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
    
    > i:first-child { 
      font-size: 20px; 
      color: #0f58a8; 
      margin-right: 12px;
      background: linear-gradient(135deg, #e6f0fa 0%, #f0f5fb 100%);
      padding: 6px;
      border-radius: 6px;
    }
    .link-info { flex: 1; min-width: 0; }
    .link-name { font-size: 13px; font-weight: 600; color: #2c3e50; }
    .link-url { 
      margin-top: 2px; 
      font-size: 12px; 
      color: #7f8c8d; 
      overflow: hidden; 
      text-overflow: ellipsis; 
      white-space: nowrap; 
    }
    .link-arrow { 
      color: #bdc3c7; 
      transition: all 0.3s ease;
      font-size: 14px;
    }
    &:hover {
      transform: translateX(3px);
      box-shadow: 0 6px 16px rgba(15, 88, 168, 0.1);
      border-left: 3px solid #0f58a8;
      .link-arrow {
        color: #0f58a8;
        transform: translateX(3px);
      }
    }
  }
}
</style>
