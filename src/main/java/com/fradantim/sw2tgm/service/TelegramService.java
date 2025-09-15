package com.fradantim.sw2tgm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fradantim.sw2tgm.dto.sw.WorkoutsResponse.WorkoutsResponseData;
import com.fradantim.sw2tgm.dto.telegram.GetMyCommandsResponse;
import com.fradantim.sw2tgm.dto.telegram.GetUpdatesResponse;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;

@Service
public class TelegramService {
	private final static Logger logger = LoggerFactory.getLogger(TelegramService.class);

	@Value("${telegram.message.max-length}")
	private Integer messageMaxLength;

	@Value("${telegram.url.send-message}")
	private String sendMessageUrl;

	@Value("${telegram.url.get-updates}")
	private String getUpdatesUrl;
	
	@Value("${telegram.url.get-me}")
	private String getMeUrl;
	
	@Value("${telegram.url.get-my-commands}")
	private String getMyCommandsUrl;
	
	@Value("${telegram.url.set-my-commands}")
	private String setMyCommandsUrl;
	
	@Value("${telegram.url.send-chat-action}")
	private String sendChatActionUrl;
	
	private String username = UUID.randomUUID().toString();

	private final RestClient restClient;

	private TelegramService(RestClient restClient) {
		this.restClient = restClient;
	}
	
	public String getUsername() {
		return username;
	}
	
	@PostConstruct
	private void onStartUp() {
		loadUsername();
	}
	
	@SuppressWarnings("rawtypes")
	private void loadUsername() {
		logger.info("Getting bot username");
		try {
			username = ((Map) restClient.get().uri(getMeUrl).retrieve().toEntity(Map.class).getBody().get("result"))
					.get("username").toString();
			logger.info("I'm {}", username);
		} catch (Exception e) {
			logger.error("Error on username retrieval", e);
		}
	}	

	public GetUpdatesResponse getUpdates(@Nullable Long offset) {
		return restClient.get().uri(getUpdatesUrl, String.valueOf(offset)).retrieve().body(GetUpdatesResponse.class);
	}	

	// https://core.telegram.org/bots/api#markdownv2-style
	private static final Pattern markdownV2Pattern = Pattern
			.compile("(-|_|\\*|\\]|\\[|\\(|\\)|>|~|`|#|\\+|=|\\||\\{|\\}|\\.|!)", Pattern.CASE_INSENSITIVE);

	public String escapeForMarkdownV2Pattern(String input) {
		return markdownV2Pattern.matcher(input.trim()).replaceAll("\\\\$1").trim();
	}

	private void append(List<StringBuilder> sbuilders, String o) {
		if (sbuilders.getLast().length() + o.length() > messageMaxLength) {
			sbuilders.add(new StringBuilder());
		}
		sbuilders.getLast().append(o);
	}

	/** Concatenates and parses to Telegram MarkdownV2 style */
	@SuppressWarnings("unchecked")
	public List<String> buildMessages(String track, LocalDate date, List<WorkoutsResponseData> items) {
		List<StringBuilder> sbuilders = new ArrayList<>();
		String titleMessage = "*" + escapeForMarkdownV2Pattern(track.trim() + " " + date) + "*";
		sbuilders.add(new StringBuilder("📅"));
		sbuilders.add(new StringBuilder());
		append(sbuilders, titleMessage);

		if (items == null || items.isEmpty()) {
			append(sbuilders, "\n");
			append(sbuilders, escapeForMarkdownV2Pattern("Nada aqui..."));
		} else {
			int count = 0;
			for (WorkoutsResponseData item : items) {
				count++;

				// title
				StringBuilder sb = new StringBuilder();
				sb.append("\n\n*\\[");
				sb.append(count);
				sb.append("/");
				sb.append(items.size());
				sb.append("\\] ");
				sb.append(escapeForMarkdownV2Pattern(item.title()));
				sb.append("*");

				append(sbuilders, sb.toString());

				if ("video".equals(item.type()) && item.mediaMetadata() != null
						&& "youtube".equals(item.mediaProvider())) {
					Optional.ofNullable(item.mediaMetadata().get("raw"))
							.map(raw -> Map.class.isAssignableFrom(raw.getClass()) ? (Map<String, Object>) raw : null)
							.map(raw -> raw.get("player"))
							.map(player -> Map.class.isAssignableFrom(player.getClass()) ? (Map<String, Object>) player
									: null)
							.map(player -> player.get("embedHtml")).map(String::valueOf).flatMap(this::getLink)
							.ifPresent(link -> {
								append(sbuilders, "\n**>Video: "); // expandable block quotation
								append(sbuilders, escapeForMarkdownV2Pattern(link));
								append(sbuilders, "||"); // end expandable block quotation
							});
				}

				// description
				if (StringUtils.hasText(item.description())) {
					sb = new StringBuilder("\n");

					sb.append("**>"); // expandable block quotation

					String description = escapeForMarkdownV2Pattern(item.description());
					while (description.endsWith("\n")) {
						description = description.substring(0, description.length() - 2);
					}
					sb.append(description.replaceAll("\n", "\n>"));
					sb.append("||"); // end expandable block quotation
					append(sbuilders, sb.toString());
				}

				// athlete notes
				if (StringUtils.hasText(item.athletesNotes().trim())) {
					sb = new StringBuilder("\n");
					if (item.athletesNotes().length() > 2048) {
						sb.append("\\(existen notas para el atleta, pero es demasiado para ingresarlo aqui\\)");
					} else {
						sb.append("**>Notas para el atleta:\n>"); // expandable block quotation
						String athleteNotes = escapeForMarkdownV2Pattern(item.athletesNotes());
						while (athleteNotes.endsWith("\n")) {
							athleteNotes = athleteNotes.substring(0, athleteNotes.length() - 2);
						}
						sb.append(athleteNotes.replaceAll("\n", "\n>"));
						sb.append("||"); // end expandable block quotation
					}
					append(sbuilders, sb.toString());
				}

				logger.info("Building msg {} {} {}/{}", track, date, count, items.size());
			}
		}

		return sbuilders.stream().map(StringBuilder::toString).toList();
	}

