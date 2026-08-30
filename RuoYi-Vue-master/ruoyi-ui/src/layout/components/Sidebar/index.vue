<template>
    <div :class="['sidebar-theme-wrapper', {'has-logo':showLogo}, settings.sideTheme]" :style="{ backgroundColor: settings.sideTheme === 'theme-dark' ? variables.menuBackground : variables.menuLightBackground }">
        <logo v-if="showLogo" :collapse="isCollapse" />
        <el-scrollbar :class="settings.sideTheme" wrap-class="scrollbar-wrapper">
            <el-menu
                class="custom-menu"
                :default-active="activeMenu"
                :collapse="isCollapse"
                :background-color="settings.sideTheme === 'theme-dark' ? variables.menuBackground : variables.menuLightBackground"
                :text-color="settings.sideTheme === 'theme-dark' ? variables.menuColor : variables.menuLightColor"
                :unique-opened="true"
                :active-text-color="'#0f58a8'"
                :collapse-transition="false"
                mode="vertical"
            >
                <sidebar-item
                    v-for="(route, index) in sidebarRouters"
                    :key="route.path  + index"
                    :item="route"
                    :base-path="route.path"
                />
            </el-menu>
        </el-scrollbar>
    </div>
</template>

<script>
import { mapGetters, mapState } from "vuex"
import Logo from "./Logo"
import SidebarItem from "./SidebarItem"
import variables from "@/assets/styles/variables.scss"

export default {
    components: { SidebarItem, Logo },
    computed: {
        ...mapState(["settings"]),
        ...mapGetters(["sidebarRouters", "sidebar"]),
        activeMenu() {
            const route = this.$route
            const { meta, path } = route
            // if set path, the sidebar will highlight the path you set
            if (meta.activeMenu) {
                return meta.activeMenu
            }
            return path
        },
        showLogo() {
            return this.$store.state.settings.sidebarLogo
        },
        variables() {
            return variables
        },
        isCollapse() {
            return !this.sidebar.opened
        }
    }
}
</script>

<style scoped>
.custom-menu {
  border-right: none;
  transition: all 0.3s ease;
}
.custom-menu .el-menu-item,
.custom-menu .el-submenu__title {
  transition: all 0.3s ease;
  border-bottom: 3px solid transparent;
}
.custom-menu .el-menu-item:hover,
.custom-menu .el-submenu__title:hover {
  background-color: rgba(15, 88, 168, 0.05) !important;
  color: #0f58a8 !important;
}
.custom-menu .el-menu-item.is-active {
  background-color: rgba(15, 88, 168, 0.08) !important;
  border-bottom: 3px solid #0f58a8;
  font-weight: 600;
}
.sidebar-theme-wrapper.has-logo .logo-container {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
</style>
