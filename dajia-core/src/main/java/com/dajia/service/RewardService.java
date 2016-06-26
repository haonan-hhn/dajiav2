package com.dajia.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dajia.domain.Product;
import com.dajia.domain.User;
import com.dajia.domain.UserOrder;
import com.dajia.domain.UserReward;
import com.dajia.repository.ProductRepo;
import com.dajia.repository.UserOrderRepo;
import com.dajia.repository.UserRepo;
import com.dajia.repository.UserRewardRepo;
import com.dajia.util.CommonUtils;
import com.dajia.vo.LoginUserVO;
import com.pingplusplus.exception.PingppException;

@Service
public class RewardService {
	Logger logger = LoggerFactory.getLogger(RewardService.class);

	@Autowired
	private UserRewardRepo rewardRepo;

	@Autowired
	private UserOrderRepo orderRepo;

	@Autowired
	private ProductRepo productRepo;

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private ApiService apiService;

	public Map<Long, LoginUserVO> getRewardSrcUsers(Long orderId) {
		Map<Long, LoginUserVO> rdUserMap = new HashMap<Long, LoginUserVO>();
		List<UserReward> rewardList = rewardRepo.findByRefOrderIdAndRewardStatus(orderId,
				CommonUtils.RewardStatus.PENDING.getKey());
		for (UserReward userReward : rewardList) {
			if (null != userReward.orderUserId) {
				User user = userRepo.findByUserId(userReward.orderUserId);
				LoginUserVO userVO = new LoginUserVO();
				userVO.userName = user.userName;
				userVO.headImgUrl = user.headImgUrl;
				rdUserMap.put(user.userId, userVO);
			}
		}
		return rdUserMap;
	}

	public void createReward(UserOrder order, Product product) {
		List<UserReward> rewardList = rewardRepo.findByOrderUserIdAndProductIdAndRewardStatus(order.userId,
				product.productId, CommonUtils.RewardStatus.PENDING.getKey());
		if (null == rewardList || rewardList.isEmpty()) {
			UserReward ur = new UserReward();
			ur.orderId = order.orderId;
			ur.productId = order.productId;
			ur.refUserId = order.refUserId;
			ur.refOrderId = order.refOrderId;
			ur.orderUserId = order.userId;
			ur.rewardRatio = 10; // ignore quantity
			ur.expiredDate = product.expiredDate;
			if (null != ur.refOrderId && ur.refOrderId.longValue() != 0L) {
				ur.rewardStatus = CommonUtils.RewardStatus.PENDING.getKey();
			} else {
				ur.rewardStatus = CommonUtils.RewardStatus.INVALID.getKey();
			}
			Calendar c = Calendar.getInstance();
			c.setTime(ur.expiredDate);
			c.add(Calendar.DATE, CommonUtils.reward_delay_days);
			ur.rewardDate = c.getTime();
			rewardRepo.save(ur);
		}
	}

	public BigDecimal calculateRewards(Long orderId, Product product) {
		BigDecimal rewardValue = new BigDecimal(0);
		List<UserReward> rewardList = rewardRepo.findByRefOrderIdAndRewardStatus(orderId,
				CommonUtils.RewardStatus.PENDING.getKey());
		if (null != rewardList && !rewardList.isEmpty()) {
			for (UserReward userReward : rewardList) {
				rewardValue = rewardValue.add(product.currentPrice.multiply(new BigDecimal(
						userReward.rewardRatio * 0.01)));
			}
		}
		return rewardValue;
	}

	public List<UserReward> getPendingPayRewards() {
		return rewardRepo.findByRewardDateBeforeAndRewardStatusAndIsActive(new Date(),
				CommonUtils.RewardStatus.PENDING.getKey(), CommonUtils.ActiveStatus.YES.toString());
	}

	public void payRewards() {
		List<UserReward> rewards = this.getPendingPayRewards();
		Map<Long, List<UserReward>> userProductMap = new HashMap<Long, List<UserReward>>();
		for (UserReward userReward : rewards) {
			Long key = userReward.refOrderId;
			List<UserReward> rwList = new ArrayList<UserReward>();
			if (userProductMap.containsKey(key)) {
				rwList = userProductMap.get(key);
				rwList.add(userReward);
				userProductMap.put(key, rwList);
			} else {
				rwList.add(userReward);
				userProductMap.put(key, rwList);
			}
		}
		Iterator<Map.Entry<Long, List<UserReward>>> iter = userProductMap.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Long, List<UserReward>> entry = (Map.Entry<Long, List<UserReward>>) iter.next();
			Long orderId = entry.getKey();
			List<UserReward> rwList = entry.getValue();
			Integer ratioSum = 0;
			for (UserReward rw : rwList) {
				ratioSum = ratioSum + rw.rewardRatio;
				if (ratioSum > 100) {
					ratioSum = 100;
				}
			}
			Long productId = rwList.get(0).productId;
			BigDecimal rewardValue = this.calculateSingleReward(productId, ratioSum);
			UserOrder userOrder = orderRepo.findByOrderIdAndOrderStatusAndIsActive(orderId,
					CommonUtils.OrderStatus.DELEVRIED.getKey(), CommonUtils.ActiveStatus.YES.toString());
			if (null != userOrder && null != userOrder.paymentId && !userOrder.paymentId.isEmpty()) {
				try {
					apiService.applyRefund(userOrder.paymentId, rewardValue, CommonUtils.refund_type_reward);
					logger.info("order " + userOrder.trackingId + " reward applied for " + rewardValue.doubleValue());
				} catch (PingppException e) {
					logger.error(e.getMessage(), e);
					for (UserReward rw : rwList) {
						rw.rewardStatus = CommonUtils.RewardStatus.ERROR.getKey();
						rewardRepo.save(rw);
					}
				}
				// mark reward finish logic - performance to be improved
				for (UserReward rw : rwList) {
					rw.rewardStatus = CommonUtils.RewardStatus.COMPLETED.getKey();
					rewardRepo.save(rw);
				}
			}
		}
	}

	private BigDecimal calculateSingleReward(Long productId, Integer rewardRatio) {
		BigDecimal rewardValue = new BigDecimal(0);
		Product product = productRepo.findOne(productId);
		rewardValue = rewardValue.add(product.currentPrice.multiply(new BigDecimal(rewardRatio * 0.01)));
		return rewardValue;
	}
}
