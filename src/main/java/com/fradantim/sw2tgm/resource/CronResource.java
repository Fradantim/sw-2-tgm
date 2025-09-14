package com.fradantim.sw2tgm.resource;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fradantim.sw2tgm.dto.telegram.GetMyCommandsResponse;
import com.fradantim.sw2tgm.dto.telegram.GetUpdatesResponse;
import com.fradantim.sw2tgm.service.SWService;
import com.fradantim.sw2tgm.service.TelegramService;

import jakarta.annotation.PostConstruct;

@RestController
public class CronResource {
	private static final Logger logger = LoggerFactory.getLogger(CronResource.class);
	
	@Value("${sw.tracks.daily}")
	private List<String> dailyTracks;
	
	@Value("${sw.tracks.weekly}")
	private List<String> weeklyTracks;

	@Value("${telegram.chat-id.group.daily}")
	private String dailyChat;
	@Value("${telegram.chat-id.group.weekly}")
	private String weeklyChat;
	@Value("${telegram.chat-id.errors}")
	private String errorsChat;
	
	private AtomicLong lastOffset = new AtomicLong(0L); 
	
	private Map<String, Consumer<GetUpdatesResponse.Update>> messageListeners = Map.of("/tracks", this::sendTracks, "/today", this::today);

	private final TelegramService telegramService;
	private final SWService swService;

	public CronResource(TelegramService telegramService, SWService swService) {
		this.telegramService = telegramService;
		this.swService = swService;
	}
	
	@PostConstruct
	private void onStartUp() {
		setCommands();
	}
	
	private void setCommands() {
		logger.info("Getting bot commands");
		try {
			Set<String> commands = telegramService.getCommands().stream().map(GetMyCommandsResponse.Command::command)
					.map(s -> "/" + s).collect(Collectors.toSet());
			if (messageListeners.keySet().stream().anyMatch(k -> !commands.contains(k))) {
				logger.info("Setting bot commands");
				telegramService.setCommands(
						messageListeners.keySet().stream().map(k -> new GetMyCommandsResponse.Command(k, k)).toList());
			}
		} catch (Exception e) {
			logger.error("Error on commands retrieval / update", e);
		}
	}
	
	@Scheduled(cron = "${cron.updates}")
	public void getUpdates() {
		try {
			logger.info("Getting messages with offset {}", lastOffset.get());
			telegramService.getUpdates(lastOffset.get()).result().forEach(update -> {
				logger.info("Processing message with offset {}", update.update_id());
				if(lastOffset.get() <= update.update_id()) {
					lastOffset.set(update.update_id() + 1); // always advance
				}
				String text = update.message().text().replaceAll("@"+telegramService.getUsername(), "").split(" ")[0];
				Optional.ofNullable(messageListeners.get(text)).ifPresent(c -> {
					telegramService.sendMessage(update.message().chat().id(), "On it 💪", update.message().message_id());
					telegramService.sendTyping(update.message().chat().id());
					c.accept(update);
					telegramService.sendMessage(update.message().chat().id(), "Done 💪", update.message().message_id());
				});
			});
		} catch (Exception e) {
			telegramService.sendMessage(errorsChat, e.getMessage());
			telegramService.sendMessage(errorsChat, ExceptionUtils.getStackTrace(e));
		}
	}

	@Scheduled(cron = "${cron.send-daily}")
	public void sendDaily() {
		try {
			sendAllNextDayTracks(dailyChat);
		} catch (Exception e) {
			telegramService.sendMessage(errorsChat, e.getMessage());
			telegramService.sendMessage(errorsChat, ExceptionUtils.getStackTrace(e));
		}
	}

	@Scheduled(cron = "${cron.send-weekly}")
	public void sendWeekly() {
		try {
			sendNextWeek(weeklyChat);
		} catch (Exception e) {
			telegramService.sendMessage(errorsChat, e.getMessage());
			telegramService.sendMessage(errorsChat, ExceptionUtils.getStackTrace(e));
		}
	}
	
	@GetMapping("/sw/tracks")
	public Set<String> getTracks() {
		return new TreeSet<>(swService.getTracks());
	}

	@PostMapping("/send/{date}")
	public void sendAllDayTracks(@PathVariable LocalDate date, @RequestParam(value= "chatId", defaultValue="${telegram.chat-id.group.daily}") String chatId) {
		swService.getWeekItemsByTrack(dailyTracks, date).forEach((track, days) -> {
			List<String> messages = telegramService.buildMessages(track, date, days.get(date));
			telegramService.sendMessages(chatId, messages);
		});
		telegramService.sendMessage(chatId, "🏋️");
	}
	
	@PostMapping("/send/{date}/{track}")
	public void sendDay(@PathVariable LocalDate date, @PathVariable String track, @RequestParam(value= "chatId", defaultValue="${telegram.chat-id.group.daily}") String chatId) {
		swService.getWeekItemsByTrack(Collections.singletonList(track), date).forEach((retrievedTrack, days) -> {
			List<String> messages = telegramService.buildMessages(retrievedTrack, date, days.get(date));
			telegramService.sendMessages(chatId, messages);
		});
		telegramService.sendMessage(chatId, "🏋️");
	}
	
	

	@PostMapping("/send/tomorrow")
	public void sendAllNextDayTracks(@RequestParam(value= "chatId", defaultValue="${telegram.chat-id.group.daily}") String chatId) {
		sendAllDayTracks(LocalDate.now().plusDays(1), chatId);
	}

	@PostMapping("/send/week/{start}")
	public void sendWeek(@PathVariable LocalDate start, @RequestParam(value= "chatId", defaultValue="${telegram.chat-id.group.weekly}") String chatId) {
		swService.getWeekItemsByTrack(weeklyTracks, start).forEach((track, days) -> days.forEach((day, items) -> {
			List<String> messages = telegramService.buildMessages(track, day, days.get(day));
			telegramService.sendMessages(chatId, messages);
		}));
		telegramService.sendMessage(chatId, "🏋️");
	}

	@PostMapping("/send/week/next")
	public void sendNextWeek(@RequestParam(value= "chatId", defaultValue="${telegram.chat-id.group.weekly}") String chatId) {
		sendWeek(LocalDate.now().plusDays(1), chatId);
	}
	
	private void sendTracks(GetUpdatesResponse.Update update) {
		swService.getTracks().stream().map(telegramService::escapeForMarkdownV2Pattern).forEach(track -> telegramService.sendMessage( update.message().chat().id(), track));
	}
	
	// expected "/today TRACK
	private void today(GetUpdatesResponse.Update update) {
		String text = update.message().text().replaceAll("@" + telegramService.getUsername(), "");
		System.out.println();
		if (text.split(" ").length < 2) {
			// no args
			sendAllDayTracks(LocalDate.now(), update.message().chat().id());
		} else {
			String track = text.substring("/today ".length());
			sendDay(LocalDate.now(), track, update.message().chat().id());
		}
	}
}