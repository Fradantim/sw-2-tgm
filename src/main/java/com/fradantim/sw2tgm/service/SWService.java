package com.fradantim.sw2tgm.service;

import java.net.HttpCookie;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fradantim.sw2tgm.dto.sw.AffiliateSessionResponse;
import com.fradantim.sw2tgm.dto.sw.LoginResponse;
import com.fradantim.sw2tgm.dto.sw.WorkoutsResponse;
import com.fradantim.sw2tgm.dto.sw.WorkoutsResponse.WorkoutsResponseData;

@Service
public class SWService {

	private final static Logger logger = LoggerFactory.getLogger(SWService.class);
	private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Value("${sw.login.username}")
	private String loginUsername;
	@Value("${sw.login.password}")
	private String loginPassword;

	@Value("${sw.page.login}")
	private String loginPage;
	@Value("${sw.page.logout}")
	private String logoutPage;
	@Value("${sw.page.calendar}")
	private String calendarPage;

	@Value("${sw.api.login}")
	private String loginApi;
	@Value("${sw.api.workouts}")
	private String workoutsApi;
	@Value("${sw.api.affiliate-session}")
	private String affiliateSessionApi;	

	private final RestClient restClient;

	private SWService(RestClient restClient) {
		this.restClient = restClient;
	}

	public Map<LocalDate, List<WorkoutsResponseData>> getWeekItemsByTrack(String track, LocalDate start) {
		return getWeekItemsByTrack(Collections.singletonList(track), start).get(track);
	}
	
	public Map<String, Map<LocalDate, List<WorkoutsResponseData>>> getWeekItemsByTrack(List<String> tracks,
			LocalDate start) {
		Map<String, HttpCookie> cookies = new LinkedHashMap<>();
		login(cookies);

		Map<String, Map<LocalDate, List<WorkoutsResponseData>>> tracksWeek = tracks.stream()
				.collect(Collectors.toMap(Function.identity(), track -> getWeek(cookies, start, track)));

		try {
			logout(cookies);
		} catch (Exception e) {
			logger.error("Error at logout {}", e.getLocalizedMessage());
		}

		return tracksWeek;
	}

	private void loadCookie(Map<String, HttpCookie> cookies, HttpCookie cookie) {
		cookies.put(cookie.getName(), cookie);
	}

	private HttpCookie buildSwSessionCookie(Map<String, HttpCookie> cookies) {
		String content = """
				{"csrfSecret":"AOxLG4VFdmtmTfgSHvt0-Z6r","token":null}""".trim(); // TODO random generate?
		return HttpCookie.parse("_sw_session=" + Base64.getEncoder().encodeToString(content.getBytes())).getFirst();
	}

	private LoginResponse login(Map<String, HttpCookie> cookies) {
		logger.info("login");
		loadCookie(cookies, buildSwSessionCookie(cookies));
		ResponseEntity<String> pageResponse = restClient.get().uri(loginPage)
				.headers(headers -> addCookies(cookies, headers)).retrieve().toEntity(String.class);
		loadCookies(cookies, pageResponse);
		Optional<String> csrfToken = findCSRFTokenFromPage(pageResponse.getBody());

		// {"username":"asdasd@gmail.com","password":"xxxxxxxx","_csrf":"EZrmZ3iC-SGLdOjtm8KUkZSbapZKiQZYmQVo","_method":"post"}
		Map<String, Object> loginRequestBody = new HashMap<>(
				Map.of("username", loginUsername, "password", loginPassword, "_method", "post"));
		csrfToken.ifPresent(csrf -> loginRequestBody.put("_csrf", csrf));

		ResponseEntity<LoginResponse> apiResponse = restClient.post().uri(loginApi)
				.headers(headers -> addCookies(cookies, headers)).body(loginRequestBody).retrieve()
				.toEntity(LoginResponse.class);
		loadCookies(cookies, apiResponse);
		Optional.ofNullable(apiResponse.getBody()).map(LoginResponse::data).map(LoginResponse.Data::sessionToken)
				.ifPresentOrElse(s -> logger.info("got session token"), () -> logger.warn("got no session token"));
		return apiResponse.getBody();
	}

	private void logout(Map<String, HttpCookie> cookies) {
		logger.info("logout");
		restClient.get().uri(logoutPage).headers(headers -> addCookies(cookies, headers)).retrieve()
				.toEntity(String.class);
	}

