package com.fradantim.sw2tgm.dto.sw;

import java.util.List;
import java.util.Map;

public record WorkoutsResponse(List<WorkoutsResponseData> data) {
	public record  WorkoutsResponseData(String type, int scheduledDateInteger, String title, String description, String athletesNotes, String mediaProvider, Map<String, Object> mediaMetadata) {
	}
}