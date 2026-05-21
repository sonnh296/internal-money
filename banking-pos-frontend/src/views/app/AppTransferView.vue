<script setup lang="ts">
import axios from "axios";
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import Decimal from "decimal.js-light";
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
import ConfirmModal from "../../components/ConfirmModal.vue";

const { run, running } = useApiAction();

const myAccount = ref<AccountResponse | null>(null);
const lookup = ref<AccountLookupResponse | null>(null);
const lookupState = ref<"idle" | "loading" | "found" | "not-found">("idle");
let lookupTimer: ReturnType<typeof setTimeout> | null = null;
/** Giữ key khi lỗi mạng/timeout để retry cùng một intent; mỗi lần submit mới = UUID mới */
const pendingTransferIdemKey = ref<string | null>(null);

const confirmModal = ref<InstanceType<typeof ConfirmModal> | null>(null);

const form = reactive({
  toAccountNumber: "",
  amount: "10000",
  reason: "Chuyển khoản nội bộ",
});

let isMounted = false;

const sourceAccountNumber = computed(
  () => myAccount.value?.accountNumber ?? "",
);
const totalBalance = computed(() => new Decimal(myAccount.value?.balance || 0));
const heldAmount = computed(() => new Decimal(myAccount.value?.totalHolds || 0));
const availableBalance = computed(() => {
  const avail = myAccount.value?.availableBalance;
  if (avail != null) return new Decimal(avail);
  return totalBalance.value.minus(heldAmount.value);
});
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
  if (new Decimal(form.amount || 0).lte(0) || new Decimal(form.amount || 0).gt(availableBalance.value)) return false;
  if (isSelfTransfer.value) return false;
  return Boolean(form.reason.trim());
});

async function loadAccount() {
  try {
    const resp = await run("Lấy tài khoản", () => getMyAccountApi(), {
      silent: true,
    });
    if (isMounted) myAccount.value = resp.data as AccountResponse;
  } catch {
    if (isMounted) myAccount.value = null;
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
    if (isMounted) {
      lookup.value = resp.data as AccountLookupResponse;
      lookupState.value = "found";
    }
  } catch {
    if (isMounted) {
      lookup.value = null;
      lookupState.value = "not-found";
    }
  }
}

watch(
  () => form.toAccountNumber,
  (value) => {
    if (lookupTimer) clearTimeout(lookupTimer);
    lookupTimer = setTimeout(() => runLookup(value), 1000);
  },
);

function isAmbiguousTransferFailure(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false;
  if (!error.response) return true;
  return error.code === "ECONNABORTED";
}

function confirmTransfer() {
  if (!canSubmit.value) return;
  confirmModal.value?.open();
}

async function submitTransfer() {
  if (!canSubmit.value) return;
  const idemKey = pendingTransferIdemKey.value ?? crypto.randomUUID();
  try {
    await run(
      "Chuyển khoản nội bộ",
      () =>
        transferApi(
          {
            toAccountNumber: form.toAccountNumber.trim(),
            amount: Number(form.amount),
            reason: form.reason.trim(),
          },
          idemKey,
        ),
      { successToast: "Chuyển khoản thành công." },
    );
    pendingTransferIdemKey.value = null;
    await loadAccount();
    if (isMounted) {
      form.toAccountNumber = "";
      lookup.value = null;
      lookupState.value = "idle";
      form.amount = "10000";
    }
  } catch (error) {
    pendingTransferIdemKey.value = isAmbiguousTransferFailure(error)
      ? idemKey
      : null;
    throw error;
  }
}

onMounted(() => {
  isMounted = true;
  loadAccount();
});

onUnmounted(() => {
  isMounted = false;
  if (lookupTimer) clearTimeout(lookupTimer);
});
</script>

<template>
  <div class="stack">
    <ConfirmModal 
      ref="confirmModal" 
      title="Xác nhận chuyển khoản" 
      @confirm="submitTransfer"
    >
      <div v-if="lookup">
        <p>Người nhận: <strong>{{ lookup.displayName }}</strong> ({{ form.toAccountNumber }})</p>
        <p>Số tiền: <strong>{{ new Intl.NumberFormat('vi-VN').format(Number(form.amount)) }} {{ lookup.currency }}</strong></p>
        <p>Lời nhắn: {{ form.reason }}</p>
      </div>
    </ConfirmModal>
    <section class="card">
      <h2>Chuyển khoản nội bộ</h2>
      <p v-if="myAccount" class="hint">
        Tài khoản nguồn: <span class="kbd">{{ sourceAccountNumber }}</span>
        · Số dư khả dụng:
        <strong>{{ availableBalance.toNumber().toLocaleString('vi-VN') }} {{ myAccount.currency }}</strong>
        <span v-if="heldAmount.gt(0)" class="muted">
          (tổng số dư {{ totalBalance.toNumber().toLocaleString('vi-VN') }}, đang giữ {{ heldAmount.toNumber().toLocaleString('vi-VN') }} do thanh toán chờ xử lý)
        </span>
      </p>
      <p v-else class="hint">
        Chưa có tài khoản nguồn. Vui lòng hoàn tất KYC và kích hoạt tài khoản.
      </p>
    </section>

    <section class="card" v-if="myAccount">
      <fieldset :disabled="running" style="border: none; padding: 0; margin: 0;">
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
      <div class="hint danger" v-if="myAccount && new Decimal(form.amount || 0).gt(availableBalance)">
        Số tiền vượt quá số dư khả dụng<span v-if="heldAmount.gt(0)">
          ({{ heldAmount.toNumber().toLocaleString('vi-VN') }} {{ myAccount.currency }} đang chờ xử lý thanh toán)</span>.
      </div>

      <div class="actions">
        <button :disabled="running || !canSubmit" @click="confirmTransfer">
          Chuyển khoản
        </button>
      </div>
      </fieldset>
    </section>
  </div>
</template>
