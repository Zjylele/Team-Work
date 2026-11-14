<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search, User } from '@element-plus/icons-vue'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const query = ref('')
const selectedId = ref(session.account?.id)
const members = computed(() => session.members.filter(member =>
  member.displayName.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase()),
))
const selected = computed(() => members.value.find(member => member.id === selectedId.value) ?? members.value[0])

</script>

<template>
  <section class="directory" aria-label="团队通讯录">
    <aside class="directory-list">
      <h1>通讯录</h1>
      <el-input v-model="query" :prefix-icon="Search" placeholder="搜索团队成员" aria-label="搜索团队成员" clearable />
      <p>{{ members.length }} 位成员</p>
      <button v-for="member in members" :key="member.id" type="button" class="member"
        :aria-pressed="selected?.id === member.id" @click="selectedId = member.id">
        <span class="initial">{{ member.displayName.slice(0, 2) }}</span>
        <span>{{ member.displayName }}</span>
      </button>
      <p v-if="!members.length">没有匹配的成员</p>
    </aside>
    <div v-if="selected" class="profile">
      <header>
        <span class="initial">{{ selected.displayName.slice(0, 2) }}</span>
        <div>
          <h2>{{ selected.displayName }}</h2>
          <span>{{ session.team?.name }}</span>
        </div>
        <RouterLink v-if="selected.id === session.account?.id" to="/settings">编辑个人资料</RouterLink>
      </header>
      <section class="profile-details">
        <h3><el-icon><User /></el-icon> 成员资料</h3>
        <dl>
          <div><dt>显示名</dt><dd>{{ selected.displayName }}</dd></div>
          <div v-if="selected.role"><dt>团队角色</dt><dd>{{ selected.role === 'CREATOR' ? '创建者' : '成员' }}</dd></div>
        </dl>
      </section>
    </div>
  </section>
</template>

<style scoped>
.directory { display: grid; grid-template-columns: minmax(180px, 242px) minmax(0, 1fr); min-height: 450px; background: #fff; border: 1px solid #e3e5ea; border-radius: 8px; overflow: hidden; }
.directory-list { padding: 16px; border-right: 1px solid #e3e5ea; }
h1, h2, h3 { margin: 0 0 12px; }
h1, h3 { font-size: 15px; }
.directory-list p { font-size: 13px; }
.member { display: flex; align-items: center; gap: 10px; width: 100%; border: 0; border-radius: 5px; padding: 7px; background: transparent; text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.member:hover, .member[aria-pressed=true] { background: #edf2ff; }
.initial { display: grid; place-items: center; width: 30px; height: 30px; flex: none; border-radius: 50%; background: #d9f3e6; font-size: 11px; }
.profile header { display: flex; align-items: center; gap: 15px; padding: 28px 24px; background: linear-gradient(115deg, #eef3ff, #f0f9f5); overflow-wrap: anywhere; }
.profile header .initial { width: 52px; height: 52px; font-size: 16px; }
.profile header h2 { margin-bottom: 4px; font-size: 22px; }
.profile header a { margin-left: auto; }
.profile-details { padding: 24px; }
dl > div { display: grid; grid-template-columns: 90px minmax(0, 1fr); padding: 12px 0; border-bottom: 1px solid #e3e5ea; }
dd { margin: 0; }
@media (max-width: 620px) {
  .directory { grid-template-columns: minmax(0, 1fr); }
  .directory-list { border-right: 0; border-bottom: 1px solid #e3e5ea; }
  .profile header { flex-wrap: wrap; }
}
</style>
