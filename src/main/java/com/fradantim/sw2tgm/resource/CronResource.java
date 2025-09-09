package com.fradantim.sw2tgm.resource;

import java.time.LocalDate;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fradantim.sw2tgm.service.SWService;
import com.fradantim.sw2tgm.service.TelegramService;

@RestController
public class CronResource {
	@Value("${sw.tracks}")
	private List<String> tracks;

	@Value("${telegram.chat-id.group.daily}")
	private String dailyChat;
	@Value("${telegram.chat-id.group.weekly}")
	private String weeklyChat;
	@Value("${telegram.chat-id.errors}")
	private String errorsChat;

	private final TelegramService telegramService;
	private final SWService swService;

	public CronResource(TelegramService telegramService, SWService swService) {
		this.telegramService = telegramService;
		this.swService = swService;
	}

	@Scheduled(cron = "${cron.send-daily}")
	public void sendDaily() {
		try {
			sendNextDay();			
		} catch (Exception e) {
			telegramService.sendMessage(errorsChat, e.getMessage());
			telegramService.sendMessage(errorsChat, ExceptionUtils.getStackTrace(e));
		}		
	}

	@Scheduled(cron = "${cron.send-weekly}")
	public void sendWeekly() {
		try {
			sendNextWeek();			
		} catch (Exception e) {
			telegramService.sendMessage(errorsChat, e.getMessage());
			telegramService.sendMessage(errorsChat, ExceptionUtils.getStackTrace(e));
		}
	}

	@PostMapping("/send/{date}")
	public void sendDay(@PathVariable LocalDate date) {
		swService.getWeekItemsByTrack(tracks, date).forEach((track, days) -> {
			List<String> messages = telegramService.buildMessages(track, date, days.get(date));
			telegramService.sendMessages(dailyChat, messages);
		});
	}

	@PostMapping("/send/tomorrow")
	public void sendNextDay() {
		sendDay(LocalDate.now().plusDays(1));
	}

	@PostMapping("/send/week/{start}")
	public void sendWeek(@PathVariable LocalDate start) {
		swService.getWeekItemsByTrack(tracks, start).forEach((track, days) -> days.forEach((day, items) -> {
			List<String> messages = telegramService.buildMessages(track, day, days.get(day));
			telegramService.sendMessages(weeklyChat, messages);
		}));
	}

	@PostMapping("/send/week/next")
	public void sendNextWeek() {
		sendWeek(LocalDate.now().plusDays(1));
	}
}