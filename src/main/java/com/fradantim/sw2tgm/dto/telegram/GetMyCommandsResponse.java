package com.fradantim.sw2tgm.dto.telegram;

import java.util.List;

public record GetMyCommandsResponse(Boolean ok, List<Command> result) {
	public record Command(String command, String description) {
	}
}
