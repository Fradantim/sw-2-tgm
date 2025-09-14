package com.fradantim.sw2tgm.dto.sw;

public record LoginResponse(Data data) {
	public record Data(String sessionToken, String objectId, Affiliate affiliate) { }
	public record Affiliate(String objectId) { }
}