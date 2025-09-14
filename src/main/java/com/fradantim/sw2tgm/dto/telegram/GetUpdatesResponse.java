package com.fradantim.sw2tgm.dto.telegram;

import java.util.List;

public record GetUpdatesResponse(List<Update> result) {
	public record Update(Long update_id, Message message) { }
	public record Message(String message_id, From from, Chat chat, String text) { }
	public record From(String id, String first_name) { }
	public record Chat(String id, String first_name, String type, String title) { }
}