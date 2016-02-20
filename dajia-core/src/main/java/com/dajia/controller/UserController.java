package com.dajia.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.dajia.domain.Location;
import com.dajia.domain.User;
import com.dajia.domain.UserContact;
import com.dajia.domain.UserFavourite;
import com.dajia.repository.LocationRepo;
import com.dajia.repository.UserRepo;
import com.dajia.service.FavouriteService;
import com.dajia.service.UserService;
import com.dajia.util.CommonUtils;
import com.dajia.util.UserUtils;
import com.dajia.vo.LocationVO;
import com.dajia.vo.LoginUserVO;

@RestController
public class UserController extends BaseController {
	Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private LocationRepo locationRepo;

	@Autowired
	private UserService userService;

	@Autowired
	private FavouriteService favouriteService;

	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public @ResponseBody LoginUserVO userLogin(@RequestBody LoginUserVO loginUser, HttpServletRequest request) {
		User user = userService.userLogin(loginUser.mobile, loginUser.password, false);
		loginUser = UserUtils.addLoginSession(loginUser, user, request);
		return loginUser;
	}

	@RequestMapping(value = "/signup", method = RequestMethod.POST)
	public @ResponseBody LoginUserVO userSignup(@RequestBody LoginUserVO loginUser, HttpServletRequest request) {
		loginUser.loginIP = request.getRemoteAddr();
		loginUser.loginDate = new Date();

		User user = new User();
		UserUtils.copyUserProperties(loginUser, user);
		userService.userSignup(user);

		loginUser = UserUtils.addLoginSession(loginUser, user, request);
		return loginUser;
	}

	@RequestMapping("/user/loginuserinfo")
	public @ResponseBody LoginUserVO getSessionUser(HttpServletRequest request, HttpServletResponse response) {
		User user = this.getLoginUser(request, response, userRepo, true);
		LoginUserVO loginUser = new LoginUserVO();
		UserUtils.copyUserProperties(user, loginUser);
		// get default userContact
		if (null != user.userContacts && user.userContacts.size() > 0) {
			for (UserContact uc : user.userContacts) {
				if (uc.isDefault.equals("Y")) {
					UserContact userContactInfo = new UserContact();
					try {
						CommonUtils.copyProperties(uc, userContactInfo);
					} catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					loginUser.userContact = userContactInfo;
				}
			}
		}
		return loginUser;
	}

	@RequestMapping("/locations")
	public @ResponseBody List<LocationVO> getLocationMap() {
		List<Location> provinces = locationRepo.findByLocationTypeOrderByLocationKey(CommonUtils.LocationType.PROVINCE
				.toString());
		List<Location> cities = locationRepo.findByLocationTypeOrderByLocationKey(CommonUtils.LocationType.CITY
				.toString());
		List<Location> districts = locationRepo.findByLocationTypeOrderByLocationKey(CommonUtils.LocationType.AREA
				.toString());

		List<LocationVO> locationMap = new ArrayList<LocationVO>();
		for (Location province : provinces) {
			LocationVO pvo = new LocationVO();
			pvo.locationKey = province.locationKey;
			pvo.locationValue = province.locationValue;
			List<LocationVO> childrenCity = new ArrayList<LocationVO>();
			for (Location city : cities) {
				if (city.parentKey.equals(pvo.locationKey)) {
					LocationVO cvo = new LocationVO();
					cvo.locationKey = city.locationKey;
					cvo.locationValue = city.locationValue;
					List<LocationVO> childrenDis = new ArrayList<LocationVO>();
					for (Location district : districts) {
						if (district.parentKey.equals(cvo.locationKey)) {
							LocationVO dvo = new LocationVO();
							dvo.locationKey = district.locationKey;
							dvo.locationValue = district.locationValue;
							childrenDis.add(dvo);
						}
					}
					cvo.children = childrenDis;
					childrenCity.add(cvo);
				}
			}
			pvo.children = childrenCity;
			locationMap.add(pvo);
		}
		return locationMap;
	}

	@RequestMapping("/user/favourite/add/{pid}")
	public void addFavourite(@PathVariable("pid") Long pid, HttpServletRequest request, HttpServletResponse response) {
		UserFavourite favourite = new UserFavourite();
		User user = this.getLoginUser(request, response, userRepo, true);
		favourite.userId = user.userId;
		favourite.productId = pid;
		favouriteService.addFavourite(favourite);
	}

	@RequestMapping("/user/favourite/remove/{pid}")
	public void removeFavourite(@PathVariable("pid") Long pid, HttpServletRequest request, HttpServletResponse response) {
		User user = this.getLoginUser(request, response, userRepo, true);
		favouriteService.removeFavourite(user.userId, pid);
	}
}
