package com.payments.orch.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.payments.orch.dto.PosRewardRequest;
import com.payments.orch.dto.RewardRedeemRequest;
import com.payments.orch.dto.RewardRedeemResponse;

@FeignClient(name = "pos-reward", url = "${pos.service.url}")
public interface PosRewardClient {

    @PostMapping("/api/rewards/single/lock")
    Map<String, Object> processReward(@RequestBody PosRewardRequest request);

    @GetMapping("/api/rewards/points/{customerId}")
    Map<String, Object> getPoints(@PathVariable("customerId") String customerId);

    @PostMapping("/api/rewards/redeem")
    RewardRedeemResponse redeemPoints(@RequestBody RewardRedeemRequest request);

    @PostMapping("/api/rewards/redeem/compensate")
    RewardRedeemResponse compensateRedeem(@RequestBody RewardRedeemRequest request);
}
