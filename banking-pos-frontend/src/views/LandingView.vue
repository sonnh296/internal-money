<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { RouterLink } from 'vue-router'
import { APP_ROUTES } from '../constants/app.constants'

function applyTheme() {
  document.body.classList.remove('theme-app', 'theme-admin')
  document.body.classList.add('theme-landing')
}

onMounted(applyTheme)
onBeforeUnmount(() => document.body.classList.remove('theme-landing'))
</script>

<template>
  <main class="landing">
    <div class="landing-inner">
      <header>
        <h1>MockBank · Banking POS</h1>
        <p class="lead">
          Hệ thống ngân hàng demo có hai cổng riêng biệt cho khách hàng và quản trị viên,
          dùng chung backend microservices.
        </p>
      </header>

      <section class="landing-picker">
        <article class="landing-card user">
          <h2>Cổng khách hàng</h2>
          <p>Dành cho người dùng cuối. Phù hợp khi bạn muốn dùng ứng dụng như một khách hàng thực.</p>
          <ul>
            <li>Đăng ký / Đăng nhập</li>
            <li>Xem tài khoản, số dư, hold</li>
            <li>Lịch sử giao dịch</li>
            <li>Quản lý nhà cung cấp và thanh toán hóa đơn</li>
            <li>Cập nhật hồ sơ cá nhân</li>
          </ul>
          <RouterLink :to="APP_ROUTES.APP_LOGIN" class="cta">Vào cổng khách hàng →</RouterLink>
        </article>

        <article class="landing-card admin">
          <h2>Cổng quản trị</h2>
          <p>Dành cho đội vận hành và kỹ sư backend. Toàn quyền cấu hình hệ thống và kiểm thử backend.</p>
          <ul>
            <li>Provision tài khoản đăng nhập (admin/user)</li>
            <li>Quản lý hồ sơ khách hàng + KYC</li>
            <li>Quản trị tài khoản: trạng thái, credit/debit, release hold</li>
            <li>Catalog nhà cung cấp toàn hệ thống</li>
            <li>Backend Testbench – chạy mọi kịch bản edge case</li>
            <li>API Playground và System Health</li>
          </ul>
          <RouterLink :to="APP_ROUTES.ADMIN_LOGIN" class="cta">Vào cổng quản trị →</RouterLink>
        </article>
      </section>

      <p class="hint" style="color:#64748b; font-size:12px;">
        Hai cổng hoàn toàn tách biệt: token, localStorage và navigation riêng. Bạn có thể đăng nhập đồng thời cả hai
        trên cùng trình duyệt mà không xung đột phiên.
      </p>
    </div>
  </main>
</template>