	private void addCookies(Map<String, HttpCookie> cookies, HttpHeaders headers) {
		if (!cookies.isEmpty())
			headers.add(HttpHeaders.COOKIE,
					cookies.values().stream().map(Object::toString).collect(Collectors.joining("; ")));
	}

	private void loadCookies(Map<String, HttpCookie> cookies, ResponseEntity<? extends Object> response) {
		Optional.ofNullable(response.getHeaders().get(HttpHeaders.SET_COOKIE))
				.ifPresent(cookieHeaders -> cookieHeaders.stream().map(HttpCookie::parse)
						.forEach(httpCookie -> httpCookie.forEach(cookie -> loadCookie(cookies, cookie))));
	}

	/**
	 * Searches for the
	 * <code>var CSRF = 'KY7OZ4ZR-zrq5wpyMhtNIaE3EkC8SmP9Qyfg';</code> in the page
	 */
	private Optional<String> findCSRFTokenFromPage(String pageContent) {
		try {
			int start = pageContent.indexOf("var CSRF = '");
			if (start > 0) {
				start += 12; // "var CSRF = '", 12 chars
				return Optional.of(pageContent.substring(start, start + 36));
			}
		} catch (Exception e) {
			logger.error("Error retrieving csrf token from page", e);
		}
		logger.warn("No csrf token retrieved from page");
		return Optional.empty();
	}

	private Map<LocalDate, List<WorkoutsResponseData>> getWeek(Map<String, HttpCookie> cookies, LocalDate start,
			String track) {
		logger.info("Get week {} {}", track, start);
		String week = start.format(dateTimeFormatter);

		ResponseEntity<String> pageResponse = restClient.get()
				.uri(calendarPage + "?week={week}&track={track}", week, track)
				.headers(headers -> addCookies(cookies, headers)).retrieve().toEntity(String.class);
		loadCookies(cookies, pageResponse);

		// https://app.sugarwod.com/api/workouts?week=20250907&track=Mayhem+Compete+%2F+Espa%C3%B1ol+(Open+%26+QF)
		// &trackId=nSN6xsHrS5&_csrf=oGLV2dEa-tyxSJLEWAxzvNq6xFjQIFs31oa0&_=1757379713562
		ResponseEntity<WorkoutsResponse> apiResponse = restClient.get()
				.uri(workoutsApi + "?week={week}&track={track}", week, track)
				.headers(headers -> addCookies(cookies, headers)).retrieve().toEntity(WorkoutsResponse.class);
		loadCookies(cookies, apiResponse);

		Map<LocalDate, List<WorkoutsResponseData>> itemsByDay = new TreeMap<>();
		apiResponse.getBody().data()
				.forEach(item -> itemsByDay.computeIfAbsent(
						LocalDate.parse(String.valueOf(item.scheduledDateInteger()), dateTimeFormatter),
						date -> new ArrayList<>()).add(item));

		return itemsByDay;
	}
	
	public Set<String> getTracks() {
		Map<String, HttpCookie> cookies = new LinkedHashMap<>();
		String affiliateId = login(cookies).data().affiliate().objectId();
		logger.info("Get session tracks");
		ResponseEntity<AffiliateSessionResponse> apiResponse = restClient.get().uri(affiliateSessionApi, affiliateId)
				.headers(headers -> addCookies(cookies, headers)).retrieve().toEntity(AffiliateSessionResponse.class);
		loadCookies(cookies, apiResponse);
		
		Set<String> tracks = Optional.ofNullable(apiResponse.getBody())
		.map(AffiliateSessionResponse::data)
		.map(AffiliateSessionResponse.Data::athlete)
		.map(AffiliateSessionResponse.Athlete::subscriptions)
		.stream()
		.flatMap(List::stream)
		.filter(Objects::nonNull)
		.map(AffiliateSessionResponse.Subscription::marketplaceProduct)
		.filter(Objects::nonNull)
		.map(AffiliateSessionResponse.MarketplaceProduct::publishingTracks)
		.filter(Objects::nonNull)
		.flatMap(List::stream)
		.map(AffiliateSessionResponse.PublishingTrack::key).collect(Collectors.toSet());

		try {
			logout(cookies);
		} catch (Exception e) {
			logger.error("Error at logout {}", e.getLocalizedMessage());
		}

		return tracks;
	}
}
