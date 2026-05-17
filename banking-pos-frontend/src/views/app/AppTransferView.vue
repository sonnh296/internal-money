<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import {
  getMyAccountApi,
  lookupAccountApi,
  transferApi,
} from "../../api/account.api";
import { useApiAction } from "../../composables/useApiAction";
import type {
  AccountLookupResponse,
  AccountResponse,
} from "../../types/api.types";
import MoneyInput from "../../components/MoneyInput.vue";

const { run, running } = useApiAction();

const myAccount = ref<AccountResponse | null>(null);
const lookup = ref<AccountLookupResponse | null>(null);
const lookupState = ref<"idle" | "loading" | "found" | "not-found">("idle");
let lookupTimer: ReturnType<typeof setTimeout> | null = null;

const form = reactive({
  toAccountNumber: "",
  amount: 10,
  reason: "Chuyển khoản nội bộ",
});

const sourceAccountNumber = computed(
  () => myAccount.value?.accountNumber ?? "",
);
const totalBalance = computed(() => Number(myAccount.value?.balance ?? 0))
const heldAmount = computed(() => Number(myAccount.value?.totalHolds ?? 0))
const availableBalance = computed(() => {
  const avail = myAccount.value?.availableBalance
  if (avail != null && Number.isFinite(Number(avail))) return Number(avail)
  return totalBalance.value - heldAmount.value
})
const isSelfTransfer = computed(
  () =>
    sourceAccountNumber.value &&
    form.toAccountNumber.trim() === sourceAccountNumber.value,
);
const canSubmit = computed(() => {
  if (
    !myAccount.value ||
    !form.toAccountNumber.trim() ||
    lookupState.value !== "found"
  )
    return false;
  if (form.amount <= 0 || form.amount > availableBalance.value) return false;
  if (isSelfTransfer.value) return false;
  return Boolean(form.reason.trim());
});

async function loadAccount() {
  try {
    const resp = await run("Lấy tài khoản", () => getMyAccountApi(), {
      silent: true,
    });
    myAccount.value = resp.data as AccountResponse;
  } catch {
    myAccount.value = null;
  }
}

async function runLookup(accountNumber: string) {
  const normalized = accountNumber.trim();
  if (normalized.length < 4) {
    lookup.value = null;
    lookupState.value = "idle";
    return;
  }
  if (normalized === sourceAccountNumber.value) {
    lookup.value = null;
    lookupState.value = "not-found";
    return;
  }
  lookupState.value = "loading";
  try {
    const resp = await run("Tra cứu STK", () => lookupAccountApi(normalized), {
      silent: true,
    });
    lookup.value = resp.data as AccountLookupResponse;
    lookupState.value = "found";
  } catch {
    lookup.value = null;
    lookupState.value = "not-found";
  }
}

watch(
  () => form.toAccountNumber,
  (value) => {
    if (lookupTimer) clearTimeout(lookupTimer);
    lookupTimer = setTimeout(() => runLookup(value), 1000);
  },
);

async function submitTransfer() {
  if (!canSubmit.value) return;
  await run(
    "Chuyển khoản nội bộ",
    () =>
      transferApi(
        {
          toAccountNumber: form.toAccountNumber.trim(),
          amount: form.amount,
          reason: form.reason.trim(),
        },
        `transfer-${Date.now()}`,
      ),
    { successToast: "Chuyển khoản thành công." },
  );
  await loadAccount();
  form.toAccountNumber = "";
  lookup.value = null;
  lookupState.value = "idle";
  form.amount = 10;
}

onMounted(loadAccount);
</script>

<template>
  <div class="stack">
    <section class="card">
      <h2>Chuyển khoản nội bộ</h2>
      <p v-if="myAccount" class="hint">
        Tài khoản nguồn: <span class="kbd">{{ sourceAccountNumber }}</span>
        · Số dư khả dụng:
        <strong>{{ availableBalance.toLocaleString('vi-VN') }} {{ myAccount.currency }}</strong>
        <span v-if="heldAmount > 0" class="muted">
          (tổng số dư {{ totalBalance.toLocaleString('vi-VN') }}, đang giữ {{ heldAmount.toLocaleString('vi-VN') }} do thanh toán chờ xử lý)
        </span>
      </p>
      <p v-else class="hint">
        Chưa có tài khoản nguồn. Vui lòng hoàn tất KYC và kích hoạt tài khoản.
      </p>
    </section>

    <section class="card" v-if="myAccount">
      <div class="form-grid">
        <label
          >Số tài khoản người nhận
          <input
            v-model="form.toAccountNumber"
            placeholder="Ví dụ: 912345678"
          />
        </label>
        <label
          >Số tiền
          <MoneyInput
            v-model="form.amount"
            allow-decimals
            placeholder="VD: 100.000"
          />
        </label>
        <label
          >Lý do
          <input v-model="form.reason" />
        </label>
      </div>

      <div class="hint" v-if="lookupState === 'loading'">
        Đang tra cứu người nhận…
      </div>
      <div class="hint" v-else-if="lookupState === 'found' && lookup">
        Người nhận: <strong>{{ lookup.displayName }}</strong> ·
        {{ lookup.currency }} · {{ lookup.status }}
      </div>
      <div
        class="hint danger"
        v-else-if="lookupState === 'not-found' && form.toAccountNumber.trim()"
      >
        Không tìm thấy tài khoản hoặc số tài khoản không hợp lệ.
      </div>
      <div class="hint danger" v-if="isSelfTransfer">
        Không thể chuyển cho chính tài khoản nguồn.
      </div>
      <div class="hint danger" v-if="myAccount && form.amount > availableBalance">
        Số tiền vượt quá số dư khả dụng<span v-if="heldAmount > 0">
          ({{ heldAmount.toLocaleString('vi-VN') }} {{ myAccount.currency }} đang chờ xử lý thanh toán)</span>.
      </div>

      <div class="actions">
        <button :disabled="running || !canSubmit" @click="submitTransfer">
          Chuyển khoản
        </button>
      </div>
    </section>
  </div>
</template>