	/**
	 * Extracts src link from
	 * <code><iframe ... src="https://www.youtube.com/embed/OAmeukKPdWc" ...></iframe></code>
	 */
	private Optional<String> getLink(String iframeHtml) {
		try {
			String link = iframeHtml.substring(iframeHtml.indexOf("src=\"") + 5).split(" ")[0];
			link = link.substring(0, link.length() - 1);
			return Optional.of(link);
		} catch (Exception e) {
		}
		return Optional.empty();
	}

	public void sendMessage(String chatId, String text) {
		sendMessage(chatId, text, null);
	}
	
	public void sendMessage(String chatId, String text, @Nullable String replyMessageId) {
		Map<String, Object> reqBody = new HashMap<>(Map.of("chat_id", chatId, "text", text, "link_preview_options",
				Map.of("is_disabled", true), "parse_mode", "MarkdownV2"));
		if (replyMessageId != null) {
			reqBody.put("reply_parameters", Map.of("message_id", replyMessageId));
		}
		try {
			// try nice looking first
			doSendMessage(reqBody);
		} catch (RestClientException e) {
			logger.error(e.getLocalizedMessage());
			reqBody.remove("parse_mode");
			doSendMessage(reqBody);
		}
	}
	
	private void doSendMessage(Object reqBody) {
		try {
			restClient.post().uri(sendMessageUrl).body(reqBody).retrieve().toBodilessEntity();
		} catch (HttpStatusCodeException e) {
			logger.warn("{}", e.getLocalizedMessage());
			if(e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
					&& e.getResponseBodyAs(Map.class).get("parameters") instanceof Map<?,?> params 
					&& params.get("retry_afer") instanceof Number retryAfter) {
				try {
					Thread.sleep(retryAfter.longValue() * 2 * 1000);
					// last try
					restClient.post().uri(sendMessageUrl).body(reqBody).retrieve().toBodilessEntity();
				} catch (InterruptedException e1) {
					throw new RuntimeException(e);
				}
			} else {
				throw e;
			}
		}		
	}

	public void sendMessages(String chatId, List<String> texts) {
		int count = 0;
		for (String text : texts) {
			logger.info("Sending to {} {}/{} {}chars", chatId, ++count, texts.size(), text.length());
			sendMessage(chatId, text);
		}
	}
	
	public void sendTyping(String chatId) {
		restClient.post().uri(sendChatActionUrl).body(Map.of("chat_id", chatId, "action", "typing")).retrieve().toBodilessEntity();
	}
	
	public void setCommands(List<GetMyCommandsResponse.Command> commands) {
		restClient.post().uri(setMyCommandsUrl).body(Map.of("commands", commands)).retrieve().toBodilessEntity();	
	}
	
	public List<GetMyCommandsResponse.Command> getCommands() {
		return restClient.get().uri(getMyCommandsUrl).retrieve().body(GetMyCommandsResponse.class).result();	
	}
}